# Specialization Histogram

This guide explains how to use the `--engine.SpecializationHistogram` option.

The specialization histogram requires Truffle DSL nodes to be generated in a special way.
So if you use the plain specialization histogram option it will just print the following:

```shell
js --engine.SpecializationHistogram test.js

[engine] Specialization histogram:
No specialization statistics data was collected. Either no node with @Specialization annotations was executed or the interpreter was not compiled with -Atruffle.dsl.GenerateSpecializationStatistics=true e.g as parameter to the javac tool.
```

Lets follow the advice of the error and recompile our interpreter.
For `mx` users this is as simple as:

```shell
mx build -c -A-Atruffle.dsl.GenerateSpecializationStatistics=true
```

After the rebuild the specialization statistics are ready to be use.
Make sure that your IDE does not recompile the sources automatically in the meantime.
In this tutorial we use a simple `test.js` script:

```js
function test() {
  var array = [42, "", {}, []]

  var globalVar = true;
  for (element of array) {
    globalVar = element;
  }
}
test();
```

Now the specialization statistics need to be enabled, in this example using the Graal.js launcher:

```shell
js --experimental-options --engine.SpecializationStatistics test.js
```

After the script was executed a histogram for each class will be printed.
The histograms will be ordered by the sum of executions of each node, whereas the most frequently used node class will be printed last.


These are some of the histograms printed when executing `test.js`. (Note the output is likely already outdated)

```
 -----------------------------------------------------------------------------------------------------------------------------------------------------------------------
| Name                                                                         Instances          Executions     Executions per instance
 -----------------------------------------------------------------------------------------------------------------------------------------------------------------------
| JSWriteCurrentFrameSlotNodeGen                                               8 (17%)            18 (12%)        Min=         1 Avg=        2.25 Max=          5  MaxNode= test.js~5-7:76-128
|   doBoolean <boolean>                                                          1 (13%)             1 (6%)         Min=         1 Avg=        1.00 Max=          1  MaxNode= test.js~4:52-71
|   doInt <int>                                                                  1 (13%)             1 (6%)         Min=         1 Avg=        1.00 Max=          1  MaxNode= test.js~5-7:76-128
|   doSafeIntegerInt                                                             0 (0%)              0 (0%)         Min=         0 Avg=        0.00 Max=          0  MaxNode=  -
|   doSafeInteger                                                                0 (0%)              0 (0%)         Min=         0 Avg=        0.00 Max=          0  MaxNode=  -
|   doLong                                                                       0 (0%)              0 (0%)         Min=         0 Avg=        0.00 Max=          0  MaxNode=  -
|   doDouble                                                                     0 (0%)              0 (0%)         Min=         0 Avg=        0.00 Max=          0  MaxNode=  -
|   doObject                                                                     7 (88%)            16 (89%)        Min=         1 Avg=        2.29 Max=          5  MaxNode= test.js~5-7:76-128
|     <DynamicObjectBasic>                                                         6 (86%)            12 (75%)        Min=         1 Avg=        2.00 Max=          5  MaxNode= test.js~5-7:76-128
|     <IteratorRecord>                                                             1 (14%)             1 (6%)         Min=         1 Avg=        1.00 Max=          1  MaxNode= test.js~1-8:16-130
|     <String>                                                                     2 (29%)             2 (13%)        Min=         1 Avg=        1.00 Max=          1  MaxNode= test.js~5-7:76-128
|     <Integer>                                                                    1 (14%)             1 (6%)         Min=         1 Avg=        1.00 Max=          1  MaxNode= test.js~6:105-123
|   --------------------------------------------------------------------------------------------------------------------------------------------------------------------
|   [doBoolean]                                                                  1 (13%)             1 (6%)         Min=         1 Avg=        1.00 Max=          1  MaxNode= test.js~4:52-71
|   [doInt, doObject]                                                            1 (13%)             4 (22%)        Min=         4 Avg=        4.00 Max=          4  MaxNode= test.js~5-7:76-128
|     doInt                                                                        1 (100%)            1 (25%)        Min=         1 Avg=        1.00 Max=          1  MaxNode= test.js~5-7:76-128
|     doObject                                                                     1 (100%)            3 (75%)        Min=         3 Avg=        3.00 Max=          3  MaxNode= test.js~5-7:76-128
|   [doObject]                                                                   6 (75%)            13 (72%)        Min=         1 Avg=        2.17 Max=          5  MaxNode= test.js~5-7:76-128
 -----------------------------------------------------------------------------------------------------------------------------------------------------------------------
| Name                                                                         Instances          Executions     Executions per instance
 -----------------------------------------------------------------------------------------------------------------------------------------------------------------------
| JSReadCurrentFrameSlotNodeGen                                                8 (17%)            25 (17%)        Min=         1 Avg=        3.13 Max=          5  MaxNode= test.js~5-7:76-128
|   doBoolean                                                                    0 (0%)              0 (0%)         Min=         0 Avg=        0.00 Max=          0  MaxNode=  -
|   doInt <no-args>                                                              1 (13%)             1 (4%)         Min=         1 Avg=        1.00 Max=          1  MaxNode= test.js~5:81-87
|   doDouble                                                                     0 (0%)              0 (0%)         Min=         0 Avg=        0.00 Max=          0  MaxNode=  -
|   doObject <no-args>                                                           8 (100%)           24 (96%)        Min=         1 Avg=        3.00 Max=          5  MaxNode= test.js~5-7:76-128
|   doSafeInteger                                                                0 (0%)              0 (0%)         Min=         0 Avg=        0.00 Max=          0  MaxNode=  -
|   --------------------------------------------------------------------------------------------------------------------------------------------------------------------
|   [doInt, doObject]                                                            1 (13%)             4 (16%)        Min=         4 Avg=        4.00 Max=          4  MaxNode= test.js~5:81-87
|     doInt                                                                        1 (100%)            1 (25%)        Min=         1 Avg=        1.00 Max=          1  MaxNode= test.js~5:81-87
|     doObject                                                                     1 (100%)            3 (75%)        Min=         3 Avg=        3.00 Max=          3  MaxNode= test.js~5:81-87
|   [doObject]                                                                   7 (88%)            21 (84%)        Min=         1 Avg=        3.00 Max=          5  MaxNode= test.js~5-7:76-128
 -----------------------------------------------------------------------------------------------------------------------------------------------------------------------
```

