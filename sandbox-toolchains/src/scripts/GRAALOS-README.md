# GraalOS

This GraalVM can be used to build GraalApps for GraalOS.

To build a GraalApp, point `JAVA_HOME` at this GraalVM, and add the option `-H:+GraalOS` to the native-image commandline.
This is currently an experimental option, so `-H:+UnlockExperimentalVMOptions` is also needed.

This can also be done by sourcing the script `$JAVA_HOME/lib/graalos/build-env.sh`.
