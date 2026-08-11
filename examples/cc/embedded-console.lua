-- Interactive embedded-computer example for the current CC-Aeroworks desk-network API.
--
-- This program intentionally runs only on an embedded Computer Control Desk. It
-- discovers every numeric desk input and every CC-Aeroworks display in the active
-- desk network, applies the same 0/1/many selection rules as the other examples,
-- then mirrors the selected input to the selected display through the
-- cc_aeroworks_console_input event stream.
--
-- The selected endpoints are tracked by stable desk identity, socket, module/display
-- identity and channel. Topology changes are revalidated instead of silently
-- switching to whichever desk happens to be found first.

local CONTROL_DESK_TYPE = "ControlDesk"
local CONSOLE_INPUT_EVENT = "cc_aeroworks_console_input"
local CONSOLE_CHANGED_EVENT = "cc_aeroworks_console_changed"
local VALIDATION_INTERVAL = 2

local networkApi = rawget(_G, "peripherals")
if type(networkApi) ~= "table"
    or type(networkApi.getNetwork) ~= "function"
    or type(networkApi.find) ~= "function" then
  error(
    "embedded-console.lua requires the global peripherals API from an embedded Computer Control Desk.\n"
      .. "Hint: run dashboard.lua or input-monitor.lua on an ordinary/wired CC:Tweaked computer.",
    0
  )
end

local function fail(message, hint)
  if hint and hint ~= "" then
    error(message .. "\nHint: " .. hint, 0)
  end
  error(message, 0)
end

local function explainNetworkError(message)
  local text = tostring(message)
  local lower = text:lower()

  if lower:find("multiple computer control desks", 1, true) then
    return text
      .. "\nHint: the desk network is in conflict. Leave exactly one embedded Computer Control Desk connected."
  elseif lower:find("partially loaded", 1, true) then
    return text
      .. "\nHint: load every chunk containing the connected desk row, then retry."
  elseif lower:find("exceeds 64 desks", 1, true) then
    return text
      .. "\nHint: split the desk network so no connected row exceeds 64 desks."
  elseif lower:find("does not own", 1, true) then
    return text
      .. "\nHint: open the terminal from the Computer Control Desk that owns this desk network."
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

local function moduleMetadata(entry, socket)
  local ok, module = pcall(entry.desk.getModule, socket)
  if ok and type(module) == "table" then
    return module
  end
  return {
    socket = socket,
    socketName = tostring(socket),
    id = "unknown",
    kind = "unknown",
  }
end

