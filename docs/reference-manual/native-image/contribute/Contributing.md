---
layout: ni-docs
toc_group: contributing
link_title: Contributing
permalink: /reference-manual/native-image/contributing/
---

# Contributing to Native Image

[GraalVM](https://github.com/oracle/graal/) is an open source project, so is [Substrate VM](https://github.com/oracle/graal/tree/master/substratevm) - the codename for the Native Image technology.
We welcome contributors to the core!

There are two common ways to contribute:

- Submit [GitHub issues](https://github.com/oracle/graal/issues) for bug reports, questions, or requests for enhancements.
- Open a [GitHub pull request](https://github.com/oracle/graal/pulls).

If you want to contribute changes to Native Image core, you must adhere to the project's standards of quality. For more information, see [Native Image Code Style](CodeStyle.md).

If you would like to ensure complete compatibility of your library with Native Image, consider contributing your library metadata to the [GraalVM Reachability Metadata Repository](https://github.com/oracle/graalvm-reachability-metadata). 
Follow [contributing rules](https://github.com/oracle/graalvm-reachability-metadata/blob/master/CONTRIBUTING.md) for this repository. 
Using this open source repository, users can share the burden of maintaining metadata for third-party dependencies.
