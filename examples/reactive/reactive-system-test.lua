local ui = require("cc_aeroworks.ui")

-- End-to-end test for the reactive large Desk Display runtime.
--
-- Setup:
--   1. Use one large Desk Display in a multiblock owned by a ComputerControlDesk.
--   2. Put exactly one inventory peripheral (for example a chest) next to any desk in that multiblock.
--      If you have several inventories, pass the desired peripheral address to ui.inventory(...).
--   3. Copy this file to the embedded computer, for example as /reactive-system-test.lua.
--   4. Select it as the large display's Boot-App. The Controller may stay empty.
--   5. Add/remove iron, copper and diamonds while watching the display.
--
-- No touch input is required for this test.

local cargo = ui.inventory()

-- Generic observed getter. This proves that a source without a native event can define a
-- dependency merely by being read from the retained UI. The value changes once per second.
local heartbeat = ui.observe("test:heartbeat", function()
    return math.floor(os.epoch("utc") / 1000)
end)

return ui.app(function()
    -- Derived state proves that downstream draw scopes only invalidate when the derived value
    -- actually changes.
    local trackedTotal = ui.derived("test:tracked_total", function()
        return cargo.count("minecraft:iron_ingot")
            + cargo.count("minecraft:copper_ingot")
    end)

    local occupiedSlots = ui.derived("test:occupied_slots", function()
        local contents = cargo.list() or {}
        local count = 0
        for _ in pairs(contents) do count = count + 1 end
        return count
    end)

    ui.Column({ padding = 2, gap = 1 }, function()
        ui.Text({ text = "REACTIVE SOURCE TEST", width = 28 })

        ui.Text({
            width = 32,
            text = function()
                return cargo.exists() and "INVENTORY ONLINE" or "INVENTORY OFFLINE"
            end,
        })

        ui.Text({
            width = 32,
            text = function()
                return "HEARTBEAT " .. tostring(heartbeat.get())
            end,
        })

        ui.Text({
            width = 32,
            text = function()
                return "SLOTS " .. tostring(occupiedSlots.get()) .. "/" .. tostring(cargo.size())
            end,
        })

        ui.Text({
            width = 32,
            text = function()
                return "IRON    " .. tostring(cargo.count("minecraft:iron_ingot"))
            end,
        })

        ui.Text({
            width = 32,
            text = function()
                return "COPPER  " .. tostring(cargo.count("minecraft:copper_ingot"))
            end,
        })

        ui.Text({
            width = 32,
            text = function()
                return "TRACKED " .. tostring(trackedTotal.get())
            end,
        })

        ui.ProgressBar({
            width = 96,
            height = 5,
            value = function()
                return math.min(cargo.count("minecraft:iron_ingot") / 256, 1)
            end,
        })

        -- This read happens during composition rather than only while drawing Text. Adding or
        -- removing the last diamond therefore tests composition invalidation, not just redraw.
        if cargo.count("minecraft:diamond") > 0 then
            ui.Text({ text = "DIAMOND DETECTED", width = 24 })
        else
            ui.Text({ text = "NO DIAMOND", width = 24 })
        end
    end)
end)
