-- Purpose: inspect the complete ControlDesk network through the embedded peripherals API.
-- Runs on: an embedded ComputerControlDesk computer.
-- Requires: cc_aeroworks.peripherals and an active desk network.

local peripherals = require("cc_aeroworks.peripherals")

local network = peripherals.getNetwork()
print(string.format(
  "Network: %s, %s desk(s), %s peripheral(s)",
  tostring(network.state),
  tostring(network.deskCount or "?"),
  tostring(network.peripheralCount or "?")
))

local desks = peripherals.find("ControlDesk")
local addresses = {}
for address in pairs(desks) do
  addresses[#addresses + 1] = address
end
table.sort(addresses)

for _, address in ipairs(addresses) do
  local desk = desks[address]
  local info = desk.getInfo()
  print(string.format(
    "%s  id=%s  modules=%d  displays=%d",
    tostring(address),
    tostring(info.id or "?"),
    #desk.getModules(),
    #desk.getDisplays()
  ))
end
