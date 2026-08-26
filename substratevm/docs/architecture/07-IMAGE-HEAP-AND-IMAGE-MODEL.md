# Image Heap And Image Model

## Purpose

The image heap and image model phase turns the shadow heap, code-cache constants, entry points, and
hosted metadata into an image model ready for image writing.

This phase is responsible for:

- running `beforeHeapLayout(...)` feature callbacks;
- verifying and sealing the shadow heap before layout;
- adding initial heap objects, code-cache constants, and trailing heap objects;
- laying out heap objects into image heap partitions/sections;
- running `afterHeapLayout(...)` callbacks;
- creating the image-kind-specific [`AbstractImage`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/image/AbstractImage.java);
- running `afterAbstractImageCreation(...)` Graal feature callbacks;
- building the in-memory object-file model before final write/link.

## Inputs and Entry State

The phase receives:

- [`NativeImageHeap`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/image/NativeImageHeap.java) connected to analysis and hosted metadata;
- [`NativeImageCodeCache`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/image/NativeImageCodeCache.java) with compiled methods, constants, and runtime metadata;
- hosted entry points and image kind;
- [`HostedUniverse`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/meta/HostedUniverse.java), [`HostedMetaAccess`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/meta/HostedMetaAccess.java), native libraries, runtime configuration, and debug context;
- embedded constants discovered from the code cache;
- image heap layouter and object sorter singletons;
- feature state for heap-layout and image-creation callbacks.

The shadow heap accepts final verification and layout work, but no abstract image or
object-file-backed image model has been built.

## Results and Completion States

The phase produces a sealed heap layout and an image-kind-specific
[`AbstractImage`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/image/AbstractImage.java) ready for `image.write(...)`:

- [`ImageHeapLayoutInfo`](../../src/com.oracle.svm.core/src/com/oracle/svm/core/image/ImageHeapLayoutInfo.java) and populated
  [`NativeImageHeap.ObjectInfo`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/image/NativeImageHeap.java) records;
- finalized in-memory object-file sections, buffers, relocations, method symbols, heap contents, and
  image metadata.

Later code must not register new image-heap objects or mutate heap layout.

## Main Classes

Core anchors:

- [`NativeImageGenerator`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/NativeImageGenerator.java) coordinates
  heap verification, heap layout, abstract image creation, and image build.
- [`NativeImage`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/image/NativeImage.java) is the concrete
  object-file-backed image model used by CC-linked images.
- [`NativeImageHeapWriter`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/image/NativeImageHeapWriter.java)
  populates the image-heap section buffer during `NativeImage.build(...)`.

## Control Flow

The normal image heap and image model flow is:

1. After compilation, `doRun(...)` gets the backend `CodeCacheProvider`, prints creation progress,
   and enters the `"create native image"` debug scope.

2. [`BeforeHeapLayoutAccessImpl`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/FeatureImpl.java) is created and
   `Feature.beforeHeapLayout(...)` callbacks run.

3. `verifyAndSealShadowHeap(...)` asks the code cache for embedded constants with
   `initAndGetEmbeddedConstants()`, then re-runs heap verification from those roots.

4. Unsupported-feature state is reported again. If verification succeeds, the analysis heap scanner
   is sealed so later attempts to register new image-heap constants or materialize new hosted values
   fail.

5. `buildNativeImageHeap(...)` starts heap model construction:
   - `ImageHeapObjectSorter.beforeImageHeapTraversal()` prepares deterministic traversal state;
   - `heap.addInitialObjects()` adds roots and normal image-heap objects;
   - `codeCache.addConstantsToHeap()` adds constants referenced by compiled code;
   - `heap.addTrailingObjects()` adds objects that must be appended after normal traversal.

6. `layoutNativeImageHeap(...)` calls `heap.getLayouter().layout(...)` with page size and object
   sorter state. The layouter assigns object offsets and returns [`ImageHeapLayoutInfo`](../../src/com.oracle.svm.core/src/com/oracle/svm/core/image/ImageHeapLayoutInfo.java).

7. [`BuildPhaseProviderImpl.markHeapLayoutFinished()`](../../src/com.oracle.svm.core/src/com/oracle/svm/core/BuildPhaseProviderImpl.java) records the phase transition.

8. [`AfterHeapLayoutAccessImpl`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/FeatureImpl.java) is created and
   `Feature.afterHeapLayout(...)` callbacks run.

9. `createAbstractImage(...)` calls [`AbstractImage.create(...)`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/image/AbstractImage.java), selecting:
   - [`ExecutableImageViaCC`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/image/ExecutableImageViaCC.java) for executable and static executable images;
   - [`SharedLibraryImageViaCC`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/image/SharedLibraryImageViaCC.java) for shared libraries;
   - [`ImageLayerViaCC`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/image/ImageLayerViaCC.java) for image layers.

10. [`AfterAbstractImageCreationAccessImpl`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/FeatureImpl.java) is created and Graal features receive
    `afterAbstractImageCreation(...)`.

11. `image.build(imageName, debug)` finalizes the in-memory image model. For the normal
    object-file-backed image, this creates section buffers, writes code and heap content into
    buffers, creates object-file sections, marks relocations, and prepares symbols.

12. Shared-layer builds persist analysis information after the image model is built.

## Key Data Structures

- [`NativeImageHeap`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/image/NativeImageHeap.java): build-time model of the generated image heap, including object metadata, static
  fields, late constants, reachability groups, partitions, and object replacement state.
- [`NativeImageHeap.ObjectInfo`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/image/NativeImageHeap.java): per-object record containing hosted constant, size, offset,
  partition, identity hash code, and reachability information.
- [`ImageHeapLayoutInfo`](../../src/com.oracle.svm.core/src/com/oracle/svm/core/image/ImageHeapLayoutInfo.java): result of heap layout, including section sizes, offsets, and heap layout
  metadata needed by image writing and reporting.
- [`ImageHeapLayouter`](../../src/com.oracle.svm.core/src/com/oracle/svm/core/image/ImageHeapLayouter.java): policy object that assigns image heap objects to sections and offsets.
- [`ImageHeapObjectSorter`](../../src/com.oracle.svm.core/src/com/oracle/svm/core/image/ImageHeapObjectSorter.java): deterministic ordering hook used during heap traversal and layout.
- [`AbstractImage`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/image/AbstractImage.java): image-kind-independent handle used by the generator after heap layout.
- [`ExecutableImageViaCC`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/image/ExecutableImageViaCC.java), [`SharedLibraryImageViaCC`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/image/SharedLibraryImageViaCC.java), and [`ImageLayerViaCC`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/image/ImageLayerViaCC.java): concrete image-kind
  wrappers for CC-linked outputs.
- [`RelocatableBuffer`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/image/RelocatableBuffer.java): byte buffer plus relocation metadata used by the concrete image model for
  text, read-only data, writable data, and heap sections.
