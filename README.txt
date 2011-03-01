To build:

1. Download the Android 3.0 SDK (the app will run on 2.2) and the NDK
2. Go to the jni directory and run ndk-build
3. Open the project in Eclipse and build with the Android plugin


To create compressed mipmaps:

1. Get etcpack from http://devtools.ericsson.com/node/6
2. If you're on unix, apply the patch in this directory to etcpack:
    patch -p0 < ~/src/gdc2011-android-opengl/etcpack_unix.patch
3. Build etcpack:
    g++ -c *.cxx
    g++ -o etcpack *.o
4. Run the mips.sh script


To recreate the geometry files:

1.  gcc -o sphere sphere.c -std=c99 && ./sphere &&  cp *.jet res/raw/


To look at the program logs:

1. /path/to/android-sdk/platform-tools/adb logcat GDC11:V *:W
