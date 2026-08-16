-- Reactive information-source adapters for CC-Aeroworks retained display UI.
--
-- A getter becomes a dependency simply by being read while a UI scope is active. Sources which do
-- not already publish native change events are observed lazily: one watcher exists per dependency
-- key, independent of how many displays read it, and disappears again when no retained scope uses
-- that key. This is intentionally separate from telemetry, which already has revision events.

local native = require("cc_aeroworks.ui_native")
local ui = require("cc_aeroworks.ui")
local rawPeripherals = require("cc_aeroworks.peripherals")

local M = {}
local watchers = {}
local timerId = nil
local POLL_SECONDS = 0.25

local function copy(value, seen)
    if type(value) ~= "table" then return value end
    seen = seen or {}
    if seen[value] then return seen[value] end
    local result = {}
    seen[value] = result
    for key, item in pairs(value) do result[copy(key, seen)] = copy(item, seen) end
    return result
end

local function equal(a, b, seen)
    if a == b then return true end
    if type(a) ~= type(b) then return false end
    if type(a) ~= "table" then return false end
    seen = seen or {}
    if seen[a] == b then return true end
    seen[a] = b
    for key, value in pairs(a) do
        if not equal(value, b[key], seen) then return false end
    end
    for key, value in pairs(b) do
        if a[key] == nil and value ~= nil then return false end
    end
    return true
end

local function schedule()
    if timerId == nil and next(watchers) ~= nil then timerId = os.startTimer(POLL_SECONDS) end
end

local function evaluate(watcher)
    local packed = table.pack(pcall(watcher.getter))
    if not packed[1] then return false, tostring(packed[2]) end
    if packed.n == 2 then return true, packed[2] end
    local values = {}
    for index = 2, packed.n do values[index - 1] = packed[index] end
    return true, values
end

local function readObserved(key, getter)
    key = tostring(key)
    native.read(key)
    local watcher = watchers[key]
    if watcher == nil then
        local ok, value = evaluate({ getter = getter })
        if not ok then error(value, 3) end
        watcher = { getter = getter, ok = true, value = copy(value), error = nil }
        watchers[key] = watcher
        schedule()
    else
        -- Closures are frequently recreated during composition/draw. Keep the newest one so it
        -- captures current handles/arguments while retaining the single cached source value.
        watcher.getter = getter
    end
    if not watcher.ok then return nil end
    return copy(watcher.value)
end

local function dependencyMap()
    local ok, value = pcall(native.getDependencies)
    return ok and type(value) == "table" and value or {}
end

local function poll()
    local dependencies = dependencyMap()
    for key, watcher in pairs(watchers) do
        if dependencies[key] == nil then
            watchers[key] = nil
        else
            local ok, value = evaluate(watcher)
            local changed = false
            if ok ~= watcher.ok then
                changed = true
            elseif ok then
                changed = not equal(watcher.value, value)
            else
                changed = watcher.error ~= value
            end
            if changed then
                watcher.ok = ok
                watcher.value = ok and copy(value) or nil
                watcher.error = ok and nil or value
                native.changed(key)
            end
        end
    end
    schedule()
end

function M.handleEvent(event)
    if type(event) ~= "table" or event[1] ~= "timer" or event[2] ~= timerId then return false end
    timerId = nil
    poll()
    return true
end

function M.watcherCount()
    local count = 0
    for _ in pairs(watchers) do count = count + 1 end
    return count
end

function ui.observe(key, getter)
    assert(type(getter) == "function", "reactive source getter must be a function")
    local dependency = tostring(key)
    return {
        key = dependency,
        get = function() return readObserved(dependency, getter) end,
        invalidate = function() native.changed(dependency) end,
    }
end

-- ui.source used to provide dependency tracking only and required producers to call invalidate().
-- Retain the name, but make the common case actually reactive. Existing explicit invalidation is
-- still harmless and remains available through ui.invalidate/source.invalidate.
ui.source = ui.observe
ui.peripheralSource = ui.observe

