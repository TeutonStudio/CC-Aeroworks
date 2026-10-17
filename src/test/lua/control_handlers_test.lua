-- Run with Lua 5.3+ from the repository root. No Minecraft or CraftOS installation required.
local runtime = "src/main/resources/data/computercraft/lua/rom/autorun/cc_aeroworks_control_handlers.lua"
local nativeLoadfile = loadfile
local queue, errors, calls = {}, {}, {}
local function enqueue(...)
    queue[#queue + 1] = table.pack(...)
end
local function input(path, value, previous, channel, socket, desk)
    enqueue("cc_aeroworks_console_input", desk or "desk-a", 1, socket or 0, "front_left",
        "aeroworks:joystick", value, channel or "x", path, previous)
end
os.pullEventRaw = function()
    local event = table.remove(queue, 1)
    assert(event, "unexpected extra event read")
    return table.unpack(event, 1, event.n)
end
printError = function(err) errors[#errors + 1] = err end
loadfile = function(path, mode, env)
    if path == "/missing.lua" then return nil, "file missing" end
    if path == "/broken.lua" then return function() error("callback failed") end end
    if path == "/oneshot.lua" then
        return function(event)
            assert(env.controlEvent == event)
            calls[#calls + 1] = event
        end
    end
    return function()
        return { onInput = function(event) calls[#calls + 1] = event end }
    end
end
assert(nativeLoadfile(runtime))()
local installedPull = os.pullEventRaw
assert(nativeLoadfile(runtime))()
assert(os.pullEventRaw == installedPull, "autorun must install once")

input("/oneshot.lua", 12, 0)
local raw = table.pack(os.pullEventRaw())
assert(raw[1] == "cc_aeroworks_console_input" and raw[7] == 12 and raw.n == 10)
assert(#calls == 1 and calls[1].value == 12 and calls[1].previousValue == 0)
assert(calls[1].channel == "x" and calls[1].socket == 0 and calls[1].deskId == "desk-a")

-- Release, negative axis values, another socket/desk, and callback style all work.
input("/handler.lua", 0, 12)
input("/handler.lua", -7, 0, "y", 2, "desk-b")
os.pullEventRaw()
os.pullEventRaw()
assert(#calls == 3 and calls[2].value == 0 and calls[3].value == -7)
assert(calls[3].socket == 2 and calls[3].deskId == "desk-b" and calls[3].channel == "y")

-- Filtered pulls still dispatch controls, then return the event the program requested.
input("/handler.lua", 1, 0)
enqueue("timer", 42)
assert(select(2, os.pullEventRaw("timer")) == 42 and #calls == 4)
input("", 1, 0)
enqueue("cc_aeroworks_console_input", "legacy", 1, 0, "front_left", "aeroworks:button", 1, "press")
os.pullEventRaw()
os.pullEventRaw()
assert(#calls == 4, "unbound and legacy inputs must not execute scripts")

input("/missing.lua", 1, 0)
input("/broken.lua", 1, 0)
input("/handler.lua", 2, 1)
os.pullEventRaw()
os.pullEventRaw()
os.pullEventRaw()
assert(#errors == 2 and #calls == 5, "script failures must leave event delivery and later scripts working")
enqueue("terminate")
assert(os.pullEventRaw("timer") == "terminate")
print("Control scripts: one-shot/callback execution, values, releases, routing, filters, legacy events and error recovery passed")
