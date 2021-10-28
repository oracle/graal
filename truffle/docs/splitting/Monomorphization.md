---
layout: docs
toc_group: truffle
link_title: Monomorphization
permalink: /graalvm-as-a-platform/language-implementation-framework/splitting/Monomorphization/
---
# Monomorphization

Truffle has an automatic approach to monomorphization (also known as "splitting"). For more information about the benefits of monomorphization, continue reading to [Monomorphization Use Cases](MonomorphizationUseCases.md).

It is controlled by the `Splitting` engine option and is on by default.
Adding `--engine.Splitting=false` to your command line will disable it.

The heuristic relies on information from the language implementation to guide the decisions. To find out more about how to use the new approach in your language implementation, refer to the [Reporting Polymorphism](ReportingPolymorphism.md) guide.

For more details on how the new approach works, see the [Splitting](Splitting.md) guide.
