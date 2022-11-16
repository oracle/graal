---
layout: ohc
permalink: /support/
---

## Support

## Available Distributions

GraalVM Enterprise distributions are based on Oracle JDK 11, Oracle JDK 17, and Oracle JDK 19.
GraalVM Enterprise releases include all Oracle Java critical patch updates (CPUs), which are released on a regular schedule to remedy defects and known vulnerabilities.

GraalVM Enterprise is available for Linux, macOS, and Windows platforms on x86 64-bit systems, for Linux and macOS (Apple Silicon) on ARM 64-bit systems.
Depending on the platform, the distributions are shipped as *.tar.gz* or *.zip* archives.

## Certified Platforms

The following are the certified platforms for GraalVM Enterprise 22.3:

| Operating System 	| Version 	| Architecture 	| Installation Guide 	|
|------------------------------------	|--------------	|--------------	|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------	|
| Oracle Linux 	| 7, 8 	| x86 64-bit, ARM 64-bit	| [Installation Guide for Linux](../getting-started/graalvm-enterprise/oci/installation-compute-instance-with-OL.md) 	|
| Red Hat Enterprise Linux(RHEL) 	| 7, 8 	| x86 64-bit 	| [Installation Guide for Linux](../getting-started/graalvm-enterprise/installation-linux.md) 	|
| macOS 	| 10.14 (Mojave), 10.15 (Catalina), 11 (Big Sur), 12.4 (Monterey)	| x86 64-bit	| [Installation Guide for macOS](../getting-started/graalvm-enterprise/installation-macos.md) 	|
| Microsoft Windows 	| Server 2016, 2019	| x86 64-bit 	| [Installation Guide for Windows](../getting-started/graalvm-enterprise/installation-windows.md) 	|

Note: GraalVM Enterprise macOS distribution for ARM 64-bit architecture (Apple Silicon) is experimental.

## Experimental Features

Oracle GraalVM Enterprise Edition features are distributed as fully supported and experimental.

Experimental features are being considered for future versions of GraalVM Enterprise.
They are not meant to be used in production and are not supported by Oracle.
The development team welcomes feedback on experimental features, but users should be aware that experimental features might never be included in a final version, or might change significantly before being considered production-ready.

For more information, check the [Oracle Technology Network License Agreement for GraalVM Enterprise Edition](https://www.oracle.com/downloads/licenses/graalvm-otn-license.html).

The following table lists supported and experimental features in GraalVM Enterprise Edition 22.3 by platform.

| Feature         | Linux AMD64   | Linux ARM64   | macOS AMD64   | Windows AMD64 |
|-----------------|---------------|---------------|---------------|---------------|
| Native Image    | supported     | supported     | supported     | supported     |
| LLVM runtime    | supported     | supported     | supported     | experimental  |
| LLVM toolchain  | supported     | supported     | supported     | experimental  |
| JavaScript      | supported     | supported     | supported     | supported     |
| Node.js         | supported     | supported     | supported     | supported     |
| Java on Truffle | supported     | experimental  | experimental  | experimental  |
| Python          | experimental  | experimental  | experimental  | not available |
| Ruby            | experimental  | experimental  | experimental  | not available |
| R               | experimental  | not available | experimental  | not available |
| WebAssembly     | experimental  | experimental  | experimental  | experimental  |


## Licensing and Support

Oracle GraalVM Enterprise Edition is licensed under the [Oracle Technology Network License Agreement for GraalVM Enterprise Edition](https://www.oracle.com/downloads/licenses/graalvm-otn-license.html) for developing, testing, prototyping, and demonstrating Your application.

For production use, GraalVM Enterprise is available as part of the [Oracle Java SE Subscription](https://www.oracle.com/uk/java/java-se-subscription/) which includes 24x7x365 [Oracle premier support](https://www.oracle.com/support/premier/) and access to [My Oracle Support (MOS)](https://www.oracle.com/support/).

GraalVM Enterprise focuses on support for Java LTS releases for production deployments.
See [GraalVM Enterprise Release Calendar](../../release-notes/enterprise/graalvm-ee-release-calendar.md) for more information.

Please note, that while Oracle JDK 17 is available under the new [Oracle No-Fee Terms and Conditions (NFTC) license](https://www.oracle.com/downloads/licenses/no-fee-license.html) which allows commercial and production use for 2 years, GraalVM Enterprise Edition license remains unchanged.