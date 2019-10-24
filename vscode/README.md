# Visual Studio Code extensions

This suite provides extensions to Visual Studio Code that support development of polyglot applications using GraalVM.

## Build VSIX packages from sources

To build VSIX packages of the GraalVM extensions, take the following steps:

* Install `vsce` (short for "Visual Studio Code Extensions"), a command-line tool for packaging, publishing and managing VS Code extensions
```bash
npm install -g vsce
```

* Compile and package all extensions
```bash
cd graalvm; npm install; vsce package
cd graalvm-r; npm install; vsce package
cd graalvm-ruby; npm install; vsce package
```

Alternatively, invoke `mx build` to create a zip file distribution with all extensions contained.

## Installation

To install the GraalVM extensions into Visual Studio Code, take the following steps:

* Install each package with `code --install-extension <extension.vsix>`
```bash
code --install-extension graalvm-0.0.1.vsix
code --install-extension graalvm-r-0.0.1.vsix
code --install-extension graalvm-ruby-0.0.1.vsix
```

## License

GraalVM VS Code Extensions are licensed under [The Universal Permissive License (UPL), Version 1.0](LICENSE)