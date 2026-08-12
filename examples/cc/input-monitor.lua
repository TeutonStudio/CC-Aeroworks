-- Live monitor for every numeric CC-Aeroworks desk input reachable from this computer.
--
-- Embedded Computer Control Desks use the multiblock console events. Normal
-- CC:Tweaked computers use local ControlDesk events and normal peripheral
-- attach/detach events. The display is scrollable and periodically revalidates
-- the topology so removed/replaced modules cannot leave stale values on screen.

local CONTROL_DESK_TYPE = "ControlDesk"
local LOCAL_INPUT_EVENT = "cc_aeroworks_desk_input"
local CONSOLE_INPUT_EVENT = "cc_aeroworks_console_input"
local CONSOLE_CHANGED_EVENT = "cc_aeroworks_console_changed"
local REFRESH_INTERVAL = 2

local networkApi = rawget(_G, "peripherals")
local embedded = type(networkApi) == "table"
  and type(networkApi.find) == "function"
  and type(networkApi.getNetwork) == "function"

local entries = {}
local desksById = {}
local deskCount = 0
local networkState = embedded and "unknown" or "local"
local networkRevision = nil
local statusMessage = "Starting discovery..."
local scroll = 0
local refreshTimer

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
    return "Network conflict: multiple embedded Computer Control Desks are connected. Remove the extra computer desk."
  elseif lower:find("partially loaded", 1, true) then
    return "Network unavailable: part of the connected desk row is in an unloaded chunk. Load the complete row."
  elseif lower:find("exceeds 64 desks", 1, true) then
    return "Network too large: more than 64 connected desks. Split the row into smaller networks."
  elseif lower:find("does not own", 1, true) then
    return "This embedded computer does not own the resolved desk network. Open the owning Computer Control Desk."
  end
  return "Network API error: " .. text
end

