---
layout: ohc
permalink: /support/
---

# Support

## Available Distributions

Oracle GraalVM for JDK 22 is based on Oracle JDK 22. 
Each release of Oracle GraalVM for JDK 22 includes all Oracle Java critical patch updates (CPUs), which are provided on a regular schedule to remedy defects and known vulnerabilities.

Oracle GraalVM for JDK 22 is available for Linux, macOS, and Windows on the x64 architecture, and for Linux and macOS on the AArch64 architecture.

## Certified Platforms

Oracle GraalVM for JDK 22 is certified on the following platforms:

| Operating System 	| Version 	| Architecture 	| Installation Guide 	|
|------------------------------------	|--------------	|--------------	|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------	|
| Oracle Linux 	| 7, 8, 9 	| x64, AArch64| [Installation Guide for Oracle Linux](../getting-started/graalvm-enterprise/oci/installation-compute-instance-with-OL.md) 	|
| Red Hat Enterprise Linux (RHEL) 	| 7, 8, 9 	| x64 	| [Installation Guide for Linux](../getting-started/graalvm-enterprise/linux.md) 	|
| macOS 	| 11 (Big Sur), 12.4 (Monterey), 13.3 (Ventura), 14.3 (Sonoma)	| x64, AArch64	| [Installation Guide for macOS](../getting-started/graalvm-enterprise/macos.md) 	|
| Microsoft Windows 	| Server 2016, 2019, 2022	| x64 	| [Installation Guide for Windows](../getting-started/graalvm-enterprise/windows.md) 	|

## Experimental Components

Oracle GraalVM for JDK 22 includes some components that are considered experimental. 
These components are not meant for use in production and are not supported by Oracle. 
Some components are considered experimental on specific platforms. 
The GraalVM team welcomes feedback on these components, but users should be aware that the components may not be included in a future release or may change significantly before being considered production-ready:
* Java on Truffle (Espresso) is _supported_ on Linux x64 only and is experimental on other certified platforms. 
* The GraalVM Python runtime (GraalPy) is _experimental_ on all certified platforms.

## Deprecated Components

The following components are deprecated and will be removed in Oracle GraalVM for JDK 23:
* LLVM Runtime
* LLVM Toolchain
* Node.js

## Related Technologies

Additional open source language runtimes designed for use with Oracle GraalVM for JDK 22 are available on [graalvm.org](https://www.graalvm.org/reference-manual/languages/).

## Licensing and Support

Oracle GraalVM is licensed under [GraalVM Free Terms and Conditions (GFTC) including License for Early Adopter Versions](https://www.oracle.com/downloads/licenses/graal-free-license.html). 
Subject to the conditions in the license, including the License for Early Adopter Versions, the GFTC is intended to permit use by any user including commercial and production use. 
Redistribution is permitted as long as it is not for a fee. 
Oracle GraalVM is also free to use on Oracle Cloud Infrastructure. 
For more information about Oracle GraalVM licensing, see the [Oracle Java SE Licensing FAQ](https://www.oracle.com/java/technologies/javase/jdk-faqs.html#GraalVM-licensing).

Oracle GraalVM is available as part of the [Oracle Java SE Subscription](https://www.oracle.com/java/java-se-subscription/) which includes 24x7x365 [Oracle premier support](https://www.oracle.com/support/premier/) and access to [My Oracle Support (MOS)](https://www.oracle.com/support/).