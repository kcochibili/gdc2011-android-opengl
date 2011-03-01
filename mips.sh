#!/bin/bash

export DYLD_LIBRARY_PATH=/Users/$USER/Downloads/ImageMagick-6.6.5/lib/

~/Downloads/ImageMagick-6.6.5/bin/convert res/drawable/jpg_earth_map.jpg map.ppm

~/Downloads/android/etcpack/etcpack map.ppm assets/earth_map_0.pkm

for level in {1..11}; do
  ~/Downloads/ImageMagick-6.6.5/bin/mogrify -resize 50% map.ppm
  ~/Downloads/android/etcpack/etcpack map.ppm assets/earth_map_$level.pkm
done
