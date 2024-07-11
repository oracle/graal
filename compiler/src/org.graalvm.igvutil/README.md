# IGV Utilities

This package contains some utility programs for inspecting and manipulating
Graal IR dump files (`.bgv` files) through a command-line interface, which allows interoperability with programs
other than IGV.

## Usage
All utilities are accessible as subcommands of the `IgvUtility` program.
You can access them by running
```commandline
mx igvutil <SUBCOMMAND> [OPTIONS]
```
in the `compiler` folder in the `graal` repo.

## Graph Printing
The `list` subcommand shows a tree-like representation of the contents
of one or more `.bgv` files.  
Example usage:
```commandline
mx igvutil list dump1.bgv dump2.bgv

dump1.bgv
├─ jdk.graal.compiler.jtt.bytecode.BC_getfield_b.test(BC_getfield_b$FieldHolder)
│  ├─ 0: Before phase PhaseSuite
│  ├─ 1: Before phase HotSpotGraphBuilder
│  ├─ 2: After phase HotSpotGraphBuilder
│  ├─ 3: Before phase VerifyEncodingDecodingPhase
│  ├─ 4: After phase VerifyEncodingDecodingPhase
│  ├─ 5: After phase PhaseSuite
│  ├─ 6: initial state
│  ├─ 7: Before phase PhaseSuite
│  ├─ 8: Before phase Canonicalizer
│  ├─ 9: After phase Canonicalizer
│  └─ [...]
└─ jdk.graal.compiler.jtt.bytecode.BC_getfield_b.test(BC_getfield_b$FieldHolder)
   ├─ 0: Before phase PhaseSuite
   ├─ 1: Before phase HotSpotGraphBuilder
   ├─ 2: After phase HotSpotGraphBuilder
   ├─ 3: Before phase VerifyEncodingDecodingPhase
   ├─ 4: After phase VerifyEncodingDecodingPhase
   ├─ 5: After phase PhaseSuite
   ├─ 6: initial state
   └─ [...]

dump2.bgv
├─ jdk.graal.compiler.jtt.bytecode.BC_dadd.test(double, double)
│  └─ [...]
└─ [...]
```

## Graph Flattening

In some cases, the IR graphs for the different phases of a given compilation
can be interleaved with other graphs dumped during compilation, such as the call tree
used during inlining.

This results in a single compilation's graphs being split up across more than one dump group,
which prevents things like diffs across phases.
To fix this, the `flatten` command groups graphs by a specifiable graph property (its name, by default)
and spits out a new `.bgv` file which can then be loaded by IGV.

This is most useful for high dump levels and/or Truffle dumps where the same graph is split across many folders.
It can also be used to combine dumps from multiple files into a single file.

Example usage:
```commandline
mx igvutil flatten dump1.bgv [dump2.bgv] --by name --output-file flattened.bgv
```

## Exporting to JSON

Graph data can be converted to a JSON representation using the `filter` command,
so called since it's possible to select only specific graph and node properties to be exported.

Example usage (piped through jq for readability):
```commandline
mx igvutil filter dump1.bgv | jq

{
  "vm.uuid": "39285",
  "compilationId": "HotSpotCompilation-13032[jdk.graal.compiler.jtt.bytecode.BC_getfield_b.test(BC_getfield_b$FieldHolder)]",
  "date": "Wed Jun 12 15:45:23 CEST 2024",
  [...]
  "jvmArguments": "-XX:ThreadPriorityPolicy=1 -XX:+UnlockExperimentalVMOptions [...]"
  "graph": "StructuredGraph:2481{HotSpotMethod<BC_getfield_b.test(BC_getfield_b$FieldHolder)>}",
  "elements": [{
    "name": "jdk.graal.compiler.jtt.bytecode.BC_getfield_b.test(BC_getfield_b$FieldHolder)",
    "type": "",
    "compilationId": "HotSpotCompilation-13029[jdk.graal.compiler.jtt.bytecode.BC_getfield_b.test(BC_getfield_b$FieldHolder)]",
    "date": "Wed Jun 12 15:45:23 CEST 2024",
    "version.truffle": "229e4fc906e95404399e6c1f013fce142e861b47",
    "version.compiler": "229e4fc906e95404399e6c1f013fce142e861b47",
    "version.sdk": "229e4fc906e95404399e6c1f013fce142e861b47",
    "version.regex": "229e4fc906e95404399e6c1f013fce142e861b47",
    "version.labsjdk-builder": "5c2b7b1e756d4202b02af8a80c6959da5a0ab34b",
    "jvmArguments": "-XX:ThreadPriorityPolicy=1 -XX:+UnlockExperimentalVMOptions [...]"
    "graph": "StructuredGraph:2478{HotSpotMethod<BC_getfield_b.test(BC_getfield_b$FieldHolder)>}",
    "elements": [{
      "id": 0,
      "name": "0: Before phase PhaseSuite",
      "graph_type": "StructuredGraph"
   [...]
}

# Select only dates and names for graphs, and ids and stamps for nodes
mx igvutil filter dump1.bgv -- --graph-properties=date,graph --node-properties=id,stamp | jq
{
  "date": "Wed Jun 12 15:45:23 CEST 2024",
  "graph": "StructuredGraph:2481{HotSpotMethod<BC_getfield_b.test(BC_getfield_b$FieldHolder)>}",
  "elements": [
    {
      "date": "Wed Jun 12 15:45:23 CEST 2024",
      "graph": "StructuredGraph:2478{HotSpotMethod<BC_getfield_b.test(BC_getfield_b$FieldHolder)>}",
      "elements": [
        {
          "id": 0,
          "name": "0: Before phase PhaseSuite",
          "graph_type": "StructuredGraph",
          "nodes": {
            "0": {
              "id": "0",
              "stamp": "void"
            }
          }
        },
        {
          "id": 1,
          "name": "1: Before phase HotSpotGraphBuilder",
          "graph_type": "StructuredGraph",
          "nodes": {
            "0": {
              "id": "0",
              "stamp": "void"
            }
          }
        },
        {
          "id": 2,
          "name": "2: After phase HotSpotGraphBuilder",
          "graph_type": "StructuredGraph",
          "nodes": {
            "0": {
              "id": 0,
              "stamp": "void"
            },
            "1": {
              "id": 1,
              "stamp": "a# jdk.graal.compiler.jtt.bytecode.BC_getfield_b$FieldHolder"
            },
            "2": {
              "id": 2,
            },
            "3": {
              "id": 3,
              "stamp": "i32 [-128 - 127]"
            },
    [...]
```