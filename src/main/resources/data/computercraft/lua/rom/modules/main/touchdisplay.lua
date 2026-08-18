local display = require("display")

local touchdisplay = {}
for key, value in pairs(display) do touchdisplay[key] = value end

local EPSILON = 0.000000001
local TANGENT_SCALE = 0.34
local MIN_DIRECTION_ALIGNMENT = -0.15

-- drawStroke is deliberately stateful per gesture. CC:Tweaked may discard queued events under
-- load, so the first sample of the next event is not necessarily the last sample Lua actually
-- rendered. Keeping the last rendered endpoint lets us bridge that gap instead of producing a
-- sequence of disconnected stroke fragments during fast pointer movement.
local strokeStates = {}

function touchdisplay.isTap(event)
    return type(event) == "table" and event.action == "tap"
end

function touchdisplay.isDraw(event)
    return type(event) == "table" and event.action == "draw"
end

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

function touchdisplay.drawDelta(event)
    if not touchdisplay.isDraw(event) then error("draw event expected", 2) end
    return event.deltaX or 0, event.deltaY or 0
end

function touchdisplay.drawDirection(event)
    if not touchdisplay.isDraw(event) then error("draw event expected", 2) end
    return event.directionU or 0, event.directionV or 0
end

-- Normalized display-surface units per second, measured before direction normalization.
function touchdisplay.drawSpeed(event)
    if not touchdisplay.isDraw(event) then error("draw event expected", 2) end
    return event.speed or 0
end

local function currentSample(event)
    return {
        x = tonumber(event.x) or 0,
        y = tonumber(event.y) or 0,
        u = tonumber(event.u),
        v = tonumber(event.v),
        directionU = tonumber(event.directionU) or 0,
        directionV = tonumber(event.directionV) or 0,
        speed = tonumber(event.speed) or 0
    }
end

local function previousEventSample(event, current)
    local dx = tonumber(event.deltaX) or 0
    local dy = tonumber(event.deltaY) or 0
    if dx == 0 and dy == 0 then return nil end

    local x = current.x - dx
    local y = current.y - dy
    local width = tonumber(event.width) or 0
    local height = tonumber(event.height) or 0
    return {
        x = x,
        y = y,
        u = width > 0 and (x - 0.5) / width or nil,
        v = height > 0 and (y - 0.5) / height or nil,
        -- Unknown historical tangent: zero deliberately makes Hermite fall back to the chord.
        directionU = 0,
        directionV = 0,
        speed = 0
    }
end

