# Optimizing Truffle Interpreters

This document is about tools for optimizing or debugging Truffle interpreters
for peak temporal performance.

## Strategy

1. Run with a profiler to sample the application and identify responsible compilation units. Use a sampling delay (`--cpusampler.Delay=MILLISECONDS`) to only profile after warmup. See [`profiling.md`](Profiling.md).

2. Understand what is being compiled and look for deoptimizations. Methods that are listed to run mostly in the interpreter likely have a problem with deoptimization.

3. Simplify the code as much as possible where it still shows the performance problem.

4. Enable performance warnings and list boundary calls.

4. Dump the Graal graph of the responsible compilation unit and look at the phase `After TruffleTier`.
4. Look at the Graal graphs at the phases `After TruffleTier` and `After PartialEscape` and check it is what you'd expect.
   If there are nodes there you don't want to be there, think about how to guard against including them.
   If there are more complex nodes there than you want, think about how to add specialisations that generate simpler code.
   If there are nodes you think should be there in a benchmark that aren't, think about how to make values dynamic so they aren't optimized away.

5. Search for `Invoke` nodes in the Graal IR. `Invoke` nodes that are not representing guest language calls should be specialized away. This may not be always possible, e.g., if the method does I/O.

6. Search for control flow splits (red lines) and investigate whether they result from control flow caused by the guest application or are just artefacts from the language implementation. The latter should be avoided if possible.

7. Search for indirections in linear code (`Load` and `LoadIndexed`) and try to minimise the code. The less code that is on the hot-path the better.

## Truffle compiler options

A full list of the latest expert and internal options can be found in [`options.md`](Options.md)

#### Observing compilations

This section provides an overview over most available command line options to observe compilations.
Note that most options also require the additional `--experimental-options` flag set.


`--engine.TraceCompilation` prints a line each time a method is compiled.

```
[engine] opt done         EqualityConstraint.execute                                  |ASTSize      17/   17 |Time   134( 110+24  )ms |DirectCallNodes I    3/D    1 |GraalNodes   222/  266 |CodeSize          691 |CodeAddress 0x113dd1250 |Source octane-deltablue.js:528
```

`--engine.TraceCompilationDetail` also prints a line when compilation is queued, starts or completes.

```
[engine] opt queued       BinaryConstraint.output                                     |ASTSize      19/   19 |Calls/Thres    1000/    3 |CallsAndLoop/Thres    1000/ 1000 |Source octane-deltablue.js:416
[engine] opt start        BinaryConstraint.output                                     |ASTSize      19/   19 |Calls/Thres    1000/    3 |CallsAndLoop/Thres    1000/ 1000 |Source octane-deltablue.js:416
[engine] opt queued       BinaryConstraint.input                                      |ASTSize      19/   19 |Calls/Thres    1000/    3 |CallsAndLoop/Thres    1000/ 1000 |Source octane-deltablue.js:409
[engine] opt start        BinaryConstraint.input                                      |ASTSize      19/   19 |Calls/Thres    1000/    3 |CallsAndLoop/Thres    1000/ 1000 |Source octane-deltablue.js:409
[engine] opt queued       OrderedCollection.at                                        |ASTSize      15/   15 |Calls/Thres    1000/    3 |CallsAndLoop/Thres    1000/ 1000 |Source octane-deltablue.js:67
... more queues ...
[engine] opt done         BinaryConstraint.output                                     |ASTSize      19/   19 |Time   734( 420+314 )ms |DirectCallNodes I    0/D    0 |GraalNodes   110/  176 |CodeSize          565 |CodeAddress 0x1102cb190 |Source octane-deltablue.js:416
[engine] opt start        OrderedCollection.at                                        |ASTSize      15/   15 |Calls/Thres   29924/    3 |CallsAndLoop/Thres   29924/ 1000 |Source octane-deltablue.js:67
[engine] opt done         BinaryConstraint.input                                      |ASTSize      19/   19 |Time   740( 408+332 )ms |DirectCallNodes I    0/D    0 |GraalNodes   109/  166 |CodeSize          530 |CodeAddress 0x1102e8690 |Source octane-deltablue.js:409
```

