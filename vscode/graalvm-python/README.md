# GraalVM Python Support for VS Code

A VS Code extension providing the basic support for editing and debugging Python programs running on [GraalVM Python](http://www.graalvm.org/docs/reference-manual/languages/python).
The extension is Technology Preview.

## Features

Upon the extension installation, the GraalVM is checked for presence of the Python component and user is provided with an option of an automatic installation of the missing component.
The folowing command from the Command Palette (Ctrl+Shift+P) can be used to install the GraalVM Python component manually:
* __Install GraalVM Component__

Once the GraalVM contains the Python component, the following debug configuration can be used to debug your Python applications running on GraalVM:
* __Launch Python Script__ - Launches a Python script using GraalVM in a debug mode.

![Image Debug Configurations](images/debug-config-python.png)

Since an easy writing of [polyglot](https://www.graalvm.org/docs/reference-manual/polyglot) applications is one of the defining features of GraalVM, the code completion invoked inside Python sources provides items for `Polyglot.eval(...)`, `Polyglot.eval_file(...)` and `Java.type(...)` calls.

![Image Code Completion](images/code-completion-python.png)

For Python sources opened in editor, all the `Polyglot.eval(...)` calls are detected and the respective embedded languages are injected to their locations. For example, having a JavaScript code snippet called via the Polyglot API from inside a Python source, the JavaScript language code is embedded inside the corresponding Python string and all VS Code's editing features (syntax highlighting, bracket matching, auto closing pairs, code completion, etc.) treat the content of the string as the JavaScript source code.

![Image Language Embedding](images/language-embedding-python.png)

## Requirements

This extension depends on the following extensions:
* [Python](https://marketplace.visualstudio.com/items?itemName=ms-python.python) - Python language support.
* [GraalVM](https://marketplace.visualstudio.com/items?itemName=oracle-labs-graalvm.graalvm) - Basic support for GraalVM.

## Privacy Policy

Please read the [Oracle Privacy Policy](https://www.oracle.com/legal/privacy/privacy-policy.html) to learn more.
