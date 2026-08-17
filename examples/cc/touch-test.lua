-- Interactive touch regression handler for CC-Aeroworks large displays.
--
-- Assign this file as the display's touch/input handler. While holding the configured
-- Display interaction key, move the pseudo finger with the mouse and trigger actions:
--   right mouse button -> draw while held, end on release
--   left mouse button  -> tap
--
-- Tap clears the display and draws a plus. Draw clears only on gesture start, then connects each
-- server-resolved event segment using deltaX/deltaY from the previous event. This deliberately
-- exercises start coordinates, per-event deltas, ordering and the explicit end flag.

local touchdisplay = require("touchdisplay")

local STATE_KEY = "__cc_aeroworks_touch_test_state"
local state = rawget(_G, STATE_KEY)
if type(state) ~= "table" then
  state = {}
  rawset(_G, STATE_KEY, state)
end

local function sourceKey(event)
  return table.concat({
    tostring(event.deskId or event.attachment or "desk"),
    tostring(event.socketName or event.socket or "socket")
  }, ":")
end

local function countersFor(event)
  local key = sourceKey(event)
  local counters = state[key]
  if type(counters) ~= "table" then
    counters = { total = 0, tap = 0, draw = 0, other = 0 }
    state[key] = counters
  end
  return counters
end

local function setPixel(event, x, y)
  local width = tonumber(event.width) or 0
  local height = tonumber(event.height) or 0
  x = math.floor(tonumber(x) or 0)
  y = math.floor(tonumber(y) or 0)
  if x < 1 or y < 1 or x > width or y > height then return end
  touchdisplay.setPixel(event, x, y, true)
end

local function drawPlus(event, x, y)
  setPixel(event, x, y)
  setPixel(event, x - 1, y)
  setPixel(event, x + 1, y)
  setPixel(event, x, y - 1)
  setPixel(event, x, y + 1)
end

local function drawLine(event, x0, y0, x1, y1)
  x0, y0 = math.floor(x0), math.floor(y0)
  x1, y1 = math.floor(x1), math.floor(y1)
  local dx = math.abs(x1 - x0)
  local sx = x0 < x1 and 1 or -1
  local dy = -math.abs(y1 - y0)
  local sy = y0 < y1 and 1 or -1
  local err = dx + dy

  while true do
    setPixel(event, x0, y0)
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

local function report(event)
  local counters = countersFor(event)
  local action = tostring(event.action or "pointer")
  counters.total = counters.total + 1
  if counters[action] ~= nil then
    counters[action] = counters[action] + 1
  else
    counters.other = counters.other + 1
  end

  local x, y, width, height = touchdisplay.position(event)
  if touchdisplay.isDraw(event) then
    local sx, sy = touchdisplay.drawStart(event)
    local dx, dy = touchdisplay.drawDelta(event)
    local gestureId, sequence = touchdisplay.drawIdentity(event)
    print(string.format(
      "[touch-test] DRAW id=%s seq=%s start=%d,%d current=%d,%d delta=%d,%d end=%s total=%d",
      tostring(gestureId), tostring(sequence), tonumber(sx) or -1, tonumber(sy) or -1,
      tonumber(x) or -1, tonumber(y) or -1, tonumber(dx) or 0, tonumber(dy) or 0,
      tostring(touchdisplay.drawEnded(event)), counters.total
    ))
    return
  end

  local u, v = touchdisplay.normalizedPosition(event)
  print(string.format(
    "[touch-test] TAP pixel=%d,%d / %dx%d u=%s v=%s total=%d tap=%d draw=%d",
    tonumber(x) or -1,
    tonumber(y) or -1,
    tonumber(width) or -1,
    tonumber(height) or -1,
    type(u) == "number" and string.format("%.4f", u) or "?",
    type(v) == "number" and string.format("%.4f", v) or "?",
    counters.total,
    counters.tap,
    counters.draw
  ))
end

return {
  onTap = function(event)
    assert(touchdisplay.isTap(event), "onTap received a non-tap event")
    touchdisplay.clear(event)
    local x, y = touchdisplay.position(event)
    drawPlus(event, x, y)
    report(event)
  end,

  onDraw = function(event)
    assert(touchdisplay.isDraw(event), "onDraw received a non-draw event")
    local x, y = touchdisplay.position(event)
    local dx, dy = touchdisplay.drawDelta(event)
    local _, sequence = touchdisplay.drawIdentity(event)

    if tonumber(sequence) == 0 then touchdisplay.clear(event) end
    drawLine(event, x - dx, y - dy, x, y)
    if touchdisplay.drawEnded(event) then drawPlus(event, x, y) end
    report(event)
  end,

  onPointer = function(event)
    report(event)
  end,
}
