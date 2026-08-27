# Functional Specifications

This directory contains functional specifications for Native Image.

- [JCA Security Provider Inclusion](security-providers.md): provider lookup and factory-call behavior
  based on metadata.
- [Java Reflection Registration](reflection.md): class acquisition, metadata queries, invocation,
  serialization, and unsafe access.
- [Functional Decisions](decisions/): product behavior decisions and tradeoffs for the
  specifications.

## Notation

The specifications in this directory share the following notation.

Non-normative text appears as an indented note.
It explains or motivates a rule and states no requirement of its own.

> This is non-normative text.
> It gives rationale, intuition, or examples.

A definition that introduces a **bold term** states the exact condition under which that term
applies, with the phrase *if and only if*.

Each specification names its default package.
Whenever it refers to a class or interface using a single identifier N, the intended reference is to
the class or interface named N in that package.
Classes and interfaces from other packages are named by their canonical name, for example
`java.util.ServiceLoader`.

A cross-reference within a specification is shown as §x.y and links to that section.
A reference to The Java Language Specification is written as JLS §12.4, and a reference to the Java
SE API documentation names the specified class, interface, or member.

The specifications use the following terms of the Java Platform specifications with their standard
meaning:

- *Loading* (JLS §12.2) is finding the binary form of a class and constructing a `java.lang.Class`
  object for it.
  *Creation of a new class instance* (JLS §12.5) is the separate act of instantiating that class.
- *Class initialization* (JLS §12.4) is the execution of a class's static initializers.
- The *binary name* (JLS §13.1) of a class is the name by which reflection metadata,
  `java.lang.Class.forName`, and diagnostics identify it.
