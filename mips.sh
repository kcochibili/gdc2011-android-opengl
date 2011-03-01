#!/bin/bash

export DYLD_LIBRARY_PATH=/Users/$USER/Downloads/ImageMagick-6.6.5/lib/

~/Downloads/ImageMagick-6.6.5/bin/convert res/drawable/jpg_earth_map.jpg map.ppm

# On 2.2, the largest uncompressed size for compressed files is 1048576. This
# file is 16 bytes to big, so keep it uncompressed. On 2.3, this is not needed.
~/Downloads/android/etcpack/etcpack map.ppm assets/earth_map_0.pkm.jet

for level in {1..11}; do
  ~/Downloads/ImageMagick-6.6.5/bin/mogrify -resize 50% map.ppm
  ~/Downloads/android/etcpack/etcpack map.ppm assets/earth_map_$level.pkm
done
