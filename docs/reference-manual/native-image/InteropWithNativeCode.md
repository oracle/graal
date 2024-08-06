---
layout: docs
toc_group: native-code-interoperability
link_title: Interoperability with Native Code
permalink: /reference-manual/native-image/native-code-interoperability/
redirect_from: /reference-manual/native-image/ImplementingNativeMethodsInJavaWithSVM/
---

# Interoperability with Native Code

You can use Native Image to convert Java code into a **native shared library** and call it from a native (C/C++) application just like any C function. 
There are two mechanisms for calling natively-compiled Java methods:

- [JNI Invocation API](https://docs.oracle.com/en/java/javase/22/docs/specs/jni/invocation.html), an API to load the JVM into an arbitrary native application. The advantage of using JNI Invocation API is support for multiple, isolated execution environments within the same process. 
- [Native Image C API](C-API.md), an API specific to GraalVM Native Image. The advantage of using Native Image C API is that you can determine what your API will look like, but parameter and return types must be non-object types.

### Related Documentation

- [Foreign Function and Memory API in Native Image](ForeignInterface.md)
- [Java Native Interface (JNI) on Native Image](JNI.md)
- [JNI Invocation API](JNIInvocationAPI.md)
- [Native Image C API](C-API.md)
- [Build a Native Shared Library](guides/build-native-shared-library.md)
- [Embedding Truffle Languages](https://nirvdrum.com/2022/05/09/truffle-language-embedding.html)&mdash;a blog post by Kevin Menard where he compares both mechanisms in Native Image for exposing Java methods