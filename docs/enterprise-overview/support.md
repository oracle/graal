---
layout: ohc
permalink: /support/
---

# Support

## Available Distributions

Oracle GraalVM distributions are based on Oracle JDK 17 and Oracle JDK 20.
Oracle GraalVM releases include all Oracle Java critical patch updates (CPUs), which are released on a regular schedule to remedy defects and known vulnerabilities.

Oracle GraalVM is available for Linux, macOS, and Windows platforms on x64 systems, for Linux and macOS on AArch64 architecture.

## Certified Platforms

The following platforms are certified for Oracle GraalVM:

| Operating System 	| Version 	| Architecture 	| Installation Guide 	|
|------------------------------------	|--------------	|--------------	|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------	|
| Oracle Linux 	| 7, 8, 9 	| x64, AArch64| [Installation Guide for Oracle Linux](../getting-started/graalvm-enterprise/oci/installation-compute-instance-with-OL.md) 	|
| Red Hat Enterprise Linux (RHEL) 	| 7, 8, 9 	| x64 	| [Installation Guide for Linux](../getting-started/graalvm-enterprise/linux.md) 	|
| macOS 	| 11 (Big Sur), 12.4 (Monterey), 13.3 (Ventura)	| x64, AArch64	| [Installation Guide for macOS](../getting-started/graalvm-enterprise/macos.md) 	|
| Microsoft Windows 	| Server 2016, 2019, 2022	| x64 	| [Installation Guide for Windows](../getting-started/graalvm-enterprise/windows.md) 	|

## Features Support

Oracle GraalVM technologies are distributed as _supported_ or _experimental_.

_Experimental_ technologies are being considered for future versions of Oracle GraalVM.
They are not meant for use in production and are **not** supported by Oracle.
The development team welcomes feedback on experimental features, but users should be aware that experimental features may not be included in a final version, or may change significantly before being considered production-ready.

GraalVM JavaScript runtime (known as GraalJS) is supported on all certified platrofms. 
The Java on Truffle runtime (known as Espresso) is supported on Linux x64 only, and experimenal on other certified platrofms. 
GraalVM Python runtime (known as GraalPy) is experimental on all certified platrofms.

The following technologies are deprecated and will be removed in Oracle GraalVM for JDK 23:
* LLVM runtime
* LLVM toolchain
* Node.js

## Licensing and Support

Oracle GraalVM is licensed under [GraalVM Free Terms and Conditions (GFTC) including License for Early Adopter Versions](https://www.oracle.com/downloads/licenses/graal-free-license.html).
Subject to the conditions in the license, including the License for Early Adopter Versions, the GFTC is intended to permit use by any user including commercial and production use. Redistribution is permitted as long as it is not for a fee.
Oracle GraalVM is also free to use on Oracle Cloud Infrastructure.
For more information about Oracle GraalVM licensing, see the [Oracle Java SE Licensing FAQ](https://www.oracle.com/java/technologies/javase/jdk-faqs.html#GraalVM-licensing).

Oracle GraalVM is available as part of the [Oracle Java SE Subscription](https://www.oracle.com/java/java-se-subscription/) which includes 24x7x365 [Oracle premier support](https://www.oracle.com/support/premier/) and access to [My Oracle Support (MOS)](https://www.oracle.com/support/).

Oracle GraalVM focuses on support for Java LTS releases for production deployments.
See the [release calendar](../../release-notes/enterprise/oracle-graalvm-release-calendar.md) for more information.
