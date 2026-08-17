-- CC-Aeroworks automatic display-handler runtime.
--
-- Display bindings belong to the embedded ComputerControlDesk computer. The server queues
-- cc_aeroworks_console_display_input with the selected handler path; this hook consumes that
-- metadata before returning the raw event to normal CraftOS programs. Raw events therefore remain
-- observable while a selected handler is also executed automatically.

if rawget(_G, "__cc_aeroworks_display_handlers_installed") then return end
rawset(_G, "__cc_aeroworks_display_handlers_installed", true)

local nativePullEventRaw = os.pullEventRaw

-- CraftOS runs /rom/autorun files through shell.run(). The shell gives each program a private
-- environment containing require/package, while BIOS globals deliberately do not expose require.
-- Keep that shell environment alive for handlers loaded later from the event hook. Without this,
-- loadfile(path) uses the BIOS global environment and every discovered handler containing
-- require("display") or require("touchdisplay") fails before its callback can run.
local handlerBaseEnvironment = _ENV
local handlerGlobalEnvironment = _G
local handlerRequire = require
local handlerPackage = package

local diagnostics = nil
do
    local ok, module = pcall(handlerRequire, "cc_aeroworks.display_diagnostics")
    if ok and type(module) == "table" then diagnostics = module end
end

local telemetryProxies = setmetatable({}, { __mode = "k" })
local lastSignature = nil
local lastEpoch = -1

local function report(message)
    if type(printError) == "function" then
        printError(message)
    else
        print(message)
    end
end

local function diagnosticBegin(path, event, phase, scope)
    if not diagnostics or type(diagnostics.begin) ~= "function" then return false end
    local ok, started = pcall(
        diagnostics.begin,
        path,
        "legacy_handler",
        tostring(event.deskId or ""),
        tonumber(event.socket) or -1,
        phase,
        scope
    )
    return ok and started == true
end

local function diagnosticFinish(started)
    if not started or not diagnostics or type(diagnostics.finish) ~= "function" then return end
    pcall(diagnostics.finish)
end

local function diagnosticRead(key, kind)
    if not diagnostics or type(diagnostics.read) ~= "function" then return end
    pcall(diagnostics.read, key, kind)
end

local function wrapTelemetry(module)
    if type(module) ~= "table" then return module end
    local existing = telemetryProxies[module]
    if existing then return existing end

    local proxy = {}
    setmetatable(proxy, { __index = module })

    if type(module.get) == "function" then
        proxy.get = function(...)
            local packed = table.pack(module.get(...))
            local value = packed[1]
            local requested = select(1, ...)
            local id = type(value) == "table" and value.id or requested
            if id ~= nil then diagnosticRead("telemetry:" .. tostring(id), "telemetry") end
            return table.unpack(packed, 1, packed.n)
        end
    end
    if type(module.list) == "function" then
        proxy.list = function(...)
            local packed = table.pack(module.list(...))
            diagnosticRead("telemetry:*", "telemetry")
            return table.unpack(packed, 1, packed.n)
        end
    end
    if type(module.find) == "function" then
        proxy.find = function(...)
            local packed = table.pack(module.find(...))
            diagnosticRead("telemetry:*", "telemetry")
            return table.unpack(packed, 1, packed.n)
        end
    end
    if type(module.getStatus) == "function" then
        proxy.getStatus = function(...)
            local packed = table.pack(module.getStatus(...))
            diagnosticRead("telemetry:status", "telemetry")
            return table.unpack(packed, 1, packed.n)
        end
    end

    telemetryProxies[module] = proxy
    return proxy
end

local function diagnosticRequire(name)
    local module = handlerRequire(name)
    if name == "cc_aeroworks.telemetry" then return wrapTelemetry(module) end
    return module
end

local function createHandlerEnvironment()
    local environment = {
        _G = handlerGlobalEnvironment,
        require = diagnosticRequire,
        package = handlerPackage,
    }
    local globalTelemetry = rawget(handlerBaseEnvironment, "telemetry")
    if type(globalTelemetry) == "table" then environment.telemetry = wrapTelemetry(globalTelemetry) end
    return setmetatable(environment, { __index = handlerBaseEnvironment })
end

local function recordTouchHandlers(path, event, handler)
    if not diagnostics or type(diagnostics.setTouchHandlers) ~= "function" then return end
    pcall(
        diagnostics.setTouchHandlers,
        path,
        tostring(event.deskId or ""),
        tonumber(event.socket) or -1,
        type(handler.onTap) == "function",
        type(handler.onDoubleTap) == "function",
        type(handler.onPointer) == "function"
    )
end

local function loadHandler(path, event)
    if type(path) ~= "string" or path == "" then return nil end

    local chunk, loadError = loadfile(path, nil, createHandlerEnvironment())
    if chunk == nil then
        report("display handler " .. path .. ": " .. tostring(loadError))
        return nil
    end

    local started = diagnosticBegin(path, event, "load", "legacy:load")
    local ok, handler = pcall(chunk)
    diagnosticFinish(started)
    if not ok then
        report("display handler " .. path .. ": " .. tostring(handler))
        return nil
    end

    if type(handler) ~= "table" then
        report("display handler " .. path .. " must return a table")
        return nil
    end
    recordTouchHandlers(path, event, handler)
    return handler
end

local function dispatch(handler, event)
    local callback
    if event.action == "tap" then
        callback = handler.onTap or handler.onPointer
    elseif event.action == "double_tap" then
        callback = handler.onDoubleTap or handler.onPointer
    else
        callback = handler.onPointer
    end

    if type(callback) ~= "function" then return end
    local action = tostring(event.action or "pointer")
    local started = diagnosticBegin(event.handler, event, "event", "legacy:" .. action)
    local ok, err = pcall(callback, event)
    diagnosticFinish(started)
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
        tostring(event[17])
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

local function dispatchConsoleEvent(event)
    if event[1] ~= "cc_aeroworks_console_display_input" then return end
    local handlerPath = event[12]
    if type(handlerPath) ~= "string" or handlerPath == "" then return end

    -- Preserve the old explicit router example if somebody deliberately runs it. Without this,
    -- both the automatic hook and that compatibility program would invoke the same callback.
    local running = type(shell) == "table" and type(shell.getRunningProgram) == "function"
        and shell.getRunningProgram() or ""
    if type(running) == "string" and running:match("display%-binding%-router%.lua$") then return end

    if not shouldDispatch(event) then return end
    local descriptor = {
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
        handler = handlerPath,
        u = event[13],
        v = event[14],
        deskX = event[15],
        deskY = event[16],
        deskZ = event[17]
    }
    local handler = loadHandler(handlerPath, descriptor)
    if not handler then return end
    dispatch(handler, descriptor)
end

-- Pull without a native filter and re-apply the filter here. This ensures a display action is still
-- observed while the foreground program waits for an unrelated filtered event such as "timer".
-- "terminate" is always returned so os.pullEvent keeps its normal termination semantics.
os.pullEventRaw = function(filter)
    while true do
        local event = table.pack(nativePullEventRaw())
        local ok, err = pcall(dispatchConsoleEvent, event)
        if not ok then report("display handler dispatcher: " .. tostring(err)) end

        if filter == nil or event[1] == filter or event[1] == "terminate" then
            return table.unpack(event, 1, event.n)
        end
    end
end
