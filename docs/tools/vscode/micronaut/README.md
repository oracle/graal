---
layout: docs
toc_group: vscode
link_title: GraalVM Tools for Micronaut Extension
permalink: /tools/vscode/micronaut-extension/
---

# GraalVM Tools for Micronaut Extension

[GraalVM Tools for Micronaut](https://marketplace.visualstudio.com/items?itemName=oracle-labs-graalvm.micronaut) provides support for developing applications based on the [Micronaut framework](https://micronaut.io/) in Visual Studio Code.
The extension is Technology Preview.

The extension also enables the [Micronaut Launch](https://micronaut.io/launch/) application that allows you to create Micronaut projects through an interface inside VS Code, in addition to using the console CLI.
There are, of course, other ways to create a new Micronaut application. If you provide a path to the [Micronaut CLI installation](https://micronaut-projects.github.io/micronaut-starter/latest/guide/#installation), you can create a project using the `mn` executable. If you prefer not to install the Micronaut CLI, and you are running on Linux or macOS, you can `curl` the project. Lastly, you can navigate to Micronaut Launch in a browser, create a new project, download it and open in VS Code.

In combination with the [GraalVM Tools for Java extension](https://marketplace.visualstudio.com/items?itemName=oracle-labs-graalvm.graalvm), you can run Micronaut projects on GraalVM, and debug them directly from the VS Code development environment with different debugging protocols enabled with the extension. This extension for Micronaut was also developed to help developers build native images directly from VS Code.

#### Table of contents
- [Installation and Setup](#installation-and-setup)
- [Features](#features)
  - [Micronaut VS Code Commands](#micronaut-vs-code-commands)
  - [Extension Settings](#extension-settings)
- [Create Micronaut Project](#create-micronaut-project)
- [Generate Native Images of Micronaut Projects](#generate-native-images-of-micronaut-projects)
- [Deploy Micronaut Projects](#deploy-micronaut-projects)
- [Feedback](#feedback)
- [Privacy Policy](#privacy-policy)
- [Known Issues](#known-issues)

## Installation and Setup

Install the GraalVM Tools for Micronaut extension from the VS Code consolde by clicking on the Extensions icon in the Activity Bar (or invoke it with _Ctrl+Shift+X_). Search for "Micronaut" and install the package. Reload will be required.

> Note: The Micronaut extension also requires the [GraalVM Tools for Java](https://marketplace.visualstudio.com/items?itemName=oracle-labs-graalvm.graalvm) extension, which provides support for editing and debugging polyglot programs running on GraalVM. Please install it the same way.

When installed, the extension might check whether there is a registered GraalVM instance, and eventually request to download it or point to a local installation (see [GraalVM Installation and Setup in VS Code](../graalvm/README.md#installation-and-setup)).

Upon installation, the Micronaut Tools Page window opens, which provides you with shortcuts to:
- create a new Micronaut project or open an exisiting one
- build a native executable of a Micronaut project
- acquaint you with available features
- redirect you to the documentation available

![Micronaut Tools Page](images/micronaut_tools_page.png)

## Features

The GraalVM Tools for Micronaut extension provides:
* Micronaut project creation wizard
* installation of Micronaut CLI
* editing and debugging capabilities for Micronaut projects
* code completion and navigation for Micronaut configuration (YAML) files and Java (available with [Apache NetBeans Language Server extension](https://marketplace.visualstudio.com/items?itemName=asf.apache-netbeans-java)).
* ability to build Micronaut projects ahead-of-time into native images with GraalVM
* __Run main with Continuous Mode__ CodeLens runs Micronaut project and reloads it automatically when source code is changed. It is not available as Debugger.
    * __Launch Java: Continuous Mode__ is the name of the launch configuration.

### Micronaut VS Code Commands

To begin, invoke the Micronaut commands from View > Command Palette (Command Palette can be also opened by pressing F1, or the _Ctrl+Shift+P_ hot keys combination for Linux, and _Command+Shift+P_ for macOS):

![Micronaut VS Code Commands](images/micronaut-vs-code-commands.png)

The following commands are available for Micronaut project development:

* `Micronaut: Show Micronaut Tools Page` - show the Micronaut Tools Page
* `Micronaut: Create Micronaut Project` - create a Micronaut project based on https://micronaut.io/launch
* `Micronaut: Build ...` - build a Micronaut project with the user-selected goal/target
* `Micronaut: Build Native Image` - build a native executable of a Micronaut project
* `Micronaut: Deploy ...` - build and deploy Docker image for Micronaut project

### Extension Settings

This extension contributes the following settings:
* __micronaut.home__ - the optional path to the Micronaut CLI installation
* __micronaut.showWelcomePage__ - show the Micronaut Tools Page on extension activation

## Create Micronaut Project

The Create Micronaut Project command in VS Code supports generating Micronaut applications, CLI applications, and other types of applications that a regular [Micronaut Launch](https://micronaut.io/launch/) application does. The wizard prompts users to:

  * pick the application type
  * pick the Micronaut version
  * pick the Java version
  * provide a project name
  * provide a base package name
  * pick the project language (Java, Kotlin, Groovy)
  * pick the project features:

  ![Micronaut Project Features](images/micronaut-project-features_view.png)

  * pick the build tool (Maven or Gradle)
  * pick the test framework (JUnit, Spock, Kotlintest)

Finally, you are asked to select the destination folder on your local disk and whether to open the created project in a new editor or add it to the current workspace.

The GUI part of the Micronaut extension adds a new view to the Explorer activity, which shows Micronaut projects in the current workspace.

## Generate Native Images of Micronaut Projects

The Micronaut support for VS Code is integrated with GraalVM to get the most from the applications and provide you with rich Native Image capabilities.

Having set up GraalVM as the default runtime and debug environment in VS Code, invoke the "View > Command Palette > Micronaut: Build..." action, where you can select the build targets (e.g., `clean`, `build`, `nativeImage`, etc.) from a list of available ones.
For example, if your project is built with Maven, and you would like to package the compiled code as a GraalVM native image, select `nativeImage`.
That will run the `mvnw package -Dpackaging=native-image` job.

![Micronaut Build Commands](images/micronaut-build-commands.png)

For more details, continue reading to the [Micronaut documentation](https://guides.micronaut.io/micronaut-creating-first-graal-app/guide/index.html#creatingGraalImage).

## Deploy Micronaut Projects

The Micronaut support in VSCode also allows to build and deploy Docker image to a Docker Registry.
Use action View > Command Palette > Micronaut: Deploy... and select **dockerPush** to deploy dockerized Micronaut application or **dockerPushNative** to build and push docker with a native executable of Micronaut application.

![Micronaut Deploy Commands](images/micronaut-deploy-commands.png)

Besides that, you can also push a Micronaut application or a native executable to a Docker Registry from the VS Code terminal window. A particular Docker Registry can be configured in the build, see the [Micronaut Deploying Application](https://micronaut-projects.github.io/micronaut-maven-plugin/latest/examples/deploy.html) documentation.

### Feedback

* [Request a feature](https://github.com/graalvm/vscode-extensions/issues/new?labels=enhancement)
* [File a bug](https://github.com/graalvm/vscode-extensions/issues/new?labels=bug)

### Privacy Policy

Read the [Oracle Privacy Policy](https://www.oracle.com/legal/privacy/privacy-policy.html) to learn more.

### Known Issues

The GraalVM Tools for Micronaut Extension extension 0.5.* is Technology Preview, meaning the functionality may not be complete.
