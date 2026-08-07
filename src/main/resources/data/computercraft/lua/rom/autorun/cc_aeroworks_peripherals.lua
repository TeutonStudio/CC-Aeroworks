local networkApi = rawget(_G, "peripherals")

if type(networkApi) == "table" and type(networkApi.getTree) == "function" then
    shell.setAlias("peripherals", "cc_aeroworks_peripherals")
end