`--engine.TraceCompilationAST` prints the Truffle AST for each compilation. 

```
[engine] opt AST          OrderedCollection.size <split-57429b3a>                     |ASTSize      10/   10 |Calls/Thres   10559/    3 |CallsAndLoop/Thres   10559/ 1000
  FunctionRootNode
    body = FunctionBodyNode
      body = DualNode
        left = JSWriteCurrentFrameSlotNodeGen
          rhsNode = JSPrepareThisNodeGen
            operandNode = AccessThisNode
        right = TerminalPositionReturnNode
          expression = PropertyNode
            target = PropertyNode
              target = JSReadCurrentFrameSlotNodeGen
              cache = PropertyGetNode
                cacheNode = ObjectPropertyGetNode
                  receiverCheck = ShapeCheckNode
            cache = PropertyGetNode
              cacheNode = ArrayLengthPropertyGetNode
                receiverCheck = ShapeCheckNode
                arrayLengthRead = ArrayLengthReadNodeGen
```

`--engine.TraceInlining` prints guest-language inlining decisions for each compilation.

```
[engine] inline start     Plan.execute                                                |call diff        0.00 |Recursion Depth      0 |Explore/inline ratio     1.00 |IR Nodes         2704 |Frequency        1.00 |Truffle Callees      5 |Forced          false |Depth               0
[engine] Inlined            Plan.size                                                 |call diff     -203.75 |Recursion Depth      0 |Explore/inline ratio      NaN |IR Nodes          175 |Frequency      101.88 |Truffle Callees      1 |Forced          false |Depth               1
[engine] Inlined              OrderedCollection.size <split-e13c02e>                  |call diff     -101.88 |Recursion Depth      0 |Explore/inline ratio      NaN |IR Nodes          157 |Frequency      101.88 |Truffle Callees      0 |Forced          false |Depth               2
[engine] Inlined            Plan.constraintAt                                         |call diff     -201.75 |Recursion Depth      0 |Explore/inline ratio      NaN |IR Nodes          206 |Frequency      100.88 |Truffle Callees      1 |Forced          false |Depth               1
[engine] Inlined              OrderedCollection.at                                    |call diff     -100.88 |Recursion Depth      0 |Explore/inline ratio      NaN |IR Nodes          232 |Frequency      100.88 |Truffle Callees      0 |Forced          false |Depth               2
[engine] Inlined            ScaleConstraint.execute                                   |call diff       -0.00 |Recursion Depth      0 |Explore/inline ratio      NaN |IR Nodes          855 |Frequency        0.00 |Truffle Callees      0 |Forced          false |Depth               1
[engine] Inlined            EqualityConstraint.execute                                |call diff     -299.63 |Recursion Depth      0 |Explore/inline ratio      NaN |IR Nodes          295 |Frequency       99.88 |Truffle Callees      2 |Forced          false |Depth               1
[engine] Inlined              BinaryConstraint.output <split-1e163df7>                |call diff      -99.88 |Recursion Depth      0 |Explore/inline ratio      NaN |IR Nodes          259 |Frequency       99.88 |Truffle Callees      0 |Forced          false |Depth               2
[engine] Inlined              BinaryConstraint.input <split-2dfade22>                 |call diff      -99.88 |Recursion Depth      0 |Explore/inline ratio      NaN |IR Nodes          259 |Frequency       99.88 |Truffle Callees      0 |Forced          false |Depth               2
[engine] Inlined            EditConstraint.execute                                    |call diff       -1.00 |Recursion Depth      0 |Explore/inline ratio      NaN |IR Nodes           22 |Frequency        1.00 |Truffle Callees      0 |Forced          false |Depth               1
[engine] inline done      Plan.execute                                                |call diff        0.00 |Recursion Depth      0 |Explore/inline ratio     1.00 |IR Nodes         2704 |Frequency        1.00 |Truffle Callees      5 |Forced          false |Depth               0
```

