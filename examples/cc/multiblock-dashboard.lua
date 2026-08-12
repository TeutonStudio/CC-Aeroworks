-- Live read-only overview of every reachable CC-Aeroworks ControlDesk.
--
-- This program supports both API views:
--   * embedded Computer Control Desk: global `peripherals` API, complete desk network
--   * ordinary/wired CC:Tweaked computer: every reachable ControlDesk peripheral
--
-- It intentionally does not mirror one input to one display. dashboard.lua already
-- owns that job. This script answers a different question: which desks are reachable
-- right now, and how many modules, numeric inputs, displays and adjacent peripherals
-- does each one expose?

local CONTROL_DESK_TYPE = "ControlDesk"
local REFRESH_SECONDS = 2

local networkApi = rawget(_G, "peripherals")
local embedded = type(networkApi) == "table"
  and type(networkApi.getNetwork) == "function"
  and type(networkApi.find) == "function"

local function compactType(value)
  return tostring(value):lower():gsub("^.-:", ""):gsub("[%s_%-]", "")
end

local function typeMatchesControlDesk(value)
  local compact = compactType(value)
  return compact == "controldesk" or compact == "ccaeroworkscontroldesk"
end

local function localPeripheralIsControlDesk(name)
  if type(peripheral.hasType) == "function" then
    local ok, matches = pcall(peripheral.hasType, name, CONTROL_DESK_TYPE)
    if ok and matches then
      return true
    end
  end

  local results = { pcall(peripheral.getType, name) }
  if not results[1] then
    return false
  end

  for index = 2, #results do
    local value = results[index]
    if type(value) == "string" and typeMatchesControlDesk(value) then
      return true
    elseif type(value) == "table" then
      for _, nested in pairs(value) do
        if type(nested) == "string" and typeMatchesControlDesk(nested) then
          return true
        end
      end
    end
  end

  return false
end

local function coordinates(info, fallback)
  if type(info) ~= "table" then
    return fallback
  end
  if type(info.address) == "string" and info.address ~= "" then
    return info.address
  end
  if info.x ~= nil and info.y ~= nil and info.z ~= nil then
    return string.format("%s,%s,%s", tostring(info.x), tostring(info.y), tostring(info.z))
  end
  return fallback
end

local function explainNetworkError(message)
  local text = tostring(message)
  local lower = text:lower()

  if lower:find("multiple computer control desks", 1, true) then
    return text .. " | leave exactly one embedded Computer Control Desk connected"
  elseif lower:find("partially loaded", 1, true) then
    return text .. " | load all chunks containing the desk row"
  elseif lower:find("exceeds 64 desks", 1, true) then
    return text .. " | split the desk row below the 64-desk limit"
  elseif lower:find("does not own", 1, true) then
    return text .. " | use the Computer Control Desk which owns this network"
  end

  return text
end

local function safeTable(fn, ...)
  if type(fn) ~= "function" then
    return nil, "method unavailable"
  end
  local ok, value = pcall(fn, ...)
  if not ok then
    return nil, tostring(value)
  end
  if type(value) ~= "table" then
    return nil, "returned " .. type(value) .. " instead of table"
  end
  return value
end

