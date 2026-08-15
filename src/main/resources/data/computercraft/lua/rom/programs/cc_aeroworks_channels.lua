local api = rawget(_G, "channels")

if type(api) ~= "table" or type(api.ls) ~= "function" then
    printError("CC-Aeroworks channel management is only available on a ComputerControlDesk")
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

local function displayName(entry)
    return entry.label or entry.name or entry.path or entry.id or "?"
end

local function printLs(path)
    local entries, ok = call(api.ls, path or "/")
    if not ok then return end
    if #entries == 0 then
        print("(empty)")
        return
    end
    for _, entry in ipairs(entries) do
        if entry.nodeType == "group" then
            local suffix = entry.mutable and " [group]" or "/"
            print(displayName(entry) .. suffix)
        else
            local status = entry.available == false and " [missing]" or ""
            local value = entry.value ~= nil and (" = " .. tostring(entry.value)) or ""
            print(tostring(entry.name) .. value .. status)
        end
    end
end

local function printStat(reference)
    local entry, ok = call(api.stat, reference)
    if not ok then return end
    if entry == nil then
        printError("Unknown channel path or id: " .. tostring(reference))
        return
    end
    local keys = {}
    for key in pairs(entry) do keys[#keys + 1] = key end
    table.sort(keys)
    for _, key in ipairs(keys) do
        local value = entry[key]
        if type(value) ~= "table" then
            print(("%-14s %s"):format(key .. ":", tostring(value)))
        end
    end
end

local function help()
    print("ComputerControlDesk channel registry")
    print("")
    print("channels [ls [path]]")
    print("channels stat <path-or-id>")
    print("channels read <path-or-id>")
    print("channels group add <name>")
    print("channels group rename <name-or-id> <new-name>")
    print("channels group remove <name-or-id>")
    print("channels bind <group> <alias> <channel-path-or-id>")
    print("channels unbind <group> <alias>")
    print("")
    print("Paths begin at /modules, /wires and /groups.")
end

local command = args[1]
if command == nil or command == "ls" then
    if args[3] then
        printError("Usage: channels ls [path]")
        return
    end
    printLs(args[2] or "/")
elseif command == "stat" then
    if not args[2] or args[3] then
        printError("Usage: channels stat <path-or-id>")
        return
    end
    printStat(args[2])
elseif command == "read" then
    if not args[2] or args[3] then
        printError("Usage: channels read <path-or-id>")
        return
    end
    local value, ok = call(api.read, args[2])
    if ok then print(value) end
elseif command == "group" then
    local sub = args[2]
    if sub == "add" and args[3] and not args[4] then
        local group, ok = call(api.createGroup, args[3])
        if ok then print("Created group " .. tostring(group.path)) end
    elseif sub == "rename" and args[3] and args[4] and not args[5] then
        local group, ok = call(api.renameGroup, args[3], args[4])
        if ok then print("Renamed group to " .. tostring(group.path)) end
    elseif sub == "remove" and args[3] and not args[4] then
        local group, ok = call(api.removeGroup, args[3])
        if ok then print("Removed group " .. tostring(group.name)) end
    else
        printError("Usage: channels group add|rename|remove ...")
    end
elseif command == "bind" then
    if not args[2] or not args[3] or not args[4] or args[5] then
        printError("Usage: channels bind <group> <alias> <channel-path-or-id>")
        return
    end
    local group, ok = call(api.bind, args[2], args[3], args[4])
    if ok then print("Bound /groups/" .. tostring(group.name) .. "/" .. tostring(args[3])) end
elseif command == "unbind" then
    if not args[2] or not args[3] or args[4] then
        printError("Usage: channels unbind <group> <alias>")
        return
    end
    local removed, ok = call(api.unbind, args[2], args[3])
    if ok then print(removed and "Binding removed." or "Binding did not exist.") end
elseif command == "help" or command == "--help" or command == "-h" then
    help()
else
    printError("Unknown channels command: " .. tostring(command))
    help()
end
