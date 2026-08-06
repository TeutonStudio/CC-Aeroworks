-- Mirrors the first numeric desk input in the multiblock to the first display.
-- Run on the embedded Computer Control Desk.

local DISPLAY_SOCKET = "big"
local desks = peripherals.find("ControlDesk")
assert(next(desks), "No ControlDesk adapters in the current network")

local inputDesk
local inputSocket
local displayDesk

for address, desk in pairs(desks) do
  if not displayDesk and desk.getDisplay(DISPLAY_SOCKET) then
    displayDesk = desk
    print("Display desk: " .. address)
  end

  if not inputDesk then
    for socket, value in pairs(desk.getInputs()) do
      if type(value) == "number" then
        inputDesk = desk
        inputSocket = socket
        print(("Input desk: %s, socket %d"):format(address, socket))
        break
      end
    end
  end
end

assert(inputDesk, "No numeric desk input found")
assert(displayDesk, "No CC-Aeroworks display is mounted in socket 'big'")

print("Dashboard active. Press Ctrl+T to stop.")
while true do
  local value = inputDesk.getInput(inputSocket)
  if type(value) == "number" then
    displayDesk.setDisplayNumber(DISPLAY_SOCKET, value, false)
  else
    displayDesk.setDisplayText(DISPLAY_SOCKET, "---")
  end
  sleep(0.1)
end
