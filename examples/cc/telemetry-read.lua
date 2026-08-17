-- Purpose: read structured Create telemetry from an embedded ComputerControlDesk.
-- Runs on: an embedded ComputerControlDesk computer.
-- Requires: cc_aeroworks.telemetry.
-- Usage: telemetry-read.lua [source-name-or-id]

local telemetry = require("cc_aeroworks.telemetry")
local args = { ... }

if args[1] then
  local source = telemetry.get(args[1])
  if not source then
    error("Telemetry source not found: " .. args[1], 0)
  end
  print(textutils.serialize(source))
  return
end

local sources = telemetry.list()
local ids = {}
for id in pairs(sources) do
  ids[#ids + 1] = id
end
table.sort(ids)

if #ids == 0 then
  print("No telemetry sources available.")
  return
end

for _, id in ipairs(ids) do
  local source = sources[id]
  local name = source.alias or source.createLabel or id
  local suffix = source.stale and " [stale]" or ""
  print(string.format("%s  kind=%s%s", tostring(name), tostring(source.kind), suffix))
end
