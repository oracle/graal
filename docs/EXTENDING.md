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

By exposing an implementation of the `SulongNodeFactory` interface as
a service one can provide a custom implementation of `SulongNodeFactory`
that the parser uses to create Truffle nodes. Changes to
`SulongNodeFactory` are kept minimal, to not force projects that implement
the `SulongNodeFactory` to frequently update their code.

To use the node facade, an option (see `mx su-options`) exists to select
a node facade implementation, which is currently `sulong.NodeConfiguration`.

## Adding options

Use the `OptionCategory` annotation to mark your class as containing options.
Annotate individual options using `Option` annotation.
An annotation processor generates a subclass of this class, which can be
instantiated and allows accessing your options. If you want to make your
options visible with `mx su-options`, instantiate the generated class in a
class that implements the `LLVMOptionsServiceProvider`.
