# JDWP for SubstrateVM

For a user oriented documentation see [JDWP](/docs/JDWP.md).


## JDWP Development, running the server on HotSpot

The default configuration is that the JDWP server is another shared library built with Native Image. However, to ease debugging the debugger, it is possible to run the JDWP server on HotSpot. 

An additional non-standard mode is available to enable that:

- `-XX:JDWPOptions=...,mode=jvm`
- `-XX:JDWPOptions=...,mode=jvm:<path/to/lib:jvm>
- `-XX:JDWPOptions=...,mode=jvm:<path/to/java/home>

