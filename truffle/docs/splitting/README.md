# Monomorphization

Truffle has an automatic approach to monomorphization (also known as
splitting). For more information about the benefits of monomorphization,
consider reading ["Monomorphization use cases"](MonomorphizationUseCases.md).

It is controlled by the `Splitting` engine option and is on by default.
Adding `--engine.Splitting=false` to your command line will disable it.

The heuristic relies on information from the language implementation to guide
the decisions. To find out more about how to use the new approach in your
language implementation please refer to ["Reporting
Polymorphism"](ReportingPolymorphism.md).

For more details on how the new approach works consider reading
["Splitting"](Splitting.md)