local function normalizedAddress(address)
    if address == nil then return nil end
    assert(type(address) == "string", "peripheral address must be a string")
    local result = address
    if string.sub(result, 1, 8) == "storage:" then result = string.sub(result, 9) end
    return result
end

local function findAll(typeName)
    local value = rawPeripherals.findAll(typeName)
    return type(value) == "table" and value or {}
end

local function selectAddress(typeName, requested)
    requested = normalizedAddress(requested)
    if requested ~= nil and requested ~= "" then return requested end
    local matches = findAll(typeName)
    local found = nil
    for address in pairs(matches) do
        if found ~= nil then error("multiple " .. typeName .. " peripherals exist; provide an address", 3) end
        found = address
    end
    if found == nil then error("no " .. typeName .. " peripheral is available", 3) end
    return found
end

local function resolve(typeName, address)
    return findAll(typeName)[address]
end

local function keyPart(value)
    local kind = type(value)
    if kind == "nil" then return "nil" end
    if kind == "string" or kind == "number" or kind == "boolean" then return tostring(value) end
    if kind == "table" and type(textutils) == "table" and type(textutils.serialize) == "function" then
        return textutils.serialize(value, { compact = true })
    end
    return kind .. ":" .. tostring(value)
end

local function reactivePeripheral(typeName, requestedAddress, readMethods)
    local address = selectAddress(typeName, requestedAddress)
    local proxy = { address = address, type = typeName }
    for _, methodName in ipairs(readMethods or {}) do
        proxy[methodName] = function(...)
            local args = table.pack(...)
            local suffix = {}
            for index = 1, args.n do suffix[#suffix + 1] = keyPart(args[index]) end
            local dependency = table.concat({ "peripheral", typeName, address, methodName, table.concat(suffix, ",") }, ":")
            return readObserved(dependency, function()
                local handle = resolve(typeName, address)
                if handle == nil then return nil end
                local method = handle[methodName]
                if type(method) ~= "function" then return nil end
                return method(table.unpack(args, 1, args.n))
            end)
        end
    end
    proxy.exists = function()
        return readObserved("peripheral:" .. typeName .. ":" .. address .. ":available", function()
            return resolve(typeName, address) ~= nil
        end)
    end
    return proxy
end

ui.reactivePeripheral = reactivePeripheral

local inventoryApi = {}
setmetatable(inventoryApi, {
    __call = function(_, requestedAddress)
        local inventory = reactivePeripheral(
            "inventory",
            requestedAddress,
            { "list", "size", "getItemDetail", "getItemLimit" }
        )
        inventory.item = inventory.getItemDetail
        inventory.limit = inventory.getItemLimit
        inventory.count = function(itemName)
            assert(type(itemName) == "string" and itemName ~= "", "item name must be a non-empty string")
            local dependency = "inventory:" .. inventory.address .. ":item:" .. itemName
            return readObserved(dependency, function()
                local handle = resolve("inventory", inventory.address)
                if handle == nil or type(handle.list) ~= "function" then return 0 end
                local contents = handle.list() or {}
                local total = 0
                for _, stack in pairs(contents) do
                    if type(stack) == "table" and stack.name == itemName then
                        total = total + (tonumber(stack.count) or 0)
                    end
                end
                return total
            end)
        end
        inventory.contents = inventory.list
        return inventory
    end,
})

function inventoryApi.findAll()
    return readObserved("inventory:*:sources", function()
        local result = {}
        for address in pairs(findAll("inventory")) do result[#result + 1] = address end
        table.sort(result)
        return result
    end)
end

ui.inventory = inventoryApi
ui.storage = inventoryApi

local fluidApi = {}
setmetatable(fluidApi, {
    __call = function(_, requestedAddress)
        local storage = reactivePeripheral("fluid_storage", requestedAddress, { "tanks" })
        storage.contents = storage.tanks
        return storage
    end,
})

function fluidApi.findAll()
    return readObserved("fluid_storage:*:sources", function()
        local result = {}
        for address in pairs(findAll("fluid_storage")) do result[#result + 1] = address end
        table.sort(result)
        return result
    end)
end

ui.fluidStorage = fluidApi

M.ui = ui
return M