local function discoverDesks()
  local desks = {}
  local warnings = {}

  if embedded then
    local okNetwork, network = pcall(networkApi.getNetwork)
    if not okNetwork then
      return nil, explainNetworkError(network)
    end
    if type(network) ~= "table" then
      return nil, "peripherals.getNetwork() returned an invalid value instead of a network table."
    end
    networkState = tostring(network.state or "unknown")
    networkRevision = network.revision
    if networkState ~= "active" then
      return nil, "Embedded desk network state is '" .. networkState .. "'; expected 'active'."
    end

    local okFind, found = pcall(networkApi.find, CONTROL_DESK_TYPE)
    if not okFind then
      return nil, explainNetworkError(found)
    end
    if type(found) ~= "table" then
      return nil, "peripherals.find('ControlDesk') returned an invalid value instead of the desk table."
    end

    for address, desk in pairs(found) do
      local okInfo, info = pcall(desk.getInfo)
      if okInfo and type(info) == "table" then
        local id = tostring(info.id or address)
        desks[#desks + 1] = {
          desk = desk,
          stableId = id,
          deskId = info.id and tostring(info.id) or nil,
          peripheralName = nil,
          address = coordinates(info, tostring(address)),
        }
      else
        warnings[#warnings + 1] = string.format(
          "Desk %s was skipped because getInfo() failed: %s",
          tostring(address), tostring(info)
        )
      end
    end
  else
    networkState = "local"
    networkRevision = nil
    for _, name in ipairs(peripheral.getNames()) do
      if localPeripheralIsControlDesk(name) then
        local desk = peripheral.wrap(name)
        if desk then
          local okInfo, info = pcall(desk.getInfo)
          if okInfo and type(info) == "table" then
            desks[#desks + 1] = {
              desk = desk,
              stableId = name,
              deskId = nil,
              peripheralName = name,
              address = coordinates(info, name),
            }
          else
            warnings[#warnings + 1] = string.format(
              "Peripheral %s was skipped because getInfo() failed: %s",
              name, tostring(info)
            )
          end
        else
          warnings[#warnings + 1] = "Peripheral " .. name .. " was skipped because peripheral.wrap() returned nil."
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
  return desks, warnings
end

local function entryKey(stableId, socket, channel)
  return table.concat({ tostring(stableId), tostring(socket), channel or "*" }, "|")
end

local function moduleMetadata(desk, socket)
  local ok, module = pcall(desk.getModule, socket)
  if ok and type(module) == "table" then
    return module
  end
  return {
    id = "unknown",
    kind = "input",
    socketName = tostring(socket),
  }
end

local function fullRefresh(reason)
  local desks, discovery = discoverDesks()
  local nextEntries = {}
  local nextDesksById = {}
  local warnings = {}

  if not desks then
    entries = {}
    desksById = {}
    deskCount = 0
    statusMessage = discovery
    scroll = 0
    return false
  end

  for _, warning in ipairs(discovery or {}) do
    warnings[#warnings + 1] = warning
  end

  deskCount = #desks
  for _, deskEntry in ipairs(desks) do
    nextDesksById[deskEntry.stableId] = deskEntry
    if deskEntry.deskId then
      nextDesksById[deskEntry.deskId] = deskEntry
    end
    if deskEntry.peripheralName then
      nextDesksById[deskEntry.peripheralName] = deskEntry
    end

    local okInputs, inputs = pcall(deskEntry.desk.getInputs)
    if not okInputs then
      warnings[#warnings + 1] = string.format(
        "Could not read inputs from %s: %s",
        deskEntry.address, tostring(inputs)
      )
    elseif type(inputs) ~= "table" then
      warnings[#warnings + 1] = string.format(
        "Desk %s returned %s from getInputs(); expected a table.",
        deskEntry.address, type(inputs)
      )
    else
      for socket, value in pairs(inputs) do
        local metadata = moduleMetadata(deskEntry.desk, socket)
        local moduleId = tostring(metadata.id or "unknown")
        local kind = tostring(metadata.kind or "input")
        local socketName = tostring(metadata.socketName or socket)

        local function put(channel, numericValue)
          local key = entryKey(deskEntry.stableId, socket, channel)
          nextEntries[key] = {
            key = key,
            stableId = deskEntry.stableId,
            deskId = deskEntry.deskId,
            peripheralName = deskEntry.peripheralName,
            deskAddress = deskEntry.address,
            socket = socket,
            socketName = socketName,
            moduleId = moduleId,
            kind = kind,
            channel = channel,
            value = numericValue,
          }
        end

        if type(value) == "number" then
          put(nil, value)
        elseif type(value) == "table" then
          local foundNumeric = false
          for channel, channelValue in pairs(value) do
            if type(channelValue) == "number" then
              foundNumeric = true
              put(tostring(channel), channelValue)
            else
              warnings[#warnings + 1] = string.format(
                "Ignored non-numeric channel %s on %s/%s (%s).",
                tostring(channel), deskEntry.address, socketName, type(channelValue)
              )
            end
          end
          if not foundNumeric then
            warnings[#warnings + 1] = string.format(
              "Input module %s on %s/%s has no numeric channels.",
              moduleId, deskEntry.address, socketName
            )
          end
        else
          warnings[#warnings + 1] = string.format(
            "Ignored %s/%s because getInputs() returned %s instead of a number or table.",
            deskEntry.address, socketName, type(value)
          )
        end
      end
    end
  end

  entries = nextEntries
  desksById = nextDesksById

  local count = 0
  for _ in pairs(entries) do
    count = count + 1
  end

  local prefix = reason and (reason .. ": ") or ""
  if deskCount == 0 then
    if embedded then
      statusMessage = prefix .. "No ControlDesk adapters are present in the active embedded network."
    else
      statusMessage = prefix .. "No ControlDesk peripherals are reachable. Connect a desk directly or through an enabled wired modem."
    end
  elseif count == 0 then
    statusMessage = prefix .. "No numeric input channels found. Install or configure an Aeroworks input module in a reachable desk."
  elseif #warnings > 0 then
    statusMessage = string.format("%sMonitoring %d input channel(s); %d discovery warning(s). First: %s", prefix, count, #warnings, warnings[1])
  else
    statusMessage = string.format("%sMonitoring %d input channel(s) across %d desk(s).", prefix, count, deskCount)
  end
  return true
end

local function sortedEntries()
  local list = {}
  for _, entry in pairs(entries) do
    list[#list + 1] = entry
  end
  table.sort(list, function(a, b)
    if a.deskAddress ~= b.deskAddress then
      return a.deskAddress < b.deskAddress
    end
    if a.socket ~= b.socket then
      return a.socket < b.socket
    end
    return tostring(a.channel or "") < tostring(b.channel or "")
  end)
  return list
end

local function fit(text, width)
  text = tostring(text)
  if width <= 0 then
    return ""
  end
  if #text <= width then
    return text
  end
  if width <= 3 then
    return text:sub(1, width)
  end
  return text:sub(1, width - 3) .. "..."
end

local function render()
  local width, height = term.getSize()
  term.clear()
  term.setCursorPos(1, 1)

  local mode = embedded and "embedded network" or "local/wired peripherals"
  print(fit("CC-Aeroworks input monitor - " .. mode, width))

  if embedded then
    print(fit(string.format(
      "Network: %s | revision=%s | desks=%d",
      tostring(networkState), tostring(networkRevision or "?"), deskCount
    ), width))
  else
    print(fit("Reachable ControlDesk peripherals: " .. deskCount, width))
  end

  print(fit(statusMessage, width))
  print(fit("Up/Down/PgUp/PgDn/Home/End scroll | r refresh | q or Ctrl+T stop", width))

  local list = sortedEntries()
  local bodyHeight = math.max(0, height - 4)
  local maxScroll = math.max(0, #list - bodyHeight)
  scroll = math.max(0, math.min(scroll, maxScroll))

  if #list == 0 and bodyHeight > 0 then
    term.setCursorPos(1, 5)
    local message
    if deskCount == 0 then
      message = embedded
        and "No desks available. Check network state/chunks."
        or "No ControlDesk peripheral is connected."
    else
      message = "Desks found, but none currently exposes a numeric input."
    end
    term.write(fit(message, width))
    return
  end

  for row = 1, bodyHeight do
    local entry = list[scroll + row]
    if not entry then
      break
    end
    local channel = entry.channel and ("/" .. entry.channel) or ""
    local line = string.format(
      "%s | %s%s | %s | %s = %s",
      entry.deskAddress,
      entry.socketName,
      channel,
      entry.kind,
      entry.moduleId,
      tostring(entry.value)
    )
    term.setCursorPos(1, 4 + row)
    term.write(fit(line, width))
  end
end

local function removeWildcard(stableId, socket)
  entries[entryKey(stableId, socket, nil)] = nil
end

local function updateEmbeddedInput(event)
  local deskId = event[2] and tostring(event[2]) or nil
  local socket = event[4]
  local socketName = event[5] and tostring(event[5]) or tostring(socket)
  local moduleId = event[6] and tostring(event[6]) or "unknown"
  local value = event[7]
  local channel = event[8] and tostring(event[8]) or nil
  if not deskId or socket == nil or not channel then
    statusMessage = "Ignored malformed cc_aeroworks_console_input event; expected desk id, socket, value, and channel."
    return
  end

  local deskEntry = desksById[deskId]
  if not deskEntry then
    fullRefresh("Desk topology changed")
    deskEntry = desksById[deskId]
  end
  if not deskEntry then
    statusMessage = "Received input event for unknown desk id " .. deskId .. "; waiting for topology refresh."
    return
  end

  removeWildcard(deskEntry.stableId, socket)
  local key = entryKey(deskEntry.stableId, socket, channel)
  if value == nil then
    entries[key] = nil
    statusMessage = string.format("Input removed: %s/%s/%s", deskEntry.address, socketName, channel)
  elseif type(value) ~= "number" then
    statusMessage = string.format(
      "Ignored non-numeric input event for %s/%s/%s (%s).",
      deskEntry.address, socketName, channel, type(value)
    )
  else
    entries[key] = {
      key = key,
      stableId = deskEntry.stableId,
      deskId = deskId,
      peripheralName = nil,
      deskAddress = deskEntry.address,
      socket = socket,
      socketName = socketName,
      moduleId = moduleId,
      kind = entries[key] and entries[key].kind or "input",
      channel = channel,
      value = value,
    }
    statusMessage = string.format("Updated %s/%s/%s = %s", deskEntry.address, socketName, channel, tostring(value))
  end
end

local function updateLocalInput(event)
  local peripheralName = event[2] and tostring(event[2]) or nil
  local socket = event[3]
  local moduleId = event[4] and tostring(event[4]) or "unknown"
  local value = event[5]
  local channel = event[6] and tostring(event[6]) or nil
  local socketName = event[7] and tostring(event[7]) or tostring(socket)
  if not peripheralName or socket == nil or not channel then
    statusMessage = "Ignored malformed cc_aeroworks_desk_input event; expected peripheral name, socket, value, and channel."
    return
  end

  local deskEntry = desksById[peripheralName]
  if not deskEntry then
    fullRefresh("Peripheral topology changed")
    deskEntry = desksById[peripheralName]
  end
  if not deskEntry then
    statusMessage = "Received input event for unknown ControlDesk peripheral " .. peripheralName .. "."
    return
  end

  removeWildcard(deskEntry.stableId, socket)
  local key = entryKey(deskEntry.stableId, socket, channel)
  if value == nil then
    entries[key] = nil
    statusMessage = string.format("Input removed: %s/%s/%s", deskEntry.address, socketName, channel)
  elseif type(value) ~= "number" then
    statusMessage = string.format(
      "Ignored non-numeric input event for %s/%s/%s (%s).",
      deskEntry.address, socketName, channel, type(value)
    )
  else
    entries[key] = {
      key = key,
      stableId = deskEntry.stableId,
      deskId = nil,
      peripheralName = peripheralName,
      deskAddress = deskEntry.address,
      socket = socket,
      socketName = socketName,
      moduleId = moduleId,
      kind = entries[key] and entries[key].kind or "input",
      channel = channel,
      value = value,
    }
    statusMessage = string.format("Updated %s/%s/%s = %s", deskEntry.address, socketName, channel, tostring(value))
  end
end

local function moveScroll(delta)
  scroll = math.max(0, scroll + delta)
end

fullRefresh("Initial discovery")
render()
refreshTimer = os.startTimer(REFRESH_INTERVAL)

while true do
  local event = { os.pullEventRaw() }
  local name = event[1]
  local shouldRender = false

  if name == "terminate" then
    break
  elseif name == "term_resize" then
    shouldRender = true
  elseif name == "timer" and event[2] == refreshTimer then
    fullRefresh("Periodic validation")
    refreshTimer = os.startTimer(REFRESH_INTERVAL)
    shouldRender = true
  elseif embedded and name == CONSOLE_CHANGED_EVENT then
    networkState = tostring(event[2] or "unknown")
    networkRevision = event[4]
    if networkState ~= "active" then
      entries = {}
      desksById = {}
      deskCount = tonumber(event[3]) or 0
      statusMessage = string.format(
        "Desk network changed to '%s'. Fix the topology; the monitor will retry automatically.",
        networkState
      )
    else
      fullRefresh("Desk network changed")
    end
    shouldRender = true
  elseif embedded and name == CONSOLE_INPUT_EVENT then
    updateEmbeddedInput(event)
    shouldRender = true
  elseif not embedded and name == LOCAL_INPUT_EVENT then
    updateLocalInput(event)
    shouldRender = true
  elseif not embedded and (name == "peripheral" or name == "peripheral_detach") then
    fullRefresh(name == "peripheral" and "Peripheral attached" or "Peripheral detached")
    shouldRender = true
  elseif name == "char" then
    local char = tostring(event[2] or ""):lower()
    if char == "q" then
      break
    elseif char == "r" then
      fullRefresh("Manual refresh")
      shouldRender = true
    end
  elseif name == "key" and type(keys) == "table" then
    local key = event[2]
    local _, height = term.getSize()
    local page = math.max(1, height - 4)
    if key == keys.up then
      moveScroll(-1)
      shouldRender = true
    elseif key == keys.down then
      moveScroll(1)
      shouldRender = true
    elseif key == keys.pageUp then
      moveScroll(-page)
      shouldRender = true
    elseif key == keys.pageDown then
      moveScroll(page)
      shouldRender = true
    elseif key == keys.home then
      scroll = 0
      shouldRender = true
    elseif key == keys["end"] then
      scroll = math.huge
      shouldRender = true
    end
  end

  if shouldRender then
    render()
  end
end

term.clear()
term.setCursorPos(1, 1)
print("CC-Aeroworks input monitor stopped.")
