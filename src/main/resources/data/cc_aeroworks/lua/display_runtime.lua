if rawget(_G, "__cc_aeroworks_display_supervisor_active") then
    print("CC-Aeroworks display runtime is already managed automatically.")
    return
end

local ui = require("cc_aeroworks.ui")
ui.supervise()
