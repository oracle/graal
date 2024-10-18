---
layout: docs
toc_group: debugging-and-diagnostics
link_title: Inspect Tool
permalink: /reference-manual/native-image/debugging-and-diagnostics/InspectTool/
redirect_from: /reference-manual/native-image/inspect/
---

# Native Image Inspect Tool

Native Image provides the Inspect Tool to list all methods included in a native executable or a native shared library. 
Run the command `$JAVA_HOME/bin/native-image-inspect <path_to_binary>` to list classes, methods, fields, and constructors in the JSON format that validates against the JSON schema defined in [`native-image-inspect-schema-v0.2.0.json`](assets/native-image-inspect-schema-v0.2.0.json) (only available in Oracle GraalVM).

The `native-image` builder, by default, includes metadata in the native executable which then enables the Inspect Tool to list the included methods.

The amount of data included is fairly minimal compared to the overall image size, however you can set the `-H:-IncludeMethodData` option to disable the metadata emission.
Images compiled with this option will not be able to be inspected by the tool.

## Software Bill of Materials (SBOM)

Native Image can embed a Software Bill of Materials (SBOM) at build time to detect any libraries that may be susceptible to known security vulnerabilities.
Native Image provides the `--enable-sbom` option to embed an SBOM into a native executable (only available in Oracle GraalVM).

The tool is able to extract the compressed SBOM using an optional `--sbom` parameter accessible through `$JAVA_HOME/bin/native-image-inspect --sbom <path_to_binary>`.

### Further Reading

- [Debugging and Diagnostics](DebuggingAndDiagnostics.md)