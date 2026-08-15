local display = {}

local function networkApi()
    local api = rawget(_G, "peripherals")
    if type(api) == "table" then return api end
    local ok, module = pcall(require, "cc_aeroworks.peripherals")
    if ok then return module end
    return nil
end

function display.resolve(event)
    if type(event) ~= "table" then error("display event table expected", 2) end
    local socket = event.socketName or event.socket

    if event.attachment then
        local desk = peripheral.wrap(event.attachment)
        if desk then return desk, socket end
    end

    local api = networkApi()
    if not api then return nil, socket end
    local desks = api.find("ControlDesk")
    if type(desks) ~= "table" then return nil, socket end
    for _, desk in pairs(desks) do
        local ok, info = pcall(desk.getInfo)
        if ok and info and info.id == event.deskId then return desk, socket end
    end
    return nil, socket
end

local function target(event)
    local desk, socket = display.resolve(event)
    if not desk then error("display source desk is unavailable", 3) end
    return desk, socket
end

function display.getSize(event)
    local desk, socket = target(event)
    return desk.getDisplaySize(socket)
end

function display.clear(event)
    local desk, socket = target(event)
    return desk.clearDisplayPixels(socket)
end

function display.getPixel(event, x, y)
    local desk, socket = target(event)
    return desk.getDisplayPixel(socket, x, y)
end

function display.setPixel(event, x, y, enabled)
    local desk, socket = target(event)
    return desk.setDisplayPixel(socket, x, y, enabled ~= false)
end

function display.setPixels(event, rows)
    local desk, socket = target(event)
    return desk.setDisplayPixels(socket, rows)
end

function display.setText(event, text)
    local desk, socket = target(event)
    return desk.setDisplayText(socket, text)
end

function display.setNumber(event, value, zeroPad)
    local desk, socket = target(event)
    return desk.setDisplayNumber(socket, value, zeroPad)
end

return display
