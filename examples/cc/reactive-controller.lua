-- Optional controller layer for a large Reactive UI display.
-- It receives pointer events before normal UI hit testing. Returning an app path
-- replaces the currently mounted application on this display.

return {
    onDoubleTap = function(event, runtime)
        -- Double-tap the upper-left 10x10 area to return to the home app.
        if event.x <= 10 and event.y <= 10 then
            return "/ui/home.lua"
        end
    end,

    onPointer = function(event, runtime)
        -- Return nil to let the retained UI tree handle the event normally.
        return nil
    end,
}
