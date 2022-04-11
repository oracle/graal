---
layout: docs
toc_group: native-image
link_title: Class Initialization in Native Image
permalink: /reference-manual/native-image/ClassInitialization/
---
# Class Initialization in Native Image

The semantics of Java requires that a class is initialized the first time it is accessed at runtime.
Class initialization has negative consequences for compiling Java applications ahead-of-time for the following two reasons:

* It significantly degrades the performance of a native executable: every access to a class (via a field or method) requires a check to ensure the class is already initialized. Without optimization, this can reduce performance by more than twofold.
* It increases the amount of computation--and time--to startup an application. For example, the simple "Hello, World!" application requires more than 300 classes to be initialized.

To reduce the negative impact of class initialization, Native Image supports class initialization at build time: it can initialize classes when it builds an executable, making runtime initialization and checks unnecessary.
All the static state from initialized classes is stored in the executable.
Access to a class's static fields that were initialized at build time is transparent to the application and works as if the class was initialized at runtime.

However, Java class initialization semantics impose several constraints that complicate class initialization policies, such as:

* When a class is initialized, all its superclasses and superinterfaces with default methods must also be initialized.
Interfaces without default methods, however, are not initialized.
To accommodate this requirement, a short-term "relevant supertype" is used, as well as a "relevant subtype" for subtypes of classes and interfaces with default methods.

* Relevant supertypes of types initialized at build time must also be initialized at build time.
* Relevant subtypes of types initialized at runtime must also be initialized at runtime.
* No instances of classes that are initialized at runtime must be present in the executable.

To enjoy the complete out-of-the-box experience of Native Image and still get the benefits of build-time initialization, Native Image does three things:

* [Build-Time Initialization of a Native Executable](#build-time-initialization-of-a-native-executable)
* [Automatic Initialization of Safe Classes](#automatic-initialization-of-safe-classes)
* [Specifying Class Initialization Explicitly](#specifying-class-initialization-explicitly)

To track which classes were initialized and why, pass the command line flag `-H:+PrintClassInitialization` to the `native-image` tool.
This flag helps you configure the `native image` builder to work as required.
The goal is to have as many classes as possible initialized at build time, yet keep the correct semantics of the application.

## Build-Time Initialization of a Native Executable

Native Image initializes most JDK classes at build time, including the garbage collector, important JDK classes, and the deoptimizer.
For all of the classes that are initialized at build time, Native Image gives proper support so that the semantics remain consistent despite class initialization occurring at build time.
If you discover an issue with a JDK class behaving incorrectly because of class initialization at build time, please [report an issue](https://github.com/oracle/graal/issues/new).


## Automatic Initialization of Safe Classes

For application classes, Native Image tries to find classes that can be safely initialized at build time.
A class is considered safe if all of its relevant supertypes are safe and if the class initializer does not call any unsafe methods or initialize other unsafe classes.

A method is considered unsafe if:

* It transitively calls into native code (such as `System.out.println`): native code is not analyzed so Native Image cannot know if illegal actions are performed.
* It calls a method that cannot be reduced to a single target (a virtual method).
This restriction avoids the explosion of search space for the safety analysis of static initializers.
* It is substituted by Native Image. Running initializers of substituted methods would yield different results in the hosting Java virtual machine (VM) than in the produced executable.
As a result, the safety analysis would consider some methods safe but calling them would lead to illegal states.

A test that shows examples of classes that are proven safe can be found [here](https://github.com/oracle/graal/blob/master/substratevm/src/com.oracle.svm.test/src/com/oracle/svm/test/clinit/TestClassInitializationMustBeSafeEarly.java).
The list of all classes that are proven safe is output to a file via the `-H:+PrintClassInitialization` command line argument to the `native-image` tool.

## Specifying Class Initialization Explicitly

Two command line flags explicitly specify when a class should be initialized: `--initialize-at-build-time` and `--initialize-at-run-time`.
The flags specify whole packages or individual classes.
For example, if you have classes `p.C1`, `p.C2`, … ,`p.Cn`, you can specify that all the classes in the package `p` are initialized at build time by passing the following argument on the command line:
```shell
--initialize-at-build-time=p
```

If you want one of the classes in package `p` to be initialized at runtime, use:
```shell
--initialize-at-run-time=p.C1
```

The whole class hierarchy can be initialized at build time by passing `--initialize-at-build-time` on the command line.

Class initialization can also be specified programmatically using [`RuntimeClassInitialization`](https://github.com/oracle/graal/blob/master/sdk/src/org.graalvm.nativeimage/src/org/graalvm/nativeimage/hosted/RuntimeClassInitialization.java) from the [Native Image feature](https://github.com/oracle/graal/blob/master/sdk/src/org.graalvm.nativeimage/src/org/graalvm/nativeimage/hosted/Feature.java).

## Related Documentation
* [Accessing Resources in Native Executables](Resources.md)
* [Logging in Native Image](Logging.md)
* [Native Image Build Configuration](BuildConfiguration.md)
* [Native Image Compatibility and Optimization Guide](Limitations.md)
* [Using System Properties in Native Executables](Properties.md)
