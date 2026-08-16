-- Lightweight boot application used only to give controller-only displays a reactive runtime.
-- The real configured bootProgram remains empty; the autorun supervisor substitutes this path
-- internally and never writes it back into the display binding.
local ui = require("cc_aeroworks.ui")

return ui.app(function() end)
