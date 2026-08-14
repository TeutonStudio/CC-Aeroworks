local controls = controls
assert(controls, "This program must run on an embedded ComputerControlDesk computer")

local function findChannel(moduleId, channelName)
    for _, channel in ipairs(controls.getChannels()) do
        if channel.module == moduleId and channel.channel == channelName then
            return channel
        end
    end
    return nil
end

local pitch = findChannel("aeroworks:yoke", "pitch")
local turn = findChannel("aeroworks:yoke", "turn")
assert(pitch and turn, "No yoke with pitch/turn channels found in this ControlDesk network")
assert(pitch.desk == turn.desk and pitch.socket == turn.socket,
    "Pitch and turn did not resolve to the same yoke")

local function command(turnValue, pitchValue)
    controls.overrideBatch({
        {
            desk = turn.desk,
            socket = turn.socket,
            channel = "turn",
            value = turnValue,
        },
        {
            desk = pitch.desk,
            socket = pitch.socket,
            channel = "pitch",
            value = pitchValue,
        },
    })
end

local ok, err = pcall(function()
    print("Computer control authority engaged. Watch the yoke move.")
    command(-8, 0)
    sleep(1)
    command(8, -5)
    sleep(1)
    command(0, 5)
    sleep(1)
    command(0, 0)
    sleep(1)
end)

local released = controls.releaseAll()
print(("Released %d control override(s)."):format(released))

if not ok then
    error(err, 0)
end
