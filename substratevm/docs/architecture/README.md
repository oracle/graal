# Native Image Architecture

The documents in this directory describe the software architecture of GraalVM Native Image in the
current `substratevm` suite. Every architecture declaration opens with a system overview that places
it in [§AR-001-native-image-topology](TOPOLOGY.md#ar-001-native-image-topology-native-image-topology).

Systems:

- [§AR-001-native-image-topology](TOPOLOGY.md#ar-001-native-image-topology-native-image-topology): the two systems and the boundaries between them.
- [§AR-002-image-builder](BUILDTIME.md#ar-002-image-builder-native-image-build-time-architecture): the `native-image` driver, hosted builder, analysis, compilation, image
  construction, linking, build-time components, and extension mechanisms.
- [§AR-003-image-runtime](RUNTIME.md#ar-003-image-runtime-native-image-runtime-architecture): the generated image's startup, isolates, threads, memory, garbage
  collection, metadata, and operating-system integration.

Build phases, the builder's subsystems:

- [§PH-001-driver-setup](phases/PH-001-driver-setup.md#ph-001-driver-setup-driver-setup)
- [§PH-002-builder-startup](phases/PH-002-builder-startup.md#ph-002-builder-startup-builder-startup)
- [§PH-003-hosted-setup](phases/PH-003-hosted-setup.md#ph-003-hosted-setup-hosted-setup)
- [§PH-004-static-analysis](phases/PH-004-static-analysis.md#ph-004-static-analysis-static-analysis)
- [§PH-005-hosted-universe-construction](phases/PH-005-hosted-universe-construction.md#ph-005-hosted-universe-construction-hosted-universe-construction)
- [§PH-006-compilation](phases/PH-006-compilation.md#ph-006-compilation-compilation)
- [§PH-007-image-heap-and-image-model](phases/PH-007-image-heap-and-image-model.md#ph-007-image-heap-and-image-model-image-heap-and-image-model)
- [§PH-008-image-writing-and-linking](phases/PH-008-image-writing-and-linking.md#ph-008-image-writing-and-linking-image-writing-and-linking)

Contracts and components:

- [§AR-005-partially-open-world-analysis](PARTIALLY-OPEN-WORLD-ANALYSIS.md#ar-005-partially-open-world-analysis-partially-open-world-analysis): extension model, closure guarantees, and extension-policy
  rules.
- [§AR-004-security-providers](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/jca/SecurityServicesFeature.java): provider inclusion, verification, and metadata tracing.
