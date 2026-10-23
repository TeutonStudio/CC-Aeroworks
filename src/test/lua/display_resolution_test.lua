-- Run with Lua 5.3+ from the repository root.
package.path = "src/main/resources/data/computercraft/lua/rom/modules/main/?.lua;" .. package.path
local calls = {}
local desk = {
    setDisplayResolution = function(socket, ppb)
        calls[#calls + 1] = {socket = socket, ppb = ppb}
        if ppb == 999 then error("Individual display resolution is disabled") end
        return {width = 14, height = 14, ppb = ppb}
    end,
    getDisplayColor = function(socket)
        calls[#calls + 1] = {socket = socket, getColor = true}
        return 0x33AAFF
    end,
    setDisplayColor = function(socket, color)
        calls[#calls + 1] = {socket = socket, color = color}
        return color
    end
}
peripheral = {wrap = function(name) assert(name == "test"); return desk end}
local display = require("display")
local touch = require("touchdisplay")
local size = display.setResolution({attachment = "test", socketName = "front_left"}, 32)
assert(size.width == 14 and size.height == 14 and size.ppb == 32)
assert(calls[1].socket == "front_left" and calls[1].ppb == 32)
touch.setResolution({attachment = "test", socket = 1}, 64)
assert(calls[2].socket == 1 and calls[2].ppb == 64)
assert(display.getColor({attachment = "test", socket = 2}) == 0x33AAFF)
assert(calls[3].socket == 2 and calls[3].getColor)
assert(touch.setColor({attachment = "test", socketName = "big"}, 0x3366CC) == 0x3366CC)
assert(calls[4].socket == "big" and calls[4].color == 0x3366CC)
local ok, err = pcall(display.setResolution, {attachment = "test", socket = 0}, 999)
assert(not ok and err:find("disabled"), "server errors must reach the caller")
print("Display resolution helpers passed")
