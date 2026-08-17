-- Purpose: temporarily override one discovered native Aeroworks control channel.
-- Runs on: an embedded ComputerControlDesk computer.
-- Requires: cc_aeroworks.controls and at least one native control channel.
-- Side effect: moves the selected control briefly, then releases only that override.

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

local selected
while not selected do
  write(string.format("Select channel [1-%d]: ", #channels))
  local choice = tonumber(read())
  if choice and choice % 1 == 0 then
    selected = channels[choice]
  end
  if not selected then
    print("Invalid selection")
  end
end

write("Temporary override value [-15..15, default 8]: ")
local requested = tonumber(read()) or 8
local value = math.max(-15, math.min(15, math.floor(requested)))

local ok, err = pcall(function()
  controls.override(selected.desk, selected.socket, selected.channel, value)
  print(string.format(
    "Override active: %s / %s / %s = %d",
    tostring(selected.desk),
    tostring(selected.socket),
    tostring(selected.channel),
    value
  ))
  sleep(2)
end)

local released, releaseError = pcall(
  controls.release,
  selected.desk,
  selected.socket,
  selected.channel
)

if released then
  print("Selected control override released.")
else
  printError("Could not release selected override: " .. tostring(releaseError))
end

if not ok then
  error(err, 0)
end
