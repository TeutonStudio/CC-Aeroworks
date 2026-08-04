-- Minimal dashboard: mirrors the first changed input value to the display
-- mounted in the named "big" socket.

local PERIPHERAL_TYPE = "cc_aeroworks_control_desk"
local EVENT_NAME = "cc_aeroworks_desk_input"
local DISPLAY_SOCKET = "big"

local desk = peripheral.find(PERIPHERAL_TYPE)
assert(desk, "No CC-Aeroworks control desk peripheral found")

local display = desk.getDisplay(DISPLAY_SOCKET)
assert(display, "No CC-Aeroworks display is mounted in socket 'big'")

local function render(value)
  if type(value) == "number" then
    desk.setDisplayNumber(DISPLAY_SOCKET, value, false)
  else
    desk.setDisplayText(DISPLAY_SOCKET, "---")
  end
end

render(0)
print("Dashboard active. Press Ctrl+T to stop.")

while true do
  local _, _, _, _, value = os.pullEvent(EVENT_NAME)
  render(value)
end
