# The simple language component for GraalVM

Truffle languages can be packaged as components which can be installed into
GraalVM using the [Graal
updater](http://www.graalvm.org/docs/reference-manual/graal-updater/). 
Running `mvn package` in the simplelanguage folder also builds a
`sl-component.jar`. 
This file is the simple languages component for GraalVM and can be installed by
running:

```
/path/to/graalvm/bin/gu install /path/to/sl-component.jar
```

