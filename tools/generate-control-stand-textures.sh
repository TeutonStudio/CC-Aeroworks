#!/usr/bin/env bash
set -euo pipefail

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source_texture="$(mktemp --suffix=.png)"
accent_mask="$(mktemp --suffix=.png)"
normal_base="$(mktemp --suffix=.png)"
advanced_base="$(mktemp --suffix=.png)"
trap 'rm -f "$source_texture" "$accent_mask" "$normal_base" "$advanced_base"' EXIT

unzip -p "$root_dir/libs/aeroworks-1.5.0.jar" \
  assets/aeroworks/textures/block/controls/consoles/control_stand.png > "$source_texture"

# Aeroworks separates the stand's painted red/orange panels from its neutral structure. Keep only
# those panels so the translucent overlay changes the computer trim while copycat base materials,
# open sides and the floor/ceiling model geometry remain visible.
magick "$source_texture" -alpha on \
  -fx 'a>0 && r>g*1.18 && r>b*1.05 ? 1 : 0' "$accent_mask"

magick "$source_texture" -colorspace Gray -level 4%,96% \
  -evaluate Multiply 0.72 -evaluate Add 0.22 "$normal_base"
magick -size 128x128 canvas:none "$normal_base" "$accent_mask" -composite \
  "$root_dir/src/main/resources/assets/cc_aeroworks/textures/block/computer_control_stand_multiblock.png"

magick "$source_texture" -colorspace Gray -level 4%,96% \
  +level-colors '#7d641d','#fff29a' "$advanced_base"
magick -size 128x128 canvas:none "$advanced_base" "$accent_mask" -composite \
  "$root_dir/src/main/resources/assets/cc_aeroworks/textures/block/advanced_computer_control_stand_multiblock.png"
