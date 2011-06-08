The classes from the projects C1X, CRI, and HotSpotVM have to be on the classpath. The classes of the HotSpotVM project have to be on the bootclasspath
Example command line arguments for HotSpot:
-XX:+UseC1X -XX:TraceC1X=5 -Xbootclasspath/a:THIS_DIRECTORY/bin;MAXINE_DIR/C1X/bin;MAXINE_DIR/CRI/bin SomeClass

