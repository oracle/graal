---
layout: docs
toc_group: native-image
link_title: Native Image ARM64 Support
permalink: /reference-manual/native-image/ARM64/
---

# Native Image ARM64 Support

As of version 21.0, GraalVM distributions for Linux on ARM 64-bit system are available.
Unless explicitly noted within the [limitations](#Limitations), all Native Image features should work on this architecture.

## Limitations

Mostly all Native Image features are supported on ARM64, except for the limitations described below.

* `-R:[+|-]WriteableCodeCache`: must be disabled.
* `--libc=<value>`: `musl` is not supported.
* `--gc=<value>`: The G1 garbage collector (`G1`) is not supported.

For more information about these options, please refer to [Native Image Options](Options.md) and [Native Image Hosted and Runtime Options](HostedvsRuntimeOptions.md).