-- Server-resolved high-frequency path for the current event. Normally non-start events already
-- begin with the previous accepted endpoint. The defensive delta fallback guarantees that even a
-- missing/empty/one-point sample table can never silently degrade a moved draw event to one dot.
function touchdisplay.drawSamples(event)
    if not touchdisplay.isDraw(event) then error("draw event expected", 2) end

    local current = currentSample(event)
    local samples = {}
    if type(event.samples) == "table" then
        for index = 1, #event.samples do
            local sample = event.samples[index]
            if type(sample) == "table" and tonumber(sample.x) and tonumber(sample.y) then
                samples[#samples + 1] = sample
            end
        end
    end

    if #samples == 0 then
        local previous = previousEventSample(event, current)
        if previous then samples[#samples + 1] = previous end
        samples[#samples + 1] = current
        return samples
    end

    local last = samples[#samples]
    if tonumber(last.x) ~= current.x or tonumber(last.y) ~= current.y then
        samples[#samples + 1] = current
    end

    if #samples == 1 then
        local previous = previousEventSample(event, current)
        if previous then table.insert(samples, 1, previous) end
    end
    return samples
end

function touchdisplay.drawEnded(event)
    return touchdisplay.isDraw(event) and event.isEnd == true
end

function touchdisplay.drawIdentity(event)
    if not touchdisplay.isDraw(event) then error("draw event expected", 2) end
    return event.gestureId, event.sequence
end

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

local function round(value)
    return math.floor(value + 0.5)
end

local function clamp(value, low, high)
    return math.max(low, math.min(high, value))
end

local function addPoint(points, seen, width, height, x, y)
    x = clamp(round(x), 1, width)
    y = clamp(round(y), 1, height)
    local key = (y - 1) * width + x
    if seen[key] then return end
    seen[key] = true
    points[#points + 1] = { x = x, y = y }
end

local function addLine(points, seen, width, height, x0, y0, x1, y1)
    x0, y0 = round(x0), round(y0)
    x1, y1 = round(x1), round(y1)
    local dx = math.abs(x1 - x0)
    local sx = x0 < x1 and 1 or -1
    local dy = -math.abs(y1 - y0)
    local sy = y0 < y1 and 1 or -1
    local err = dx + dy

    while true do
        addPoint(points, seen, width, height, x0, y0)
        if x0 == x1 and y0 == y1 then break end
        local e2 = 2 * err
        if e2 >= dy then
            err = err + dy
            x0 = x0 + sx
        end
        if e2 <= dx then
            err = err + dx
            y0 = y0 + sy
        end
    end
end

-- Preserve sub-pixel pointer information until the final rasterisation step. event.x/event.y are
-- integer cells, while u/v are the continuous surface coordinates which contain the shape lost by
-- early rounding during fast motion.
local function continuousSample(sample, width, height)
    local u = tonumber(sample.u)
    local v = tonumber(sample.v)
    local x = tonumber(sample.x) or 1
    local y = tonumber(sample.y) or 1
    if u and u == u and width > 1 then x = 1 + clamp(u, 0, 1) * (width - 1) end
    if v and v == v and height > 1 then y = 1 + clamp(v, 0, 1) * (height - 1) end
    return {
        x = x,
        y = y,
        u = u,
        v = v,
        directionU = tonumber(sample.directionU) or 0,
        directionV = tonumber(sample.directionV) or 0,
        speed = tonumber(sample.speed) or 0
    }
end

local function samePosition(a, b)
    if not a or not b then return false end
    local dx = (tonumber(a.x) or 0) - (tonumber(b.x) or 0)
    local dy = (tonumber(a.y) or 0) - (tonumber(b.y) or 0)
    return dx * dx + dy * dy <= EPSILON
end

local function tangent(sample, width, height, fallbackX, fallbackY)
    local tx = (tonumber(sample.directionU) or 0) * width
    local ty = (tonumber(sample.directionV) or 0) * height
    local length = math.sqrt(tx * tx + ty * ty)
    local chordLength = math.sqrt(fallbackX * fallbackX + fallbackY * fallbackY)
    if length <= EPSILON or chordLength <= EPSILON then
        tx, ty = fallbackX, fallbackY
        length = chordLength
    else
        tx, ty = tx / length, ty / length
        local cx, cy = fallbackX / chordLength, fallbackY / chordLength
        -- A stale/noisy velocity pointing backwards creates loops and visible mini-strokes at high
        -- speed. Prefer the actual chord in that case; otherwise retain the measured pointer tangent.
        if tx * cx + ty * cy < MIN_DIRECTION_ALIGNMENT then
            tx, ty = cx, cy
        end
        return tx, ty
    end
    if length <= EPSILON then return 0, 0 end
    return tx / length, ty / length
end

local function rasterizeHermite(points, seen, width, height, a, b)
    local x0 = tonumber(a.x) or 0
    local y0 = tonumber(a.y) or 0
    local x1 = tonumber(b.x) or 0
    local y1 = tonumber(b.y) or 0
    local chordX = x1 - x0
    local chordY = y1 - y0
    local distance = math.sqrt(chordX * chordX + chordY * chordY)
    if distance <= EPSILON then
        addPoint(points, seen, width, height, x1, y1)
        return
    end

    local t0x, t0y = tangent(a, width, height, chordX, chordY)
    local t1x, t1y = tangent(b, width, height, chordX, chordY)
    local tangentLength = distance * TANGENT_SCALE
    local m0x, m0y = t0x * tangentLength, t0y * tangentLength
    local m1x, m1y = t1x * tangentLength, t1y * tangentLength
    local steps = math.max(2, math.ceil(distance * 1.75))
    local previousX, previousY = x0, y0

    for step = 1, steps do
        local t = step / steps
        local t2 = t * t
        local t3 = t2 * t
        local h00 = 2 * t3 - 3 * t2 + 1
        local h10 = t3 - 2 * t2 + t
        local h01 = -2 * t3 + 3 * t2
        local h11 = t3 - t2
        local x = h00 * x0 + h10 * m0x + h01 * x1 + h11 * m1x
        local y = h00 * y0 + h10 * m0y + h01 * y1 + h11 * m1y
        addLine(points, seen, width, height, previousX, previousY, x, y)
        previousX, previousY = x, y
    end
end

local function strokeKey(event)
    if event.gestureId == nil then return nil end
    return table.concat({
        tostring(event.deskId or event.attachment or "desk"),
        tostring(event.socketName or event.socket or "socket"),
        tostring(event.gestureId)
    }, "\0")
end

-- Draw the current batch as part of one gesture-wide continuous Hermite stroke. The state bridge is
-- important: if CC:Tweaked skipped an intermediate queued event, the server's prepended sample is
-- newer than Lua's last rendered endpoint. We explicitly connect those two points, so fast motion
-- degrades to a slightly coarser curve instead of a dashed collection of independent strokes.
function touchdisplay.drawStroke(event)
    if not touchdisplay.isDraw(event) then error("draw event expected", 2) end
    local rawSamples = touchdisplay.drawSamples(event)
    local width = tonumber(event.width) or 0
    local height = tonumber(event.height) or 0
    if width <= 0 or height <= 0 or #rawSamples == 0 then return 0 end

    local key = strokeKey(event)
    local sequence = tonumber(event.sequence) or 0
    if key and sequence == 0 then strokeStates[key] = nil end
    local state = key and strokeStates[key] or nil

    local samples = {}
    for index = 1, #rawSamples do
        local sample = continuousSample(rawSamples[index], width, height)
        local last = samples[#samples]
        if not samePosition(last, sample) then samples[#samples + 1] = sample end
    end

    -- Bridge from the last endpoint this Lua runtime really painted, not merely the previous packet
    -- known to the server. This is what keeps the stroke connected across queue/backpressure gaps.
    if state and state.lastSample and #samples > 0 and not samePosition(state.lastSample, samples[1]) then
        table.insert(samples, 1, state.lastSample)
    end

    local points = {}
    local seen = {}
    if #samples > 0 then addPoint(points, seen, width, height, samples[1].x, samples[1].y) end
    for index = 1, #samples - 1 do
        rasterizeHermite(points, seen, width, height, samples[index], samples[index + 1])
    end

    if key and #samples > 0 then
        strokeStates[key] = {
            lastSample = samples[#samples],
            sequence = sequence
        }
    end

    local changed = #points > 0 and display.setPixelBatch(event, points, true) or 0
    if key and touchdisplay.drawEnded(event) then strokeStates[key] = nil end
    return changed
end

return touchdisplay
