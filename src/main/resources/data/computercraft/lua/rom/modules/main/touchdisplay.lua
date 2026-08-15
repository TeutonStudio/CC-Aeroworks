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

-- Resolution-independent position across the physical display surface.
-- New CC-Aeroworks events provide the exact pointer coordinates. The fallback keeps handlers
-- usable with older event producers by returning the centre of the selected raster cell.
function touchdisplay.normalizedPosition(event)
    if type(event) ~= "table" then error("display event table expected", 2) end
    if type(event.u) == "number" and type(event.v) == "number" then
        return event.u, event.v
    end
    if type(event.width) ~= "number" or type(event.height) ~= "number" or
       type(event.x) ~= "number" or type(event.y) ~= "number" then
        return nil, nil
    end
    return (event.x - 0.5) / event.width, (event.y - 0.5) / event.height
end

return touchdisplay