`--engine.TraceSplitting` prints guest-language splitting decisions

```
[engine] split   0-4310d43-1     Strength                                                    |ASTSize       6/    6 |Calls/Thres       2/    3 |CallsAndLoop/Thres       2/ 1000 |SourceSection /Users/christianhumer/graal/4dev/js-benchmarks/octane-deltablue.js~139:4062-4089
[engine] split   1-4b0d79fc-1     Strength                                                    |ASTSize       6/    6 |Calls/Thres       2/    3 |CallsAndLoop/Thres       2/ 1000 |SourceSection /Users/christianhumer/graal/4dev/js-benchmarks/octane-deltablue.js~140:4119-4150
```

`--engine.TraceTransferToInterpreter` prints a stack trace on explicit internal invalidations.

```
[engine] transferToInterpreter at
  BinaryConstraint.output(../../../../4dev/js-benchmarks/octane-deltablue.js:416)
    Constraint.satisfy(../../../../4dev/js-benchmarks/octane-deltablue.js:183)
    Planner.incrementalAdd(../../../../4dev/js-benchmarks/octane-deltablue.js:597) <split-609bcfb6>
    Constraint.addConstraint(../../../../4dev/js-benchmarks/octane-deltablue.js:165) <split-7d94beb9>
    UnaryConstraint(../../../../4dev/js-benchmarks/octane-deltablue.js:219) <split-560348e6>
    Function.prototype.call(<builtin>:1) <split-1df8b5b8>
    EditConstraint(../../../../4dev/js-benchmarks/octane-deltablue.js:315) <split-23202fce>
    ...
  com.oracle.truffle.api.CompilerDirectives.transferToInterpreterAndInvalidate(CompilerDirectives.java:90)
    com.oracle.truffle.js.nodes.access.PropertyCacheNode.deoptimize(PropertyCacheNode.java:1269)
    com.oracle.truffle.js.nodes.access.PropertyGetNode.getValueOrDefault(PropertyGetNode.java:305)
    com.oracle.truffle.js.nodes.access.PropertyGetNode.getValueOrUndefined(PropertyGetNode.java:191)
    com.oracle.truffle.js.nodes.access.PropertyNode.executeWithTarget(PropertyNode.java:153)
    com.oracle.truffle.js.nodes.access.PropertyNode.execute(PropertyNode.java:140)
    ...
```


`--engine.TracePerformanceWarnings=(call|instanceof|store|all)` prints code which may not be ideal for performance. The `call` enables warinings when partial evaluation cannot inline the virtual runtime call. The `instanceof` enables warninigs when partial evaluation cannot resolve virtual instanceof to an exact type. The `store` enables warninigs when store location argument is not a partial evaluation constant.

```
[engine] perf warn        ScaleConstraint.execute                                     |Partial evaluation could not inline the virtual runtime call Virtual to HotSpotMethod<ConditionProfile.profile(boolean)> (167|MethodCallTarget).
  Approximated stack trace for [167 | MethodCallTarget:    at com.oracle.truffle.js.nodes.control.IfNode.execute(IfNode.java:158)
    at com.oracle.truffle.js.nodes.binary.DualNode.execute(DualNode.java:125)
    at com.oracle.truffle.js.nodes.function.FunctionBodyNode.execute(FunctionBodyNode.java:73)
    at com.oracle.truffle.js.nodes.function.FunctionRootNode.executeInRealm(FunctionRootNode.java:147)
    at com.oracle.truffle.js.runtime.JavaScriptRealmBoundaryRootNode.execute(JavaScriptRealmBoundaryRootNode.java:93)
    at org.graalvm.compiler.truffle.runtime.OptimizedCallTarget.executeRootNode(OptimizedCallTarget.java:503)
    at org.graalvm.compiler.truffle.runtime.OptimizedCallTarget.profiledPERoot(OptimizedCallTarget.java:480)
```

`--engine.CompilationStatistics` prints at the end of the process statistics on compilations

