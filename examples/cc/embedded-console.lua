-- Runs inside a computer control desk. No peripheral is required.
local network = aeroworks.getNetwork()
print("desks:", network.memberCount)

local desks = aeroworks.getDesks()
for _, desk in ipairs(desks) do
  print(desk.index, desk.id, desk.variant, desk.owner)
end

local target = desks[#desks]
aeroworks.setDisplayText(target.id, "big", "ON")

while true do
  local _, deskId, deskIndex, socket, socketName, moduleId, value, channel =
    os.pullEvent("cc_aeroworks_console_input")

  print(deskIndex, deskId, socketName, moduleId, channel, value)
  if value == nil then
    aeroworks.clearDisplay(target.id, "big")
  else
    aeroworks.setDisplayNumber(target.id, "big", value, false)
  end
end
