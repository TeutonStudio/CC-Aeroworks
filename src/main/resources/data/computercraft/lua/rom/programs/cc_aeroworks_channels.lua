local runtime = rawget(_G, "channels")
local admin = rawget(_G, "__cc_aeroworks_channel_admin")

if type(runtime) ~= "table" or type(runtime.ls) ~= "function" or type(admin) ~= "table" then
    printError("CC-Aeroworks channel management is only available on a ComputerControlDesk")
    return
end

local args = { ... }
local function call(fn, ...)
    local ok, result = pcall(fn, ...)
    if not ok then printError(result); return nil, false end
    return result, true
end

local function printLs(path)
    local rows, ok = call(runtime.ls, path or "/")
    if not ok then return end
    for _, row in ipairs(rows) do
        local suffix = row.nodeType == "group" and "/" or (row.available == false and " [missing]" or "")
        print((row.name or "?") .. suffix .. "  " .. (row.path or ""))
    end
end

local function help()
    print("channels ls [path]")
    print("channels group add <name>")
    print("channels group remove <name>")
    print("channels group rename <old> <new>")
    print("channels bind <group> <alias> <channel-id>")
    print("channels binding rename <group> <old-alias> <new-alias>")
    print("channels unbind <group> <alias>")
end

local cmd = args[1]
if cmd == nil or cmd == "ls" then
    printLs(args[2] or "/")
elseif cmd == "group" and args[2] == "add" and args[3] and not args[4] then
    local result, ok = call(admin.addGroup, args[3]); if ok then print("Added group " .. result.name) end
elseif cmd == "group" and args[2] == "remove" and args[3] and not args[4] then
    local result, ok = call(admin.removeGroup, args[3]); if ok then print("Removed group " .. result.name) end
elseif cmd == "group" and args[2] == "rename" and args[3] and args[4] and not args[5] then
    local result, ok = call(admin.renameGroup, args[3], args[4]); if ok then print("Renamed group to " .. result.name) end
elseif cmd == "bind" and args[2] and args[3] and args[4] and not args[5] then
    local result, ok = call(admin.bind, args[2], args[3], args[4]); if ok then print("Bound " .. args[2] .. "/" .. args[3]) end
elseif cmd == "binding" and args[2] == "rename" and args[3] and args[4] and args[5] and not args[6] then
    local result, ok = call(admin.renameBinding, args[3], args[4], args[5]); if ok then print("Renamed binding to " .. args[5]) end
elseif cmd == "unbind" and args[2] and args[3] and not args[4] then
    local result, ok = call(admin.unbind, args[2], args[3]); if ok then print("Unbound " .. args[2] .. "/" .. args[3]) end
else
    help()
end
