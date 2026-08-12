-- Read-only inspector for the embedded Computer Control Desk network.
--
-- Unlike dashboard.lua this program never writes to a display. Its purpose is to
-- inspect the current desk topology, modules, raw input values, displays and nearby
-- peripherals through the embedded global `peripherals` API.
--
-- That distinction is intentional: Aeroworks input values are raw control values.
-- Their sign/orientation is useful diagnostic information and should not be silently
-- reformatted just to fit a two-character display.

local CONTROL_DESK_TYPE = "ControlDesk"
local networkApi = rawget(_G, "peripherals")

if type(networkApi) ~= "table"
    or type(networkApi.getNetwork) ~= "function"
    or type(networkApi.find) ~= "function" then
  error(
    "embedded-console.lua requires the global peripherals API from an embedded Computer Control Desk.\n"
      .. "Use input-monitor.lua or multiblock-dashboard.lua on an ordinary/wired CC:Tweaked computer.",
    0
  )
end

local function explainNetworkError(message)
  local text = tostring(message)
  local lower = text:lower()

  if lower:find("multiple computer control desks", 1, true) then
    return text
      .. "\nHint: leave exactly one embedded Computer Control Desk connected to this desk row."
  elseif lower:find("partially loaded", 1, true) then
    return text
      .. "\nHint: load every chunk containing the connected desk row, then refresh."
  elseif lower:find("exceeds 64 desks", 1, true) then
    return text
      .. "\nHint: split the desk row so no connected network exceeds 64 desks."
  elseif lower:find("does not own", 1, true) then
    return text
      .. "\nHint: open the terminal from the Computer Control Desk which owns this network."
  end

  return text
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

local function safeCall(label, fn, ...)
  if type(fn) ~= "function" then
    return nil, label .. " is not available on this Desk handle"
  end

  local ok, value = pcall(fn, ...)
  if not ok then
    return nil, label .. " failed: " .. tostring(value)
  end
  return value
end

local function discover()
  local okNetwork, network = pcall(networkApi.getNetwork)
  if not okNetwork then
    return nil, explainNetworkError(network)
  end
  if type(network) ~= "table" then
    return nil, "peripherals.getNetwork() returned " .. type(network) .. "; expected a table"
  end

  local state = tostring(network.state or "unknown")
  if state ~= "active" then
    return {
      network = network,
      desks = {},
      error = string.format(
        "Desk network state is '%s'. Global Desk inspection is available only while the network is active.",
        state
      ),
    }
  end

  local okFind, found = pcall(networkApi.find, CONTROL_DESK_TYPE)
  if not okFind then
    return nil, explainNetworkError(found)
  end
  if type(found) ~= "table" then
    return nil, "peripherals.find('ControlDesk') returned " .. type(found) .. "; expected a table"
  end

  local desks = {}
  local warnings = {}

  for address, desk in pairs(found) do
    local info, infoError = safeCall("getInfo", desk.getInfo)
    if type(info) == "table" then
      desks[#desks + 1] = {
        desk = desk,
        address = coordinates(info, tostring(address)),
        stableId = tostring(info.id or address),
        info = info,
      }
    else
      warnings[#warnings + 1] = string.format(
        "Skipped Desk %s because %s",
        tostring(address),
        tostring(infoError or "getInfo returned an invalid value")
      )
    end
  end

  table.sort(desks, function(a, b)
    if a.address ~= b.address then
      return a.address < b.address
    end
    return a.stableId < b.stableId
  end)

  return {
    network = network,
    desks = desks,
    warnings = warnings,
  }
end

