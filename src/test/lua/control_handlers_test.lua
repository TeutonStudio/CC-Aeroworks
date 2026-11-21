-- Run with Lua 5.3+ from the repository root. No Minecraft or CraftOS installation required.
local runtime = "src/main/resources/data/computercraft/lua/rom/autorun/cc_aeroworks_control_handlers.lua"
local nativeLoadfile = loadfile
local queue, errors, calls = {}, {}, {}
local sequence = 0
local broadcast = false
local function enqueue(...)
    queue[#queue + 1] = table.pack(...)
end
local function input(path, value, previous, channel, socket, desk)
    sequence = sequence + 1
    enqueue("cc_aeroworks_console_input", desk or "desk-a", 1, socket or 0, "front_left",
        "aeroworks:joystick", value, channel or "x", path, previous, sequence)
end
os.pullEventRaw = function()
    if broadcast then return coroutine.yield() end
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
assert(raw[1] == "cc_aeroworks_console_input" and raw[7] == 12 and raw.n == 11)
assert(#calls == 1 and calls[1].value == 12 and calls[1].previousValue == 0)
assert(calls[1].id == 1)
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

-- CraftOS parallel reads an event in its parent and broadcasts it to waiting children.
-- All three pulls must preserve delivery while executing the bound handler just once.
broadcast = true
local received = 0
local parent = coroutine.create(function()
    local children = {}
    for i = 1, 2 do
        children[i] = coroutine.create(function()
            local event = table.pack(os.pullEventRaw())
            assert(event[1] == "cc_aeroworks_console_input")
            received = received + 1
        end)
        assert(coroutine.resume(children[i]))
    end
    local event = table.pack(os.pullEventRaw())
    for _, child in ipairs(children) do
        assert(coroutine.resume(child, table.unpack(event, 1, event.n)))
    end
end)
assert(coroutine.resume(parent))
sequence = sequence + 1
local event = table.pack("cc_aeroworks_console_input", "desk-a", 1, 0, "left",
    "aeroworks:button", 15, "press", "/handler.lua", 0, sequence)
assert(coroutine.resume(parent, table.unpack(event, 1, event.n)))
assert(received == 2 and #calls == 6, "parallel broadcast must execute the handler exactly once")
broadcast = false

-- Repeated payloads are separate inputs when their server IDs differ. A late copy of an
-- earlier event remains suppressed, even after another event has been handled.
input("/handler.lua", 15, 0, "press", 0)
os.pullEventRaw()
enqueue(table.unpack(event, 1, event.n))
os.pullEventRaw()
assert(#calls == 7, "distinct IDs must dispatch, but late duplicate IDs must not")
print("Control scripts: execution, routing, filters, error recovery and once-only parallel dispatch passed")
