-- CC-Aeroworks automatic display-handler runtime.
--
-- Display bindings belong to the embedded ComputerControlDesk computer. The server queues
-- cc_aeroworks_console_display_input with the selected handler path; this hook consumes that
-- metadata before returning the raw event to normal CraftOS programs. Raw events therefore remain
-- observable while a selected handler is also executed automatically.
--
-- Draw is a hot path. A handler is loaded once per draw gesture instead of once per sample, and
-- ordinary middle-of-gesture samples never bridge INFO diagnostics back onto Minecraft's main
-- thread. Start/end/error diagnostics remain available without starving CC:Tweaked's event queue.

if rawget(_G, "__cc_aeroworks_display_handlers_installed") then return end
rawset(_G, "__cc_aeroworks_display_handlers_installed", true)

local nativePullEventRaw = os.pullEventRaw
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
local drawHandlers = {}
local lastSignature = nil
local lastEpoch = -1

local function report(message)
    if type(printError) == "function" then
        printError(message)
    else
        print(message)
    end
end

local function bridgeTouchLog(event, message)
    if type(event) ~= "table" then return false end
    local socket = event.socketName or event.socket

    local function tryDesk(desk)
        if not desk or type(desk.debugDisplayTouchLog) ~= "function" then return false end
        local ok = pcall(desk.debugDisplayTouchLog, socket, tostring(message))
        return ok
    end

    if event.attachment and type(peripheral) == "table" and type(peripheral.wrap) == "function" then
        local ok, desk = pcall(peripheral.wrap, event.attachment)
        if ok and tryDesk(desk) then return true end
    end

    local networkApi = rawget(_G, "peripherals")
    if type(networkApi) == "table" and type(networkApi.wrap) == "function" and
       type(event.deskX) == "number" and type(event.deskY) == "number" and type(event.deskZ) == "number" then
        local ok, desk = pcall(networkApi.wrap, event.deskX, event.deskY, event.deskZ, "ControlDesk")
        if ok and tryDesk(desk) then return true end
    end

    return false
end

local function reportEvent(event, message)
    report(message)
    -- Errors are exceptional and should always remain visible in latest.log.
    bridgeTouchLog(event, message)
end

local function shouldBridgeRoutine(event)
    if type(event) ~= "table" then return false end
    if event.action ~= "draw" then return true end
    return tonumber(event.sequence) == 0 or event.isEnd == true
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
        type(handler.onDraw) == "function",
        type(handler.onDoubleTap) == "function",
        type(handler.onPointer) == "function"
    )
end

local function loadHandler(path, event)
    if type(path) ~= "string" or path == "" then
        reportEvent(event, "display handler: event contains no handler path")
        return nil
    end

    if shouldBridgeRoutine(event) then
        bridgeTouchLog(event, "loading handler path='" .. path .. "'")
    end
    local chunk, loadError = loadfile(path, nil, createHandlerEnvironment())
    if chunk == nil then
        reportEvent(event, "display handler " .. path .. ": loadfile failed: " .. tostring(loadError))
        return nil
    end

    local started = diagnosticBegin(path, event, "load", "legacy:load")
    local ok, handler = pcall(chunk)
    diagnosticFinish(started)
    if not ok then
        reportEvent(event, "display handler " .. path .. ": top-level execution failed: " .. tostring(handler))
        return nil
    end
    if type(handler) ~= "table" then
        reportEvent(event, "display handler " .. path .. " must return a table, got " .. type(handler))
        return nil
    end

    recordTouchHandlers(path, event, handler)
    if shouldBridgeRoutine(event) then
        bridgeTouchLog(event, string.format(
            "handler loaded path='%s' callbacks tap=%s draw=%s hold=%s doubleTap=%s pointer=%s",
            path,
            tostring(type(handler.onTap) == "function"),
            tostring(type(handler.onDraw) == "function"),
            tostring(type(handler.onHold) == "function"),
            tostring(type(handler.onDoubleTap) == "function"),
            tostring(type(handler.onPointer) == "function")
        ))
    end
    return handler
end

local function drawHandlerKey(path, event)
    return table.concat({
        tostring(event.deskId or event.attachment or "desk"),
        tostring(event.socket or event.socketName or "socket"),
        tostring(path)
    }, "\0")
end

local function handlerFor(path, event)
    if event.action ~= "draw" then return loadHandler(path, event), nil end

    local key = drawHandlerKey(path, event)
    local gesture = tostring(event.gestureId or "")
    local cached = drawHandlers[key]
    local mustReload = tonumber(event.sequence) == 0 or
        type(cached) ~= "table" or
        cached.gesture ~= gesture or
        type(cached.handler) ~= "table"

    if mustReload then
        local handler = loadHandler(path, event)
        if not handler then
            drawHandlers[key] = nil
            return nil, key
        end
        cached = { gesture = gesture, handler = handler }
        drawHandlers[key] = cached
    end
    return cached.handler, key