local function flattenInputs(inputs)
  local lines = {}

  if type(inputs) ~= "table" then
    return lines
  end

  local sockets = {}
  for socket in pairs(inputs) do
    sockets[#sockets + 1] = socket
  end
  table.sort(sockets, function(a, b) return tostring(a) < tostring(b) end)

  for _, socket in ipairs(sockets) do
    local value = inputs[socket]
    if type(value) == "number" then
      lines[#lines + 1] = string.format("socket %s: raw=%s", tostring(socket), tostring(value))
    elseif type(value) == "table" then
      local channels = {}
      for channel in pairs(value) do
        channels[#channels + 1] = channel
      end
      table.sort(channels, function(a, b) return tostring(a) < tostring(b) end)
      for _, channel in ipairs(channels) do
        lines[#lines + 1] = string.format(
          "socket %s / %s: raw=%s",
          tostring(socket),
          tostring(channel),
          tostring(value[channel])
        )
      end
    else
      lines[#lines + 1] = string.format("socket %s: %s", tostring(socket), type(value))
    end
  end

  return lines
end

local function moduleLines(modules)
  local lines = {}
  if type(modules) ~= "table" then
    return lines
  end

  for _, module in ipairs(modules) do
    if type(module) == "table" then
      lines[#lines + 1] = string.format(
        "%s: %s (%s)",
        tostring(module.socketName or module.socket or "?"),
        tostring(module.id or "unknown"),
        tostring(module.kind or "module")
      )
    else
      lines[#lines + 1] = tostring(module)
    end
  end
  return lines
end

local function displayLines(displays)
  local lines = {}
  if type(displays) ~= "table" then
    return lines
  end

  for _, display in ipairs(displays) do
    if type(display) == "table" then
      lines[#lines + 1] = string.format(
        "%s: %s, text-width=%s, pixels=%sx%s, mode=%s",
        tostring(display.socketName or display.socket or "?"),
        tostring(display.id or "unknown"),
        tostring(display.width or "?"),
        tostring(display.pixelWidth or "?"),
        tostring(display.pixelHeight or "?"),
        tostring(display.mode or "?")
      )
    else
      lines[#lines + 1] = tostring(display)
    end
  end
  return lines
end

local function peripheralLines(peripherals)
  local lines = {}
  if type(peripherals) ~= "table" then
    return lines
  end

  local addresses = {}
  for address in pairs(peripherals) do
    addresses[#addresses + 1] = address
  end
  table.sort(addresses, function(a, b) return tostring(a) < tostring(b) end)

  for _, address in ipairs(addresses) do
    local handle = peripherals[address]
    local label = "unknown"
    if type(handle) == "table" and type(handle.getPeripheralInfo) == "function" then
      local ok, info = pcall(handle.getPeripheralInfo)
      if ok and type(info) == "table" then
        label = tostring(info.type or info.primaryType or "unknown")
      end
    end
    lines[#lines + 1] = string.format("%s: %s", tostring(address), label)
  end

  return lines
end

local function appendSection(lines, title, values, failure)
  lines[#lines + 1] = ""
  lines[#lines + 1] = title
  if failure then
    lines[#lines + 1] = "  ERROR: " .. failure
  elseif #values == 0 then
    lines[#lines + 1] = "  (none)"
  else
    for _, value in ipairs(values) do
      lines[#lines + 1] = "  " .. value
    end
  end
end

local function buildDeskDetails(entry)
  local info = entry.info
  local lines = {
    "Desk " .. entry.address,
    string.rep("=", math.min(40, #entry.address + 5)),
    "stable id: " .. entry.stableId,
    "variant: " .. tostring(info.variant or "unknown"),
    "facing: " .. tostring(info.facing or "unknown"),
    "computer: " .. tostring(info.computer or false),
    "dimension: " .. tostring(info.dimension or "unknown"),
  }

  local modules, moduleError = safeCall("getModules", entry.desk.getModules)
  appendSection(lines, "Modules", moduleLines(modules), moduleError)

  local inputs, inputError = safeCall("getInputs", entry.desk.getInputs)
  appendSection(lines, "Inputs (raw values)", flattenInputs(inputs), inputError)

  local displays, displayError = safeCall("getDisplays", entry.desk.getDisplays)
  appendSection(lines, "Displays", displayLines(displays), displayError)

  local nearby, peripheralError = safeCall("getPeripherals", entry.desk.getPeripherals)
  appendSection(lines, "Adjacent CC:Tweaked peripherals", peripheralLines(nearby), peripheralError)

  return lines
end

local function showPaged(lines)
  local page = 1

  while true do
    local _, height = term.getSize()
    local pageSize = math.max(3, height - 3)
    local pages = math.max(1, math.ceil(#lines / pageSize))
    if page > pages then page = pages end

    term.clear()
    term.setCursorPos(1, 1)

    local first = (page - 1) * pageSize + 1
    local last = math.min(#lines, first + pageSize - 1)
    for index = first, last do
      print(lines[index])
    end

    print(string.format("[%d/%d] Enter/n: next, p: previous, b: back", page, pages))
    write("> ")
    local command = tostring(read() or ""):lower():match("^%s*(.-)%s*$")

    if command == "b" or command == "back" or command == "q" then
      return
    elseif command == "p" or command == "prev" or command == "previous" then
      page = math.max(1, page - 1)
    elseif command == "" or command == "n" or command == "next" then
      if page < pages then
        page = page + 1
      else
        return
      end
    end
  end
end

local function printSummary(snapshot)
  term.clear()
  term.setCursorPos(1, 1)

  local network = snapshot.network or {}
  print("CC-Aeroworks embedded network inspector")
  print(string.format(
    "state=%s desks=%s peripherals=%s revision=%s",
    tostring(network.state or "unknown"),
    tostring(network.deskCount or #snapshot.desks),
    tostring(network.peripheralCount or "?"),
    tostring(network.revision or "?")
  ))

  if snapshot.error then
    printError(snapshot.error)
  end

  if snapshot.warnings and #snapshot.warnings > 0 then
    printError("Discovery warning: " .. snapshot.warnings[1])
  end

  print("")
  if #snapshot.desks == 0 then
    print("No inspectable ControlDesk adapters found.")
  else
    print("Desks:")
    for index, entry in ipairs(snapshot.desks) do
      print(string.format(
        "  %d) %s  %s%s",
        index,
        entry.address,
        tostring(entry.info.variant or "control_desk"),
        entry.info.computer and " [computer]" or ""
      ))
    end
  end

  print("")
  print("Enter a Desk number, r to refresh, or q to quit.")
end

local snapshot, discoveryError = discover()
if not snapshot then
  error("Embedded network discovery failed:\n" .. tostring(discoveryError), 0)
end

local autoInspected = false
while true do
  printSummary(snapshot)

  if #snapshot.desks == 1 and not autoInspected then
    print("One Desk found; inspecting it automatically.")
    sleep(0.6)
    showPaged(buildDeskDetails(snapshot.desks[1]))
    autoInspected = true
  end

  write("> ")
  local command = tostring(read() or ""):lower():match("^%s*(.-)%s*$")

  if command == "q" or command == "quit" then
    term.clear()
    term.setCursorPos(1, 1)
    print("Embedded network inspector stopped.")
    return
  elseif command == "r" or command == "refresh" then
    local refreshed, refreshError = discover()
    if refreshed then
      snapshot = refreshed
      autoInspected = false
    else
      term.clear()
      term.setCursorPos(1, 1)
      printError("Refresh failed:")
      printError(tostring(refreshError))
      print("Press Enter to continue.")
      read()
    end
  else
    local choice = tonumber(command)
    if choice and choice % 1 == 0 and snapshot.desks[choice] then
      showPaged(buildDeskDetails(snapshot.desks[choice]))
    else
      printError("Invalid selection. Enter a listed Desk number, r, or q.")
      sleep(0.8)
    end
  end
end