local function discover()
  local okNetwork, network = pcall(networkApi.getNetwork)
  if not okNetwork then
    return nil, explainNetworkError(network)
  end
  if type(network) ~= "table" then
    return nil, "peripherals.getNetwork() returned " .. type(network) .. "; expected a table"
  end
  if network.state ~= "active" then
    return nil, string.format(
      "The embedded desk network is '%s', not 'active'.\n"
        .. "Hint: keep exactly one embedded computer, load the whole row and stay within the 64-desk limit.",
      tostring(network.state)
    )
  end

  local okFind, found = pcall(networkApi.find, CONTROL_DESK_TYPE)
  if not okFind then
    return nil, explainNetworkError(found)
  end
  if type(found) ~= "table" then
    return nil, "peripherals.find('ControlDesk') returned " .. type(found) .. "; expected the desk table"
  end

  local desks = {}
  local warnings = {}

  for address, desk in pairs(found) do
    local okInfo, info = pcall(desk.getInfo)
    if okInfo and type(info) == "table" then
      desks[#desks + 1] = {
        desk = desk,
        address = coordinates(info, tostring(address)),
        stableId = tostring(info.id or address),
        deskId = info.id and tostring(info.id) or nil,
        info = info,
      }
    else
      warnings[#warnings + 1] = string.format(
        "Skipped desk %s because getInfo() failed: %s",
        tostring(address),
        tostring(info)
      )
    end
  end

  table.sort(desks, function(a, b)
    if a.address ~= b.address then
      return a.address < b.address
    end
    return a.stableId < b.stableId
  end)

  if #desks == 0 then
    local detail = warnings[1] and ("\nDiscovery warning: " .. warnings[1]) or ""
    return nil,
      "No ControlDesk adapters were found in the active embedded network."
        .. detail
        .. "\nHint: verify that this computer belongs to the fully loaded desk row."
  end

  local inputs = {}
  local displays = {}

  for _, entry in ipairs(desks) do
    local okInputs, values = pcall(entry.desk.getInputs)
    if not okInputs then
      warnings[#warnings + 1] = string.format(
        "Could not read inputs from %s: %s",
        entry.address,
        tostring(values)
      )
    elseif type(values) ~= "table" then
      warnings[#warnings + 1] = string.format(
        "Desk %s returned %s from getInputs(); expected a table",
        entry.address,
        type(values)
      )
    else
      for socket, value in pairs(values) do
        local metadata = moduleMetadata(entry, socket)
        local moduleId = tostring(metadata.id or "unknown")
        local socketName = tostring(metadata.socketName or socket)
        local kind = tostring(metadata.kind or "input")

        local function add(channel, numericValue)
          inputs[#inputs + 1] = {
            desk = entry.desk,
            deskEntry = entry,
            deskAddress = entry.address,
            stableId = entry.stableId,
            deskId = entry.deskId,
            socket = socket,
            socketName = socketName,
            moduleId = moduleId,
            kind = kind,
            channel = channel,
            initialValue = numericValue,
          }
        end

        if type(value) == "number" then
          add(nil, value)
        elseif type(value) == "table" then
          local numericCount = 0
          for channel, channelValue in pairs(value) do
            if type(channelValue) == "number" then
              numericCount = numericCount + 1
              add(tostring(channel), channelValue)
            else
              warnings[#warnings + 1] = string.format(
                "Ignored non-numeric channel %s on %s/%s (%s)",
                tostring(channel),
                entry.address,
                socketName,
                type(channelValue)
              )
            end
          end
          if numericCount == 0 then
            warnings[#warnings + 1] = string.format(
              "Input module %s on %s/%s has no numeric channels",
              moduleId,
              entry.address,
              socketName
            )
          end
        else
          warnings[#warnings + 1] = string.format(
            "Ignored %s input value on %s/%s",
            type(value),
            entry.address,
            socketName
          )
        end
      end
    end

    local okDisplays, foundDisplays = pcall(entry.desk.getDisplays)
    if not okDisplays then
      warnings[#warnings + 1] = string.format(
        "Could not read displays from %s: %s",
        entry.address,
        tostring(foundDisplays)
      )
    elseif type(foundDisplays) ~= "table" then
      warnings[#warnings + 1] = string.format(
        "Desk %s returned %s from getDisplays(); expected a table",
        entry.address,
        type(foundDisplays)
      )
    else
      for _, display in ipairs(foundDisplays) do
        if type(display) == "table" and type(display.socketName) == "string" then
          displays[#displays + 1] = {
            desk = entry.desk,
            deskEntry = entry,
            deskAddress = entry.address,
            stableId = entry.stableId,
            deskId = entry.deskId,
            socket = display.socketName,
            displayId = tostring(display.id or "unknown"),
            display = display,
          }
        else
          warnings[#warnings + 1] = "Ignored malformed display description on desk " .. entry.address
        end
      end
    end
  end

  table.sort(inputs, function(a, b)
    if a.deskAddress ~= b.deskAddress then
      return a.deskAddress < b.deskAddress
    end
    if a.socket ~= b.socket then
      return a.socket < b.socket
    end
    return tostring(a.channel or "") < tostring(b.channel or "")
  end)

  table.sort(displays, function(a, b)
    if a.deskAddress ~= b.deskAddress then
      return a.deskAddress < b.deskAddress
    end
    return a.socket < b.socket
  end)

  return {
    network = network,
    desks = desks,
    inputs = inputs,
    displays = displays,
    warnings = warnings,
  }
end

local function inputIdentity(source)
  return table.concat({
    source.stableId,
    tostring(source.socket),
    source.moduleId,
    source.channel or "*",
  }, "|")
end

local function displayIdentity(target)
  return table.concat({
    target.stableId,
    target.socket,
    target.displayId,
  }, "|")
end

local function displaySizeName(display)
  if display.width == 2 then
    return "small"
  elseif display.width == 3 then
    return "large"
  end
  return tostring(display.id or "unknown")
end

local function inputLabel(source)
  return string.format(
    "%s / %s / %s / %s (current=%s)",
    source.deskAddress,
    source.socketName,
    source.kind,
    source.channel and ("channel " .. source.channel) or "single value",
    tostring(source.initialValue)
  )
end

local function displayLabel(target)
  local display = target.display
  return string.format(
    "%s / %s / %s display (%sx%s pixels)",
    target.deskAddress,
    target.socket,
    displaySizeName(display),
    tostring(display.pixelWidth or "?"),
    tostring(display.pixelHeight or "?")
  )
end

local function choose(title, items, labelFunction)
  if #items == 0 then
    return nil, "none"
  end

  if #items == 1 then
    print(title .. ": one match found; selecting it automatically.")
    print("  " .. labelFunction(items[1]))
    return items[1]
  end

  local _, terminalHeight = term.getSize()
  local pageSize = math.max(3, terminalHeight - 7)
  local pageCount = math.max(1, math.ceil(#items / pageSize))
  local page = 1

  while true do
    term.clear()
    term.setCursorPos(1, 1)
    print(string.format("%s: %d matches (page %d/%d)", title, #items, page, pageCount))
    print("Enter an absolute number, n/p for pages, or q to cancel.")
    print("")

    local first = (page - 1) * pageSize + 1
    local last = math.min(#items, first + pageSize - 1)
    for index = first, last do
      print(string.format("%3d) %s", index, labelFunction(items[index])))
    end

    write("Selection: ")
    local raw = read()
    local normalized = tostring(raw or ""):lower():match("^%s*(.-)%s*$")

    if normalized == "q" or normalized == "quit" then
      return nil, "cancelled"
    elseif normalized == "n" or normalized == "next" then
      if page < pageCount then
        page = page + 1
      else
        print("Already on the last page.")
        sleep(0.7)
      end
    elseif normalized == "p" or normalized == "prev" or normalized == "previous" then
      if page > 1 then
        page = page - 1
      else
        print("Already on the first page.")
        sleep(0.7)
      end
    else
      local choice = tonumber(normalized)
      if choice and choice % 1 == 0 and items[choice] then
        return items[choice]
      end
      print(string.format("Invalid selection '%s'. Choose an integer from 1 to %d.", normalized, #items))
      sleep(1)
    end
  end
end

local function readInputValue(source)
  local ok, value = pcall(source.desk.getInput, source.socket)
  if not ok then
    return nil, string.format(
      "Could not read selected input %s/%s: %s",
      source.deskAddress,
      source.socketName,
      tostring(value)
    )
  end

  if source.channel == nil then
    if type(value) ~= "number" then
      return nil, string.format(
        "Selected input %s/%s changed shape: expected one numeric value, got %s.",
        source.deskAddress,
        source.socketName,
        type(value)
      )
    end
    return value
  end

  if type(value) ~= "table" then
    return nil, string.format(
      "Selected channel %s on %s/%s is no longer a multi-channel input.",
      source.channel,
      source.deskAddress,
      source.socketName
    )
  end

  local channelValue = value[source.channel]
  if type(channelValue) ~= "number" then
    return nil, string.format(
      "Selected channel %s on %s/%s no longer exists or is not numeric.",
      source.channel,
      source.deskAddress,
      source.socketName
    )
  end
  return channelValue
end

local function writeDisplay(target, value)
  local ok, rendered = pcall(target.desk.setDisplayNumber, target.socket, value, false)
  if not ok then
    return nil, string.format(
      "Could not write selected display %s/%s: %s",
      target.deskAddress,
      target.socket,
      tostring(rendered)
    )
  end
  return rendered
end

local function snapshotDisplay(target)
  local ok, state = pcall(target.desk.getDisplay, target.socket)
  if ok and type(state) == "table" then
    return state
  end
  return nil
end

local function restoreDisplay(target, snapshot)
  if not snapshot then
    return false, "no pre-example display state was available"
  end

  if snapshot.mode == "pixels" and type(snapshot.pixels) == "table" then
    local ok, result = pcall(target.desk.setDisplayPixels, target.socket, snapshot.pixels)
    return ok, result
  end

  local ok, result = pcall(target.desk.setDisplayText, target.socket, tostring(snapshot.text or ""))
  return ok, result
end

local function rebind(inputKey, displayKey)
  local state, discoveryError = discover()
  if not state then
    return nil, nil, nil, discoveryError
  end

  local source
  local target

  for _, candidate in ipairs(state.inputs) do
    if inputIdentity(candidate) == inputKey then
      source = candidate
      break
    end
  end

  for _, candidate in ipairs(state.displays) do
    if displayIdentity(candidate) == displayKey then
      target = candidate
      break
    end
  end

  if not source then
    return nil, nil, state,
      "The selected input disappeared, changed module identity, changed shape, or left the desk network."
  end
  if not target then
    return nil, nil, state,
      "The selected display disappeared, was replaced, or left the desk network."
  end

  return source, target, state
end

local function drawStatus(state, source, target, value, rendered, lastEvent)
  term.clear()
  term.setCursorPos(1, 1)
  print("CC-Aeroworks embedded console example")
  print(string.format(
    "Network: %s, desks=%s, peripherals=%s, revision=%s",
    tostring(state.network.state),
    tostring(state.network.deskCount),
    tostring(state.network.peripheralCount),
    tostring(state.network.revision)
  ))
  print("Input:   " .. inputLabel(source))
  print("Display: " .. displayLabel(target))
  print("")
  print(string.format("Value: %s -> display text: %s", tostring(value), tostring(rendered)))
  print("Last event: " .. tostring(lastEvent or "initial read"))
  print("")
  print("Ctrl+T stops the example and attempts to restore the previous display state.")
end

local state, discoveryError = discover()
if not state then
  fail("Embedded-console discovery failed: " .. discoveryError)
end

if #state.inputs == 0 then
  local detail = state.warnings[1] and ("\nFirst discovery warning: " .. state.warnings[1]) or ""
  fail(
    "No numeric CC-Aeroworks desk inputs were found." .. detail,
    "Install an Aeroworks input module in any desk of this network, then rerun the example."
  )
end

if #state.displays == 0 then
  local detail = state.warnings[1] and ("\nFirst discovery warning: " .. state.warnings[1]) or ""
  fail(
    "No CC-Aeroworks desk displays were found." .. detail,
    "Mount a small or large CC-Aeroworks display in any desk of this network, then rerun the example."
  )
end

local source, sourceChoice = choose("Select input", state.inputs, inputLabel)
if not source then
  print(sourceChoice == "cancelled" and "Example cancelled before selecting an input." or "No input selected.")
  return
end

local target, targetChoice = choose("Select display", state.displays, displayLabel)
if not target then
  print(targetChoice == "cancelled" and "Example cancelled before selecting a display." or "No display selected.")
  return
end

local selectedInputKey = inputIdentity(source)
local selectedDisplayKey = displayIdentity(target)
local previousDisplay = snapshotDisplay(target)

local function runExample()
  local value, readError = readInputValue(source)
  if value == nil then
    fail(readError)
  end

  local rendered, writeError = writeDisplay(target, value)
  if rendered == nil then
    fail(writeError)
  end

  drawStatus(state, source, target, value, rendered, "initial read")

  local timer = os.startTimer(VALIDATION_INTERVAL)

  while true do
    local event = { os.pullEventRaw() }
    local name = event[1]

    if name == "terminate" then
      return
    elseif name == "term_resize" then
      drawStatus(state, source, target, value, rendered, "terminal resized")
    elseif name == "timer" and event[2] == timer then
      local reboundSource, reboundTarget, reboundState, rebindError =
        rebind(selectedInputKey, selectedDisplayKey)

      if not reboundSource then
        fail("Periodic validation failed: " .. tostring(rebindError))
      end

      source = reboundSource
      target = reboundTarget
      state = reboundState

      local current, currentError = readInputValue(source)
      if current == nil then
        fail("Periodic validation failed: " .. tostring(currentError))
      end
      value = current

      local nextRendered, nextWriteError = writeDisplay(target, value)
      if nextRendered == nil then
        fail("Periodic validation failed: " .. tostring(nextWriteError))
      end
      rendered = nextRendered

      drawStatus(state, source, target, value, rendered, "periodic validation")
      timer = os.startTimer(VALIDATION_INTERVAL)
    elseif name == CONSOLE_CHANGED_EVENT then
      local nextStateName = tostring(event[2])
      if nextStateName ~= "active" then
        fail(
          "The desk network changed to state '" .. nextStateName .. "' while the example was running.",
          "Restore one fully loaded network of at most 64 desks and rerun the example."
        )
      end

      local reboundSource, reboundTarget, reboundState, rebindError =
        rebind(selectedInputKey, selectedDisplayKey)
      if not reboundSource then
        fail("The desk network changed and the selected endpoint could not be rebound: " .. tostring(rebindError))
      end

      source = reboundSource
      target = reboundTarget
      state = reboundState

      local current, currentError = readInputValue(source)
      if current == nil then
        fail(currentError)
      end
      value = current

      local nextRendered, nextWriteError = writeDisplay(target, value)
      if nextRendered == nil then
        fail(nextWriteError)
      end
      rendered = nextRendered

      drawStatus(state, source, target, value, rendered, "network topology changed")
    elseif name == CONSOLE_INPUT_EVENT then
      local deskId = event[2] and tostring(event[2]) or nil
      local deskIndex = event[3]
      local socket = event[4]
      local socketName = event[5]
      local moduleId = event[6] and tostring(event[6]) or ""
      local newValue = event[7]
      local channel = event[8] and tostring(event[8]) or nil
      local channelMatches = source.channel == nil or source.channel == channel

      if deskId == source.deskId
          and socket == source.socket
          and moduleId == source.moduleId
          and channelMatches then
        if newValue == nil then
          fail(
            "The selected input channel was removed while the example was running.",
            "Reinstall or reconfigure the module, then rerun the example to select the intended source."
          )
        elseif type(newValue) ~= "number" then
          fail("The selected console input event returned " .. type(newValue) .. "; expected a number.")
        end

        value = newValue
        local nextRendered, nextWriteError = writeDisplay(target, value)
        if nextRendered == nil then
          fail(nextWriteError)
        end
        rendered = nextRendered

        drawStatus(
          state,
          source,
          target,
          value,
          rendered,
          string.format(
            "%s deskIndex=%s socket=%s channel=%s",
            CONSOLE_INPUT_EVENT,
            tostring(deskIndex),
            tostring(socketName),
            tostring(channel or "<single>")
          )
        )
      end
    end
  end
end

local ok, result = pcall(runExample)
local restored, restoreError = restoreDisplay(target, previousDisplay)

term.clear()
term.setCursorPos(1, 1)

if ok then
  print("Embedded console example stopped.")
else
  printError("Embedded console example stopped because of an error:")
  printError(tostring(result))
end

if restored then
  print("Previous display state restored.")
else
  printError("Could not restore the previous display state: " .. tostring(restoreError))
end
