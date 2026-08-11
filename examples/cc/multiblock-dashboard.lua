-- Demonstrates the ordinary/wired CC:Tweaked view of multiple CC-Aeroworks desks.
--
-- The old CC-Aeroworks multiblock facade no longer exists: one local ControlDesk
-- peripheral represents one physical desk. This example therefore discovers every
-- ControlDesk exposed through CC:Tweaked's normal peripheral network, discovers all
-- numeric inputs and displays on those desks, then applies explicit 0/1/many
-- selection before mirroring one selected input to one selected display.
--
-- It intentionally does not use the embedded global `peripherals` API. Stable desk
-- identity is used to rebind a selected desk if its CC:Tweaked attachment name
-- changes after a wired-network reconnect.

local CONTROL_DESK_TYPE = "ControlDesk"
local LOCAL_INPUT_EVENT = "cc_aeroworks_desk_input"
local VALIDATION_INTERVAL = 2

local function fail(message, hint)
  if hint and hint ~= "" then
    error(message .. "\nHint: " .. hint, 0)
  end
  error(message, 0)
end

local function compactType(value)
  return tostring(value):lower():gsub("^.-:", ""):gsub("[%s_%-]", "")
end

local function typeMatchesControlDesk(value)
  local compact = compactType(value)
  return compact == "controldesk" or compact == "ccaeroworkscontroldesk"
end

local function peripheralIsControlDesk(name)
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

