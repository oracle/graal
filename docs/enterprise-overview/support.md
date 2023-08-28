---
layout: ohc
permalink: /support/
---

## Support

## Available Distributions

GraalVM Enterprise 21 distributions are based on Oracle JDK 8, 11, and 17.
They include all Oracle Java critical patch updates (CPUs), which are released on a regular schedule to remedy defects and known vulnerabilities.

GraalVM Enterprise 21 distributions for Java 8, 11, and 17 are available for Linux, macOS, and Windows platforms on x86 64-bit systems. There are also GraalVM Enterprise 21 distributions for Linux on ARM 64-bit system based on Oracle JDK 11 and 17. 

The GraalVM Enterprise distribution based on Oracle JDK 17 is experimental with [several known limitations](https://docs.oracle.com/en/graalvm/enterprise/21/docs/overview/known-issues/).
Depending on the platform, the distributions are shipped as *.tar.gz* or *.zip* archives.

## Certified Platforms

The following are the certified platforms for GraalVM Enterprise 21:

| Operating System 	| Version 	| Architecture 	| Installation Guide 	|
|------------------------------------	|--------------	|--------------	|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------	|
| Oracle Linux 	| 7, 8, 9 	| x64, AArch64 | [Installation Guide for Oracle Linux](../getting-started/graalvm-enterprise/oci/compute-instances.md) 	|
| Red Hat Enterprise Linux (RHEL) 	| 7, 8, 9 	| x64 	| [Installation Guide for Linux](../getting-started/graalvm-enterprise/linux.md) 	|
| macOS 	| 11 (Big Sur), 12.4 (Monterey), 13.3 (Ventura)	| x64 | [Installation Guide for macOS](../getting-started/graalvm-enterprise/macos.md) 	|
| Microsoft Windows 	| Server 2016, 2019, 2022	| x64 	| [Installation Guide for Windows](../getting-started/graalvm-enterprise/windows.md) 	|

## Experimental Features

Oracle GraalVM Enterprise Edition features are distributed as fully supported, early adopter, and experimental.

Experimental features are being considered for future versions of GraalVM Enterprise.
They are not meant to be used in production and are not supported by Oracle.
The development team welcomes feedback on experimental features, but users should be aware that experimental features might never be included in a final version, or might change significantly before being considered production-ready.

For more information, check the [Oracle Technology Network License Agreement for GraalVM Enterprise Edition](https://www.oracle.com/downloads/licenses/graalvm-otn-license.html).

The following table lists supported and experimental features in GraalVM Enterprise Edition 21 by platform.

| Feature | Linux AMD64 | Linux ARM64 | macOS | Windows |
|--------------------|---------------|---------------|---------------|
| Native Image | early adopter | early adopter | early adopter | early adopter |
| LLVM runtime | supported | supported | supported | not available |
| LLVM toolchain | supported | supported | supported | not available |
| JavaScript | supported | supported | supported | supported |
| Node.js  | supported | supported | supported | supported |
| Java on Truffle | experimental | experimental | experimental | experimental |
| Python | experimental | not available | experimental | not available |
| Ruby | experimental | experimental | experimental | not available |
| R | experimental | not available | experimental | not available |
| WebAssembly | experimental | experimental | experimental | experimental |

## Licensing and Support

Oracle GraalVM Enterprise Edition is licensed under the [Oracle Technology Network License Agreement for GraalVM Enterprise Edition](https://www.oracle.com/downloads/licenses/graalvm-otn-license.html) for developing, testing, prototyping, and demonstrating Your application.

For production use, GraalVM Enterprise is available as part of the [Oracle Java SE Subscription](https://www.oracle.com/uk/java/java-se-subscription/) which includes 24x7x365 [Oracle premier support](https://www.oracle.com/support/premier/) and the access to [My Oracle Support (MOS)](https://www.oracle.com/support/).