#!/usr/bin/env bash
set -euo pipefail

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source_texture="$(mktemp --suffix=.png)"
accent_mask="$(mktemp --suffix=.png)"
normal_base="$(mktemp --suffix=.png)"
advanced_base="$(mktemp --suffix=.png)"
trap 'rm -f "$source_texture" "$accent_mask" "$normal_base" "$advanced_base"' EXIT

unzip -p "$root_dir/libs/aeroworks-1.5.0.jar" \
  assets/aeroworks/textures/block/controls/consoles/controldesk.png > "$source_texture"

# The native desk palette keeps its structural wood below red=89 and uses brighter pixels for the
# painted brass panels. Recolor only those panel pixels so copycat materials and open sides remain
# visible through the transparent overlay.
magick "$source_texture" -alpha on \
  -fx 'a>0 && r>88/255 ? 1 : 0' "$accent_mask"

magick "$source_texture" -colorspace Gray -level 4%,96% \
  -evaluate Multiply 0.72 -evaluate Add 0.22 "$normal_base"
magick -size 64x64 canvas:none "$normal_base" "$accent_mask" -composite \
  "$root_dir/src/main/resources/assets/cc_aeroworks/textures/block/computer_control_desk_multiblock.png"

magick "$source_texture" -colorspace Gray -level 4%,96% \
  +level-colors '#7d641d','#fff29a' "$advanced_base"
magick -size 64x64 canvas:none "$advanced_base" "$accent_mask" -composite \
  "$root_dir/src/main/resources/assets/cc_aeroworks/textures/block/advanced_computer_control_desk_multiblock.png"
