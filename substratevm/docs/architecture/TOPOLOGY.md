# AR-001-native-image-topology: Native Image Topology

Native Image is two systems joined by one artifact. The image builder runs on a JVM, derives a
closed-world model of the application, and writes a native executable, shared library, static
executable, or image layer. The generated image runs that artifact without the builder. Every
component of Native Image belongs to one of the two systems, or spans both by crossing the
build/run boundary in [§AR-001-native-image-topology.design.2.2](TOPOLOGY.md#design22-buildrun-boundary).

## system-overview: System Overview

Every box below is a system or subsystem with a declaration of its own, and every arrow a boundary;
the labels are their citations in shorthand, and [§AR-001-native-image-topology.design.2](TOPOLOGY.md#design2-boundaries) lists the
boundaries in full.

<!-- grund:fmt off -->
<pre>
   user: native-image, Maven and Gradle plugins
                     │ arguments, native-image.properties
                     ▼
   ┌──────────────────────────────┐
   │ driver               §PH-001 │                            driver process
   └──────────────┬───────────────┘
                  │ builder JVM, argument file, keep-alive      §AR-001.design.2.1
                  ▼
   ┌────────────────────────────────────────────────────────┐   builder JVM §AR-002
   │ builder startup §PH-002 → hosted setup §PH-003         │
   │ → static analysis §PH-004 → hosted universe §PH-005    │   analysis contract §AR-005
   │ → compilation §PH-006 → image heap and model §PH-007   │
   │ → image writing and linking §PH-008                    │
   │ features · substitutions · singletons · options        │   §AR-002.design.4
   └──────────────┬─────────────────────────────────────────┘
                  │ code, image heap, metadata, singletons      §AR-001.design.2.2
                  ▼
   ┌────────────────────────────────────────────────────────┐   generated image §AR-003
   │ startup and isolates · threads, safepoints · heap, GC  │
   │ runtime metadata: reflection, resources, JNI, JFR      │
   │ OS and CPU support                                     │
   └────────────────────────────────────────────────────────┘

   spans both systems: security providers §AR-004
</pre>
<!-- grund:fmt on -->

## terminology: Terminology

- **Driver**: the `native-image` command; it prepares the build and launches the builder.
- **Image builder**: the hosted builder that runs on a JVM and executes the build lifecycle.
- **Hosted**: code, metadata, or state that exists only in the builder.
- **Generated image**: the artifact the builder writes, and the process that runs it.
- **Image kind**: executable, static executable, shared library, or image layer.
- **Runtime substrate**: the VM runtime embedded in every generated image.
- **Build/run boundary**: the artifact through which builder results reach the generated image.

## design: Design

### design.1: Systems

#### design.1.1: Driver

The driver parses user options and embedded `native-image.properties`, derives the builder and image
class paths and module paths, configures the builder JVM, and launches it. It then waits for and
interprets the builder exit status, and can launch a rebuild-after-analysis invocation when the
builder requests one. [§PH-001-driver-setup](phases/PH-001-driver-setup.md#ph-001-driver-setup-driver-setup) describes the phase.

#### design.1.2: Image Builder

The builder loads application and builder classes, executes the eight build phases, and writes the
artifact. [§AR-002-image-builder](BUILDTIME.md#ar-002-image-builder-native-image-build-time-architecture) describes it.

#### design.1.3: Generated Image

The generated image contains compiled application code, selected JDK and runtime support, runtime
metadata, the image heap, startup entry points, and the runtime substrate.
[§AR-003-image-runtime](RUNTIME.md#ar-003-image-runtime-native-image-runtime-architecture) describes it.

### design.2: Boundaries

#### design.2.1: Driver to Builder

The driver starts the builder JVM with system properties and module settings, an image-builder
argument file with hosted and runtime options and the image class path and module path, and a
keep-alive file through which the builder detects driver death. The builder reports back only
through its exit status.

#### design.2.2: Build/Run Boundary

The builder embeds selected runtime substrate code, compiled code, runtime metadata, image singletons,
and image-heap state into the artifact. Hosted state reaches the generated image only through this
artifact; there is no channel from the running image back to the builder.

## requirements: Requirements

### requirements.1: Hosted Code Stays Out of the Generated Image

Runtime code may be included in generated images. Hosted builder code must stay out of generated
images unless it is explicitly modeled as runtime support.
