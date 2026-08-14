local runtime = rawget(_G, "wires")
local admin = rawget(_G, "__cc_aeroworks_wire_admin")

if type(runtime) ~= "table" or type(runtime.list) ~= "function"
    or type(admin) ~= "table" or type(admin.add) ~= "function" then
    printError("CC-Aeroworks wire configuration is only available on a ComputerControlDesk")
    return
end

local args = { ... }

local function call(fn, ...)
    local ok, result = pcall(fn, ...)
    if not ok then
        printError(result)
        return nil, false
    end
    return result, true
end

local function sortedNames(channels)
    local names = {}
    for name in pairs(channels) do
        names[#names + 1] = name
    end
    table.sort(names)
    return names
end

local function linkText(info)
    if info.backend == "none" then return "unavailable" end
    if info.connected then return "connected" end
    return "disconnected"
end

local function printList()
    local channels, ok = call(runtime.list)
    if not ok then return end

    print("ComputerControlDesk Wire Channels")
    print("")
    local names = sortedNames(channels)
    if #names == 0 then
        print("No channels configured.")
    else
        print(("%-24s %5s  %s"):format("NAME", "VALUE", "LINK"))
        for _, name in ipairs(names) do
            local info = channels[name]
            print(("%-24s %5s  %s"):format(
                name,
                tostring(info.value or 0),
                linkText(info)
            ))
        end
    end

    print("")
    print(("%d channel%s"):format(#names, #names == 1 and "" or "s"))
    print("Backend: " .. tostring(runtime.getBackend()))
    print("Outputs: " .. (runtime.isEnabled() and "enabled" or "failsafe/disabled"))
end

local function printInfo(name)
    local info, ok = call(runtime.getInfo, name)
    if not ok then return end

    print("Name:        " .. tostring(info.name))
    print("ID:          " .. tostring(info.id))
    print("Value:       " .. tostring(info.value))
    print("Backend:     " .. tostring(info.backend))
    print("Connected:   " .. tostring(info.connected))
    print("Connections: " .. tostring(info.connections))
    print("Enabled:     " .. tostring(info.enabled))
end

local function printHelp()
    print("ComputerControlDesk wire configuration")
    print("")
    print("wires [list]")
    print("wires add <name>")
    print("wires remove <name>")
    print("wires rename <old> <new>")
    print("wires info <name>")
    print("wires help")
    print("")
    print("Channel values are controlled from Lua with the wires API.")
end

local command = args[1]
if command == nil or command == "list" then
    printList()
elseif command == "add" then
    if not args[2] or args[3] then
        printError("Usage: wires add <name>")
        return
    end
    local result, ok = call(admin.add, args[2])
    if ok then
        print(('Added channel "%s".'):format(result.name))
    end
elseif command == "remove" then
    if not args[2] or args[3] then
        printError("Usage: wires remove <name>")
        return
    end

    local info, ok = call(runtime.getInfo, args[2])
    if not ok then return end

    print("Channel: " .. tostring(info.name))
    print("Current value: " .. tostring(info.value))
    print("Connections: " .. tostring(info.connections))
    print("")
    print("Removing this channel sets it to 0 and permanently removes its wire connections.")
    write(('Remove "%s"? [y/N] '):format(info.name))
    local answer = read()
    if type(answer) ~= "string" or answer:lower() ~= "y" then
        print("Cancelled.")
        return
    end

    local removed, removedOk = call(admin.remove, info.name)
    if removedOk then
        print(('Removed channel "%s".'):format(removed.name))
    end
elseif command == "rename" then
    if not args[2] or not args[3] or args[4] then
        printError("Usage: wires rename <old> <new>")
        return
    end
    local result, ok = call(admin.rename, args[2], args[3])
    if ok then
        print(('Renamed channel "%s" to "%s".'):format(args[2], result.name))
        print("Existing Drive By Wire connections were migrated to the new name.")
    end
elseif command == "info" then
    if not args[2] or args[3] then
        printError("Usage: wires info <name>")
        return
    end
    printInfo(args[2])
elseif command == "help" or command == "--help" or command == "-h" then
    printHelp()
else
    printError("Unknown wires command: " .. tostring(command))
    printHelp()
end
