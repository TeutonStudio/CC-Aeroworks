local runtime = rawget(_G, "channels")
local admin = rawget(_G, "__cc_aeroworks_channel_admin")
if type(runtime) == "table" and type(runtime.ls) == "function"
    and type(admin) == "table" and type(admin.addGroup) == "function" then
    shell.setAlias("channels", "cc_aeroworks_channels")
end