```
[engine] Truffle runtime statistics for engine 1
  Compilations                                      : 170
    Success                                         : 166
    Temporary Bailouts                              : 4
      org.graalvm.compiler.core.common.CancellationBailoutException: Compilation cancelled.: 4
    Permanent Bailouts                              : 0
    Failed                                          : 0
    Interrupted                                     : 0
  Invalidated                                       : 0
  Queues                                            : 479
  Dequeues                                          : 315
      Target inlined into only caller               : 314
      Split call node                               : 1
  Splits                                            : 450
  Compilation Accuracy                              : 1.000000
  Queue Accuracy                                    : 0.342380
  Compilation Utilization                           : 0.984846
  Remaining Compilation Queue                       : 0
  Time to queue                                     : count= 479, sum= 2595151, min=      39, average=     5417.85, max=   19993 (milliseconds), maxTarget=Array
  Time waiting in queue                             : count= 170, sum=  681895, min=       0, average=     4011.15, max=    8238 (milliseconds), maxTarget=EditConstraint.isInput
  Time for compilation                              : count= 170, sum=   39357, min=       5, average=      231.51, max=    2571 (milliseconds), maxTarget=change
    Truffle Tier                                    : count= 166, sum=   23654, min=       4, average=      142.50, max=    1412 (milliseconds), maxTarget=change
    Graal Tier                                      : count= 166, sum=   13096, min=       0, average=       78.89, max=    1002 (milliseconds), maxTarget=change
    Code Installation                               : count= 166, sum=    1865, min=       0, average=       11.24, max=     190 (milliseconds), maxTarget=deltaBlue
  Graal node count                                  :
    After Truffle Tier                              : count= 168, sum=  146554, min=      13, average=      872.35, max=    7747, maxTarget=change
    After Graal Tier                                : count= 166, sum=  213434, min=       3, average=     1285.75, max=   15430, maxTarget=MeasureDefault
  Graal compilation result                          :
    Code size                                       : count= 166, sum=  874667, min=      70, average=     5269.08, max=   64913, maxTarget=deltaBlue
    Total frame size                                : count= 166, sum=   25328, min=      16, average=      152.58, max=    1248, maxTarget=MeasureDefault
    Exception handlers                              : count= 166, sum=     199, min=       0, average=        1.20, max=      41, maxTarget=MeasureDefault
    Infopoints                                      : count= 166, sum=    6414, min=       3, average=       38.64, max=     517, maxTarget=MeasureDefault
      CALL                                          : count= 166, sum=    4125, min=       3, average=       24.85, max=     357, maxTarget=MeasureDefault
      IMPLICIT_EXCEPTION                            : count= 166, sum=    2164, min=       0, average=       13.04, max=     156, maxTarget=MeasureDefault
      SAFEPOINT                                     : count= 166, sum=     125, min=       0, average=        0.75, max=       9, maxTarget=change
    Marks                                           : count= 166, sum=    1385, min=       5, average=        8.34, max=      52, maxTarget=MeasureDefault
    Data references                                 : count= 166, sum=   10219, min=       1, average=       61.56, max=     758, maxTarget=MeasureDefault
```


`--engine.CompilationStatisticDetails` prints histogram information on individual Graal nodes in addition to the previous compilation statistics.

```
  Graal nodes after Truffle tier                    :
      FrameState                                    : count= 168, sum=   35502, min=       1, average=      211.32, max=    2048, maxTarget=deltaBlue
      FixedGuardNode                                : count= 168, sum=   18939, min=       0, average=      112.73, max=    1048, maxTarget=change
      LoadFieldNode                                 : count= 168, sum=   14432, min=       0, average=       85.90, max=     814, maxTarget=EditConstraint
      ...
  Graal nodes after Graal tier                      :
      BeginNode                                     : count= 166, sum=   33333, min=       0, average=      200.80, max=    2110, maxTarget=change
      FrameState                                    : count= 166, sum=   30591, min=       0, average=      184.28, max=    2393, maxTarget=MeasureDefault
      AMD64AddressNode                              : count= 166, sum=   20072, min=       0, average=      120.92, max=    1960, maxTarget=MeasureDefault
      ...
```

