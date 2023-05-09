---
layout: ohc
permalink: /support/
---

## Support

## Available Distributions

Oracle GraalVM distributions are based on Oracle JDK 17 and Oracle JDK 20.
Oracle GraalVM releases include all Oracle Java critical patch updates (CPUs), which are released on a regular schedule to remedy defects and known vulnerabilities.

Oracle GraalVM is available for Linux, macOS, and Windows platforms on x64 systems, for Linux and macOS on AArch64 architecture.
Depending on the platform, the distributions are shipped as *.tar.gz* or *.zip* archives.

## Certified Platforms

The following are the certified platforms for Oracle GraalVM:

| Operating System 	| Version 	| Architecture 	| Installation Guide 	|
|------------------------------------	|--------------	|--------------	|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------	|
| Oracle Linux 	| 7, 8 	| x64, AArch64| [Installation Guide for Linux](../getting-started/graalvm-enterprise/oci/installation-compute-instance-with-OL.md) 	|
| Red Hat Enterprise Linux(RHEL) 	| 7, 8 	| x64 	| [Installation Guide for Linux](../getting-started/graalvm-enterprise/installation-linux.md) 	|
| macOS 	| 10.14 (Mojave), 10.15 (Catalina), 11 (Big Sur), 12.4 (Monterey), 13.2 (Ventura)	| x64, AArch64	| [Installation Guide for macOS](../getting-started/graalvm-enterprise/installation-macos.md) 	|
| Microsoft Windows 	| Server 2016, 2019	| x64 	| [Installation Guide for Windows](../getting-started/graalvm-enterprise/installation-windows.md) 	|

## Experimental Features

Oracle GraalVM features are distributed as fully supported and experimental.

Experimental features are being considered for future versions of Oracle GraalVM.
They are not meant to be used in production and are not supported by Oracle.
The development team welcomes feedback on experimental features, but users should be aware that experimental features might never be included in a final version, or might change significantly before being considered production-ready.

For more information, check the [Oracle Technology Network License Agreement for GraalVM Enterprise Edition](https://www.oracle.com/downloads/licenses/graalvm-otn-license.html).

The following table lists supported and experimental features in Oracle GraalVM by platform.

| Feature         | Linux x64     | Linux AArch64 | macOS x64     | macOS AArch64 | Windows x64   |
|-----------------|---------------|---------------|---------------|---------------|---------------|
| Native Image    | supported     | supported     | supported     | supported     | supported     |
| LLVM runtime    | supported     | supported     | supported     | supported     | experimental  |
| LLVM toolchain  | supported     | supported     | supported     | supported     | experimental  |
| JavaScript      | supported     | supported     | supported     | supported     | supported     |
| Node.js         | supported     | supported     | supported     | supported     | supported     |
| Java on Truffle | supported     | experimental  | experimental  | experimental  | experimental  |
| Python          | experimental  | experimental  | experimental  | experimental  | not available |
| Ruby            | experimental  | experimental  | experimental  | experimental  | not available |
| WebAssembly     | experimental  | experimental  | experimental  | experimental  | experimental  |

## Licensing and Support

Oracle GraalVM is licensed under the [Oracle Technology Network License Agreement for GraalVM Enterprise Edition](https://www.oracle.com/downloads/licenses/graalvm-otn-license.html) for developing, testing, prototyping, and demonstrating Your application.

For production use, Oracle GraalVM is available as part of the [Oracle Java SE Subscription](https://www.oracle.com/uk/java/java-se-subscription/) which includes 24x7x365 [Oracle premier support](https://www.oracle.com/support/premier/) and access to [My Oracle Support (MOS)](https://www.oracle.com/support/).

Oracle GraalVM focuses on support for Java LTS releases for production deployments.
See the [release calendar](../../release-notes/enterprise/graalvm-ee-release-calendar.md) for more information.
