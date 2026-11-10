# Functional Specifications

This directory contains functional specifications for Native Image.

- [JCA Security Provider Inclusion](security-providers.md): provider lookup and factory-call behavior based on metadata.
- [Functional Decisions](decisions/): product behavior decisions and tradeoffs for the specifications.

## Structure

Each functional specification starts with a Reason for Existence section explaining the problem,
motivation, and intended benefit of the specified behavior.
Terminology is a required section immediately after Reason for Existence.
Glossary entry labels are bold text without section addresses.
Write definitions inline wherever they are needed rather than citing Terminology or its entries.
Requirements is the final section.
The Native Image Grund schema requires these sections and their placement.

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
[`java.util.ServiceLoader`](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/ServiceLoader.html).

A cross-reference within a specification is shown as §x.y and links to that section.
A reference to The Java Language Specification links directly to the cited section, for example
[JLS 12.4](https://docs.oracle.com/javase/specs/jls/se25/html/jls-12.html#jls-12.4) for Java SE 25, section 12.4.
A reference to the Java SE API documentation names the specified class, interface, or member.

The specifications use the following terms of the Java Platform specifications with their standard
meaning:

- *Loading* ([JLS 12.2](https://docs.oracle.com/javase/specs/jls/se25/html/jls-12.html#jls-12.2)) is finding the binary form of a class and constructing a
  [`java.lang.Class`](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Class.html)
  object for it.
  *Creation of a new class instance* ([JLS 12.5](https://docs.oracle.com/javase/specs/jls/se25/html/jls-12.html#jls-12.5)) is the separate act of instantiating that class.
- *Class initialization* ([JLS 12.4](https://docs.oracle.com/javase/specs/jls/se25/html/jls-12.html#jls-12.4)) is the execution of a class's static initializers.
- The *binary name* ([JLS 13.1](https://docs.oracle.com/javase/specs/jls/se25/html/jls-13.html#jls-13.1)) of a class is the name by which reflection metadata,
  [`java.lang.Class.forName`](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Class.html#forName(java.lang.String)),
  and diagnostics identify it.
