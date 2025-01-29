# JDWP for SubstrateVM

For a user oriented documentation see [JDWP](/docs/JDWP.md).


## JDWP Development, running the server on HotSpot

The default configuration is that the JDWP server is another shared library built with Native Image. However, to ease debugging the debugger, it is possible to run the JDWP server on HotSpot. 

An additional non-standard mode is available to enable that:

- `-XX:JDWPOptions=...,mode=jvm`
- `-XX:JDWPOptions=...,mode=jvm:<path/to/lib:jvm>
- `-XX:JDWPOptions=...,mode=jvm:<path/to/java/home>

## Build `lib:svmjdwp`

Run `mx --native-images=lib:svmjdwp build` to build the library.

## Build a Native Executable with JDWP Support

Add the `-H:+JDWP` option to the `native-image` command:
```shell
mx native-image -H:+UnlockExperimentalVMOptions -H:+JDWP -cp <class/path> MainClass ...
```

To build and include `lib:svmjdwp` as a build artifact, run:
```shell
mx --native-images=lib:svmjdwp native-image -H:+UnlockExperimentalVMOptions -H:+JDWP -cp <class/path> MainClass ...
```

Both commands produce a binary, an `<image-name>.metadata` file.
