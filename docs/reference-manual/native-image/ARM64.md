---
layout: docs
toc_group: native-image
link_title: Native Image ARM64 Support
permalink: /reference-manual/native-image/ARM64/
---

# Native Image ARM64 Support

## Supported Functionality

Unless explicitly noted within the [limitations](#Limitations), all
native-image features should also work on ARM64.

## Limitations

Currently not all native-image features are supported on ARM64. The following
options have the limitations described below. For more information about these
options, please refer to [Native Image Options](Options.md) and [Native Image Hosted and Runtime Options](HostedvsRuntimeOptions.md).

* `-R:[+|-]WriteableCodeCache`: must be disabled.
* `--libc=<value>`: `musl` is not supported.
* `--gc=<value>`: The G1 garbage collector (`G1`) is not supported.

