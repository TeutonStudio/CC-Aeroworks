local runtime = rawget(_G, "wires")
local admin = rawget(_G, "__cc_aeroworks_wire_admin")

if type(runtime) == "table"
    and type(runtime.list) == "function"
    and type(runtime.set) == "function"
    and type(admin) == "table"
    and type(admin.add) == "function" then
    shell.setAlias("wires", "cc_aeroworks_wires")
end
