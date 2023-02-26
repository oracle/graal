---
layout: ni-docs
toc_group: native-image
link_title: FAQ
permalink: /reference-manual/native-image/FAQ/
---

# Frequently Asked Questions

### How is GraalVM Native Image licensed?

The Native Image technology is distributed as a separate installable to GraalVM.
Native Image for GraalVM Community Edition is licensed under the [GPL 2 with Classpath Exception](https://github.com/oracle/graal/blob/master/substratevm/LICENSE).

Native Image for GraalVM Enterprise Edition is licensed under the [Oracle Technology Network License Agreement for GraalVM Enterprise Edition](https://www.oracle.com/downloads/licenses/graalvm-otn-license.html).

### What is the licence for artifacts produced by Native Image? For example, compiling an AWT application produces an `awt.dll/so` file. Under what license can I ship it?

Everything that is included in a product (GraalVM Native Image) or produced by a product (a native executable, a native shared library, etc.) is covered by the same licence. That is [Oracle Technology Network License Agreement for GraalVM Enterprise Edition](https://www.oracle.com/downloads/licenses/graalvm-otn-license.html).

### Native Image doesn't work on my Windows 10?

Make sure you execute the `native-image` command from the **x64 Native Tools Command Prompt**.
This is the prerequisite for Windows: installing [Visual Studio](https://visualstudio.microsoft.com/vs/) and Microsoft Visual C++ (MSVC) with the Windows 10 SDK.
You can use Visual Studio 2017 version 15.9 or later.

Check [this link](https://medium.com/graalvm/using-graalvm-and-native-image-on-windows-10-9954dc071311) for more information and step-by-step instructions.

### What is the recommended base Docker image for deploying a static or mostly static native executable?

A fully static native executable gives you the most flexibility to choose a base image - it can run on anything including a `FROM scratch` image.
A mostly static native executable requires a container image that provides `glibc`, but has no additional requirements.
In both cases, choosing the base image mostly depends on what your particular executable needs without having to worry about run-time library dependencies.

###  Does AWS provide support by Native Image? OR Can I deploy Native Image in AWS Lambda?

Yes, you can. AWS SDK for Java 2.x (version 2.16.1 or later) has out-of-the-box support for GraalVM Native Image compilation.

<!-- ### Can I distribute a static native image built with GraalVM Enterprise Native Image?

If you statically link any GPL code into a native image with GraalVM Enterprise, you will be violating the licence.

### Does Native Image support Java AWT?

Answer goes here.

### Can I compile my Swing application ahead-of-time with GraalVM Native Image?

Answer goes here.

### Do I need to configure Native Image to be compatible with third-party libraries? 

Answer goes here. -->
