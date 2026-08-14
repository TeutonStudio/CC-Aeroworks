-- Example module for a large Desk Display configured with:
--   desk.setDisplayScriptSource("big", "/display-source-example.lua")
--
-- The built-in ComputerControlDesk runtime loads this file as a module. It lights one pixel and
-- moves it when telemetry changes, demonstrating that content ownership is independent from the
-- optional touch handler.

local cursor = 1

local function render(ctx)
    if not ctx.desk then return end
    local size = ctx.desk.getDisplaySize(ctx.socket)
    ctx.desk.clearDisplayPixels(ctx.socket)
    local x = ((cursor - 1) % size.width) + 1
    local y = (math.floor((cursor - 1) / size.width) % size.height) + 1
    ctx.desk.setDisplayPixel(ctx.socket, x, y, true)
end

return {
    onStart = function(ctx)
        cursor = 1
        render(ctx)
    end,

    render = render,

    onEvent = function(ctx, event)
        if event == "cc_aeroworks_telemetry_added"
            or event == "cc_aeroworks_telemetry_changed"
            or event == "cc_aeroworks_remote_telemetry_changed" then
            cursor = cursor + 1
            render(ctx)
        end
    end,
}