end

local function dispatch(handler, event)
    local callback
    local callbackName
    if event.action == "tap" then
        callback = handler.onTap or handler.onPointer
        callbackName = handler.onTap and "onTap" or "onPointer"
    elseif event.action == "draw" then
        callback = handler.onDraw or handler.onPointer
        callbackName = handler.onDraw and "onDraw" or "onPointer"
    elseif event.action == "double_tap" then
        callback = handler.onDoubleTap or handler.onPointer
        callbackName = handler.onDoubleTap and "onDoubleTap" or "onPointer"
    elseif event.action == "hold" then
        callback = handler.onHold or handler.onPointer
        callbackName = handler.onHold and "onHold" or "onPointer"
    else
        callback = handler.onPointer
        callbackName = "onPointer"
    end

    if type(callback) ~= "function" then
        reportEvent(event, "display handler " .. tostring(event.handler) .. ": no callback for action " .. tostring(event.action))
        return false
    end

    local action = tostring(event.action or "pointer")
    if shouldBridgeRoutine(event) then
        local drawSuffix = ""
        if action == "draw" then
            drawSuffix = string.format(
                " gesture=%s seq=%s start=%s,%s delta=%s,%s direction=%s,%s speed=%s samples=%s end=%s",
                tostring(event.gestureId), tostring(event.sequence), tostring(event.startX), tostring(event.startY),
                tostring(event.deltaX), tostring(event.deltaY), tostring(event.directionU), tostring(event.directionV),
                tostring(event.speed), tostring(type(event.samples) == "table" and #event.samples or 0), tostring(event.isEnd)
            )
        end
        bridgeTouchLog(event, string.format(
            "dispatch action=%s callback=%s pixel=%s,%s size=%sx%s u=%s v=%s%s",
            action, tostring(callbackName), tostring(event.x), tostring(event.y), tostring(event.width),
            tostring(event.height), tostring(event.u), tostring(event.v), drawSuffix
        ))
    end

    local started = diagnosticBegin(event.handler, event, "event", "legacy:" .. action)
    local ok, err = pcall(callback, event)
    diagnosticFinish(started)
    if not ok then
        reportEvent(event, "display handler " .. tostring(event.handler) .. ": callback failed: " .. tostring(err))
        return false
    end

    if shouldBridgeRoutine(event) then
        bridgeTouchLog(event, "callback completed successfully action=" .. action .. " callback=" .. tostring(callbackName))
    end
    return true
end

local function signatureFor(event)
    return table.concat({
        tostring(event[2]), tostring(event[3]), tostring(event[4]), tostring(event[7]), tostring(event[8]),
        tostring(event[9]), tostring(event[12]), tostring(event[13]), tostring(event[14]), tostring(event[15]),
        tostring(event[16]), tostring(event[17]), tostring(event[18]), tostring(event[19]), tostring(event[20]),
        tostring(event[21]), tostring(event[22]), tostring(event[23]), tostring(event[24]), tostring(event[25]),
        tostring(event[26]), tostring(event[27])
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

    local running = type(shell) == "table" and type(shell.getRunningProgram) == "function"
        and shell.getRunningProgram() or ""
    if type(running) == "string" and running:match("display%-binding%-router%.lua$") then return end
    if not shouldDispatch(event) then return end

    local descriptor = {
        deskId = event[2], deskIndex = event[3], socket = event[4], socketName = event[5], moduleId = event[6],
        action = event[7], x = event[8], y = event[9], width = event[10], height = event[11], handler = handlerPath,
        u = event[13], v = event[14], deskX = event[15], deskY = event[16], deskZ = event[17],
        gestureId = event[18], sequence = event[19], startX = event[20], startY = event[21],
        deltaX = event[22], deltaY = event[23], isEnd = event[24] == true,
        directionU = event[25], directionV = event[26], speed = event[27],
        samples = type(event[28]) == "table" and event[28] or nil
    }

    if shouldBridgeRoutine(descriptor) then
        bridgeTouchLog(descriptor, string.format(
            "console event received action=%s handler='%s' pixel=%s,%s size=%sx%s gesture=%s seq=%s samples=%s end=%s",
            tostring(descriptor.action), tostring(handlerPath), tostring(descriptor.x), tostring(descriptor.y),
            tostring(descriptor.width), tostring(descriptor.height), tostring(descriptor.gestureId),
            tostring(descriptor.sequence), tostring(type(descriptor.samples) == "table" and #descriptor.samples or 0),
            tostring(descriptor.isEnd)
        ))
    end

    local handler, drawKey = handlerFor(handlerPath, descriptor)
    if not handler then return end
    dispatch(handler, descriptor)
    if drawKey and descriptor.isEnd then drawHandlers[drawKey] = nil end
end

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
