---
layout: docs
toc_group: native-image
link_title: Native Image ARM64 Support
permalink: /reference-manual/native-image/ARM64/
---

# Native Image ARM64 Support

As of version 21.0, GraalVM distributions for Linux version 7.6 or higher on ARM 64-bit system are available.
Unless explicitly noted within the [limitations](#Limitations), all Native Image features should also work on this architecture.

## Limitations

Currently not all native-image features are supported on ARM64. The following
options have the limitations described below. For more information about these
options, please refer to [Native Image Options](Options.md) and [Native Image Hosted and Runtime Options](HostedvsRuntimeOptions.md).

* `-R:[+|-]WriteableCodeCache`: must be disabled.
* `--libc=<value>`: `musl` is not supported.
* `--gc=<value>`: The G1 garbage collector (`G1`) is not supported.


The experimental status means that all components in the JDK 16 based binaries are considered experimental regardless what their status is in other distribution versions.

Linux AArch64 platform compatibility: The GraalVM distributions for Linux AArch64 architecture remain experimental in this release. Supported features include the GraalVM compiler, the gu tool, the Node.js JavaScript runtime, Native Image, some developer tools.

As of version 21.0, we provide GraalVM Community Edition for Linux on ARM 64-bit system, based on OpenJDK 11 for AArch64 architecture. This distribution can be installed on Linux systems for AArch64 CPU architecture, version 7.6 or higher.
