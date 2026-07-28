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

To enable just-in-time (JIT) compilation of run-time-loaded bytecode, use `-H:+GraalJITCompileAtRuntime`.

## Resource URLs
The semantics of the internal `resource:` URL depends on `ClassForNameRespectsClassLoader`:
* `-H:-ClassForNameRespectsClassLoader`: the host part of the URL is the module name, matching the legacy resource lookup scheme.
  For example, `resource://java.base/0!/module-info.class`.
* `-H:+ClassForNameRespectsClassLoader`: the user info part of the URL is the module name and the host part of the URL is the resource loader key (`boot`, `platform`, `app`, or `synthetic-N`).
  For example, `resource://java.base@boot/0!/module-info.class`.
  The loader is the discriminator required to preserve Java class-loader resource lookup behavior.
  This is needed for module-reader lookups that cross the `ModuleReader.find(String)` interface as a `URI`: the URI string must preserve the module identity because URL stream handler state does not survive a `URL` to `URI` to `URL` round trip.

The path part of a `resource:` URL has the form `/<root-id>!/<resource-path>`.
The root id is a dense id for the source root that provided the resource within the selected resource loader.
For class-path resources, the source root is a class-path entry such as a jar file or an exploded classes directory.
For example, if the application loader sees this class path:

```text
project-foo.jar
project-bar.jar
```

then Native Image assigns dense root ids for that loader:

```text
project-foo.jar -> 0
project-bar.jar -> 1
```

The root id is preserved when a resource URL is converted to a `URI` and then to a `Path`, so resource file-system operations continue to observe the resource as it appears in that source root.

This matters for directory resources that exist in more than one source root.
For example, if two class-path entries contain different `META-INF/micronaut/` directories, resource lookup returns one URL per directory variant, such as:

```text
resource://app/0!/META-INF/micronaut/
resource://app/1!/META-INF/micronaut/
```

If user code converts each URL to a `Path` and walks it with `Files.walk`, the Native Image resource file system lists the selected directory variant's content.
It does not list the merged children from all roots for every URL.
This matches HotSpot-style resource lookup behavior where each directory resource URL represents the contents from one class-path root.

## Current Limitations
* Parallel class loading is explicitly disabled and not supported.
* The assertion status of classes is fixed at image build time.
* Methods or static fields removed by analysis from a class included in the image have no fallback and cause an error if run-time-loaded code uses them.
