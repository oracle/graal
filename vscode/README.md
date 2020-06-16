# Visual Studio Code extension

This suite provides extension to Visual Studio Code that supports development of polyglot applications using GraalVM.
The extension is Technology Preview.

## Build VSIX package from sources

To build VSIX package of the GraalVM extension, take the following steps:

* Install `vsce` (short for "Visual Studio Code Extensions"), a command-line tool for packaging, publishing and managing VS Code extensions
```bash
npm install -g vsce
```

* Compile and package the extension
```bash
pushd graalvm; npm install; vsce package; popd;
```

Alternatively, invoke `mx build` to create a zip file distribution with the extension contained.

## Installation

To install the GraalVM extension into Visual Studio Code, take the following step:

* Install desired package with `code --install-extension <extension.vsix>`
```bash
code --install-extension graalvm/graalvm-*.vsix
```
## License

GraalVM VS Code Extension is licensed under [The Universal Permissive License (UPL), Version 1.0](LICENSE)