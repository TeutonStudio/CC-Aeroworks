-- CC-Aeroworks automatic programmable-display runtime.
--
-- Automatic reactive applications run synchronously on the real CraftOS coroutine which is
-- currently pulling events. This deliberately avoids a private child coroutine: main-thread Lua
-- calls may yield through CC:Tweaked and must therefore remain owned by its scheduler.
-- Controller-only bindings stay on the reload-on-touch compatibility path below.

if rawget(_G, "__cc_aeroworks_display_handlers_installed") then return end
rawset(_G, "__cc_aeroworks_display_handlers_installed", true)

local nativePullEventRaw = os.pullEventRaw

-- /rom/autorun programs run with the shell module environment. Preserve it for legacy handlers and
-- also expose require/package to chunks loaded by cc_aeroworks.ui with plain loadfile().
local handlerBaseEnvironment = _ENV
local handlerGlobalEnvironment = _G
local handlerRequire = require
local handlerPackage = package
rawset(handlerGlobalEnvironment, "require", handlerRequire)
rawset(handlerGlobalEnvironment, "package", handlerPackage)

local lastSignature = nil
local lastEpoch = -1
local supervisor = nil

local function report(message)
    if type(printError) == "function" then
        printError(message)
    else
        print(message)
    end
end

local function createHandlerEnvironment()
    local environment = {
        _G = handlerGlobalEnvironment,
        require = handlerRequire,
        package = handlerPackage,
    }
    return setmetatable(environment, { __index = handlerBaseEnvironment })
end

local function loadHandler(path)
    if type(path) ~= "string" or path == "" then return nil end

    local chunk, loadError = loadfile(path, nil, createHandlerEnvironment())
    if chunk == nil then
        report("display handler " .. path .. ": " .. tostring(loadError))
        return nil
    end

    local ok, handler = pcall(chunk)
    if not ok then
        report("display handler " .. path .. ": " .. tostring(handler))
        return nil
    end

    if type(handler) ~= "table" then
        report("display handler " .. path .. " must return a table")
        return nil
    end
    return handler
end

local function dispatchLegacy(handler, event)
    local callback
    if event.action == "tap" then
        callback = handler.onTap or handler.onPointer
    elseif event.action == "double_tap" then
        callback = handler.onDoubleTap or handler.onPointer
    else
        callback = handler.onPointer
    end

    if type(callback) ~= "function" then return end
    local ok, err = pcall(callback, event)
    if not ok then report("display handler " .. tostring(event.handler) .. ": " .. tostring(err)) end
end

local function signatureFor(event)
    return table.concat({
        tostring(event[2]),
        tostring(event[3]),
        tostring(event[4]),
        tostring(event[7]),
        tostring(event[8]),
        tostring(event[9]),
        tostring(event[12]),
        tostring(event[13]),
        tostring(event[14]),
        tostring(event[15]),
        tostring(event[16]),
        tostring(event[17]),
        tostring(event[18])
    }, "\0")
end

local function shouldDispatch(event)
    local signature = signatureFor(event)
    local now = os.epoch("utc")
    if signature == lastSignature and lastEpoch >= 0 and now - lastEpoch <= 10 then return false end
    lastSignature = signature
    lastEpoch = now
    return true
end

local function normalizeReactiveTarget(event)
    if event[2] ~= nil then event[2] = tostring(event[2]) end
    local socket = tonumber(event[4])
    if socket ~= nil then event[4] = socket end
end

local function asPointerEvent(event)
    return {
        deskId = event[2],
        deskIndex = event[3],
        socket = event[4],
        socketName = event[5],
        moduleId = event[6],
        action = event[7],
        x = event[8],
        y = event[9],
        width = event[10],
        height = event[11],
        handler = event[12],
        u = event[13],
        v = event[14],
        deskX = event[15],
        deskY = event[16],
        deskZ = event[17],
        bootProgram = event[18]
    }
end

local function dispatchLegacyConsoleEvent(event)
    if event[1] ~= "cc_aeroworks_console_display_input" then return end
    local handlerPath = event[12]
    if type(handlerPath) ~= "string" or handlerPath == "" then return end

    local running = type(shell) == "table" and type(shell.getRunningProgram) == "function"
        and shell.getRunningProgram() or ""
    if type(running) == "string" and running:match("display%-binding%-router%.lua$") then return end

    local handler = loadHandler(handlerPath)
    if handler then dispatchLegacy(handler, asPointerEvent(event)) end
end

local function startSupervisor()
    if supervisor ~= nil then return true end
    local ok, ui = pcall(handlerRequire, "cc_aeroworks.ui")
    if not ok or type(ui) ~= "table" or type(ui.createSupervisor) ~= "function" then
        rawset(handlerGlobalEnvironment, "__cc_aeroworks_display_supervisor_active", false)
        return false
    end

    supervisor = ui.createSupervisor()
    supervisor:start()
    rawset(handlerGlobalEnvironment, "__cc_aeroworks_display_supervisor_active", true)
    return true
end

-- A configured application is authoritative. If CraftOS missed the binding-change event while
-- starting, the next pointer event carries the boot path and reconstructs only that missing runtime.
-- This is especially important for application-only displays, where there is deliberately no
-- legacy controller available as a fallback.
local function ensureReactiveRuntime(event)
    normalizeReactiveTarget(event)
    if supervisor == nil and not startSupervisor() then return false end
    if supervisor == nil or type(supervisor.hasRuntime) ~= "function" then return supervisor ~= nil end
    if supervisor:hasRuntime(event[2], event[4]) then return true end

    local bootProgram = event[18]
    if type(bootProgram) ~= "string" or bootProgram == "" then return false end

    supervisor:handle(
        "cc_aeroworks_display_application_changed",
        event[2],
        event[3],
        event[4],
        event[5],
        type(event[12]) == "string" and event[12] or "",
        bootProgram
    )
    return supervisor:hasRuntime(event[2], event[4])
end

local function supervisorEvent(name)
    return name == "cc_aeroworks_console_display_input"
        or name == "cc_aeroworks_display_application_changed"
        or name == "cc_aeroworks_ui_invalidated"
        or name == "cc_aeroworks_telemetry_added"
        or name == "cc_aeroworks_telemetry_changed"
        or name == "cc_aeroworks_telemetry_removed"
end

-- Start once during CraftOS autorun. listDisplays/beginFrame/commit may yield here, but they remain
-- on the real CraftOS coroutine, so CC:Tweaked owns the task_completed lifecycle end to end.
startSupervisor()

-- Pull without a native filter and re-apply the caller's filter afterwards. This lets automatic
-- display applications observe input while the foreground program waits for an unrelated event.
os.pullEventRaw = function(filter)
    while true do
        local event = table.pack(nativePullEventRaw())
        local relevant = supervisorEvent(event[1])
        local unique = event[1] ~= "cc_aeroworks_console_display_input" or shouldDispatch(event)
        local handled = false

        if relevant and unique then
            if event[1] == "cc_aeroworks_console_display_input" then
                ensureReactiveRuntime(event)
            elseif supervisor == nil then
                startSupervisor()
            end

            if supervisor ~= nil then
                handled = supervisor:handle(table.unpack(event, 1, event.n)) == true
            end
            if not handled and event[1] == "cc_aeroworks_console_display_input" then
                local ok, err = pcall(dispatchLegacyConsoleEvent, event)
                if not ok then report("display handler dispatcher: " .. tostring(err)) end
            end
        end

        if filter == nil or event[1] == filter or event[1] == "terminate" then
            return table.unpack(event, 1, event.n)
        end
    end
end
