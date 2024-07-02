---
layout: docs
toc_group: build-overview
link_title: Layered Native Image
permalink: /reference-manual/native-image/overview/LayeredNativeImage/
---

# Layered Native Image

The Layered Native Image feature allows splitting an application and its required libraries into separate binaries: a
thin layer executable supported by a chain of shared libraries.
In contrast to regular Native Image building, this mode of operation enables sharing common libraries and VM/JDK code
between multiple applications, resulting in reduced footprint at run time.
Moreover, it reduces the time to build an application since shared layers are only built once.

### Table of Contents

* [Layers Architecture](#layers-architecture)
* [Creating Layered Executables](#creating-layered-executables)
* [Packaging Layers](#packaging-layers)

## Layers Architecture

A Layered Native Image application is logically composed of the final application executable plus all the supporting
layers containing code that the application requires.
The supporting layers are called _shared layers_ and the application executable is referred to as either the
_application layer_ or _executable layer_.
The _initial_ or _base_ layer is a shared layer containing VM internals and core _java.base_ functionality at a minimum.
It can also contain modules specific to a certain framework that the application may be built upon.

At run time a shared layer is a shared object file on which other intermediate layers or executable application
layers can be dependent.
Thus, the application and its supporting shared layers form a chain:

```shell
base-layer.so                    # initial layer (includes VM/JDK code)
└── mid-layer.so                 # intermediate layer, depends on base-layer.so, adds extra functionality 
         └── executable-image    # final application executable, depends on mid-layer.so and base-layer.so
```

This architecture enables sharing of layers between applications and the hierarchy of layers forms a tree structure.
For example at run time there could be four applications that share one base layer and two intermediate layers:

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

## Creating Layered Executables

To create layered executables `native-image` accepts two options: `--layer-create` and `--layer-use`.

First, `--layer-create` builds an image layer archive from code available on the class or module path:

```
--layer-create[=layer.nil][,module[=<module-name>][,package=<package-name>]]
                      it builds an image layer file (with the *.nil extension) containing a 'layer.so' shared object binary, 
                      a 'layer.json' metadata file for the current layer, and a 'layer.properties' file containg layer input metadata
                      like the options used to create the layer, the input files and their checksum.
                      The metadata file allows building subsequent layers or final executables which depend on the *.so.
                      If a layer-file is specified, the layer will be created with the given name.
                      Otherwise, the layer-file name is derived from the image name.
                      The option can be extended with ",module" and ",package" to specify which code should be included in the layer.
```

A layer archive file has a _.nil_ extension, acronym for **N**ative **I**mage **L**ayer.

Second, `--layer-use` consumes an existing layer, and it can extend it or create a final executable:

```
--layer-use=some-layer.nil
                      loads the given the layer and makes it available to the build process.
                      If option --layer-create=some-other-layer.nil is specified then a new layer will be created which 
                      has the current layer as a predecesor.
                      If no other layer option is specified then a native executable that depends on the specified layer will be created.
```

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

- Every image build only creates one layer.
- The image build command must work without `--layer-use` and create a standalone image
    - The standalone command must completely specify the module/classpath and all other necessary configuration options
- The layer specified by `--layer-use` must be compatible with the standalone command line
    - class/jar file compatibility (`-cp`, `-p`, same JDK, same GraalVM, same libs, etc.)
    - config compatibility: GC config, etc.
    - access compatibility: no additional unsafe and field accesses are allowed

### Limitations

- Layers are platform dependent. A layer created with `--layer-create` on a specific OS/architecture
  cannot be loaded by `--layer-use` on a different OS/architecture.
- Each layer can only depend on a previous layer. We explicitly make it impossible to depend on more than one layer to
  avoid any potential issues that can stem from _multiple inheritance_.
- A shared layer is using the _.so_ extension to conform with the standard OS loader restrictions. However, it is not a
  standard shared library file, and it cannot be used with other applications.

## Packaging Layers

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

### Interaction with Native Image Bundles

When using [Native Image Bundles](Bundles.md), a layer archive can belong to either the `input` or `output` section of
the bundle, depending on whether the bundle produces or consumes the layer.

For example a bundle of a build process that uses a `base-layer.nil` and produces an `executable-image` may contain:

```shell
[bundle.nib]                            # bundle file
├── input                               # all information required to rebuild the image
│      ├── auxiliary
│      │   └── base-layer.nil           # the base layer that the mid layer build depends on 
│      │      ├── base-layer.json
│      │      ├── base-layer.so
│      │      └── base-layer.properties
│      ├── classes                      # all class-path and module-path entries passed to the builder
│      │    ├── cp
│      │    └── p
│      └── ...                          # additional input information such as the build command line
│
└── output                              # all artifacts produced by the image builder
    └── default
        └── executable-image            # created image that depends on the base-layer.so 
```

The `classes` entry captures all class-path and module-path entries passed to the builder.
This includes all JAR files that were used to build `base-layer.nil`, and they must match the checksums
in `base-layer.properties`.

Similarly, a bundle of a build process that uses a `base-layer.nil` and produces a `mid-layer.nil` layer may contain:

```shell
[bundle.nib]                            # bundle file
├── input                               # all information required to rebuild the image
│      ├── auxiliary
│      │   └── base-layer.nil           # the base layer that the mid layer build depends on 
│      │      ├── base-layer.json
│      │      ├── base-layer.so
│      │      └── base-layer.properties
│      ├── classes                      # all class-path and module-path entries passed to the builder
│      │    ├── cp
│      │    └── p
│      └── ...                          # additional input information such as the build command line
│
└── output                              # all artifacts produced by the image builder
    └── default
        └── mid-layer.nil               # the mid layer created by the image builder, depends on the base layer
            ├── mid-layer.json
            ├── mid-layer.so
            └── mid-layer.properties
```

#### Example Layers and Bundles Option Usage

```shell
# given an application that would usually be built just like in the previous section, but this time creating a bundle
native-image --bundle-create=awesome-bundle.nib --module-path target/AwesomeLib-1.0-SNAPSHOT.jar --another-extra-option -cp . AwesomeHelloWorld

# then you can use the base layer from the previous section while applying the bundle; the bundle must be compatible with the layer, i.e., same jars, options, etc.
native-image --layer-use=base-layer.nil --bundle-apply=awesome-bundle.nib

# or you can create the bundle while using the layer; the bundle will capture the base-layer.nil file as an input file
native-image --layer-use=base-layer.nil --module-path target/AwesomeLib-1.0-SNAPSHOT.jar --another-extra-option -cp . AwesomeHelloWorld --bundle-create=awesome-bundle.nib

# the created bundle can then be applied as usual; since the bundle was created with a layer, applying it will reuse the layer which is already captured in the bundle
native-image --bundle-apply=awesome-bundle.nib

# when applying the bundle you can also specify a layer to use; but if the bundle already contained a --layer-use, the layer will be replaced
native-image --bundle-apply=awesome-bundle.nib --layer-use=new-base-layer.nil

# additionally you can create a bundle which captures the creation of a layer; base-layer.nil is an output file of base-bundle.nib
native-image --layer-create=base-layer.nil,module=java.base,module=java.awesome.lib --module-path target/AwesomeLib-1.0-SNAPSHOT.jar --bundle-create=base-bundle.nib

# applying base-bundle.nib will recreate the base layer; example usage: upgrade GraalVM version by just applying the bundle with a newer VM version
native-image --bundle-apply=base-bundle.nib

# or recreate the base layer with some extra options
native-image --bundle-apply=base-bundle.nib --another-extra-option

# you can also build a bundle which captures the creation of a layer, but not actually perform the image building using the dry-run bundle-create option
native-image --layer-create=base-layer.nil,module=java.base,module=java.awesome.lib --module-path target/AwesomeLib-1.0-SNAPSHOT.jar --bundle-create=base-bundle.nib,dry-run

```

When using bundles the layer file becomes either an input of the bundle in case of `--layer-use` or an output of the
bundle in case of `--layer-create`.
