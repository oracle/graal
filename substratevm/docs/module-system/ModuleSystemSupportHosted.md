# Module Support in the Image Builder

### Running the image builder exclusively as modularized application on the JVM

Currently, the builder consists of the following modules:

#### graal

* org.graalvm.nativeimage.builder (contains main entry point)

* org.graalvm.nativeimage.base (common utilities shared with other builder modules)
* org.graalvm.nativeimage.pointsto (static analysis)
* org.graalvm.nativeimage.objectfile (image (object-file and debuginfo) writing)

* org.graalvm.nativeimage.llvm (native-image LLVM backend - optional)
* org.graalvm.truffle.runtime.svm (runtime support for Truffle languages - optional)

#### graal-enterprise

* com.oracle.svm.svm_enterprise (native-image enterprise optimizations)
* com.oracle.truffle.enterprise.svm (truffle-related enterprise optimizations)
* com.oracle.svm_enterprise.ml_dataset (ML-based PGO profile inference)
* com.oracle.svm.svm_enterprise.llvm (LLVM backend enterprise optimizations)

We want the image builder to **be able to operate with minimal amount of JDK modules** (especially its mandatory modules).
Ideally the builder should be usable when running with a stripped down JDK that only contains a few modules beside `java.base`.

Adding new module dependencies to the builder will be detected and reported as am error.
E.g. if we add a module dependency to any of our builder modules (directly of transitively) to e.g. `jdk.sctp` we get:
```text
$ mx native-image HelloWorld
Fatal error: com.oracle.svm.core.util.VMError$HostedError: Unexpected image builder module-dependencies: jdk.sctp
	at org.graalvm.nativeimage.builder/com.oracle.svm.core.util.VMError.shouldNotReachHere(VMError.java:78)
	at org.graalvm.nativeimage.builder/com.oracle.svm.hosted.NativeImageGeneratorRunner.checkBootModuleDependencies(NativeImageGeneratorRunner.java:210)
	at org.graalvm.nativeimage.builder/com.oracle.svm.hosted.NativeImageGeneratorRunner.start(NativeImageGeneratorRunner.java:140)
	at org.graalvm.nativeimage.builder/com.oracle.svm.hosted.NativeImageGeneratorRunner.main(NativeImageGeneratorRunner.java:97)
```

Using `-H:CheckBootModuleDependencies=0` can be used to temporarily disable this check. `-H:CheckBootModuleDependencies=2` 
gives more information and helps with debugging module dependency issues.

### Handling support of non-essential JDK modules in the builder

For non-essential modules of the JDK we create module dependencies to those modules at builder runtime. 
E.g. in `com.oracle.svm.hosted.jdk.JNIRegistrationJavaNio` we have
```java
public class JNIRegistrationJavaNio extends JNIRegistrationUtil implements InternalFeature {

    private static final boolean isJdkSctpModulePresent;
    // ...

    static {
        Module thisModule = JNIRegistrationJavaNio.class.getModule();
        var sctpModule = ModuleLayer.boot().findModule("jdk.sctp");
        if (sctpModule.isPresent()) {
            thisModule.addReads(sctpModule.get());
        }
        isJdkSctpModulePresent = sctpModule.isPresent();
        // ...
    }
}
```
Now we use `isJdkSctpModulePresent` to provide sctp support when the JDK we are running on is actually containing it:  
```java
public class JNIRegistrationJavaNio extends JNIRegistrationUtil implements InternalFeature {
    
    // ...

    @Override
    public void duringSetup(DuringSetupAccess a) {
        // ...

        if (isPosix()) {
            // ...
            if (isLinux() && isJdkSctpModulePresent) {
                rerunClassInit(a, "sun.nio.ch.sctp.SctpChannelImpl");
            }
        } // ... 
    }    
    
    @Override
    public void beforeAnalysis(BeforeAnalysisAccess a) {
        // ...
        
        if (isPosix()) {
            // ...
            if (isLinux() && isJdkSctpModulePresent) {
                a.registerReachabilityHandler(JNIRegistrationJavaNio::registerSctpChannelImplInitIDs, method(a, "sun.nio.ch.sctp.SctpChannelImpl", "initIDs"));
            }

        } // ...
    }
}
```

Simulating a JDK that does not contain a non-essential module can be simulated/tested with
```text
 $ mx native-image -J--limit-modules=<module-name> ...
```

Modules that the builder itself needs to operate should be added as explicit module dependencies in `suite.py`. 

## Transformation of "requiresConcealed" to --add-exports

