# Run-Time Class Loading

Run-time class loading, also called Crema, allows `ClassLoader.defineClass` to work at run time, creating new `DynamicHub`s.
It is enabled at build time with the `-H:+RuntimeClassLoading` option.

## Extra Setup

While `-H:+RuntimeClassLoading` is sufficient to allow `defineClass` to work, additional setup may be needed depending on the specific use case.
* If jar files are to be loaded with a `URLClassLoader`, register the `sun.net.www.protocol.jar.Handler` constructor in reachability metadata.
* If JDK classes need to be loaded at run time (e.g. to allow a dynamically loaded app to access JDK classes not in the image),
  set `-H:+AllowJRTFileSystem` at build time, and ensure the `java.home` system property points to a JDK at run time.
  For example, pass `-Djava.home=...` when starting the native image.
  Note that the only requirement for this directory is to contain a `lib/modules` file with the necessary classes.
* If classes from the system (a.k.a. "App") class loader need to be loaded at run time, use `--initialize-at-run-time=jdk.internal.loader.ClassLoaders` at build time, and set the `java.class.path` system property at run time.

Run-time-loaded classes can link to other run-time-loaded classes or to build-time-loaded classes.
For build-time-loaded classes, the native image build process may have removed some methods or static fields.
Run-time class loading cannot reload a class that was already partially included into the native image at build time.
This will result in errors if run-time-loaded classes try to use such removed methods or static fields.
A typical way to address this is to use `-H:Preserve=package=...` at build time to ensure all methods and fields of a package are included in the native image.

## Resource URLs
The internal `resource:` URL format uses mode-dependent location semantics.
Without class-loader-aware lookup (`-H:-ClassForNameRespectsClassLoader`), the URL host is the module name, matching the legacy resource lookup scheme.
With class-loader-aware lookup (`-H:+ClassForNameRespectsClassLoader`), the URL host is the resource loader key (`boot`, `platform`, `app`, or `synthetic-N`), because the loader is the discriminator required to preserve Java class-loader resource lookup behavior.
For named module resources in class-loader-aware mode, the URL user-info stores the module name, for example `resource://java.base@boot/module-info.class`.
This is needed for module-reader lookups that cross the `ModuleReader.find(String)` interface as a `URI`: the URI string must preserve the module identity because URL stream handler state does not survive a `URL` to `URI` to `URL` round trip.
When such a URL is converted to a `Path`, the Native Image resource file system exposes the loader key as the top-level path entry, similar to how `jrt:` paths expose the module name as the top-level path entry.

## Current Limitations
* Parallel class loading is explicitly disabled and not supported.
* JNI is not supported (JNI will not find run-time-loaded classes/methods/fields, and native methods in run-time-loaded classes will not link).
* "condy" entries are not supported in the constant pool of run-time-loaded classes.
* The boot layer modules are frozen at build time, and it may not be possible to load additional classes at run time from modules of the build-time module path.
* The assertion status of classes is fixed at image build time.
* Methods or static fields that are removed by analysis in a class that is inluded in the image have no fall-back and will cause an error if used by runtime loaded code.