`--engine.PrintExpansionHistogram` prints at the end of each compilation a histogram of AST interpreter method calls. 

```
[engine] Expansion Histograms:
Graal Nodes Histogram: Number of non-trival Graal nodes created for a method during partial evaluation.
 Expansions  |Nodes   Sum   Min   Max      Avg | Method Name
           6 |         24     4     4     4.00 | DynamicObject.getShape()
           1 |         20    20    20    20.00 | JSPrepareThisNodeGen.execute_generic3(VirtualFrame, int)
           2 |         18     9     9     9.00 | PropertyCacheNode$ShapeCheckNode.accept(Object)
           2 |         18     9     9     9.00 | PropertyCacheNode$PrototypeChainShapeCheckNode.accept(Object)
           3 |         12     4     4     4.00 | PropertyGetNode.getValueOrDefault(Object, Object, Object)
           5 |         10     2     2     2.00 | JSObject.isDynamicObject(Object)
           2 |         10     5     5     5.00 | OptimizedDirectCallNode.call(Object[])
           4 |          8     2     2     2.00 | ShapeImpl.check(DynamicObject)
           ...
Graal Invoke Histogram: Number of invokes created for a method during partial evaluation.
 Expansions  |Nodes   Sum   Min   Max      Avg | Method Name
           2 |          2     1     1     1.00 | OptimizedDirectCallNode.call(Object[])
```

`--engine.InstrumentBoundaries` prints at the end of the process information about runtime calls (`@TruffleBoundary`) made from compiled code. These cause objects to escape, are black-boxes to further optimization, and should generally be minimised. 
Also see [`BranchInstrumentation.md`](BranchInstrumentation.md) for more details about instrumenting branches and boundaries.

```
Execution profile (sorted by hotness)
=====================================
  0: *******************************************************************************
  1:

com.oracle.truffle.js.nodes.binary.JSAddNode.doStringInt(JSAddNode.java:177) [bci: 2]
[0] count = 22525269

com.oracle.truffle.js.builtins.ConstructorBuiltins$ConstructDateNode.constructDateZero(ConstructorBuiltins.java:837) [bci: 6]
[1] count = 69510
```

`--engine.InstrumentBranches` prints at the end of the process information of branch usage in compiled code. 

```
Execution profile (sorted by hotness)
=====================================
  2: ***************
  1: **************
  5: *************
  4: ************
  3: *********
 10: **
  8: *
  9: *
 14: *
 ...

com.oracle.truffle.js.nodes.access.PropertyGetNode.getValueOrDefault(PropertyGetNode.java:301) [bci: 55]
[2] state = BOTH(if=36486564#, else=44603498#)

com.oracle.truffle.js.nodes.control.IfNode.execute(IfNode.java:158) [bci: 12]
[1] state = BOTH(if=72572593#, else=1305851#)

com.oracle.truffle.js.nodes.function.JSFunctionCallNode.executeCall(JSFunctionCallNode.java:233) [bci: 18]
[5] state = BOTH(if=38703322#, else=32550439#)

com.oracle.truffle.js.nodes.access.PropertyCacheNode$PrototypeShapeCheckNode.accept(PropertyCacheNode.java:364) [bci: 4]
[4] state = ELSE(if=0#, else=64094316#)

com.oracle.truffle.js.nodes.control.WhileNode$WhileDoRepeatingNode.executeRepeating(WhileNode.java:230) [bci: 5]
[3] state = BOTH(if=44392142#, else=7096299#)
...
```

`--vm.XX:+TraceDeoptimization` prints deoptimization events, whether code compiled by Truffle or conventional compilers. The output of HotSpot and native images may vary for this flag.

