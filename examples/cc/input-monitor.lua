-- Prints current input values for every Desk adapter in the multiblock.
-- Run on the embedded Computer Control Desk.

local desks = peripherals.find("ControlDesk")
assert(next(desks), "No ControlDesk adapters in the current network")

print("Monitoring all desk inputs. Press Ctrl+T to stop.")
while true do
  term.clear()
  term.setCursorPos(1, 1)

  local network = peripherals.getNetwork()
  print(("Network: %s, desks=%d, peripherals=%d"):format(
    network.state,
    network.deskCount,
    network.peripheralCount
  ))

  for address, desk in pairs(desks) do
    print("\n" .. address)
    local inputs = desk.getInputs()
    if next(inputs) == nil then
      print("  no input modules")
    else
      for socket, value in pairs(inputs) do
        print(("  socket %d = %s"):format(
          socket,
          textutils.serialize(value, { compact = true })
        ))
      end
    end
  end

  sleep(0.25)
  desks = peripherals.find("ControlDesk")
end
