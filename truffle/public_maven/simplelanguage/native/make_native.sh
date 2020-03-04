#!/usr/bin/env bash
if [[ $SL_BUILD_NATIVE == "false" ]]; then
    echo "Skipping the native image build because SL_BUILD_NATIVE is set to false."
    exit 0
fi
"$JAVA_HOME"/bin/native-image \
    --macro:truffle --no-fallback --initialize-at-build-time \
    -cp ../language/target/simplelanguage.jar:../launcher/target/sl-launcher.jar \
    com.oracle.truffle.sl.launcher.SLMain \
    slnative
