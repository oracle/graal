# Experimental Monomorphization

Truffle has a new, experimental approach to monomorphization (also known as
splitting). For more information about the benefits of monomorphization,
consider reading ["Monomorphization use cases"](MonomorphizationUseCases.md).

It is controlled by the `TruffleExperimentalSplitting` flag, i.e. adding
`-Dgraal.TruffleExperimentalSplitting=true` to your command line. This
activates the new heuristic (replacing the old one, as well as disabling manual
[cloning of call
targets](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/nodes/DirectCallNode.html#cloneCallTarget--)
which can be re-enabled by setting the
`TruffleExperimentalSplittingAllowForcedSplits` flag) 

The heuristic relies on information from the language implementation to guide
the decisions.  To find out more about how to use the new approach in your
language implementation please refer to ["Reporting
Polymorphism"](ReportingPolymorphism.md).

For more details on how the new apporach works consider reading ["Experimental
Splitting"](ExperimentalSplitting.md)
