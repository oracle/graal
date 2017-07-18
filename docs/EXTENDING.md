# Extending Sulong

Sulong is designed to be easily extensible (see [`ARCHITECTURE.md`](ARCHITECTURE.md)).
It is possible to replace (most of) the Truffle nodes of the current implementation
with other Truffle nodes, without having to change Sulong's parser.

## Extension mechanism

To implement the extension mechanism we use Java's `ServiceLoader` to load
implementations of a specified interface.

## Replacing nodes

Sulong's assumption is that all nodes inherit from either `LLVMNode` or
`LLVMExpressionNode`. `LLVMNode` does not return a result and is used to
implement statements, while `LLVMExpressionNode` returns a result and is
used to implement expressions.

By exposing an implementation of the `NodeFactory` interface as a service
one can provide a custom implementation of `NodeFactory` that the parser
uses to create Truffle nodes. Changes to `NodeFactory` are kept minimal,
to not force projects that implement the `NodeFactory` to frequently
update their code.
