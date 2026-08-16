local ui = require("cc_aeroworks.ui")

return ui.app(function()
    local pointer = ui.input.pointer()
    local taps = ui.state("test_taps", 0)
    local inverted = ui.state("test_inverted", false)

    ui.Canvas {
        modifier = ui.modifier():fillMaxSize(),

        onTap = function()
            taps.update(function(value)
                return value + 1
            end)
        end,

        onDoubleTap = function()
            inverted.set(not inverted.get())
        end,

        draw = function(canvas)
            local width = canvas.width
            local height = canvas.height
            local event = pointer.get()
            local reverse = inverted.get()

            -- A permanent frame proves that the application booted and produced a frame.
            canvas.fillRect(1, 1, width, 1, true)
            canvas.fillRect(1, height, width, 1, true)
            canvas.fillRect(1, 1, 1, height, true)
            canvas.fillRect(width, 1, 1, height, true)

            canvas.text("READY", 4, 4, 1)
            canvas.text(("TAP %d"):format(taps.get()), 4, 11, 1)

            if reverse then
                canvas.text("DBL ON", 4, 18, 1)

                -- A coarse checker pattern makes a double-tap visibly undeniable.
                for y = 27, height - 3, 8 do
                    for x = 4, width - 3, 8 do
                        if ((x + y) / 8) % 2 < 1 then
                            canvas.fillRect(x, y, 3, 3, true)
                        end
                    end
                end
            else
                canvas.text("DBL OFF", 4, 18, 1)
            end

            if not event then
                canvas.text("NO INPUT", 4, 25, 1)
                return
            end

            local x = event.x
            local y = event.y
            local label = event.action == "double_tap" and "DOUBLE" or "TAP"

            canvas.text(label, 4, 25, 1)
            canvas.text(("X%d Y%d"):format(x, y), 4, 32, 1)

            -- Crosshair at the most recent pointer position.
            for offset = -4, 4 do
                canvas.setPixel(x + offset, y, true)
                canvas.setPixel(x, y + offset, true)
            end

            -- Small corner marks around the hit point remain legible even on dense PPB settings.
            local radius = 7
            canvas.setPixel(x - radius, y - radius, true)
            canvas.setPixel(x + radius, y - radius, true)
            canvas.setPixel(x - radius, y + radius, true)
            canvas.setPixel(x + radius, y + radius, true)
        end
    }
end)
