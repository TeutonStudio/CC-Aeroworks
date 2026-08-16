local ui = require("cc_aeroworks.ui")

-- Runtime-only placeholder used when a display has an input controller but no boot application.
-- Keeping a mounted empty app lets the controller receive touch events and switch to a real app.
return ui.app(function() end)