local function discoverDesks()
  local desks = {}
  local warnings = {}
  local network = nil
  local state = embedded and "unknown" or "local"
  local errorMessage = nil

  if embedded then
    local okNetwork, currentNetwork = pcall(networkApi.getNetwork)
    if not okNetwork then
      return {
        mode = "embedded",
        state = "error",
        desks = desks,
        warnings = warnings,
        error = explainNetworkError(currentNetwork),
      }
    end

    if type(currentNetwork) ~= "table" then
      return {
        mode = "embedded",
        state = "error",
        desks = desks,
        warnings = warnings,
        error = "peripherals.getNetwork() returned " .. type(currentNetwork) .. " instead of a table",
      }
    end

    network = currentNetwork
    state = tostring(network.state or "unknown")

    if state == "active" then
      local okFind, found = pcall(networkApi.find, CONTROL_DESK_TYPE)
      if not okFind then
        errorMessage = explainNetworkError(found)
      elseif type(found) ~= "table" then
        errorMessage = "peripherals.find('ControlDesk') returned " .. type(found) .. " instead of a table"
      else
        for address, desk in pairs(found) do
          local okInfo, info = pcall(desk.getInfo)
          if okInfo and type(info) == "table" then
            desks[#desks + 1] = {
              desk = desk,
              address = coordinates(info, tostring(address)),
              stableId = tostring(info.id or address),
              peripheralName = nil,
              info = info,
            }
          else
            warnings[#warnings + 1] = string.format(
              "Skipped Desk %s because getInfo failed: %s",
              tostring(address),
              tostring(info)
            )
          end
        end
      end
    else
      errorMessage = string.format(
        "embedded desk network is '%s'; topology details require state=active",
        state
      )
    end
  else
    for _, name in ipairs(peripheral.getNames()) do
      if localPeripheralIsControlDesk(name) then
        local desk = peripheral.wrap(name)
        if desk then
          local okInfo, info = pcall(desk.getInfo)
          if okInfo and type(info) == "table" then
            desks[#desks + 1] = {
              desk = desk,
              address = coordinates(info, name),
              stableId = tostring(info.id or name),
              peripheralName = name,
              info = info,
            }
          else
            warnings[#warnings + 1] = string.format(
              "Skipped peripheral %s because getInfo failed: %s",
              name,
              tostring(info)
            )
          end
        else
          warnings[#warnings + 1] = "Skipped peripheral " .. name .. " because peripheral.wrap returned nil"
        end
      end
    end
  end

  table.sort(desks, function(a, b)
    if a.address ~= b.address then
      return a.address < b.address
    end
    return a.stableId < b.stableId
  end)

  return {
    mode = embedded and "embedded" or "local/wired",
    state = state,
    network = network,
    desks = desks,
    warnings = warnings,
    error = errorMessage,
  }
end

local function countNumericInputs(inputs)
  if type(inputs) ~= "table" then
    return 0
  end

  local count = 0
  for _, value in pairs(inputs) do
    if type(value) == "number" then
      count = count + 1
    elseif type(value) == "table" then
      for _, channelValue in pairs(value) do
        if type(channelValue) == "number" then
          count = count + 1
        end
      end
    end
  end
  return count
end

local function buildRows(snapshot)
  local rows = {}

  for _, entry in ipairs(snapshot.desks) do
    local modules, moduleError = safeTable(entry.desk.getModules)
    local inputs, inputError = safeTable(entry.desk.getInputs)
    local displays, displayError = safeTable(entry.desk.getDisplays)

    local neighbours = nil
    local neighbourError = nil
    if type(entry.desk.getPeripherals) == "function" then
      local nearby
      nearby, neighbourError = safeTable(entry.desk.getPeripherals)
      if nearby then
        local count = 0
        for _ in pairs(nearby) do count = count + 1 end
        neighbours = count
      end
    end

    local errors = {}
    if moduleError then errors[#errors + 1] = "modules: " .. moduleError end
    if inputError then errors[#errors + 1] = "inputs: " .. inputError end
    if displayError then errors[#errors + 1] = "displays: " .. displayError end
    if neighbourError and type(entry.desk.getPeripherals) == "function" then
      errors[#errors + 1] = "peripherals: " .. neighbourError
    end

    rows[#rows + 1] = {
      address = entry.address,
      variant = tostring(entry.info.variant or "control_desk"),
      computer = entry.info.computer == true,
      modules = modules and #modules or nil,
      inputs = inputs and countNumericInputs(inputs) or nil,
      displays = displays and #displays or nil,
      neighbours = neighbours,
      peripheralName = entry.peripheralName,
      errors = errors,
    }
  end

  return rows
end

local function clip(value, width)
  local text = tostring(value or "")
  if #text <= width then
    return text .. string.rep(" ", width - #text)
  end
  if width <= 1 then
    return text:sub(1, width)
  end
  return text:sub(1, width - 1) .. "~"
end

local function cell(value)
  if value == nil then
    return "-"
  end
  return tostring(value)
end

local function render(snapshot, rows, offset)
  term.clear()
  term.setCursorPos(1, 1)

  local width, height = term.getSize()
  local visibleRows = math.max(1, height - 7)
  local maxOffset = math.max(0, #rows - visibleRows)
  offset = math.max(0, math.min(offset, maxOffset))

  print("CC-Aeroworks multiblock dashboard")

  if snapshot.mode == "embedded" then
    local network = snapshot.network or {}
    print(string.format(
      "Mode: embedded | state=%s | desks=%s | peripherals=%s",
      tostring(snapshot.state),
      tostring(network.deskCount or #rows),
      tostring(network.peripheralCount or "?")
    ))
  else
    print(string.format("Mode: local/wired | reachable desks=%d", #rows))
  end

  if snapshot.error then
    printError("Status: " .. snapshot.error)
  elseif #rows == 0 then
    if snapshot.mode == "embedded" then
      printError("No inspectable desks. Verify the embedded desk network and refresh.")
    else
      printError("No reachable ControlDesk peripherals. Attach directly or enable the wired modems.")
    end
  elseif snapshot.warnings and #snapshot.warnings > 0 then
    printError("Warning: " .. snapshot.warnings[1])
  else
    print("Live topology; refreshes automatically every " .. REFRESH_SECONDS .. "s")
  end

  if width >= 48 then
    print("#  address             M  In D  P  type")
  else
    print("#  address          M/I/D/P")
  end

  local first = offset + 1
  local last = math.min(#rows, offset + visibleRows)
  for index = first, last do
    local row = rows[index]
    if width >= 48 then
      local typeLabel = row.variant .. (row.computer and "*" or "")
      print(string.format(
        "%2d %s %2s %3s %2s %2s %s",
        index,
        clip(row.address, 18),
        cell(row.modules),
        cell(row.inputs),
        cell(row.displays),
        cell(row.neighbours),
        clip(typeLabel, math.max(1, width - 36))
      ))
    else
      print(string.format(
        "%2d %s %s/%s/%s/%s",
        index,
        clip(row.address, 15),
        cell(row.modules),
        cell(row.inputs),
        cell(row.displays),
        cell(row.neighbours)
      ))
    end
  end

  local scroll = #rows > visibleRows and string.format(" | rows %d-%d/%d", first, last, #rows) or ""
  term.setCursorPos(1, height)
  write("q quit | r refresh | arrows/PgUp/PgDn" .. scroll)

  return offset, visibleRows
end

local function refresh()
  local snapshot = discoverDesks()
  local rows = buildRows(snapshot)
  return snapshot, rows
end

local snapshot, rows = refresh()
local offset = 0
local timer = os.startTimer(REFRESH_SECONDS)

while true do
  local visible
  offset, visible = render(snapshot, rows, offset)

  local event = { os.pullEventRaw() }
  local name = event[1]

  if name == "terminate" then
    term.clear()
    term.setCursorPos(1, 1)
    print("Multiblock dashboard stopped.")
    return
  elseif name == "key" then
    local key = event[2]
    if key == keys.q then
      term.clear()
      term.setCursorPos(1, 1)
      print("Multiblock dashboard stopped.")
      return
    elseif key == keys.r then
      snapshot, rows = refresh()
      offset = 0
    elseif key == keys.up then
      offset = math.max(0, offset - 1)
    elseif key == keys.down then
      offset = math.min(math.max(0, #rows - visible), offset + 1)
    elseif key == keys.pageUp then
      offset = math.max(0, offset - visible)
    elseif key == keys.pageDown then
      offset = math.min(math.max(0, #rows - visible), offset + visible)
    elseif key == keys.home then
      offset = 0
    elseif key == keys['end'] then
      offset = math.max(0, #rows - visible)
    end
  elseif name == "timer" and event[2] == timer then
    snapshot, rows = refresh()
    timer = os.startTimer(REFRESH_SECONDS)
  elseif name == "term_resize" then
    -- render() adapts on the next loop iteration.
  elseif embedded and (
      name == "cc_aeroworks_console_changed"
      or name == "cc_aeroworks_peripheral_attached"
      or name == "cc_aeroworks_peripheral_detached") then
    snapshot, rows = refresh()
  elseif not embedded and (name == "peripheral" or name == "peripheral_detach") then
    snapshot, rows = refresh()
  end
end
