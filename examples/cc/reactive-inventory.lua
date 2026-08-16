local ui = require("cc_aeroworks.ui")

-- Omit the address when the multiblock has exactly one inventory peripheral.
-- Information-source IDs such as "storage:12,64,-7/north" are accepted too.
local cargo = ui.inventory()

return ui.app(function()
    ui.Column({ padding = 2, gap = 1 }, function()
        ui.Text({ text = "CARGO", width = 24 })

        ui.Text({
            width = 32,
            text = function()
                return "IRON " .. tostring(cargo.count("minecraft:iron_ingot"))
            end,
        })

        ui.Text({
            width = 32,
            text = function()
                return "COPPER " .. tostring(cargo.count("minecraft:copper_ingot"))
            end,
        })

        ui.Text({
            width = 32,
            text = function()
                return cargo.exists() and "ONLINE" or "OFFLINE"
            end,
        })
    end)
end)
