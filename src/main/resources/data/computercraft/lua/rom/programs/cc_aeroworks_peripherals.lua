local networkApi = rawget(_G, "peripherals")

if type(networkApi) ~= "table" or type(networkApi.getTree) ~= "function" then
    printError("CC-Aeroworks peripheral tree is not available on this computer")
    return
end

local ok, tree = pcall(networkApi.getTree)
if not ok then
    printError(tree)
    return
end

local deskAddresses = {}
for address in pairs(tree) do
    deskAddresses[#deskAddresses + 1] = address
end
table.sort(deskAddresses)

print("Control Desk Peripherals:")
if #deskAddresses == 0 then
    print("None")
    return
end

local sideOrder = {
    north = 1,
    south = 2,
    east = 3,
    west = 4,
    up = 5,
    down = 6,
}

for _, address in ipairs(deskAddresses) do
    local desk = tree[address]
    local suffix = desk.computer and " [computer]" or ""
    print(("ControlDesk %s%s"):format(address, suffix))

    local children = desk.peripherals or {}
    local sides = {}
    for side in pairs(children) do
        sides[#sides + 1] = side
    end
    table.sort(sides, function(a, b)
        local ai = sideOrder[a] or 100
        local bi = sideOrder[b] or 100
        if ai == bi then return a < b end
        return ai < bi
    end)

    if #sides == 0 then
        print("  (no attached peripherals)")
    else
        for _, side in ipairs(sides) do
            local child = children[side]
            local position = child.position or {}
            local positionText = ("%s,%s,%s"):format(
                tostring(position.x or child.x or "?"),
                tostring(position.y or child.y or "?"),
                tostring(position.z or child.z or "?")
            )
            print(("  %s -> %s [%s]"):format(
                side,
                tostring(child.type or "unknown"),
                positionText
            ))
        end
    end
end
