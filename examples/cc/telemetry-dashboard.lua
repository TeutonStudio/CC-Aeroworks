-- Purpose: inspect local and docked Create telemetry without changing it.
-- Runs on: an embedded ComputerControlDesk computer.
-- Requires: cc_aeroworks.telemetry; Create: Simulated is optional for docking data.

local telemetry = require("cc_aeroworks.telemetry")

local running = true
local scroll = 0
local rows = {}
local lastError = nil

local function clamp(value, minimum, maximum)
  if value < minimum then return minimum end
  if value > maximum then return maximum end
  return value
end

local function percent(source)
  if source and source.kind == "fill_level" and source.value then
    return source.value.percent
  end
  return nil
end

local function sourceName(id, source)
  return source.alias or source.createLabel or id:sub(1, 8)
end

local function formatSource(id, source, prefix)
  local name = sourceName(id, source)
  local stale = source.stale and " STALE" or ""
  local p = percent(source)
  if p then
    return ("%s%-14s %6.1f%%%s"):format(prefix, name, p, stale)
  end

  if source.kind == "item_count" then
    return ("%s%-14s %d items%s"):format(prefix, name, source.value.count or 0, stale)
  end

  if source.kind == "fluid_amount" then
    return ("%s%-14s %.2f B%s"):format(prefix, name, source.value.buckets or 0, stale)
  end

  if source.kind == "item_list" then
    return ("%s%-14s %d items / %d types%s"):format(
      prefix,
      name,
      source.value.totalCount or 0,
      source.value.entryCount or 0,
      stale
    )
  end

  if source.kind == "fluid_list" then
    return ("%s%-14s %.2f B / %d types%s"):format(
      prefix,
      name,
      (source.value.totalAmount or 0) / 1000,
      source.value.entryCount or 0,
      stale
    )
  end

  local fallback = "unsupported"
  if source.displayText and source.displayText[1] then
    fallback = tostring(source.displayText[1])
  end
  return ("%s%-14s %s%s"):format(prefix, name, fallback, stale)
end

local function rebuild()
  local nextRows = {}
  lastError = nil

  local ok, status = pcall(telemetry.getStatus)
  if not ok then
    lastError = tostring(status)
    rows = {}
    return
  end

  nextRows[#nextRows + 1] = ("Local: %d fresh / %d stale"):format(
    status.freshCount or 0,
    status.staleCount or 0
  )

  local sources = telemetry.list()
  local sourceIds = {}
  for id in pairs(sources) do sourceIds[#sourceIds + 1] = id end
  table.sort(sourceIds)
  for _, id in ipairs(sourceIds) do
    nextRows[#nextRows + 1] = formatSource(id, sources[id], "  ")
  end

  local docks = telemetry.getDocks()
  local dockKeys = {}
  for key in pairs(docks) do dockKeys[#dockKeys + 1] = key end
  table.sort(dockKeys)

  for _, key in ipairs(dockKeys) do
    local dock = docks[key]
    local info = dock.getInfo()
    local remoteName = info.remote and (info.remote.name or info.remote.subLevelId) or "-"
    nextRows[#nextRows + 1] = ("Dock %-12s %-8s %s"):format(key, info.state or "?", remoteName)

    if info.locked then
      local remote = dock.listTelemetry()
      local remoteIds = {}
      for id in pairs(remote) do remoteIds[#remoteIds + 1] = id end
      table.sort(remoteIds)
      for _, id in ipairs(remoteIds) do
        nextRows[#nextRows + 1] = formatSource(id, remote[id], "    ")
      end
    end
  end

  rows = nextRows
end

local function draw()
  term.setBackgroundColor(colors.black)
  term.setTextColor(colors.white)
  term.clear()

  local width, height = term.getSize()
  term.setCursorPos(1, 1)
  term.setTextColor(colors.cyan)
  term.write("CC-Aeroworks Telemetry")

  term.setCursorPos(1, 2)
  term.setTextColor(colors.gray)
  term.write("r refresh | arrows scroll | q quit")

  if lastError then
    term.setCursorPos(1, 4)
    term.setTextColor(colors.red)
    term.write(lastError:sub(1, width))
    return
  end

  local visible = math.max(1, height - 3)
  local maximumScroll = math.max(0, #rows - visible)
  scroll = clamp(scroll, 0, maximumScroll)

  for line = 1, visible do
    local text = rows[scroll + line]
    if not text then break end
    term.setCursorPos(1, line + 3)
    term.setTextColor(colors.white)
    term.write(text:sub(1, width))
  end
end

local function refresh()
  local ok, err = pcall(rebuild)
  if not ok then
    lastError = tostring(err)
  end
  draw()
end

refresh()

local timer = os.startTimer(2)
while running do
  local event, a = os.pullEvent()

  if event == "key" then
    if a == keys.q then
      running = false
    elseif a == keys.r then
      refresh()
    elseif a == keys.up then
      scroll = math.max(0, scroll - 1)
      draw()
    elseif a == keys.down then
      scroll = scroll + 1
      draw()
    elseif a == keys.pageUp then
      local _, h = term.getSize()
      scroll = math.max(0, scroll - math.max(1, h - 4))
      draw()
    elseif a == keys.pageDown then
      local _, h = term.getSize()
      scroll = scroll + math.max(1, h - 4)
      draw()
    elseif a == keys.home then
      scroll = 0
      draw()
    end
  elseif event == "timer" and a == timer then
    refresh()
    timer = os.startTimer(2)
  elseif event == "cc_aeroworks_telemetry_added"
      or event == "cc_aeroworks_telemetry_changed"
      or event == "cc_aeroworks_telemetry_removed"
      or event == "cc_aeroworks_dock_changed"
      or event == "cc_aeroworks_remote_telemetry_changed" then
    refresh()
  end
end

term.setBackgroundColor(colors.black)
term.setTextColor(colors.white)
term.clear()
term.setCursorPos(1, 1)
