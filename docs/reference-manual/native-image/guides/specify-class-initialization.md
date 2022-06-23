---
layout: ni-docs
toc_group: how-to-guides
link_title: Specify Class Initialization
permalink: /reference-manual/native-image/guides/specify-class-initialization/
---

# Specify Class Initialization Explicitly

Two command line flags explicitly specify when a class should be initialized: `--initialize-at-build-time` and `--initialize-at-run-time`.
You can use the flags to specify whole packages or individual classes.
For example, if you have the classes `p.C1`, `p.C2`, … ,`p.Cn`, you can specify that all the classes in the package `p` are initialized at build time by passing the following argument to `native-image` on the command line:
```shell
--initialize-at-build-time=p
```

If you want only one of the classes in package `p` to be initialized at runtime, use:
```shell
--initialize-at-run-time=p.C1
```

The whole class hierarchy can be initialized at build time by passing `--initialize-at-build-time` on the command line.

Class initialization can also be specified programmatically using [`RuntimeClassInitialization`](https://github.com/oracle/graal/blob/master/sdk/src/org.graalvm.nativeimage/src/org/graalvm/nativeimage/hosted/RuntimeClassInitialization.java) from the [Native Image feature](https://github.com/oracle/graal/blob/master/sdk/src/org.graalvm.nativeimage/src/org/graalvm/nativeimage/hosted/Feature.java).

### Related Documentation

* [Class Initialization](../ClassInitialization.md)
* [Native Image Build Configuration](../BuildConfiguration.md)
* [Use System Properties in a Native Executable](use-system-properties.md)