The histogram prints two inner tables for every node class.
The first table groups specialization and dynamic type combination.
For example in this histogram the node class `JSWriteCurrentFrameSlotNodeGen` was instantiated `8` and executed `18` times.
This is `20%` of the total instances and `11%` of all node executions of the run.
Three specializations were instantiated in this script namely `doBoolean`, `doObject` and `doInt`.
The `doBoolean` specialization was instantiated and executed only once which accounts to `13%` of all instances and `6%` of all executions of this node class.
The `doObject` specializations was invoked using three different input value combinations: `DynamicObjectBasic`, `IteratorRecord`, `String`.
Similar to specializations we can see the numbers of times per node they were used and how many times they were executed.
For each line we can see minimum, average and maximum execution numbers per instance.
The last column prints the source section of the instance with the maximum executions.
The second table groups for each combination of specializations that were used by node class.


Here are some ideas of what questions you would want to ask these specialization statistics:

1. Is a certain specialization used only rarely and can it be removed/consolidated into a single specialization?
2. Is there a specialization with a very common type combination that could benefit from further specialization?
3. Which specialization combination is common and could deserve its own specialization? This could indicate common polymorphism in the code that could be investigated.
4. What are common specializations, and does the order match the number of executions? Specializations that are most commonly used should be ordered first in the node class. This may lead to improvements in interpreter performance.
5. Are there unexpected specializations instantiated? If yes, investigate further using the printed source section.
6. Which specializations are instantiated often, and should therefore be optimized for memory footprint?
7. Were there nodes with the name `Uncached` in the profile? The use of uncached nodes should be rare. It can be worth to dig deeper why they were used often.
