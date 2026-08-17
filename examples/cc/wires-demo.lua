-- Purpose: inspect configured Drive By Wire / Redstone channels without changing them.
-- Runs on: an embedded ComputerControlDesk computer.
-- Requires: cc_aeroworks.wires.

local wires = require("cc_aeroworks.wires")

print("Backend: " .. tostring(wires.getBackend()))
print("Enabled: " .. tostring(wires.isEnabled()))
print("Configured wire channels:")
print(textutils.serialize(wires.list()))
