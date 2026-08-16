local ui = require("cc_aeroworks.ui")

-- Starts every large display which has a boot program configured in its module UI.
-- Controller scripts may return another application path from onTap/onDoubleTap/onPointer
-- to replace the currently mounted app without restarting the computer.
ui.supervise()
