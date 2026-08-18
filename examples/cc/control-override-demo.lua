-- Purpose: temporarily override one or more discovered native Aeroworks control channels as one batch.
-- Runs on: an embedded ComputerControlDesk computer.
-- Requires: cc_aeroworks.controls and at least one native control channel.
-- Side effect: moves the selected controls briefly, then releases every override owned by this computer.

local controls = require("cc_aeroworks.controls")

local channels = controls.getChannels()
if #channels == 0 then
  error("No native Aeroworks control channels found in this ControlDesk network", 0)
end

table.sort(channels, function(a, b)
  local left = table.concat({ tostring(a.desk), tostring(a.socket), tostring(a.channel) }, "/")
  local right = table.concat({ tostring(b.desk), tostring(b.socket), tostring(b.channel) }, "/")
  return left < right
end)

print("Native control channels:")
for index, channel in ipairs(channels) do
  print(string.format(
    "  %d) %s / %s / %s (%s)",
    index,
    tostring(channel.desk),
    tostring(channel.socket),
    tostring(channel.channel),
    tostring(channel.module or "unknown module")
  ))
end

local selected = {}
while #selected == 0 do
  write(string.format("Select channel numbers [1-%d], separated by commas: ", #channels))
  local seen = {}
  for token in tostring(read() or ""):gmatch("[^,%s]+") do
    local index = tonumber(token)
    if index and index % 1 == 0 and channels[index] and not seen[index] then
      seen[index] = true
      table.insert(selected, channels[index])
    end
  end
  if #selected == 0 then
    print("No valid channel selected")
  end
end

write("Temporary override value [-15..15, default 8]: ")
local requested = tonumber(read()) or 8
local value = math.max(-15, math.min(15, math.floor(requested)))

local commands = {}
for _, channel in ipairs(selected) do
  table.insert(commands, {
    desk = channel.desk,
    socket = channel.socket,
    channel = channel.channel,
    value = value,
  })
end

local ok, err = pcall(function()
  local applied = controls.overrideBatch(commands)
  print(string.format("Grouped override active: %d channel(s) = %d", applied, value))
  sleep(2)
end)

-- This demo intentionally owns the complete override lifetime. Always release all authority even
-- when overrideBatch or the work performed while authority is held raises an error.
local released, releaseResult = pcall(controls.releaseAll)
if released then
  print(string.format("Released %d control override(s).", tonumber(releaseResult) or 0))
else
  printError("Could not release control overrides: " .. tostring(releaseResult))
end

if not ok then
  error(err, 0)
end
if not released then
  error(releaseResult, 0)
end
