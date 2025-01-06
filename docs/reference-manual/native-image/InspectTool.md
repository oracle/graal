---
layout: docs
toc_group: debugging-and-diagnostics
link_title: Inspect Tool
permalink: /reference-manual/native-image/debugging-and-diagnostics/InspectTool/
redirect_from: /reference-manual/native-image/inspect/
---

# Native Image Inspect Tool

The Native Image Inspect Tool extracts embedded Software Bill of Materials (SBOM) from native executables. The functionality for extracting class-level metadata is now deprecated.

## Extracting Embedded SBOM

Native Image can embed a SBOM at build time to detect any libraries that may be susceptible to known security vulnerabilities.
Native Image provides the `--enable-sbom` option to embed an SBOM into a native executable (only available in Oracle GraalVM).

The Native Image Inspect Tool can extract the compressed SBOM using the optional `--sbom` parameter, as shown in the command: `$JAVA_HOME/bin/native-image-inspect --sbom <path_to_binary>`.

## Extracting Class-Level Metadata (Deprecated)

> The extraction of class-level metadata using `native-image-inspect` is deprecated. In GraalVM for JDK 24, a deprecation warning is printed to `stderr`, and this functionality will be removed in GraalVM for JDK 25. Please migrate to using [class-level SBOMs](../../security/SBOM.md#including-class-level-metadata-in-the-sbom) instead by passing `--enable-sbom=class-level,export` to the `native-image` builder, which generates an SBOM containing the same kind of class-level metadata information.

Native Image provides the Inspect Tool to list all classes, fields, and methods included in a native executable or a native shared library. 
Run the command `$JAVA_HOME/bin/native-image-inspect <path_to_binary>` to list classes, methods, fields, and constructors in the JSON format that validates against the JSON schema defined in [`native-image-inspect-schema-v0.2.0.json`](assets/native-image-inspect-schema-v0.2.0.json) (only available in Oracle GraalVM).

The `native-image` builder, by default, includes metadata in the native executable which then enables the Inspect Tool to list the included methods.

The amount of data included is fairly minimal compared to the overall size of the native executable, however you can set the `-H:-IncludeMethodData` option to disable the metadata emission.
Images compiled with this option will not be able to be inspected by the tool.

### Further Reading

- [Software Bill of Materials (SBOM) in Native Image](../../security/SBOM.md)
