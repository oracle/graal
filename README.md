# truffle-lsp-server

## setup
Build with `mx build`. Run with mx `mx lsp`. Use dynamic imports to enable languages.

### IDE setup
Use `mx ideinit`. Run `TruffleLSPLauncher.java`.

## Warning
The LSP server implementation is still experimental. Object property code completion (typing a `.` in most languages) triggers code evaluation in a full-IO-enabled file system by evaluating the source file in which the code completion was triggered. So avoid code completion in files where IO or other unwanted side-effects may occur.
