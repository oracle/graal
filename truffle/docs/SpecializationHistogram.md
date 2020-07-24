# Specialization Histogram Tutorial

This document explains how to use the `--engine.SpecializationHistogram` option.

The specialization histogram requires Truffle DSL nodes to be generated in a special way.
So if you use the plain specialization histogram option it will just print the following:

```
$ js --engine.SpecializationHistogram test.js

[engine] Specialization histogram:
No specialization statistics data was collected. Either no node with @Specialization annotations was executed or the interpreter was not compiled with -Atruffle.dsl.GenerateSpecializationStatistics=true e.g as parameter to the javac tool.
```

Lets follow the advice of the error and recompile our interpreter.
For `mx` users this is as simple as:

```
$ mx build -c -A-Atruffle.dsl.GenerateSpecializationStatistics=true
```

After the rebuild the specialization statistics are ready to be use. 
Make sure that your IDE does not recompile in the sources automatically in the meantime.
In this tutorial we use a simple `test.js` script:

```
function test() {
  var array = [42, "", {}, []]

  var globalVar = true;
  for (element in array) {
    globalVar = element;
  }
}
test();
```

Now the specialization statistics need to be enabled, in this example using the Graal.js launcher:

```
js --experimental-options --engine.SpecializationStatistics test.js
```

After the script was executed a histogram for each class will be printed.
The histograms will be ordered by the sum of executions of each node, whereas the most frequently used node class will be printed last.


These are some of the histograms printed when executing `test.js`. (Note the output is likely already outdated)

```
Name                                                                           Instances          Executions     Executions per instance
  JSWriteCurrentFrameSlotNodeGen                                               8 (20%)            18 (11%)        Min=         1 Avg=        2.25 Max          5  MaxNode test.js~5-7:76-128
    doBoolean <boolean>                                                          1 (13%)             1 (6%)         Min=         1 Avg=        1.00 Max          1  MaxNode test.js~4:52-71
    doInt                                                                        0 (0%)              0 (0%)         Min=         0 Avg=        0.00 Max          0  MaxNode  -
    doSafeIntegerInt                                                             0 (0%)              0 (0%)         Min=         0 Avg=        0.00 Max          0  MaxNode  -
    doSafeInteger                                                                0 (0%)              0 (0%)         Min=         0 Avg=        0.00 Max          0  MaxNode  -
    doLong                                                                       0 (0%)              0 (0%)         Min=         0 Avg=        0.00 Max          0  MaxNode  -
    doDouble                                                                     0 (0%)              0 (0%)         Min=         0 Avg=        0.00 Max          0  MaxNode  -
    doObject                                                                     7 (88%)            17 (94%)        Min=         1 Avg=        2.43 Max          5  MaxNode test.js~5-7:76-128
      <DynamicObjectBasic>                                                         4 (57%)             8 (47%)        Min=         1 Avg=        2.00 Max          5  MaxNode test.js~5-7:76-128
      <IteratorRecord>                                                             1 (14%)             1 (6%)         Min=         1 Avg=        1.00 Max          1  MaxNode test.js~1-8:16-130
      <String>                                                                     2 (29%)             8 (47%)        Min=         4 Avg=        4.00 Max          4  MaxNode test.js~5-7:76-128
Name                                                                           Instances          Executions     Executions per instance
  JSReadCurrentFrameSlotNodeGen                                                8 (20%)            25 (16%)        Min=         1 Avg=        3.13 Max          5  MaxNode test.js~5-7:76-128
    doBoolean                                                                    0 (0%)              0 (0%)         Min=         0 Avg=        0.00 Max          0  MaxNode  -
    doInt                                                                        0 (0%)              0 (0%)         Min=         0 Avg=        0.00 Max          0  MaxNode  -
    doDouble                                                                     0 (0%)              0 (0%)         Min=         0 Avg=        0.00 Max          0  MaxNode  -
    doObject <no-args>                                                           8 (100%)           25 (100%)       Min=         1 Avg=        3.13 Max          5  MaxNode test.js~5-7:76-128
    doSafeInteger                                                                0 (0%)              0 (0%)         Min=         0 Avg=        0.00 Max          0  MaxNode  -
```

The histogram groups the lines using the node class, the method name annotated with `@Specialization` and the dynamic types the specialization method was invoked with.
For example in this histogram the node class `JSWriteCurrentFrameSlotNodeGen` was instantiated `8` and executed `18` times.
This is `20%` of the total instances and `11%` of all node executions of the run.
Two specializations were instantiated in this script namely `doBoolean` and `doObject`.
The first specialization was likely triggered by the `var globalVar = true` snippet and the second by `globalVar = element;`.
The `doBoolean` specialization was instantiated and executed only once which accounts to `13%` of all instances and `11%` of all executions of this node class.
Note that multiple specializations can instantiated for a single node instance. 
Lastly the `doObject` specializations was invoked using three different input value combinations: `DynamicObjectBasic`, `IteratorRecord`, `String`.
Similar to specializations we can see the numbers of times per node they were used and how many times there were executed.
For each line we can see minimum, average and maximum execution numbers per instance.
The last column prints the source section of the instance with the maximum executions.


Here are some ideas of what questions you would want to ask these specialization statistics:

1. Is a certain specialization used only rarely and can it be removed/consolidated into a single specialization?
2. Is there a specialization with a very common type combination that could benefit from further specialization?
3. What are common specializations, and does the order match the number of executions? Specializations that are most commonly used should be ordered first in the node class. This may lead to improvements in interpreter performance.
4. Are there unexpected specializations instantiated? If yes, investigate further using the printed source section.
5. Which specializations are instantiated often, and should therefore be optimized for memory footprint?
6. Were there nodes with the name `Uncached` in the profile? The use of uncached nodes should be rare. It can be worth to dig deeper why they were used often.


