-- Exercises the complete pixel API after discovering every reachable CC-Aeroworks display.
-- Embedded Computer Control Desks scan the complete desk network. Normal CC:Tweaked
-- computers scan every locally attached ControlDesk peripheral.

local CONTROL_DESK_TYPE = "ControlDesk"

local function deskAddress(desk, fallback)
  local info = desk.getInfo()
  if info.address then
    return info.address
  end
  if info.x ~= nil and info.y ~= nil and info.z ~= nil then
    return string.format("%s,%s,%s", info.x, info.y, info.z)
  end
  return fallback
end

local function displayLabel(display)
  local sizeName
  if display.width == 2 then
    sizeName = "small"
  elseif display.width == 3 then
    sizeName = "large"
  else
    sizeName = display.id or "unknown"
  end

  return string.format(
    "%s display at %s (%dx%d pixels)",
    sizeName,
    display.socketName,
    display.pixelWidth,
    display.pixelHeight
  )
end

local targets = {}

local function addDeskDisplays(desk, address)
  for _, display in ipairs(desk.getDisplays()) do
    targets[#targets + 1] = {
      desk = desk,
      deskAddress = address,
      socket = display.socketName,
      display = display,
    }
  end
end

local networkApi = rawget(_G, "peripherals")
if type(networkApi) == "table" and type(networkApi.find) == "function" then
  local desks = networkApi.find(CONTROL_DESK_TYPE)
  if type(desks) == "table" then
    for address, desk in pairs(desks) do
      addDeskDisplays(desk, address)
    end
  end
else
  local desks = { peripheral.find(CONTROL_DESK_TYPE) }
  for index, desk in ipairs(desks) do
    addDeskDisplays(desk, deskAddress(desk, "local-" .. index))
  end
end

table.sort(targets, function(a, b)
  if a.deskAddress ~= b.deskAddress then
    return a.deskAddress < b.deskAddress
  end
  return a.socket < b.socket
end)

if #targets == 0 then
  error("No CC-Aeroworks displays found", 0)
end

local target
if #targets == 1 then
  target = targets[1]
  print("Found one display; using it automatically:")
  print("  " .. target.deskAddress .. " / " .. displayLabel(target.display))
else
  print("Found " .. #targets .. " CC-Aeroworks displays:")
  for index, candidate in ipairs(targets) do
    print(string.format(
      "  %d) %s / %s",
      index,
      candidate.deskAddress,
      displayLabel(candidate.display)
    ))
  end

  while not target do
    write(string.format("Select display [1-%d]: ", #targets))
    local choice = tonumber(read())
    if choice and choice % 1 == 0 then
      target = targets[choice]
    end
    if not target then
      print("Invalid selection")
    end
  end
end

local size = target.desk.getDisplaySize(target.socket)
local rows = {}
for y = 1, size.height do
  local row = {}
  for x = 1, size.width do
    local border = x == 1 or x == size.width or y == 1 or y == size.height
    row[x] = border and "1" or "0"
  end
  rows[y] = table.concat(row)
end

print(string.format(
  "Writing %dx%d test pattern to %s / %s",
  size.width,
  size.height,
  target.deskAddress,
  target.socket
))

local written = target.desk.setDisplayPixels(target.socket, rows)
print("Wrote " .. #written .. " pixel rows")
sleep(2)

target.desk.clearDisplayPixels(target.socket)
print("Pixel display cleared")
