# Class Initialization in Native Image

The semantics of Java requires that a class is initialized the first time it is accessed at run time.
Class initialization has negative consequences for ahead-of-time compilation of Java as:

* It significantly degrades the performance of native images: every class access (via field or method) requires a check if the class is already initialized. Without special optimizations, this can reduce performance by more than 2x.
* It increases the amount of work to start the application. For example, the simple "Hello, World!" program requires initialization of more than 300 classes.

To reduce the negative impact of class initialization, Native Image supports class initialization at build time: certain classes can be initialized during image building, making run-time initialization and checks unnecessary.
All the static state information from initialized classes is stored in the image.
Access to the static fields that were initialized at build time is transparent to the application and works as if the class was initialized at run time.

Specifying class initialization policies can be complicated due to the following constraints that come from class initialization semantics:

* When a class is initialized, all super classes and super interfaces with default methods must also be initialized.
Interfaces without default methods, however, are not initialized. To describe this, a short-term "relevant supertype" is used furhter, and a relevant subtype for subtypes of classes and interfaces with default methods.
* Relevant supertypes of types initialized at build time must also be initialized at build time.
* Relevant subtypes of types initialized at run time must also be initialized at run time.
* No instances classes that are initialized at run time must be present in the image.

To enjoy the complete out-of-the-box experience of Native Image and still get the benefits of build-time initailization, Native Image does three things:

* [Build-Time Initialization of Native Image Runtime](#build-time-initialization-of-native-image-runtime)
* [Automatic Initialization of Safe Classes](#automatic-initialization-of-safe-classes)
* [Explicitly Specifying Class Initialization](#explicitly-specifying-class-initialization)

To track which classes were initialized and why, one can use the flag `-H:+PrintClassInitialization`.
This flag greatly helps to configure the image build to work as intended. The goal is to have as many classes as possible initialized at build time, yet keep the correct semantics of the program.

## Build-Time Initialization of Native Image Runtime

In the Native Image runtime most of the classes are initialized at image build time.
This includes the garbage collector, important JDK classes, the deoptimizer, etc.
For all of the build-time initialized classes from the runtime, Native Image gives proper support so the semantics remains the same even if initialization happened at build time.
If there is an issue with a JDK class behaving incorrectly because of class initialization at build time, please [report an issue](https://github.com/oracle/graal/issues/new).


## Automatic Initialization of Safe Classes

For application classes, Native Image tries to find classes that can be safely initialized at build time.
A class is considered safe if all of its relevant super types are safe and if the class initializer does not call any unsafe methods or initialize other unsafe classes.

A method is considered as unsafe:

* If it transitively calls into native code (e.g., `System.out.println`): native code is not analyzed so Native Image cannot know which illegal actions could have been performed.
* If it calls methods that cannot be reduced to a single target (virtual methods).
This restriction is there to avoid the explosion of search space for the safety analysis of static initializers.
* If it is substituted by Native Image. Running initializers of substituted methods would yield different results in the hosting VM than in the produced image.
As a result, the safety analysis would consider some methods safe but their execution would lead to illegal states.

A test that shows examples of classes that are proven safe can be found [here](https://github.com/oracle/graal/blob/master/substratevm/src/com.oracle.svm.test/src/com/oracle/svm/test/TestClassInitializationMustBeSafe.java).
The list of all classes that are proven safe is displayed in a file when `-H:+PrintClassInitialization` is set on the command line.


## Explicitly Specifying Class Initialization

Each class can be initialized either (1) at run time, or (2) at build time.
To specify class-initialization policies, two flags are provided: `--initialize-at-build-time` and `--initialize-at-run-time`.
These flags allow specifying a policy for whole packages or individual classes.
For example, if you have classes `p.C1`, `p.C2`, … , or `p.Cn`, you can eagerly initialize this package with:
```shell
--initialize-at-build-time=p
```

If you want to delay one of the classes in package `p`, simply add:
```shell
--initialize-at-run-time=p.C1
```

The whole class hierarchy can be initialized at build time by passing `--initialize-at-build-time` on the command line.

Class initialization can also be specified programatically by using [`RuntimeClassInitialization`](https://github.com/oracle/graal/blob/master/sdk/src/org.graalvm.nativeimage/src/org/graalvm/nativeimage/hosted/RuntimeClassInitialization.java) from the [Native Image feature](https://github.com/oracle/graal/blob/master/sdk/src/org.graalvm.nativeimage/src/org/graalvm/nativeimage/hosted/Feature.java).
