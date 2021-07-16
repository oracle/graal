---
layout: docs
toc_group: truffle
link_title: On-Stack Replacement 
permalink: /graalvm-as-a-platform/language-implementation-framework/OnStackReplacement/
---
# On-Stack Replacement (OSR)

During execution, Truffle will schedule "hot" call targets for compilation.
Once a target is compiled, later invocations of the target can execute the compiled version.
However, an ongoing execution of a call target will not benefit from this compilation, since it cannot transfer execution to the compiled code.
This means that a long-running target can get "stuck" in the interpreter, harming warmup performance.

On-stack replacement (OSR) is a technique used in Truffle to "break out" of the interpreter, transferring execution from interpreted to compiled code.
Truffle supports OSR for both AST interpreters (i.e., ASTs with `LoopNode`s) and bytecode interpreters (i.e., nodes with dispatch loops).
In either case, Truffle uses heuristics to detect when a long-running loop is being interpreted and can perform OSR to speed up execution.

## OSR for AST interpreters 

Languages using standard Truffle APIs get OSR for free on Graal.
The runtime tracks the number of times a `LoopNode` (created using `TruffleRuntime.createLoopNode(RepeatingNode)`) executes.
Once the loop iterations exceed a threshold, the runtime considers the loop "hot", and it will transparently perform OSR on the loop.
The OSR execution returns after the loop exits, and execution continues in the interpreter.

## OSR for bytecode interpreters

OSR for bytecode interpreters requires slightly more cooperation from the language.
A bytecode dispatch node typically looks something like the following:

```java
class BytecodeDispatchNode extends Node {
  @CompilationFinal byte[] bytecode;
  
  ...
  
  @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.MERGE_EXPLODE)
  Object execute(VirtualFrame frame) {
    int bci = 0;
    while (true) {
      int nextBCI;
      switch (bytecode[bci]) {
        case OP1:
          ...
          nextBCI = ...
          ...
        case OP2:
          ...
          nextBCI = ...
          ...
        ...
      }
      bci = nextBCI;
    }
  }
}
```

Unlike with AST interpreters, loops in a bytecode interpreter are often unstructured (and implicit).
Though bytecode languages do not have structured loops, backward jumps in the code ("back-edges") tend to be a good proxy for loop iterations.
Thus, Truffle's bytecode OSR is designed around back-edges and the destination of those edges (which generally correspond to loop headers).

To make use of Truffle's bytecode OSR, a language's dispatch node should implement the `BytecodeOSRNode` interface.
This interface requires three method implementations:

- `executeOSR(osrFrame, parentFrame, target)`: This method dispatches execution to the given `target` (i.e., bytecode index). It also handles any necessary state transfers between the frames before and after OSR.
- `getOSRMetadata()` and `setOSRMetadata(osrMetadata)`: These methods proxy accesses to a field declared on the class. The runtime will use these accessors to maintain state related to OSR compilation (e.g., back-edge counts). The field should be annotated `@CompilationFinal`.

Then, in the main dispatch loop, when the language hits a back-edge, it should invoke the provided `BytecodeOSRNode.reportOSRBackEdge(osrNode, parentFrame, target)` method to notify the runtime of the back-edge.
If the runtime performs OSR compilation starting from the target of this back-edge, it will transparent invoke the compiled code and return the computed result.

Note: Currently, Truffle only supports bytecode OSR when the `Frame` is explicitly marked non-materializable. This can be specified using the `FrameDescriptor(defaultValue, canMaterialize)` constructor.

The example above can be refactored to support OSR as follows:

```java
class BytecodeDispatchNode extends Node implements BytecodeOSRNode {
  @CompilationFinal byte[] bytecode;
  @CompilationFinal private Object osrMetadata;
  
  ...

  Object execute(VirtualFrame frame) {
    return executeFromBCI(frame, 0);
  }

  Object executeOSR(VirtualFrame osrFrame, Frame parentFrame, int target) {
    BytecodeOSRNode.doOSRFrameTransfer(this, parentFrame, osrFrame); // transfer state into OSR frame
    try {
      return executeFromBCI(osrFrame, target);
    } finally {
      BytecodeOSRNode.doOSRFrameTransfer(this, osrFrame, parentFrame); // transfer state back into parent frame (if needed)
    }
  }

  Object getOSRMetadata() {
    return osrMetadata;
  }

  void setOSRMetadata(Object osrMetadata) {
    this.osrMetadata = osrMetadata;
  }

  @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.MERGE_EXPLODE)
  Object executeFromBCI(VirtualFrame frame, int bci) {
    while (true) {
      int nextBCI;
      switch (bytecode[bci]) {
        case OP1:
          ...
          nextBCI = ...
          ...
        case OP2:
          ...
          nextBCI = ...
          ...
        ...
      }

      if (nextBCI < bci) { // back-edge
        Object result = BytecodeOSRNode.reportOSRBackEdge(this, frame, nextBCI);
        if (result != null) { // OSR was performed
          return result;
        }
      }
      bci = nextBCI;
    }
  }
}
```

A subtle difference with bytecode OSR is that the OSR execution continues past the end of the loop until the end of the call target.
Thus, execution does not need to continue in the interpreter once execution returns from OSR; the result can simply be forwarded to the caller.

## Command-line options
There are two (experimental) options which can be used to configure OSR:
- `engine.OSR`: whether to perform OSR (default: `true`)
- `engine.OSRCompilationThreshold`: the number of loop iterations/back-edges required to trigger OSR compilation (default: `100,000`).

## Debugging
OSR compilation targets are marked with `<OSR>`. These targets can be seen and debugged using standard debugging tools like the compilation log and IGV.
For example, in the compilation log, an OSR entry may look something like:

```
[engine] opt done     BytecodeNode@2d3ca632<OSR>                                  |AST    2|Tier 1|Time   21(  14+8   )ms|Inlined   0Y   0N|IR   161/  344|CodeSize   1234|Addr 0x7f3851f45c10|Src n/a
```

See [Debugging](https://github.com/oracle/graal/blob/master/compiler/docs/Debugging.md) for more details on debugging Graal compilations. 

When debugging issues with bytecode-based OSR, ensure that the metadata field is marked `@CompilationFinal` and the node's `Frame` is explicitly marked non-materializable, otherwise OSR may not work.

