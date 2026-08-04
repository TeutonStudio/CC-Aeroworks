-- Prints all CC-Aeroworks control desk input events.
-- Run on a directly attached computer or one connected through a wired modem.

local PERIPHERAL_TYPE = "cc_aeroworks_control_desk"
local EVENT_NAME = "cc_aeroworks_desk_input"

local desk = peripheral.find(PERIPHERAL_TYPE)
assert(desk, "No CC-Aeroworks control desk peripheral found")

print("Connected sockets:")
for _, socket in ipairs(desk.getSockets()) do
  print(("  %s = %d"):format(socket.name, socket.index))
end

print("Waiting for " .. EVENT_NAME .. " events. Press Ctrl+T to stop.")
while true do
  local _, peripheralName, socket, moduleId, value, channel, socketName =
    os.pullEvent(EVENT_NAME)

  print(("%s %s[%d] %s/%s = %s"):format(
    peripheralName,
    socketName,
    socket,
    moduleId,
    channel,
    textutils.serialize(value, { compact = true })
  ))
end
