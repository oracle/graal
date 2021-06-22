---
layout: docs
toc_group: vscode
link_title: Visual Studio Code Extensions
permalink: /tools/vscode/
---

# Visual Studio Code Extensions

Here you will learn about GraalVM intergation into Visual Studio Code (VS Code).
Visual Studio Code (VS Code) is an integrated development environment that provides the embedded Git and GitHub control, syntax highlighting, code refactoring, and much more.

[GraalVM Extension](graalvm/README.md), introduced with the GraalVM 19.2 release, has been created to provide a polyglot environment in VS Code.
It provides users the environment for editing and debugging programs running on GraalVM, including JavaScript and Node.js support by default. Users can edit, run and debug either single-language applications written in any of the GraalVM-supported languages (JS, Ruby, R, and Python) or polyglot applications in the same runtime.
The extension has progressed since then and now offers an installation wizzard that allows downloading and installing GraalVM and its optional features directly from the user interface.

By installing the [Apache NetBeans Language Server](https://marketplace.visualstudio.com/items?itemName=asf.apache-netbeans-java) extension on top of it, users receive also editting and debugging support for Java.

[Micronaut Extension](micronaut/README.md), introduced with the GraalVM 20.3 release, provides the basic support for developing applications based on the [Micronaut Framework](https://micronaut.io/). Apart from the Micronaut project creation wizard, it adds also special special support for generating native images with GraalVM in VS Code.