```
Uncommon trap   bci=9 pc=0x00000001097f2235, relative_pc=501, method=com.oracle.truffle.js.nodes.access.PropertyNode.executeInt(Ljava/lang/Object;Ljava/lang/Object;)I, debug_id=0
Uncommon trap occurred in org.graalvm.compiler.truffle.runtime.OptimizedCallTarget::profiledPERoot compiler=JVMCI compile_id=2686 (JVMCI: installed code name=BinaryConstraint.output#2)  (@0x00000001097f2235) thread=5891 reason=transfer_to_interpreter action=reinterpret unloaded_class_index=-1 debug_id=0
```

`--vm.XX:+TraceDeoptimizationDetails` prints more information (only available for native images).

```
[Deoptimization initiated
    name: BinaryConstraint.output
    sp: 0x7ffee7324d90  ip: 0x1126c51a8
    reason: TransferToInterpreter  action: InvalidateReprofile
    debugId: 3  speculation: jdk.vm.ci.meta.SpeculationLog$NoSpeculationReason@10f942aa0
[Deoptimization of frame
    name: BinaryConstraint.output
    sp: 0x7ffee7324d90  ip: 0x1126c51a8
    stack trace where execution continues:
        at org.graalvm.compiler.truffle.runtime.OptimizedCallTarget.profiledPERoot(OptimizedCallTarget.java:475) bci 0  return address 0x10aab9e5e
            org.graalvm.compiler.truffle.runtime.OptimizedCallTarget.profiledPERoot(OptimizedCallTarget.java:475)
            bci: 0  deoptMethodOffset: 35524160  deoptMethod: 0x10aab9e40  return address: 0x10aab9e5e  offset: 0
            slot 0  kind: Object  value: com.oracle.svm.truffle.api.SubstrateOptimizedCallTarget@0x112cbbaa0  offset: 64
            slot 1  kind: Object  value: [Ljava.lang.Object;@0x1144a7db0  offset: 56
]
]
```

#### Controlling what is compiled

To make best use of the former options, limit what is compiled to the methods that you are interested in.

`--engine.CompileOnly=foo` restricts compilation to methods with `foo` in their name. Use this in combination with returning a value or taking parameters to avoid code being compiled away.

`--engine.CompileImmediately` compiles methods as soon as they are run.

`--engine.BackgroundCompilation=false` compiles synchronously, which can simplify things.

`--engine.Inlining=false` disables inlining which can make code easier to understand.

`--engine.OSR=false` disables on-stack-replacement (compilation of the bodies of `while` loops for example) which can make code easier to understand.

`--engine.Compilation=false` turns off Truffle compilation all together.


## Ideal Graph Visualizer

IGV, the *Ideal Graph Visualizer*, is a tool to understand Truffle ASTs and
Graal graphs. It's available from
https://www.oracle.com/technetwork/graalvm/downloads/index.html.

Typical usage is to run with `--vm.Dgraal.Dump=Truffle:1 --vm.Dgraal.PrintGraph=Network`,
which will show you Truffle ASTs, guest-language call graphs, and the Graal
graphs as they leave the Truffle phase.
If the `-Dgraal.PrintGraph=Network` flag is omitted then the dump file are placed in the `graal_dumps` directory, which you should then open in IGV.

Use `--vm.Dgraal.Dump=Truffle:2` to dump Graal graphs between each compiler phase.

## C1 Visualizer

The C1 Visualizer, is a tool to understand the LIR, register allocation, and
code generation stages of Graal. It's available from
http://lafo.ssw.uni-linz.ac.at/c1visualizer/.

Typical usage is `--vm.Dgraal.Dump=:3`. Files are put into a `graal_dumps`
directory which you should then open in the C1 Visualizer.

## Disassembler

`--vm.XX:+UnlockDiagnosticVMOptions --vm.XX:+PrintAssembly` prints assembly
code. You will need to install `hsdis` using `mx hsdis` in `graal/compiler`,
or manually into the current directory from
https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/hsdis/intel/.

Combine with `--vm.XX:TieredStopAtLevel=0` to disable compilation of runtime
routines so that it's easier to find your guest-language method.

Note that you can also look at assembly code in the C1 Visualizer.
