-- Interactive touch regression handler for CC-Aeroworks large displays.
--
-- Assign this file as the display's touch/input handler. While holding the configured
-- Display interaction key, move the pseudo finger with the mouse and trigger actions:
--   right mouse button -> hold
--   left mouse button  -> tap
--
-- Every accepted action is printed to the embedded computer terminal and leaves a small
-- marker at the server-resolved pixel coordinate. The display is cleared before each marker,
-- so a stale marker cannot be mistaken for a newly accepted input.

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
    counters = { total = 0, tap = 0, hold = 0, double_tap = 0, other = 0 }
    state[key] = counters
  end
  return counters
end

local function setPixel(event, x, y)
  local width = tonumber(event.width) or 0
  local height = tonumber(event.height) or 0
  if x < 1 or y < 1 or x > width or y > height then return end
  touchdisplay.setPixel(event, x, y, true)
end

local function drawPlus(event)
  local x, y = touchdisplay.position(event)
  setPixel(event, x, y)
  setPixel(event, x - 1, y)
  setPixel(event, x + 1, y)
  setPixel(event, x, y - 1)
  setPixel(event, x, y + 1)
end

local function drawBox(event)
  local x, y = touchdisplay.position(event)
  for dx = -1, 1 do
    setPixel(event, x + dx, y - 1)
    setPixel(event, x + dx, y + 1)
  end
  setPixel(event, x - 1, y)
  setPixel(event, x + 1, y)
end

local function drawX(event)
  local x, y = touchdisplay.position(event)
  setPixel(event, x, y)
  setPixel(event, x - 1, y - 1)
  setPixel(event, x + 1, y - 1)
  setPixel(event, x - 1, y + 1)
  setPixel(event, x + 1, y + 1)
end

local function drawDot(event)
  local x, y = touchdisplay.position(event)
  setPixel(event, x, y)
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
  local u, v = touchdisplay.normalizedPosition(event)
  local normalized
  if type(u) == "number" and type(v) == "number" then
    normalized = string.format("u=%.4f v=%.4f", u, v)
  else
    normalized = "u=? v=?"
  end

  print(string.format(
    "[touch-test] %-10s pixel=%d,%d / %dx%d  %s  total=%d tap=%d hold=%d",
    action,
    tonumber(x) or -1,
    tonumber(y) or -1,
    tonumber(width) or -1,
    tonumber(height) or -1,
    normalized,
    counters.total,
    counters.tap,
    counters.hold
  ))
end

local function render(event, marker)
  touchdisplay.clear(event)
  marker(event)
  report(event)
end

return {
  onTap = function(event)
    assert(touchdisplay.isTap(event), "onTap received a non-tap event")
    render(event, drawPlus)
  end,

  onHold = function(event)
    assert(touchdisplay.isHold(event), "onHold received a non-hold event")
    render(event, drawBox)
  end,

  -- Kept as a compatibility diagnostic. Current combined mouse input no longer emits it.
  onDoubleTap = function(event)
    assert(touchdisplay.isDoubleTap(event), "onDoubleTap received a non-double-tap event")
    render(event, drawX)
  end,

  onPointer = function(event)
    render(event, drawDot)
  end,
}
