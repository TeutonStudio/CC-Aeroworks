local display = require("display")

local touchdisplay = {}
for key, value in pairs(display) do touchdisplay[key] = value end

function touchdisplay.isTap(event)
    return type(event) == "table" and event.action == "tap"
end

function touchdisplay.isDraw(event)
    return type(event) == "table" and event.action == "draw"
end

-- Legacy protocol helpers remain available for old/manual event producers. New combined input emits
-- tap and draw only.
function touchdisplay.isDoubleTap(event)
    return type(event) == "table" and event.action == "double_tap"
end

function touchdisplay.isHold(event)
    return type(event) == "table" and event.action == "hold"
end

function touchdisplay.position(event)
    if type(event) ~= "table" then error("display event table expected", 2) end
    return event.x, event.y, event.width, event.height
end

function touchdisplay.drawStart(event)
    if not touchdisplay.isDraw(event) then error("draw event expected", 2) end
    return event.startX, event.startY
end

-- Delta from the immediately preceding accepted draw event, already resolved in display pixels by
-- the server. A handler can therefore draw x-dx,y-dy -> x,y without keeping cross-event state.
function touchdisplay.drawDelta(event)
    if not touchdisplay.isDraw(event) then error("draw event expected", 2) end
    return event.deltaX or 0, event.deltaY or 0
end

function touchdisplay.drawEnded(event)
    return touchdisplay.isDraw(event) and event.isEnd == true
end

function touchdisplay.drawIdentity(event)
    if not touchdisplay.isDraw(event) then error("draw event expected", 2) end
    return event.gestureId, event.sequence
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
