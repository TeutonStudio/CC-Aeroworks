-- Exercises the complete pixel API on the display mounted in "big".

local PERIPHERAL_TYPE = "cc_aeroworks_control_desk"
local DISPLAY_SOCKET = "big"

local desk = peripheral.find(PERIPHERAL_TYPE)
assert(desk, "No CC-Aeroworks control desk peripheral found")

local size = desk.getDisplaySize(DISPLAY_SOCKET)
assert(size, "No CC-Aeroworks display is mounted in socket 'big'")

local rows = {}
for y = 1, size.height do
  local row = {}
  for x = 1, size.width do
    local border = x == 1 or x == size.width or y == 1 or y == size.height
    row[x] = border and "1" or "0"
  end
  rows[y] = table.concat(row)
end

local written = desk.setDisplayPixels(DISPLAY_SOCKET, rows)
print("Wrote " .. #written .. " pixel rows")
sleep(2)

desk.clearDisplayPixels(DISPLAY_SOCKET)
print("Pixel display cleared")
