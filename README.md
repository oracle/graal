# truffle-lsp-server

## setup
Build with `mx build`. Run with mx `mx lsp`. Use dynamic imports to enable languages.

### IDE setup
Use `mx ideinit`. Run `GraalLanguageServerLauncher.java`.

## Warning
The Graal language server implementation is still experimental. Object property code completion (typing a `.` in most languages) triggers code evaluation in a read-only file system by evaluating the source file in which the code completion was triggered. Network communication is not restricted yet. So avoid code completion in files where network-IO or other unwanted side-effects may occur.
