local ui = require("cc_aeroworks.ui")

return ui.app(function()
    local fuelPercent = ui.derived("fuelPercent", function()
        local fuel = ui.telemetry.get("fuel")
        if not fuel or not fuel.available or not fuel.value then return nil end
        return math.floor((fuel.value.percent or 0) + 0.5)
    end)

    ui.Column({ padding = 2, gap = 1 }, function()
        ui.Text({ text = "FUEL", width = 16 })

        ui.ProgressBar({
            width = 80,
            height = 5,
            value = function()
                local percent = fuelPercent.get()
                return percent and percent / 100 or 0
            end,
        })

        ui.Text({
            width = 16,
            text = function()
                local percent = fuelPercent.get()
                return percent and (tostring(percent) .. "%") or "---"
            end,
        })
    end)
end)
