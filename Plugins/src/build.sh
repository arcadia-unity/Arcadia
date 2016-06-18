#!/bin/sh
mv ForceEditorUpdates_m ForceEditorUpdates.m
clang -c -I/usr/local/Cellar/mono/4.2.0.179/include/mono-2.0 ForceEditorUpdates.m
mv ForceEditorUpdates.m ForceEditorUpdates_m
libtool -dynamic -o ForceEditorUpdates.dylib -macosx_version_min 10.10 -undefined dynamic_lookup ForceEditorUpdates.o
mv ForceEditorUpdates.dylib ForceEditorUpdates.bundle
rm *.o