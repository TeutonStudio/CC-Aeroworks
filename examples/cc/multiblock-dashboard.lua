-- One modem or direct attachment exposes the complete control-desk multiblock.
local console = peripheral.find("cc_aeroworks_control_desk")
assert(console, "No CC-Aeroworks control desk found")

local desks = console.getDesks()
for _, desk in ipairs(desks) do
  print(desk.index, desk.id, desk.variant)
end

while true do
  local _, _, deskId, deskIndex, socket, _, value, channel, socketName =
    os.pullEvent("cc_aeroworks_multiblock_input")

  print(deskIndex, deskId, socketName, channel, value)

  local target = desks[#desks]
  if value == nil then
    console.clearDeskDisplay(target.id, "big")
  else
    console.setDeskDisplayNumber(target.id, "big", value, false)
  end
end
