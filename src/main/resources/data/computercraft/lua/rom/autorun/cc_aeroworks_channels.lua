local api = rawget(_G, "channels")

if type(api) == "table"
    and type(api.ls) == "function"
    and type(api.read) == "function"
    and type(api.createGroup) == "function" then
    shell.setAlias("channels", "cc_aeroworks_channels")
end
