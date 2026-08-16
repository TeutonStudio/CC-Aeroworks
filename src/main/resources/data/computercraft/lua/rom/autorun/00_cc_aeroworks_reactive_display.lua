-- Automatic non-blocking runtime for configured reactive Desk Displays.
--
-- ui.supervise() owns the actual reactive semantics. This autorun drives that blocking loop inside
-- a coroutine from the normal CraftOS event stream, so configured displays work immediately while
-- foreground programs still receive the same events afterwards.

if rawget(_G, "__cc_aeroworks_reactive_display_installed") then return end

local function report(message)
    if type(printError) == "function" then printError(message) else print(message) end
end

local okNative, native = pcall(require, "cc_aeroworks.ui_native")
local okUi, ui = pcall(require, "cc_aeroworks.ui")
if not okNative or not okUi then
    report("CC-Aeroworks display runtime unavailable: " .. tostring(okNative and ui or native))
    return
end

local CONTROLLER_HOST = "/cc_aeroworks/controller_host.lua"
local nativePullEventRaw = os.pullEventRaw
local supervisor = nil
local supervisorFilter = nil

local function withControllerHosts(displays)
    for _, display in ipairs(displays or {}) do
        local hasController = type(display.controller) == "string" and display.controller ~= ""
        local hasBoot = type(display.bootProgram) == "string" and display.bootProgram ~= ""
        if hasController and not hasBoot then display.bootProgram = CONTROLLER_HOST end
    end
    return displays
end

local function resumeSupervisor(...)
    if not supervisor or coroutine.status(supervisor) == "dead" then return false end

    local savedPullEvent = os.pullEvent
    os.pullEvent = function(filter)
        return coroutine.yield(filter)
    end

    local packed = table.pack(coroutine.resume(supervisor, ...))
    os.pullEvent = savedPullEvent

    if not packed[1] then
        report("CC-Aeroworks display supervisor stopped: " .. tostring(packed[2]))
        supervisor = nil
        supervisorFilter = nil
        return false
    end

    supervisorFilter = packed[2]
    return true
end

-- ui.supervise() normally starts only displays with a boot program. During the initial scan we
-- substitute a blank internal app for controller-only displays, giving their input controller the
-- same Runtime object without changing the persistent display configuration.
local originalListDisplays = native.listDisplays
local patchedList = function(...)
    return withControllerHosts(originalListDisplays(...))
end

local patched = pcall(function() native.listDisplays = patchedList end)
if not patched then
    report("CC-Aeroworks display runtime could not wrap ui_native.listDisplays")
    return
end

supervisor = coroutine.create(function() ui.supervise() end)
resumeSupervisor()
native.listDisplays = originalListDisplays

if not supervisor then return end

rawset(_G, "__cc_aeroworks_reactive_display_installed", true)
-- Prevent the older one-path automatic handler from installing as a second dispatcher when this
-- branch is later merged with a master containing that compatibility autorun.
rawset(_G, "__cc_aeroworks_display_handlers_installed", true)

local function supervisorEvent(event)
    if event[1] ~= "cc_aeroworks_display_application_changed" then return event end

    local controller = event[6]
    local bootProgram = event[7]
    if type(controller) ~= "string" or controller == "" or (type(bootProgram) == "string" and bootProgram ~= "") then
        return event
    end

    local copy = table.pack(table.unpack(event, 1, event.n))
    copy[7] = CONTROLLER_HOST
    return copy
end

os.pullEventRaw = function(filter)
    while true do
        local event = table.pack(nativePullEventRaw())

        if supervisor and (supervisorFilter == nil or event[1] == supervisorFilter or event[1] == "terminate") then
            local routed = supervisorEvent(event)
            resumeSupervisor(table.unpack(routed, 1, routed.n))
        end

        if filter == nil or event[1] == filter or event[1] == "terminate" then
            return table.unpack(event, 1, event.n)
        end
    end
end
