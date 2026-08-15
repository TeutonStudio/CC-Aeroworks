-- CC-Aeroworks display binding router
--
-- Configure a large Desk Display with:
--   desk.setDisplayTouchScript("big", "/ui/main.lua")
--
-- A handler module may return a table containing onTap, onDoubleTap or onPointer.
-- Handlers are reloaded for every event so edits take effect immediately.

local handlerBaseEnvironment = _ENV
local handlerGlobalEnvironment = _G
local handlerRequire = require
local handlerPackage = package

local function createHandlerEnvironment()
    local environment = {
        _G = handlerGlobalEnvironment,
        require = handlerRequire,
        package = handlerPackage,
    }
    return setmetatable(environment, { __index = handlerBaseEnvironment })
end

local function loadHandler(path)
    if path == nil or path == "" then return nil end

    local chunk, loadError = loadfile(path, nil, createHandlerEnvironment())
    if chunk == nil then
        printError("display handler " .. path .. ": " .. tostring(loadError))
        return nil
    end

    local ok, handler = pcall(chunk)
    if not ok then
        printError("display handler " .. path .. ": " .. tostring(handler))
        return nil
    end

    if type(handler) ~= "table" then
        printError("display handler " .. path .. " must return a table")
        return nil
    end

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
    local ok, err = pcall(callback, event)
    if not ok then printError(tostring(err)) end
end

while true do
    local packed = table.pack(os.pullEvent())
    local name = packed[1]

    if name == "cc_aeroworks_console_display_input" then
        local handlerPath = packed[12]
        local handler = loadHandler(handlerPath)
        if handler then
            dispatch(handler, {
                deskId = packed[2],
                deskIndex = packed[3],
                socket = packed[4],
                socketName = packed[5],
                moduleId = packed[6],
                action = packed[7],
                x = packed[8],
                y = packed[9],
                width = packed[10],
                height = packed[11],
                handler = handlerPath
            })
        end
    elseif name == "cc_aeroworks_desk_display_input" then
        local handlerPath = packed[11]
        local handler = loadHandler(handlerPath)
        if handler then
            dispatch(handler, {
                attachment = packed[2],
                socket = packed[3],
                socketName = packed[4],
                moduleId = packed[5],
                action = packed[6],
                x = packed[7],
                y = packed[8],
                width = packed[9],
                height = packed[10],
                handler = handlerPath
            })
        end
    end
end
