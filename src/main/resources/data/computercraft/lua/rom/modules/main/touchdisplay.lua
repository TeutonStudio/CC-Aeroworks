local display = require("display")

local touchdisplay = {}
for key, value in pairs(display) do touchdisplay[key] = value end

function touchdisplay.isTap(event)
    return type(event) == "table" and event.action == "tap"
end

function touchdisplay.isDoubleTap(event)
    return type(event) == "table" and event.action == "double_tap"
end

function touchdisplay.position(event)
    if type(event) ~= "table" then error("display event table expected", 2) end
    return event.x, event.y, event.width, event.height
end

return touchdisplay
