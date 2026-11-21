-- Selected normal-control scripts run on changes, including releases and analog motion.
-- Preserve raw events and filters, just like the bundled display-handler runtime.
if rawget(_G, "__cc_aeroworks_control_handlers_installed") then return end
rawset(_G, "__cc_aeroworks_control_handlers_installed", true)

local nativePullEventRaw = os.pullEventRaw
local baseEnvironment = _ENV
local dispatching = false
local lastEventId = -1

local function dispatch(raw)
    if raw[1] ~= "cc_aeroworks_console_input" or type(raw[9]) ~= "string" or raw[9] == "" then return end
    -- parallel broadcasts each event to its children after the parent has received it.
    -- Server IDs increase for this computer, so late copies must not execute again.
    local eventId = raw[11]
    if type(eventId) == "number" then
        if eventId <= lastEventId then return end
        lastEventId = eventId
    end
    local event = {
        deskId = raw[2], deskIndex = raw[3], socket = raw[4], socketName = raw[5],
        moduleId = raw[6], value = raw[7], channel = raw[8], handler = raw[9],
        previousValue = raw[10],
        id = eventId,
    }
    local environment = setmetatable({ controlEvent = event }, { __index = baseEnvironment })
    local chunk, err = loadfile(event.handler, nil, environment)
    if not chunk then error(err, 0) end
    -- A one-shot file receives the event as its first argument. A returned handler may use onInput.
    local handler = chunk(event)
    if type(handler) == "table" and type(handler.onInput) == "function" then
        handler.onInput(event)
    elseif type(handler) == "function" then
        handler(event)
    end
end

os.pullEventRaw = function(filter)
    while true do
        local event = table.pack(nativePullEventRaw())
        if not dispatching then
            dispatching = true
            local ok, err = pcall(dispatch, event)
            dispatching = false
            if not ok then
                local report = printError or print
                report("control script " .. tostring(event[9]) .. ": " .. tostring(err))
            end
        end
        if filter == nil or event[1] == filter or event[1] == "terminate" then
            return table.unpack(event, 1, event.n)
        end
    end
end
