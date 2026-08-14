-- CC-Aeroworks display source/input dispatcher.
--
-- Installed only on an embedded ComputerControlDesk (deskio + peripherals globals). It wraps the
-- CraftOS pull-event path so display controllers receive relevant events without occupying the
-- foreground shell or requiring one background process per display.

if rawget(_G, "__cc_aeroworks_display_runtime_installed") then return end

local deskio = rawget(_G, "deskio")
local peripherals = rawget(_G, "peripherals")
if type(deskio) ~= "table" or type(deskio.find) ~= "function" then return end
if type(peripherals) ~= "table" or type(peripherals.getDesks) ~= "function" then return end

_G.__cc_aeroworks_display_runtime_installed = true

local moduleCache = {}
local active = {}
local unpack = table.unpack or unpack

local function report(path, message)
    printError("display controller " .. tostring(path) .. ": " .. tostring(message))
end

local function loadController(path)
    if type(path) ~= "string" or path == "" then return nil end
    if moduleCache[path] ~= nil then
        return moduleCache[path] or nil
    end

    local chunk, loadError = loadfile(path)
    if not chunk then
        report(path, loadError)
        moduleCache[path] = false
        return nil
    end

    local ok, controller = pcall(chunk)
    if not ok then
        report(path, controller)
        moduleCache[path] = false
        return nil
    end
    if type(controller) ~= "table" then
        report(path, "must return a table")
        moduleCache[path] = false
        return nil
    end

    moduleCache[path] = controller
    return controller
end

local function call(path, controller, name, ...)
    local callback = controller and controller[name]
    if type(callback) ~= "function" then return end
    local ok, err = pcall(callback, ...)
    if not ok then report(path, err) end
end

local function findDesk(memberId)
    local ok, desks = pcall(peripherals.getDesks)
    if not ok or type(desks) ~= "table" then return nil end
    for _, desk in pairs(desks) do
        if type(desk) == "table" and type(desk.getInfo) == "function" then
            local infoOk, info = pcall(desk.getInfo)
            if infoOk and type(info) == "table" and tostring(info.id) == tostring(memberId) then
                return desk
            end
        end
    end
    return nil
end

local function contextFor(object)
    return {
        id = object.id,
        memberId = object.memberId,
        memberIndex = object.memberIndex,
        socket = object.socket,
        socketName = object.socketName,
        moduleId = object.moduleId,
        binding = object.binding,
        desk = findDesk(object.memberId),
    }
end

local function refresh()
    local ok, displays = pcall(deskio.find, "display")
    if not ok or type(displays) ~= "table" then return end

    local seen = {}
    for _, object in pairs(displays) do
        if type(object) == "table" and type(object.id) == "string" then
            seen[object.id] = true
            local binding = type(object.binding) == "table" and object.binding or {}
            local content = type(binding.content) == "table" and binding.content or {}
            local input = type(binding.input) == "table" and binding.input or {}
            local sourcePath = content.type == "script_source" and content.path or nil
            local inputPath = input.type == "lua_handler" and input.path or nil
            local previous = active[object.id]
            local changed = previous == nil
                or previous.sourcePath ~= sourcePath
                or previous.inputPath ~= inputPath
                or previous.memberId ~= object.memberId
                or previous.socket ~= object.socket

            if changed then
                if previous and previous.sourceController then
                    call(previous.sourcePath, previous.sourceController, "onStop", previous.context)
                end

                local context = contextFor(object)
                local sourceController = loadController(sourcePath)
                local inputController = loadController(inputPath)
                local state = {
                    memberId = object.memberId,
                    socket = object.socket,
                    sourcePath = sourcePath,
                    inputPath = inputPath,
                    sourceController = sourceController,
                    inputController = inputController,
                    context = context,
                }
                active[object.id] = state

                if sourceController then
                    call(sourcePath, sourceController, "onStart", context)
                    call(sourcePath, sourceController, "render", context)
                end
            else
                previous.context.binding = binding
                previous.context.desk = previous.context.desk or findDesk(object.memberId)
            end
        end
    end

    for id, state in pairs(active) do
        if not seen[id] then
            if state.sourceController then
                call(state.sourcePath, state.sourceController, "onStop", state.context)
            end
            active[id] = nil
        end
    end
end

local function pointerCallback(state, action, event)
    local controller = state.inputController
    if not controller then return end
    local callback = "onPointer"
    if action == "tap" and type(controller.onTap) == "function" then
        callback = "onTap"
    elseif action == "double_tap" and type(controller.onDoubleTap) == "function" then
        callback = "onDoubleTap"
    end
    call(state.inputPath, controller, callback, state.context, event)
end

local function dispatch(name, ...)
    if name == "cc_aeroworks_display_binding_changed" or name == "cc_aeroworks_console_changed" then
        refresh()
    end

    if name == "cc_aeroworks_console_display_input" then
        local args = table.pack(...)
        local memberId = tostring(args[1])
        local socket = tonumber(args[3])
        local id = "module:" .. memberId .. ":" .. tostring(socket)
        local state = active[id]
        if state then
            pointerCallback(state, tostring(args[6]), {
                deskId = args[1],
                deskIndex = args[2],
                socket = args[3],
                socketName = args[4],
                moduleId = args[5],
                action = args[6],
                x = args[7],
                y = args[8],
                width = args[9],
                height = args[10],
                handler = args[11],
            })
        end
    end

    for _, state in pairs(active) do
        if state.sourceController then
            call(state.sourcePath, state.sourceController, "onEvent", state.context, name, ...)
        end
    end
end

refresh()

_G.display_sources = {
    refresh = refresh,
    list = function()
        return deskio.find("display")
    end,
}

local rawPullEventRaw = os.pullEventRaw
local function pull(filter, terminateOnTerminate)
    while true do
        local event = table.pack(rawPullEventRaw())
        dispatch(unpack(event, 1, event.n))
        if event[1] == "terminate" and terminateOnTerminate then
            error("Terminated", 0)
        end
        if filter == nil or event[1] == filter or event[1] == "terminate" then
            return unpack(event, 1, event.n)
        end
    end
end

os.pullEventRaw = function(filter)
    return pull(filter, false)
end

os.pullEvent = function(filter)
    return pull(filter, true)
end
