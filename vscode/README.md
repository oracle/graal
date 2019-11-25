# Visual Studio Code extensions

This suite provides extensions to Visual Studio Code that support development of polyglot applications using GraalVM.
The extensions are Technology Preview.

## Build VSIX packages from sources

To build VSIX packages of the GraalVM extensions, take the following steps:

* Install `vsce` (short for "Visual Studio Code Extensions"), a command-line tool for packaging, publishing and managing VS Code extensions
```bash
npm install -g vsce
```

* Compile and package all extensions
```bash
for ext in "" -python -r -ruby -complete ; do
   pushd graalvm${ext}; npm install; vsce package; popd;
done
```

Alternatively, invoke `mx build` to create a zip file distribution with all extensions contained.

## Installation

To install the GraalVM extensions into Visual Studio Code, take the following steps:

* Install desired package one by one with `code --install-extension <extension.vsix>`
```bash
code --install-extension graalvm/graalvm-*.vsix \
     --install-extension graalvm-python/graalvm-python-*.vsix \
     --install-extension graalvm-r/graalvm-r-*.vsix \
     --install-extension graalvm-ruby/graalvm-ruby-*.vsix
```

Alternatively, you may want to install all-in-one extension that contains all of them, by issuing:

```bash
code --install-extension graalvm-complete/graalvm-complete-*.vsix
```

However, `graalvm-complete` extension actually downloads all the dependant extensions from the Marketplace (not from locally built sources).

## License

GraalVM VS Code Extensions are licensed under [The Universal Permissive License (UPL), Version 1.0](LICENSE)