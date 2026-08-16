local ui = require("cc_aeroworks.ui")

return ui.app(function()
    local pointer = ui.input.pointer()

    ui.Canvas {
        modifier = ui.modifier():fillMaxSize(),

        draw = function(canvas)
            local event = pointer.get()
            if not event then return end

            local x = event.x
            local y = event.y

            canvas.setPixel(x, y, true)
            canvas.setPixel(x - 1, y, true)
            canvas.setPixel(x + 1, y, true)
            canvas.setPixel(x, y - 1, true)
            canvas.setPixel(x, y + 1, true)

            if event.action == "tap" then
                local radius = 8
                for angle = 0, 352, 8 do
                    local radians = math.rad(angle)
                    canvas.setPixel(
                        math.floor(x + math.cos(radians) * radius + 0.5),
                        math.floor(y + math.sin(radians) * radius + 0.5),
                        true
                    )
                end
            end
        end
    }
end)
