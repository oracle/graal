# Native Image Architecture

The documents in this directory describe the software architecture of GraalVM Native Image in the current
`substratevm` suite.

Native Image has two closely related architectural views:

- [Build-Time Architecture](BUILDTIME.md): the `native-image` driver, hosted builder, analysis,
  compilation, image construction, linking, build-time components, and extension mechanisms.
- [Runtime Architecture](RUNTIME.md): the generated image's startup, isolates, threads, memory,
  garbage collection, metadata, and operating-system integration.

The build-time guide links to the eight detailed lifecycle phases.
The runtime guide describes the generated image after image writing has finished.
