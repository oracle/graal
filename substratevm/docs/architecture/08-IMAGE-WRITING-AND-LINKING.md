# Image Writing And Linking

## Purpose

The image writing and linking phase serializes the in-memory image model to object-file form, invokes
the platform linker when needed, runs final image-write callbacks, archives shared-layer state, and
reports final build metrics. It starts after `image.build(...)` has finalized the abstract image and
ends when the final executable, shared library, static executable, image layer, or relocatable image
has been written.

## Inputs and Entry State

Before this phase begins:

- static analysis, hosted-universe construction, compilation, and image-heap layout have completed;
- `createAbstractImage(...)` and `image.build(...)` have completed, so the concrete
  [`AbstractImage`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/image/AbstractImage.java) owns a populated in-memory
  [`ObjectFile`](../../src/com.oracle.objectfile/src/com/oracle/objectfile/ObjectFile.java) containing code, read-only data, writable data, image-heap sections, symbols, and relocation
  information;
- the image name and kind, target object-file format, entry points, native-library configuration,
  generated-files output directory, and temporary build directory are available;
- [`NativeImageCodeCache`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/image/NativeImageCodeCache.java), [`NativeImageHeap`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/image/NativeImageHeap.java), [`ImageHeapLayoutInfo`](../../src/com.oracle.svm.core/src/com/oracle/svm/core/image/ImageHeapLayoutInfo.java), hosted metadata,
  runtime configuration, option provider, debug context, and feature state are available;
- native linker inputs include resolved static-library archives, dynamic-library names and search
  paths, previously built image-layer libraries where applicable, and additional object files or
  libraries contributed by features;
- `.symbols` manifests accompanying Base JDK static libraries generate an object containing the
  built-in JNI symbol lookup table. References from that object cause the corresponding definitions
  to be linked from the static archives and support runtime lookup by name;
- `beforeImageWrite(...)` and `afterImageWrite(...)` callbacks have not yet run, and neither the
  relocatable object file nor a final linked image has been written. `beforeImageWrite(...)`
  callbacks may still register transformations of the subsequently created `LinkerInvocation`.

## Results and Completion States

The phase always writes a relocatable object file in the temporary build directory, normally for use
as a linker input.

If `ExitAfterRelocatableImageWrite` is enabled, the relocatable object file is the terminal image
artifact and the phase returns immediately.
It does not create a [`LinkerInvocation`](../../src/com.oracle.svm.core/src/com/oracle/svm/core/LinkerInvocation.java), invoke the platform linker, run `afterImageWrite(...)` callbacks,
persist or archive shared-layer state, or report final creation metrics.

Otherwise, normal completion:

- creates a [`LinkerInvocation`](../../src/com.oracle.svm.core/src/com/oracle/svm/core/LinkerInvocation.java), uses it to invoke the platform linker, and subsequently passes it to
  `afterImageWrite(...)` callbacks;
- produces the requested linked image: an executable, static executable, shared library, or linked
  image-layer library;
- emits platform-specific companion artifacts when applicable, including separate debug information
  and Windows import libraries for non-executable images;
- runs final image-write callbacks and, for shared-layer builds, writes a `.nil` Native Image Layer
  archive containing the persisted layer snapshot and singleton state, graphs, build metadata, and
  linked layer library;
- records and reports final image, heap, code, debug-info, compilation-count, and disk-size metrics
  through `ProgressReporter`.

After normal completion, the linked image is the observable output. Earlier phase models such as the
hosted universe, heap layout, code cache, and linker invocation are diagnostic or reporting inputs
rather than mutable build state.

## Main Classes

Core anchors:

- [`NativeImageGenerator`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/NativeImageGenerator.java) coordinates
  image writing, final callbacks, layer archiving, and reporting.
- [`NativeImageViaCC`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/image/NativeImageViaCC.java) writes the
  object file and invokes the platform linker if `ExitAfterRelocatableImageWrite` is disabled.

## Control Flow

The normal image writing and linking flow is:

1. After `image.build(...)`, `doRun(...)` purges the compile queue and records the number of
   code-area compilations.

2. The write timer starts and [`BeforeImageWriteAccessImpl`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/FeatureImpl.java) is created with image name, image model,
   runtime configuration, analysis universe, hosted universe, option provider, and hosted
   meta-access. `Feature.beforeImageWrite(...)` callbacks then run and may register
   `LinkerInvocation` transformations.

3. The generator obtains the temporary build directory from `TemporaryBuildDirectoryProvider` and
   calls `image.write(...)` with the generated-files output directory.

4. For a [`NativeImageViaCC`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/image/NativeImageViaCC.java), `write(...)`:
   - purges no-longer-needed code-cache state;
   - writes the already-populated [`ObjectFile`](../../src/com.oracle.objectfile/src/com/oracle/objectfile/ObjectFile.java) as a relocatable object file and determines its debug-info size;
   - returns immediately if [`ExitAfterRelocatableImageWrite`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/NativeImageOptions.java) is enabled;
   - otherwise creates and populates the format-specific [`LinkerInvocation`](../../src/com.oracle.svm.core/src/com/oracle/svm/core/LinkerInvocation.java), applies registered transformations,
     invokes the linker, and records the linked image and applicable companion artifacts.

5. If early exit was requested, the generator returns after `image.write(...)` and skips all
   subsequent work.

6. Otherwise, [`AfterImageWriteAccessImpl`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/FeatureImpl.java) is created and `Feature.afterImageWrite(...)` callbacks run.
   The generator then measures the possibly modified final output.

7. For shared-layer builds, the generator persists image singletons and writes the `.nil` archive.

8. `ProgressReporter.printCreationEnd(...)` reports final image metrics: image file size, current
   layer object count, image heap size, code size, number of compilations, debug-info size, and
   disk file size.

## Key Data Structures

- [`AbstractImage`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/image/AbstractImage.java): phase input whose `build(...)` operation has completed and whose `write(...)`
  operation produces the relocatable object and, for CC-backed images, drives final linking.
- [`ObjectFile`](../../src/com.oracle.objectfile/src/com/oracle/objectfile/ObjectFile.java): populated in-memory ELF, Mach-O, or PE/COFF object-file model serialized to the temporary
  relocatable object file.
- [`LinkerInvocation`](../../src/com.oracle.svm.core/src/com/oracle/svm/core/LinkerInvocation.java): mutable model of linker inputs, libraries, search paths, symbols, options,
  temporary directory, output path, and command; implemented by the format-specific
  [`CCLinkerInvocation`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/image/CCLinkerInvocation.java) variants.
- [`BeforeImageWriteAccessImpl`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/FeatureImpl.java): callback state containing the finalized image context and registered
  `LinkerInvocation` transformations.
- [`AfterImageWriteAccessImpl`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/FeatureImpl.java): normal-completion callback state exposing the linked output, temporary
  directory, image kind, symbols, and hosted metadata.
- [`BuildArtifacts`](../../src/com.oracle.svm.core/src/com/oracle/svm/core/BuildArtifacts.java): registry of the final image and applicable debug-info, import-library, and image-layer
  archive artifacts.
- [`HostedImageLayerBuildingSupport`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/imagelayer/HostedImageLayerBuildingSupport.java): coordinates shared-layer finalization;
  [`SVMImageLayerWriter`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/imagelayer/SVMImageLayerWriter.java) serializes layer state, and
  [`WriteLayerArchiveSupport`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/imagelayer/WriteLayerArchiveSupport.java) packages it together with the linked layer library
  into the `.nil` archive.
