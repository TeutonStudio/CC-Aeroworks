-- Interactive touch regression handler for CC-Aeroworks large displays.
--
-- Assign this file as the display's touch/input handler. While holding the configured
-- Display interaction key, move the pseudo finger with the mouse and trigger actions:
--   right mouse button -> draw while held, end on release
--   left mouse button  -> tap
--
-- Draw uses touchdisplay.drawStroke(event): the server-resolved sub-tick sample batch is
-- Hermite-interpolated and persisted through one native packed-raster patch.
--
-- Diagnostics are intentionally throttled. Printing every 20 Hz draw sample makes the regression
-- test itself part of the scheduling problem it is trying to measure, a surprisingly human design.

local touchdisplay = require("touchdisplay")

local STATE_KEY = "__cc_aeroworks_touch_test_state"
local LOG_EVERY = 10

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
    counters = {
      total = 0,
      tap = 0,
      draw = 0,
      other = 0,
      lastGesture = nil,
      lastSequence = nil,
      gaps = 0,
    }
    state[key] = counters
  end
  return counters
end

local function drawPlus(event, x, y)
  local points = {
    { x = x, y = y },
    { x = x - 1, y = y },
    { x = x + 1, y = y },
    { x = x, y = y - 1 },
    { x = x, y = y + 1 },
  }
  local width = tonumber(event.width) or 0
  local height = tonumber(event.height) or 0
  local valid = {}
  for _, point in ipairs(points) do
    if point.x >= 1 and point.y >= 1 and point.x <= width and point.y <= height then
      valid[#valid + 1] = point
    end
  end
  if #valid > 0 then touchdisplay.setPixelBatch(event, valid, true) end
end

local function reportDraw(event, changed, counters)
  local x, y = touchdisplay.position(event)
  local sx, sy = touchdisplay.drawStart(event)
  local dx, dy = touchdisplay.drawDelta(event)
  local directionU, directionV = touchdisplay.drawDirection(event)
  local speed = touchdisplay.drawSpeed(event)
  local effectiveSamples = touchdisplay.drawSamples(event)
  local rawSamples = type(event.samples) == "table" and #event.samples or 0
  local gestureId, sequence = touchdisplay.drawIdentity(event)
  local numericSequence = tonumber(sequence) or -1
  local gestureKey = tostring(gestureId)

  local gap = false
  local expected = nil
  if counters.lastGesture == gestureKey and counters.lastSequence ~= nil and numericSequence > counters.lastSequence + 1 then
    gap = true
    expected = counters.lastSequence + 1
    counters.gaps = counters.gaps + 1
  end

  if counters.lastGesture ~= gestureKey then
    counters.lastGesture = gestureKey
    counters.lastSequence = nil
  end
  counters.lastSequence = numericSequence

  if gap then
    printError(string.format(
      "[touch-test] SEQ GAP id=%s expected=%d got=%d missing=%d",
      gestureKey, expected, numericSequence, numericSequence - expected
    ))
  end

  local ended = touchdisplay.drawEnded(event)
  local shouldLog = numericSequence == 0 or ended or gap or (numericSequence >= 0 and numericSequence % LOG_EVERY == 0)
  if not shouldLog then return end

  print(string.format(
    "[touch-test] DRAW id=%s seq=%d start=%d,%d current=%d,%d delta=%d,%d dir=%.3f,%.3f speed=%.4f/s raw=%d effective=%d changed=%d gaps=%d end=%s total=%d",
    gestureKey, numericSequence, tonumber(sx) or -1, tonumber(sy) or -1,
    tonumber(x) or -1, tonumber(y) or -1, tonumber(dx) or 0, tonumber(dy) or 0,
    tonumber(directionU) or 0, tonumber(directionV) or 0, tonumber(speed) or 0,
    rawSamples, #effectiveSamples, tonumber(changed) or 0, counters.gaps,
    tostring(ended), counters.total
  ))
end

local function report(event, changed)
  local counters = countersFor(event)
  local action = tostring(event.action or "pointer")
  counters.total = counters.total + 1
  if counters[action] ~= nil then counters[action] = counters[action] + 1 else counters.other = counters.other + 1 end

  if touchdisplay.isDraw(event) then
    reportDraw(event, changed, counters)
    return
  end

  local x, y, width, height = touchdisplay.position(event)
  local u, v = touchdisplay.normalizedPosition(event)
  print(string.format(
    "[touch-test] TAP pixel=%d,%d / %dx%d u=%s v=%s total=%d tap=%d draw=%d",
    tonumber(x) or -1, tonumber(y) or -1, tonumber(width) or -1, tonumber(height) or -1,
    type(u) == "number" and string.format("%.4f", u) or "?",
    type(v) == "number" and string.format("%.4f", v) or "?",
    counters.total, counters.tap, counters.draw
  ))
end

return {
  onTap = function(event)
    assert(touchdisplay.isTap(event), "onTap received a non-tap event")
    touchdisplay.clear(event)
    local x, y = touchdisplay.position(event)
    drawPlus(event, x, y)
    report(event, 0)
  end,

  onDraw = function(event)
    assert(touchdisplay.isDraw(event), "onDraw received a non-draw event")
    local _, sequence = touchdisplay.drawIdentity(event)

    if tonumber(sequence) == 0 then touchdisplay.clear(event) end

    local changed = touchdisplay.drawStroke(event)
    report(event, changed)
  end,

  onPointer = function(event)
    report(event, 0)
  end,
}