Often our sources need access to packages that are not exported by their modules. To even compile those sources we
need to tell `mx` via `suite.py` that we need certain non-public packages to be opened up for us. For that we have
`requiresConcealed` snippets in `suite.py`. For example, the `"com.oracle.svm.util"` project has:
```text
"requiresConcealed" : {
    "java.base" : ["jdk.internal.module"],
},
```
because it contains utilities to modify the package visibility of modules at builder-runtime. To implement those,
access to package `jdk.internal.module` in the `java.base` module is needed. Since **`"com.oracle.svm.util"` is part our
`org.graalvm.nativeimage.base` module** any use that module needs to go along with command line option
```
--add-exports=java.base/jdk.internal.module=org.graalvm.nativeimage.base`
```

The image builder is started by the native-image driver via a java command line invocation that gets constructed by the
driver. Using `native-image --verbose` makes that visible:
```text
Executing [
HOME=/home/graaluser \
LANG=en_US.UTF-8 \
PATH=/home/graaluser/OLabs/jdk-21/bin:/home/graaluser/OLabs/main/mx:/home/graaluser/.sdkman/candidates/java/current/bin:/home/graaluser/.sdkman/candidates/gradle/current/bin:/home/graaluser/.local/bin:/home/graaluser/bin:/usr/local/bin:/usr/local/sbin:/usr/bin:/usr/sbin \
PWD=/home/graaluser/OLabs/main/graal-enterprise/substratevm-enterprise/spring-boot-3_2-graalvm-21-native-image-bug/mvn \
USE_NATIVE_IMAGE_JAVA_PLATFORM_MODULE_SYSTEM=true \
/home/graaluser/OLabs/main/graal/sdk/mxbuild/linux-amd64/GRAALVM_427B7851E4_JAVA21/graalvm-427b7851e4-java21-24.0.0-dev/bin/java \
-XX:+UseParallelGC \
-XX:+UnlockExperimentalVMOptions \
-XX:+EnableJVMCI \
-Dtruffle.TrustAllTruffleRuntimeProviders=true \
-Dtruffle.TruffleRuntime=com.oracle.truffle.api.impl.DefaultTruffleRuntime \
-Dgraalvm.ForcePolyglotInvalid=true \
-Dgraalvm.locatorDisabled=true \
-Dsubstratevm.HostLibC=glibc \
-Dsubstratevm.IgnoreGraalVersionCheck=true \
--add-exports=java.base/com.sun.crypto.provider=org.graalvm.nativeimage.builder \
--add-exports=java.base/jdk.internal.access=org.graalvm.nativeimage.builder \
--add-exports=java.base/jdk.internal.event=org.graalvm.nativeimage.builder \
--add-exports=java.base/jdk.internal.loader=org.graalvm.nativeimage.builder \
--add-exports=java.base/jdk.internal.logger=org.graalvm.nativeimage.builder \
--add-exports=java.base/jdk.internal.misc=com.oracle.svm.svm_enterprise,org.graalvm.nativeimage.builder,org.graalvm.nativeimage.objectfile,org.graalvm.nativeimage.pointsto \
--add-exports=java.base/jdk.internal.module=org.graalvm.nativeimage.base,org.graalvm.nativeimage.builder \
...
```
As we can see a lot of `--add-exports=` are part of that VM invocation. All of those are autogenerated by
`substratevm/mx.substratevm/mx_substratevm.py` are part of building the substratevm suite. Function
`mx_substratevm.SubstrateCompilerFlagsBuilder.compute_graal_compiler_flags_map` contains the following call:
```text
    distributions_transitive = mx.classpath_entries(self.buildDependencies)
    required_exports = mx_javamodules.requiredExports(distributions_transitive, get_jdk())
    exports_flags = mx_sdk_vm.AbstractNativeImageConfig.get_add_exports_list(required_exports)
    graal_compiler_flags_map['11'].extend(exports_flags)
```
Here based on the transitive dependencies of all entries in the `SubstrateCompilerFlagsBuilder.flags_build_dependencies`
class-field, the `--add-exports=` clauses needed to run the builder are generated. In
`substratevm-enterprise/mx.substratevm-enterprise/mx_substratevm_enterprise.py` we have to extend that class-field to
ensure we also get `--add-exports=` clauses for the enterprise modules.

In case we add more modules for the image builder that require `--add-exports=`, the class-field needs to be adjusted
accordingly. **Please do not hardcode any `--add-exports=` in compute_graal_compiler_flags_map and instead rely on automatic generation described here.**

### Add-Exports in launchers

Our bash/cmd launchers also make use of this auto-generation of required `--add-exports=` options. For example, the
reason the bash-launcher of the native-image driver is able to get started as a JVM application **on the module-path**
is that the required `--add-exports=` were automatically added to its bash launcher file.
```text
module_launcher="True"
if [[ "${module_launcher}" == "True" ]]; then
    main_class='--module org.graalvm.nativeimage.driver/com.oracle.svm.driver.NativeImage'
    app_path_arg="--module-path"
    IFS=" " read -ra add_exports <<< "--add-exports=java.base/com.sun.crypto.provider=org.graalvm.nativeimage.builder
                                      --add-exports=java.base/jdk.internal.access=org.graalvm.nativeimage.builder
                                      --add-exports=java.base/jdk.internal.event=org.graalvm.nativeimage.builder
                                      --add-exports=java.base/jdk.internal.loader=org.graalvm.nativeimage.builder
                                      --add-exports=java.base/jdk.internal.logger=org.graalvm.nativeimage.builder
                                      ... 
```

## Opening up modules for features at image-buildtime

For native-image `Feature` implementations that are not always used (e.g. because they are only enabled when its related
option is on) and all other optional code paths in the builder it makes sense to open up packages/classes only
on-demand. To make this process as painless as possible we have the `access*`-methods in class
`com.oracle.svm.util.ModuleSupport` that is part of our `org.graalvm.nativeimage.base` module.
```java
class ModuleSupport {

    @Platforms(Platform.HOSTED_ONLY.class)
    public enum Access {
        OPEN { /*...*/ }, // same semantics as --add-opens
        EXPORT { /*...*/ };  // same semantics as --add-exports
        /*...*/
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void accessModuleByClass(Access access, Class<?> accessingClass, Class<?> declaringClass) { /*...*/ }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void accessPackagesToClass(Access access, Class<?> accessingClass, boolean optional, String moduleName, String... packageNames) { /*...*/ }
    
}
```
How to use those methods can easily be inferred by the exiting calls in our codebase. 

### Do not use (rely on) USE_NATIVE_IMAGE_JAVA_PLATFORM_MODULE_SYSTEM=false

When environment variable `USE_NATIVE_IMAGE_JAVA_PLATFORM_MODULE_SYSTEM` was introduced in June 2022 we made clear 
from the beginning that it will be removed eventually. Do not rely on `USE_NATIVE_IMAGE_JAVA_PLATFORM_MODULE_SYSTEM`.
We are close to removing it:

* `GR-30433` Disallow the deprecated environment variable USE_NATIVE_IMAGE_JAVA_PLATFORM_MODULE_SYSTEM=false.

⚠️ If an image-build uses builder-internal classes (e.g. as part of one of its NI `Feature` implementations) removing
`USE_NATIVE_IMAGE_JAVA_PLATFORM_MODULE_SYSTEM=false` often requires adding `--add-exports=...` to the arguments of the VM
that runs the image builder. E.g. often `--add-exports=jdk.graal.compiler/jdk.graal.compiler.options=ALL-UNNAMED`
needs to be added because a NI Feature defines/uses something like
```text
@Option //
public static final OptionKey<Boolean> MyFeatureOption = new OptionKey<>(true);
```
Better than adding that add exports is to not rely on Builder (or Graal) internals. E.g. use a system property instead. 

# NativeImageClassLoader

## The problem with the previous approach

Originally we used the following classloader setup to support building images for applications on class-path and module-path.

* jdk.internal.loader.ClassLoaders$AppClassLoader (image builder modules)
* * ∟ java.net.URLClassLoader (loader for classes from image class-path - `-imagecp`)
* * * ∟ jdk.internal.loader.Loader (created by `ModuleLayer.defineModulesWithOneLoader` for classes from image module-path - `-imagemp`)

This worked well until we had to support building applications that use both - class-path and module-path - and want to
access classes from the given module-path from classes given on class-path.

Assume we have a **Java Module "MyModule"** on the image module-path that **exports "mypackage"**:

```java
package mypackage;

public class MyClass {
    public static void myMethod() {
        // ...
    }
}
```
and the following **class on the image class-path**:
```java
public class Main {
    public static void main(String[] args) {
        // Calling into class from module-path
        mypackage.MyClass.myMethod();
    }
}
```

This can be run on JVM just fine
```text
java -p mymodule.jar -cp . --add-modules=MyModule Main
```

Using the old classloader setup we are not able to build an image for this!

* java.net.URLClassLoader is responsible for loading `Main`
* jdk.internal.loader.Loader is responsible for loading `mypackage.MyClass`

**The URLClassLoader has no way to see `mypackage.MyClass`.**

* Q: What about reversing the classloader order and make `jdk.internal.loader.Loader` the parent of `java.net.URLClassLoader`
* A: Congrats! You just broke automatic modules! Automatic Modules allow bridging to classes from class-path. 
* --> There is no way around the implementation of a custom classloader that can load from class- and module-path.

## What we have now

[#7302](https://github.com/oracle/graal/pull/7302) Unified native-image classloader for module- and class-path.

* jdk.internal.loader.ClassLoaders$AppClassLoader (image builder modules)
* * ∟ com.oracle.svm.hosted.NativeImageClassLoader (loader for classes from `-imagecp` and/or `-imagemp`)

This change now allows us to build the above example as expected
```text
native-image -p mymodule.jar -cp . --add-modules=MyModule Main ✅
```

## What is still missing 

The new classloader currently does not respect native-image options `--add-modules` and `--limit-modules`.
It behaves as if `--add-modules` always contains all modules that where provided on image module-path. I.e. all classes
from image class-path are able to use classes from image module-path.

`GR-48330` Make --add-modules and --limit-modules work correctly at image-buildtime
