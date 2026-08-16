# Reactive Display examples

These are compact teaching examples for the retained `cc_aeroworks.ui` runtime. Unlike the scripts in `examples/cc/`, they are not interactive topology-diagnostic regression programs.

- `reactive-inventory.lua` demonstrates a direct inventory peripheral read becoming a retained UI dependency. Item changes automatically invalidate only the scopes which read the changed value.
- `reactive-fuel.lua` demonstrates event-backed Create Display Link telemetry and derived state.

Configure a large Desk Display boot program to one of these files after copying it to the embedded ComputerControlDesk filesystem. The runtime supervisor starts configured applications automatically.
