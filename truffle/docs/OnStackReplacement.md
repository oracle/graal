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
The runtime tracks the number of times a `LoopNode` (created using `TruffleRuntime.createLoopNode(RepeatingNode)`) executes in the interpreter.
Once the loop iterations exceed a threshold, the runtime considers the loop "hot", and it will transparently compile the loop, poll for completion, and then call the compiled OSR target.
The OSR target uses the same `Frame` used by the interpreter.
When the loop exits in the OSR execution, it returns to the interpreted execution, which forwards the result.

See the `LoopNode` [javadoc](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/nodes/LoopNode.html) for more details.

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
Thus, Truffle's bytecode OSR is designed around back-edges and the destination of those edges (which often correspond to loop headers).

To make use of Truffle's bytecode OSR, a language's dispatch node should implement the `BytecodeOSRNode` interface.
This interface requires (at minimum) three method implementations:

- `executeOSR(osrFrame, target, interpreterState)`: This method dispatches execution to the given `target` (i.e., bytecode index) using `osrFrame` as the current program state. The `interpreterState` object can pass any additional interpreter state needed to resume execution.
- `getOSRMetadata()` and `setOSRMetadata(osrMetadata)`: These methods proxy accesses to a field declared on the class. The runtime will use these accessors to maintain state related to OSR compilation (e.g., back-edge counts). The field should be annotated `@CompilationFinal`.

In the main dispatch loop, when the language hits a back-edge, it should invoke the provided `BytecodeOSRNode.pollOSRBackEdge(osrNode)` method to notify the runtime of the back-edge.
If the runtime deems the node eligible for OSR compilation, this method returns `true`.

If (and only if) `pollOSRBackEdge` returns `true`, the language can call `BytecodeOSRNode.tryOSR(osrNode, target, interpreterState, beforeTransfer, parentFrame)` to attempt OSR.
This method will request compilation starting from `target`, and once compiled code is available, a subsequent call can transparently invoke the compiled code and return the computed result.
We will discuss the `interpreterState` and `beforeTransfer` parameters shortly.

The example above can be refactored to support OSR as follows:

```java
class BytecodeDispatchNode extends Node implements BytecodeOSRNode {
  @CompilationFinal byte[] bytecode;
  @CompilationFinal private Object osrMetadata;

  ...

  Object execute(VirtualFrame frame) {
    return executeFromBCI(frame, 0);
  }

  Object executeOSR(VirtualFrame osrFrame, int target, Object interpreterState) {
    return executeFromBCI(osrFrame, target);
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
        if (BytecodeOSRNode.pollOSRBackEdge(this)) { // OSR can be tried
          Object result = BytecodeOSRNode.tryOSR(this, nextBCI, null, null, frame);
          if (result != null) { // OSR was performed
            return result;
          }
        }
      }
      bci = nextBCI;
    }
  }
}
```

A subtle difference with bytecode OSR is that the OSR execution continues past the end of the loop until the end of the call target.
Thus, execution does not need to continue in the interpreter once execution returns from OSR; the result can simply be forwarded to the caller.

The `interpreterState` parameter to `tryOSR` can contain any additional interpreter state required for execution.
This state is passed to `executeOSR` and can be used to resume execution.
For example, if an interpreter uses a data pointer to manage reads/writes, and it is unique for each `target`, this pointer can be passed in `interpreterState`.
It will be visible to the compiler and used in partial evaluation.

The `beforeTransfer` parameter to `tryOSR` is an optional callback which will be invoked before performing OSR.
Since `tryOSR` may or may not perform OSR, this parameter is a way to perform any actions before transferring to OSR code.
For example, a language may pass a callback to send an instrumentation event before jumping to OSR code.

The `BytecodeOSRNode` interface also contains a few hook methods whose default implementations can be overridden:

- `copyIntoOSRFrame(osrFrame, parentFrame, target)` and `restoreParentFrame(osrFrame, parentFrame)`: Reusing the interpreted `Frame` inside OSR code is not optimal, because it escapes the OSR call target and prevents scalar replacement (for background on scalar replacement, see [this paper](https://dl.acm.org/doi/10.1145/2581122.2544157)).
When possible, Truffle will use `copyIntoOSRFrame` to copy the interpreted state (`parentFrame`) into the OSR `Frame` (`osrFrame`), and `restoreParentFrame` to copy state back into the parent `Frame` afterwards.
By default, both hooks copy each slot between the source and destination frames, but this can be overridden for finer control (e.g., to only copy over live variables).
If overridden, these methods should be written carefully to support scalar replacement.
- `prepareOSR(target)`: This hook gets called before compiling an OSR target.
It can be used to force any initialization to happen before compilation.
For example, if a field can only be initialized in the interpreter, `prepareOSR` can ensure it is initialized, so that OSR code does not deoptimize when trying to access it.

Bytecode-based OSR can be tricky to implement. Some debugging tips:

- Ensure that the metadata field is marked `@CompilationFinal`.
- If a `Frame` with a given `FrameDescriptor` has been materialized before, Truffle will reuse the interpreter `Frame` instead of copying (if copying is used, any existing materialized `Frame` could get out of sync with the OSR `Frame`).
- It is helpful to trace compilation and deoptimization logs to identify any initialization work which could be done in `prepareOSR`.
- Inspecting the compiled OSR targets in IGV can be useful to ensure the copying hooks interact well with partial evaluation.

See the `BytecodeOSRNode` [javadoc](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/nodes/BytecodeOSRNode.html) for more details.

## Command-line options
There are two (experimental) options which can be used to configure OSR:
- `engine.OSR`: whether to perform OSR (default: `true`)
- `engine.OSRCompilationThreshold`: the number of loop iterations/back-edges required to trigger OSR compilation (default: `100,352`).

## Debugging
OSR compilation targets are marked with `<OSR>` (or `<OSR@n>` where `n` is the dispatch target, in the case of bytecode OSR).
These targets can be seen and debugged using standard debugging tools like the compilation log and IGV.
For example, in the compilation log, a bytecode OSR entry may look something like:

```
[engine] opt done     BytecodeNode@2d3ca632<OSR@42>                               |AST    2|Tier 1|Time   21(  14+8   )ms|Inlined   0Y   0N|IR   161/  344|CodeSize   1234|Addr 0x7f3851f45c10|Src n/a
```

See [Debugging](https://github.com/oracle/graal/blob/master/compiler/docs/Debugging.md) for more details on debugging Graal compilations.
