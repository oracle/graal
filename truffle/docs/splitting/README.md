# Experimental Splitting

Truffle has a new, experimental approach to splitting. 

It is controlled by the `TruffleExperimentalSplitting` flag, i.e. adding
`-Dgraal.TruffleExperimentalSplitting=true` to your command line. This activates
the new heuristic, but the heuristic relies on information from the language
implementation to guide the decisions.

To find out more about how to use the new approach in your language
implementation please refer to ["Reporting
Polymorphism"](ReportingPolymorphism.md).

For more details how the new apporach works consider reading ["Experimental
Splitting"](ExperimentalSplitting.md)
