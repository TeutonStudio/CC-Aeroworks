-- Purpose: inspect a logical CC-Aeroworks channel without changing cockpit state.
-- Runs on: an embedded ComputerControlDesk computer.
-- Requires: cc_aeroworks.channels.
-- Usage: channels-demo.lua [path]

local channels = require("cc_aeroworks.channels")
local args = { ... }
local path = args[1] or "/"

if path == "/" then
  print("Available channel tree entries:")
  print(textutils.serialize(channels.ls(path)))
  return
end

print("Path: " .. path)
print("Metadata:")
print(textutils.serialize(channels.stat(path)))
print("Value:")
print(textutils.serialize(channels.read(path)))
