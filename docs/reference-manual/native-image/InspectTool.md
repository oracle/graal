---
layout: docs
toc_group: debugging-and-diagnostics
link_title: Inspect Tool
permalink: /reference-manual/native-image/debugging-and-diagnostics/InspectTool/
redirect_from: /reference-manual/native-image/inspect/
---

# Native Image Inspect Tool

The Native Image Inspect Tool extracts embedded Software Bill of Materials (SBOM) from native executables. The functionality for extracting class-level metadata is no longer supported.

## Extracting Embedded SBOM

Native Image embeds an SBOM at build time to detect any libraries that may be susceptible to known security vulnerabilities.
(Not available in GraalVM Community Edition.)

The Native Image Inspect Tool can extract the compressed SBOM using the `--sbom` parameter, as shown in the command:
```bash
$JAVA_HOME/bin/native-image-inspect --sbom <path_to_binary>
```

The Native Image Inspect Tool previously supported listing the classes, fields, and methods included in a native executable or a native shared library.
This functionality is no longer supported for security reasons.
Migrate to using [class-level SBOMs](../../security/native-image.md#including-class-level-metadata-in-the-sbom) instead by passing `--enable-sbom=class-level,export` to the `native-image` builder, which generates an SBOM containing the same kind of class-level metadata information.

### Further Reading

- [Software Bill of Materials (SBOM) in Native Image](../../security/SBOM.md)
