---
layout: docs
toc_group: build-overview
link_title: Native Image Layers
permalink: /reference-manual/native-image/overview/NativeImageLayers/
---

# Native Image Layers

The Native Image Layers feature allows splitting an application and its required libraries into separate binaries: a
thin layer executable supported by a chain of shared libraries.
In contrast to regular Native Image building, this mode of operation enables sharing common libraries and VM/JDK code
between multiple applications, resulting in reduced memory usage at run time.
Moreover, it reduces the time to build an application since shared layers are only built once.

> Note this feature is experimental and under development.

### Table of Contents

* [Native Image Layers Architecture](#native-image-layers-architecture)
* [Creating Native Image Layers](#creating-native-image-layers)
* [Packaging Native Image Layers](#packaging-native-image-layers)

## Native Image Layers Architecture

When using Native Image Layers an application is logically composed of the final application executable plus one or more
supporting layers containing code that the application requires.
The supporting layers are called _shared layers_ and the application executable is referred to as either the
_application layer_ or _executable layer_.
The _initial_ or _base_ layer is a shared layer containing VM internals and core _java.base_ functionality at a minimum.
It can also contain modules specific to a certain framework that the application may be built upon.
We refer to any subsequent layer built on top of a shared layer as an _extension layer_.

At run time a shared layer is a shared object file on which other intermediate layers or executable application
layers can be dependent.
Thus, the application and its supporting shared layers form a chain:

```shell
base-layer.so                    # initial layer (includes VM/JDK code)
└── mid-layer.so                 # intermediate layer, depends on base-layer.so, adds extra functionality 
    └── executable-image         # final application executable, depends on mid-layer.so and base-layer.so
```

This architecture enables the sharing of layers between applications when the hierarchy of layers forms a tree structure.
For example, at run time there could be four applications that share one base layer and two intermediate layers:

```shell
base-layer.so                    # initial layer (includes VM/JDK code)
├── executable-image-0           # final application executable, depends on base-layer.so
├── mid-layer-0.so               # intermediate layer, depends on base-layer.so, adds extra functionality 
│   ├── executable-image-00      # final application executable, depends on mid-layer-0.so and base-layer.so
│   └── executable-image-01      # final application executable, depends on mid-layer-0.so and base-layer.so
└── mid-layer-1.so               # intermediate layer, depends on base-layer.so, adds extra functionality
    └── executable-image-10      # final application executable, depends on mid-layer-1.so and base-layer.so
```

> Note: The current implementation is limited to only a base layer and an application layer.

## Creating Native Image Layers

> Note: The API options described in this section are experimental and not yet released.
> To interact with layers you must currently use their hosted variant: -H:LayerCreate and -H:LayerUse.

To create and use layers `native-image` accepts two options: `--layer-create` and `--layer-use`.

First, `--layer-create` builds an image layer archive from code available on the class or module path:

```
--layer-create=[layer-file.nil][,module=<module-name>][,package=<package-name>]
              builds an image layer file from the modules and packages specified by "module" and "package".
              The file name, if specified, must be a simple file name, i.e., not contain any path separators, 
              and have the *.nil extension. Otherwise, the layer-file name is derived from the image name.
              This will generate a Native Image Layer archive file containing metadata required to build
              either another layer or a final application executable that depends on this layer.
              The archive also contains the shared object file corresponding to this layer. 
              If this option is specified with an empty value then it disables any prior layer creation option on the command line.
```

A layer archive file has a _.nil_ extension, acronym for **N**ative **I**mage **L**ayer.

Second, `--layer-use` consumes a shared layer, and can extend it or create a final executable:

```
--layer-use=layer-file.nil
            loads the given layer archive and makes it available to the build process.
            If option --layer-create=another-layer.nil is specified this creates a new layer that depends on the loaded layer.
            If no other layer option is specified this creates a final application executable that depends on the loaded layer.
            If this option is specified with an empty value then it disables any prior layer application option on the command line.
```

Specifying each option more than once is allowed, however all but the last instance is ignored.
Passing an empty value will disable the option, overwriting any previous value.
These capabilities are useful in builds with long dependency chains where one may want to overwrite or disable layer
related options from a library dependency.

#### Example Layers Option Usage

```shell
# given an application that would usually be built like this
native-image --module-path target/AwesomeLib-1.0-SNAPSHOT.jar --another-extra-option -cp . AwesomeHelloWorld

# you can now create a base-layer.nil layer from the java.base and java.awesome.lib modules
native-image --layer-create=base-layer.nil,module=java.base,module=java.awesome.lib --module-path target/AwesomeLib-1.0-SNAPSHOT.jar

# then build the application on top of the preexisting base layer 
native-image --layer-use=base-layer.nil --module-path target/AwesomeLib-1.0-SNAPSHOT.jar --another-extra-option -cp . AwesomeHelloWorld

# extend the base layer with a mid layer that adds an extra module, chaining the layers together
native-image --layer-use=base-layer.nil --layer-create=mid-layer.nil,module=java.ultimate.io.lib --module-path target/UltimateIoLib-1.0-SNAPSHOT.jar

# create an executable based on the mid layer (and implicitly on the base layer) and using the additional UltimateAwesomeHelloWorld.class from the classpath
native-image --layer-use=mid-layer.nil --module-path target/AwesomeLib-1.0-SNAPSHOT.jar:target/UltimateIoLib-1.0-SNAPSHOT.jar -cp . UltimateAwesomeHelloWorld

# the same application can be built directly on the base-layer.nil
native-image --layer-use=base-layer.nil --module-path target/AwesomeLib-1.0-SNAPSHOT.jar:target/UltimateIoLib-1.0-SNAPSHOT.jar -cp . UltimateAwesomeHelloWorld

# again, the same application can be built without any layers
native-image --module-path target/AwesomeLib-1.0-SNAPSHOT.jar:target/UltimateIoLib-1.0-SNAPSHOT.jar -cp . UltimateAwesomeHelloWorld

# additionally a shared library can be built as a top layer containing the extra C entry points, based on a preexisting layer
native-image --layer-use=base-layer.nil --module-path target/AwesomeLib-1.0-SNAPSHOT.jar --shared

# or without the layer
native-image --module-path target/AwesomeLib-1.0-SNAPSHOT.jar --shared

```

### Invariants

1. Every image build only creates one layer.

2. The image build command used to create the application layer must work without `--layer-use` and create a standalone image.
   The standalone command must completely specify the module/classpath and all other necessary configuration options

3. The layer specified by `--layer-use` must be compatible with the standalone command line.
   The compatibility rules refer to:
    - class/jar file compatibility (`-cp`, `-p`, same JDK, same GraalVM, same libs, etc.)
    - config compatibility: GC config, etc.
    - Java system properties and Environment variables compatibility
    - access compatibility: no additional unsafe and field accesses are allowed

   In case of incompatibility an error message is printed and the build process is aborted.

### Limitations

- Layers are platform dependent. A layer created with `--layer-create` on a specific OS/architecture
  cannot be loaded by `--layer-use` on a different OS/architecture.
- Each layer can only depend on a previous layer. We explicitly make it impossible to depend on more than one layer to
  avoid any potential issues that can stem from _multiple inheritance_.
- A shared layer is using the _.so_ extension to conform with the standard OS loader restrictions. However, it is not a
  standard shared library file, and it cannot be used with other applications.

## Packaging Native Image Layers

At build time a shared layer is stored in a layer archive that contains the following artifacts:

```shell
[shared-layer.nil]                   # shared layer archive file
  ├── shared-layer.json              # snapshot of the shared layer metadata; used by subsequent build processes
  ├── shared-layer.so                # shared object of the shared layer; used by subsequent build processes and at run time
  └── shared-layer.properties        # contains info about layer input data
```

The layer snapshot file will be consumed by subsequent build processes that depend on this layer.
It contains Native Image metadata, such as the analysis universe and available image singletons.
The shared object file will be used at build time for symbol resolution, and at run time for application execution.
The layer properties file contains metadata that uniquely identifies this layer: the options used to create the
layer, all the input files and their checksum.
Subsequent layer builds use the properties file to validate the layers they depend on: the JAR files that the build
depends on must exactly match those that were used to build the previous layers.