local function discoverDesks()
  local desks = {}
  local warnings = {}

  local okNames, names = pcall(peripheral.getNames)
  if not okNames then
    return nil, "peripheral.getNames() failed: " .. tostring(names)
  end
  if type(names) ~= "table" then
    return nil, "peripheral.getNames() returned " .. type(names) .. "; expected a table"
  end

  for _, name in ipairs(names) do
    if peripheralIsControlDesk(name) then
      local desk = peripheral.wrap(name)
      if not desk then
        warnings[#warnings + 1] =
          "Skipped " .. tostring(name) .. " because peripheral.wrap() returned nil"
      else
        local okInfo, info = pcall(desk.getInfo)
        if okInfo and type(info) == "table" then
          desks[#desks + 1] = {
            desk = desk,
            peripheralName = tostring(name),
            address = coordinates(info, tostring(name)),
            stableId = tostring(info.id or name),
            deskId = info.id and tostring(info.id) or nil,
            info = info,
          }
        else
          warnings[#warnings + 1] = string.format(
            "Skipped %s because getInfo() failed: %s",
            tostring(name),
            tostring(info)
          )
        end
      end
    end
  end

  table.sort(desks, function(a, b)
    if a.address ~= b.address then
      return a.address < b.address
    end
    return a.peripheralName < b.peripheralName
  end)

  if #desks == 0 then
    local detail = warnings[1] and ("\nDiscovery warning: " .. warnings[1]) or ""
    return nil,
      "No reachable CC-Aeroworks ControlDesk peripherals were found."
        .. detail
        .. "\nHint: attach the computer directly to a desk or connect each intended desk through an enabled wired modem."
  end

  return desks, warnings
end

local function collectInputs(desks)
  local sources = {}
  local warnings = {}

  for _, entry in ipairs(desks) do
    local okInputs, values = pcall(entry.desk.getInputs)
    if not okInputs then
      warnings[#warnings + 1] = string.format(
        "Could not read inputs from %s (%s): %s",
        entry.address,
        entry.peripheralName,
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
          sources[#sources + 1] = {
            desk = entry.desk,
            deskEntry = entry,
            deskAddress = entry.address,
            stableId = entry.stableId,
            peripheralName = entry.peripheralName,
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
  end

  table.sort(sources, function(a, b)
    if a.deskAddress ~= b.deskAddress then
      return a.deskAddress < b.deskAddress
    end
    if a.socket ~= b.socket then
      return a.socket < b.socket
    end
    return tostring(a.channel or "") < tostring(b.channel or "")
  end)

  return sources, warnings
end

local function collectDisplays(desks)
  local targets = {}
  local warnings = {}

  for _, entry in ipairs(desks) do
    local okDisplays, displays = pcall(entry.desk.getDisplays)
    if not okDisplays then
      warnings[#warnings + 1] = string.format(
        "Could not read displays from %s (%s): %s",
        entry.address,
        entry.peripheralName,
        tostring(displays)
      )
    elseif type(displays) ~= "table" then
      warnings[#warnings + 1] = string.format(
        "Desk %s returned %s from getDisplays(); expected a table",
        entry.address,
        type(displays)
      )
    else
      for _, display in ipairs(displays) do
        if type(display) == "table" and type(display.socketName) == "string" then
          targets[#targets + 1] = {
            desk = entry.desk,
            deskEntry = entry,
            deskAddress = entry.address,
            stableId = entry.stableId,
            peripheralName = entry.peripheralName,
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

  table.sort(targets, function(a, b)
    if a.deskAddress ~= b.deskAddress then
      return a.deskAddress < b.deskAddress
    end
    return a.socket < b.socket
  end)

  return targets, warnings
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
    "%s / %s / %s / %s [%s] (current=%s)",
    source.deskAddress,
    source.socketName,
    source.kind,
    source.channel and ("channel " .. source.channel) or "single value",
    source.peripheralName,
    tostring(source.initialValue)
  )
end

local function displayLabel(target)
  local display = target.display
  return string.format(
    "%s / %s / %s display (%sx%s pixels) [%s]",
    target.deskAddress,
    target.socket,
    displaySizeName(display),
    tostring(display.pixelWidth or "?"),
    tostring(display.pixelHeight or "?"),
    target.peripheralName
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
      "Could not read selected input %s/%s on %s: %s",
      source.deskAddress,
      source.socketName,
      source.peripheralName,
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
      "Could not write selected display %s/%s on %s: %s",
      target.deskAddress,
      target.socket,
      target.peripheralName,
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
    return false, "no pre-dashboard display state was available"
  end

  if snapshot.mode == "pixels" and type(snapshot.pixels) == "table" then
    local ok, result = pcall(target.desk.setDisplayPixels, target.socket, snapshot.pixels)
    return ok, result
  end

  local ok, result = pcall(target.desk.setDisplayText, target.socket, tostring(snapshot.text or ""))
  return ok, result
end

local function rebind(inputKey, displayKey)
  local desks, discoveryError = discoverDesks()
  if not desks then
    return nil, nil, discoveryError
  end

  local inputs = collectInputs(desks)
  local displays = collectDisplays(desks)
  local source
  local target

  for _, candidate in ipairs(inputs) do
    if inputIdentity(candidate) == inputKey then
      source = candidate
      break
    end
  end

  for _, candidate in ipairs(displays) do
    if displayIdentity(candidate) == displayKey then
      target = candidate
      break
    end
  end

  if not source then
    return nil, nil,
      "The selected input is no longer reachable with the same desk identity, socket, module and channel."
  end
  if not target then
    return nil, nil,
      "The selected display is no longer reachable with the same desk identity, socket and display identity."
  end

  return source, target
end

local function drawStatus(source, target, value, rendered, lastEvent)
  term.clear()
  term.setCursorPos(1, 1)
  print("CC-Aeroworks wired/multi-desk dashboard")
  print("This uses normal CC:Tweaked ControlDesk peripherals, not the embedded peripherals API.")
  print("Input:   " .. inputLabel(source))
  print("Display: " .. displayLabel(target))
  print("")
  print(string.format("Value: %s -> display text: %s", tostring(value), tostring(rendered)))
  print("Last event: " .. tostring(lastEvent or "initial read"))
  print("")
  print("Ctrl+T stops the dashboard and attempts to restore the previous display state.")
end

local desks, deskWarnings = discoverDesks()
if not desks then
  fail("Dashboard discovery failed: " .. deskWarnings)
end

local inputs, inputWarnings = collectInputs(desks)
if #inputs == 0 then
  local detail = inputWarnings[1] and ("\nFirst discovery warning: " .. inputWarnings[1]) or ""
  fail(
    "No numeric CC-Aeroworks desk inputs were found." .. detail,
    "Install an Aeroworks input module on any reachable ControlDesk, then rerun the example."
  )
end

local displays, displayWarnings = collectDisplays(desks)
if #displays == 0 then
  local detail = displayWarnings[1] and ("\nFirst discovery warning: " .. displayWarnings[1]) or ""
  fail(
    "No CC-Aeroworks desk displays were found." .. detail,
    "Mount a small or large display on any reachable ControlDesk, then rerun the example."
  )
end

local source, sourceChoice = choose("Select input", inputs, inputLabel)
if not source then
  print(sourceChoice == "cancelled" and "Dashboard cancelled before selecting an input." or "No input selected.")
  return
end

local target, targetChoice = choose("Select display", displays, displayLabel)
if not target then
  print(targetChoice == "cancelled" and "Dashboard cancelled before selecting a display." or "No display selected.")
  return
end

local selectedInputKey = inputIdentity(source)
local selectedDisplayKey = displayIdentity(target)
local previousDisplay = snapshotDisplay(target)

local function runDashboard()
  local value, readError = readInputValue(source)
  if value == nil then
    fail(readError)
  end

  local rendered, writeError = writeDisplay(target, value)
  if rendered == nil then
    fail(writeError)
  end

  drawStatus(source, target, value, rendered, "initial read")
  local timer = os.startTimer(VALIDATION_INTERVAL)

  while true do
    local event = { os.pullEventRaw() }
    local name = event[1]

    if name == "terminate" then
      return
    elseif name == "term_resize" then
      drawStatus(source, target, value, rendered, "terminal resized")
    elseif name == "timer" and event[2] == timer then
      local reboundSource, reboundTarget, rebindError =
        rebind(selectedInputKey, selectedDisplayKey)
      if not reboundSource then
        fail("Periodic validation failed: " .. tostring(rebindError))
      end

      source = reboundSource
      target = reboundTarget

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

      drawStatus(source, target, value, rendered, "periodic validation")
      timer = os.startTimer(VALIDATION_INTERVAL)
    elseif name == LOCAL_INPUT_EVENT then
      local peripheralName = event[2] and tostring(event[2]) or nil
      local socket = event[3]
      local moduleId = event[4] and tostring(event[4]) or ""
      local newValue = event[5]
      local channel = event[6] and tostring(event[6]) or nil
      local socketName = event[7]
      local channelMatches = source.channel == nil or source.channel == channel

      if peripheralName == source.peripheralName
          and socket == source.socket
          and moduleId == source.moduleId
          and channelMatches then
        if newValue == nil then
          fail(
            "The selected input channel was removed while the dashboard was running.",
            "Reinstall or reconfigure the module, then rerun the example."
          )
        elseif type(newValue) ~= "number" then
          fail("The selected local input event returned " .. type(newValue) .. "; expected a number.")
        end

        value = newValue
        local nextRendered, nextWriteError = writeDisplay(target, value)
        if nextRendered == nil then
          fail(nextWriteError)
        end
        rendered = nextRendered

        drawStatus(
          source,
          target,
          value,
          rendered,
          string.format(
            "%s peripheral=%s socket=%s channel=%s",
            LOCAL_INPUT_EVENT,
            tostring(peripheralName),
            tostring(socketName),
            tostring(channel or "<single>")
          )
        )
      end
    elseif name == "peripheral" then
      local attached = tostring(event[2] or "")
      local reboundSource, reboundTarget = rebind(selectedInputKey, selectedDisplayKey)
      if reboundSource then
        source = reboundSource
        target = reboundTarget
        drawStatus(source, target, value, rendered, "peripheral attached: " .. attached)
      end
    elseif name == "peripheral_detach" then
      local detached = tostring(event[2] or "")
      if detached == source.peripheralName or detached == target.peripheralName then
        local reboundSource, reboundTarget, rebindError =
          rebind(selectedInputKey, selectedDisplayKey)
        if not reboundSource then
          fail(
            "A selected ControlDesk peripheral detached (" .. detached .. ") and the endpoint could not be rebound: "
              .. tostring(rebindError),
            "Reconnect the desk/wired modem, then rerun the dashboard if the endpoint is still intended."
          )
        end
        source = reboundSource
        target = reboundTarget
        drawStatus(source, target, value, rendered, "selected peripheral rebound after detach")
      end
    end
  end
end

local ok, result = pcall(runDashboard)

-- The peripheral attachment name may have changed while the script was running.
-- Rebind once more before restoring the display state.
local restoreTarget = target
local _, reboundTarget = rebind(selectedInputKey, selectedDisplayKey)
if reboundTarget then
  restoreTarget = reboundTarget
end

local restored, restoreError = restoreDisplay(restoreTarget, previousDisplay)

term.clear()
term.setCursorPos(1, 1)

if ok then
  print("Wired/multi-desk dashboard stopped.")
else
  printError("Wired/multi-desk dashboard stopped because of an error:")
  printError(tostring(result))
end

if restored then
  print("Previous display state restored.")
else
  printError("Could not restore the previous display state: " .. tostring(restoreError))
end
