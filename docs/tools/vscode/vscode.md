---
layout: docs
toc_group: vscode
link_title: Visual Studio Code Extensions
permalink: /tools/vscode/
---

# Visual Studio Code Extensions

Here you will learn about GraalVM integration into [Visual Studio Code (VS Code)](https://code.visualstudio.com/).
VS Code is an integrated development environment that provides the embedded Git and GitHub control, syntax highlighting, code refactoring, and much more.

The following extensions are available for download from Visual Studio Code Marketplace:

- [**GraalVM Tools for Java**](graalvm/README.md) provides a full-fledged support for the Java language and, additionally, enables a polyglot environment in VS Code, making it a comfortable and convenient integrated development environment to work with.
Users can edit, run and debug either single-language applications written in any of the GraalVM-supported languages (Java, JS, Ruby, R, and Python), or polyglot applications without the need to install any other additional extensions.
The extension also offers the installation wizard that allows to download and install GraalVM and its optional features directly from the user interface. Get the extension from [Visual Studio Code Marketplace](https://marketplace.visualstudio.com/items?itemName=oracle-labs-graalvm.graalvm).

- [**GraalVM Tools for Micronaut**](micronaut/README.md) provides full support for developing applications based on the [Micronaut framework](https://micronaut.io/). The extension also enables the [Micronaut Launch](https://micronaut.io/launch/) application that allows you to create Micronaut projects through an interface inside VS Code, in addition to using the console CLI. This extension is integrated with GraalVM to provide all Native Image capabilities. You can generate native images directly from VS Code, and deploy them to a Docker Registry. Get the extension from [Visual Studio Code Marketplace](https://marketplace.visualstudio.com/items?itemName=oracle-labs-graalvm.micronaut).

- [**GraalVM Extension Pack for Java**](graalvm-pack/README.md) is a collection of extensions that helps users write, debug and test Java, JavaScript, Python, Ruby, R and polyglot applications running on GraalVM, either standalone or using the Micronaut framework.
GraalVM Extension Pack for Java bundles [GraalVM Tools for Java](https://marketplace.visualstudio.com/items?itemName=oracle-labs-graalvm.graalvm), [GraalVM Tools for Micronaut](https://marketplace.visualstudio.com/items?itemName=oracle-labs-graalvm.micronaut), and [Apache NetBeans Language Server](https://marketplace.visualstudio.com/items?itemName=asf.apache-netbeans-java) extensions. Get the extension pack from [Visual Studio Code Marketplace](https://marketplace.visualstudio.com/items?itemName=oracle-labs-graalvm.graalvm-pack).

As of GraalVM 21.2.0, the GraalVM Tools for Java extension introduced a new feature - the integration with VisualVM (https://visualvm.github.io), which is the all-in-one Java (and polyglot) monitoring and troubleshooting tool.
This feature brings the visual Java tooling to VS Code. Proceed to each extension documentation respectively to learn more.
