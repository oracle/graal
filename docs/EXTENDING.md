# Extending Sulong

Sulong is designed to be easily extensible (see [`ARCHITECTURE.md`](ARCHITECTURE.md)).
It is possible to replace (most of) the Truffle nodes of the current implementation
with other Truffle nodes, without having to change Sulong's parser.
Also, Sulong is extensible in its option mechanism.

## Extension mechanism

To implement the extension mechanism we use Java's `ServiceLoader` to load
implementations of a specified interface.

## Replacing nodes

Sulong's assumption is that all nodes inherit from either `LLVMNode` or
`LLVMExpressionNode`. `LLVMNode` does not return a result and is used to
implement statements, while `LLVMExpressionNode` returns a result and is
used to implement expressions (see [`DATATYPES.md`](DATATYPES.md)).

By implementing the `NodeFactoryFacadeProvider` one can provide a custom
implementation of `NodeFactoryFacade` that the parser uses to create
Truffle nodes. Changes to `NodeFactoryFacade` are kept minimal, to not
force projects that implement the `NodeFactoryFacade` to frequently update
their code.

To use the node facade, an option (see `mx su-options`) exists to select
a node facade implementation, which is currently `sulong.NodeConfiguration`.

## Adding options

To implement new options, one can implement the `LLVMOptionsServiceProvider`.
The additional options are automatically added to the existing options,
and are thus also visible with `mx su-options`.


