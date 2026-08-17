-- Purpose: inspect one locally reachable CC-Aeroworks ControlDesk.
-- Runs on: a normal or wired CC:Tweaked computer.
-- Requires: a reachable ControlDesk peripheral.

local desk = peripheral.find("ControlDesk")
if not desk then
  error("No reachable ControlDesk peripheral found", 0)
end

local info = desk.getInfo()
print("ControlDesk")
print("  id: " .. tostring(info.id or "?"))
print("  address: " .. tostring(info.address or "local"))
print("  variant: " .. tostring(info.variant or "?"))

print("Modules:")
for _, module in ipairs(desk.getModules()) do
  print(string.format(
    "  %s: %s (%s)",
    tostring(module.socketName or module.socket or "?"),
    tostring(module.id or "unknown"),
    tostring(module.kind or "unknown")
  ))
end

print("Displays:")
for _, display in ipairs(desk.getDisplays()) do
  print(string.format(
    "  %s: %dx%d pixels",
    tostring(display.socketName or display.socket or "?"),
    tonumber(display.pixelWidth) or 0,
    tonumber(display.pixelHeight) or 0
  ))
end
