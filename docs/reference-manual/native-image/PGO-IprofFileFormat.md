---
layout: docs
toc_group: pgo
link_title: The iprof File Format
permalink: /reference-manual/native-image/optimizations-and-performance/PGO/iprof-file-format/
---

# The _iprof_ File Format

> Note: This document assumes that the reader is familiar with [GraalVM Profile-Guided Optimization](PGO.md).

To build an optimized native image using Profile-Guided Optimization (PGO),
it is necessary to provide the `native-image` tool with profiling data,
gathered by executing workloads on an instrumented image.
This profiling information is stored as a JSON object in a file with the .iprof extension.
This document outlines the structure and semantics of the _iprof_ file format.

## Structure

The full schema of the JSON format used for _iprof_ files can be found in the [iprof-v1.1.0.schema.json](https://github.com/oracle/graal/blob/master/docs/reference-manual/native-image/assets/iprof-v1.1.0.schema.json) document.
This JSON schema fully defines the _iprof_ file format and can be used to validate the structure of an arbitrary _iprof_ file.

A minimal valid _iprof_ file consists of a JSON object containing 3 fields: `types`, `methods` and `version`.
The following is a minimal valid _iprof_ file for the current version (`1.1.0`).

```json
{
  "version": "1.1.0",
  "types": [],
  "methods": []
}
```

In addition to these fields, the _iprof_ file may optionally contain others that provide information on various run-time profiles.
The following is an example of a fully populated _iprof_ file (version `1.1.0`) with the actual content of each of the fields replaced with `...`.

```json
{
  "version": "1.1.0",
  "types": [...],
  "methods": [...],
  "monitorProfiles": [...],
  "virtualInvokeProfiles": [...],
  "callCountProfiles": [...],
  "conditionalProfiles": [...],
  "samplingProfiles": [...],
  "instanceofProfiles": [...],
}
```

The subsequent sections of this document provide a motivating example and describe each of the fields of the _iprof_ file in more detail.

## Motivating Example

Consider the following Java program that calculates and prints the first 10 Fibonacci numbers.

```java
import java.io.*;

public class Fib {

    private int n;

    Fib(int n) {
        this.n = n;
    }

    synchronized void fibonacci() {
        int num1 = 0, num2 = 1;

        for (int i = 0; i < n; i++) {
            try {
                Thread.sleep(10);
            } catch (Exception e) {
                // ignored
            }
            // Print the number
            System.out.print(num1 + " ");

            // Swap
            int num3 = num2 + num1;
            num1 = num2;
            num2 = num3;
        }
    }

    public static void main(String args[])
    {
        new Fib(10).fibonacci();
    }
}
```

This application will be used as an example to explain the structure and semantics of the _iprof_ file.
To generate an _iprof_ file from this application, save it as _Fib.java_ and execute the next commands one by one:

```bash
javac Fib.java
```
```bash
native-image --pgo-instrument -cp . Fib
```
```bash
./fib
```

After the termination of `fib`, there should be a `default.iprof` file in the working directory.

> Note: The exact values shown throughout this document will likely be different in your run,
so understanding the semantics of the values is required if you try to confirm the claims made throughout the document.

## Version

This section describes the version of the _iprof_ file format.
The _iprof_ format uses a semantic versioning scheme (ie. `major.minor.patch`) to ensure any consumers of the _iprof_ file can know which information to expect and in which format.
The major version is updated for breaking changes (for example, a new way of encoding the information),
minor for non-breaking ones (for example, adding a new optional field in the top-level JSON object),
and the patch version is updated for minor fixes that should not impact any client.
The current version of the _iprof_ file format is `1.1.0`, which can be seen in the _iprof_ file from the example application.

```json
...
    "version": "1.1.0",
...
```

## Types

This entry in the _iprof_ file contains information about all the types which are required to understand the profile.
This includes, but is not limited to, primitive types, types that declare methods that were profiled,
as well as any type mentioned in the signatures of those methods.

The `types` field in an _iprof_ file is a JSON array of objects,
where each element of the array represents one type.

Each type is uniquely identified with their fully qualified name which is stored in the `name` field of the type object.
The _iprof_ format relies on the user to not use _iprof_ files out of context,
for example, gathering profiles on one application and applying them to another that has fully different types that share fully qualified names.
Also, each type in this section is identified with a unique ID - an integer value.
This ID is specific to one _iprof_ file, meaning that, for example,
a type with an ID of 3 in one _iprof_ file can be completely differ to a type with an ID of 3 in another _iprof_ file.

These IDs are used throughout the _iprof_ file whenever there is a need to reference a type
(for example, the return type of a method, see the [Methods](#Methods) section).
This is done to reduce the footprint of the _iprof_ file,
as referring to the fully qualified name of a type every time would dramatically increase its size.

See below a selection of the values from the types array of the Fibonacci example _iprof_ file.

```json
...
    "types": [
        {
            "id": 0,
            "name": "boolean"
        },
        {
            "id": 1,
            "name": "byte"
        },
        {
            "id": 2,
            "name": "short"
        },
...
        {
            "id": 8,
            "name": "void"
        },
        {
            "id": 9,
            "name": "java.lang.Object"
        },
        {
            "id": 10,
            "name": "Fib"
        },
...
        {
            "id": 629,
            "name": "java.lang.System"
        },
...
        {
            "id": 4823,
            "name": "[Ljava.lang.String;"
        },
...
    ]
...
```

Each entry is comprised of two components explained before: `id` and `name`.
Primitive types (for example, `boolean`, `byte`, `short`,
the `Fib` class declared in the motivating example,
as well as any other types used in the example
(for example, `java.lang.System` is used to call the `print` method)
are all present in the list.

> Note: Only a selection of types is shown here because, despite our motivating example being very small,
the _iprof_ file contains a total of 5927 types, mostly from the JDK.

## Methods

This entry in the _iprof_ file encompasses information about all the methods which are required to understand the profile.
This includes, but is not limited to, all the methods that were instrumented during the instrumentation build of the application.
It can include methods that were not instrumented as well,
for example, if profiles are usually gathered through sampling rather than instrumentation.

As with types, the methods are (within one _iprof_ file) uniquely identified with an integer ID,
and this ID is used throughout the _iprof_ file to refer to the method.
Unlike types, they cannot be globally identified by just their name, which is also stored in the _iprof_ file.
For this reason, the _iprof_ file also stores the method's signature information.
This information is stored in the `signature` field of the method object and is modeled as an array of integers.
Each of these integer values is an ID of a type that must be present in the `types` entry of the _iprof_ file.
The order of values in this array is significant:
the first value is the type that declares the method,
the second value is the return type of the method, and the remaining values are in-order parameter types of the method.
Note that the receiver type is not a part of the signature.

Consider this selection of methods from the example application _iprof_ file:

```json
    "methods": [
...
        {
            "id": 19547,
            "name": "main",
            "signature": [
                10,
                8,
                4823
            ]
        },
...
        {
            "id": 19551,
            "name": "fibonacci",
            "signature": [
                10,
                8
            ]
        },
    ]
...
```

Each method object is comprised of three components: `id`, `name` and, `signature`.
The method with the name `main` has an ID of `19547`.
The values in the `signature` field are `10`, `8`, and `4823`.
This leads to the conclusion that the `main` method was declared in a type with an ID of `10`,
and checking the example given in the Types section, you see that it is indeed the `Fib` class.
The second value identifies the return value of the method, which is `void` (with an ID of `8`).
The final value (`4823`) is the ID of the type of `main`'s single parameter - an array of `java.lang.String`.

## Call-Count Profiles

This section describes arguably the simplest of the profiles in the _iprof_ file - the call-count profiles.
These profiles hold information about how many times a method was executed in all inlining contexts.
This means that the _iprof_ file contains a separate count not just for each instrumented method,
but also for each case where the method in question was inlined into another method.
This inlining information is called "partial calling context" or just "context",
and understanding this concept is vital for understanding how much of the data in the _iprof_ file is stored.

### Partial Calling Contexts

The partial calling context describes several levels of caller methods to a particular location in the code,
and a different profile can be assigned to each partial calling context.
The length of the partial calling context can be chosen arbitrarily,
and it is also possible to always specify a single code location without the callers
(i.e. to always use a context-insensitive code location).

These contexts identify a particular location in the code so that the related profile can be applied to the correct location.
At a high level, a context is just an ordered list of methods and bytecode indexes (BCIs)
that signify that the profile is related to method `a` on BCI `x` which was inlined into method `b` and the invoke was on BCI `y`,
and so on.

Consider the following example Java program, especially the call graph of the program.

```java
public class EvenOrOddLength {

    public static void main(String[] args) {
        printEvenOrOdd(args[0]);
    }

    private static void printEvenOrOdd(String s) {
        if (s.length() % 2 == 0) {
            printEven();
        } else {
            printOdd();
        }
    }

    private static void printEven() {
        print("even");
    }

    private static void printOdd() {
        print("odd");
    }

    private static void print(String s) {
        System.out.println(s);
    }
}
```

This program has the following incomplete call graph,
where the boxes are methods (with their name and ID as per the _iprof_ file)
and they are connected by labeled arrows representing a "calls on BCI" relationship.

```

             BCI 2    +----------+     BCI 9
          +-----------| printEven|<-----------+
          |           |   ID 3   |            |
          V           +----------+            |
     +-------+                        +----------------+   BCI 3   +------+
     | print |                        | printEvenOrOdd |<----------| main |
     | ID 5  |                        |    ID 2        |           | ID 1 |
     +-------+                        +----------------+           +------+
          ^                                   |
          |           +---------+             |
          +---------- | printOdd|<------------+
             BCI 2    |   ID 4  |    BCI 15
                      +---------+
```

The simplest example partial context is the beginning of a method which was not inlined.
Note that this does not mean the method was *never* inlined - only that in this context it serves as a compilation root.
This information is stored as a pair of integers separated by a `:`.
The first of these two integers is the method ID (as discussed before) and the second one is the BCI.
Since the example is about the very start of the method, the BCI will be 0.
In this example application, an example of such beginning-of-single-method partial contexts would be `main` at BCI 0, or `1:0` in the notation (ID:BCI).

If additional locations within a single-method partial context need to be identified,
you can have a partial context like `1:3`, which indicates the location at BCI 3 of `main`.
The call graph shows that this context corresponds to the invocation of `printEvenOrOdd`.

Now consider a context where a method was inlined into another one.
Let's assume that, during compilation of this example application, the compilation starts at `main`.
Assume also, that the inliner decides to inline the call to `printEvenOrOdd` into `main` (at BCI 3).
The compilation unit superimposed over the call graph looks as follows.

```

             BCI 2    +----------+     BCI 9
          +-----------| printEven|<-----------+
          |           |   ID 3   |            |
          V           +----------+ +----------|-----------------------------+
     +-------+                     |  +----------------+   BCI 3   +------+ |
     | print |                     |  | printEvenOrOdd |<----------| main | |
     | ID 5  |                     |  |    ID 2        |           | ID 1 | |
     +-------+                     |  +----------------+           +------+ |
          ^                        +----------|-----------------------------+
          |           +---------+             |
          +---------- | printOdd|<------------+
             BCI 2    |   ID 4  |    BCI 15
                      +---------+
```

It is now required to identify the location which can be described as "beginning of `printEvenOrOdd` when inlined into `main` at BCI 3".
The context would start the same as in the previous example - the ID of the method (2 for `printEvenOrOdd`),
followed by `:` and the BCI (which is 0 for the beginning of a method).
But, it is also necessary to encode the additional context information - the fact that `printEvenOrOdd` was inlined into `main` at BCI 3.
To do so, the context appends the `<` character and then appends the additional context.
This resulting context is written down as `2:0<1:3` - method with id 2 at BCI 0, inlined into method with id 1 at BCI 3.
Similarly, the call to `printEven` (which is on BCI 9 in `printEvenOrOdd`) from this compilation unit can be written down as `2:9<1:3`.

Let's extend this compilation unit to also include a few more methods:
the `print` method inlined into `printEven` at BCI 3, which is inlined into `printEvenOrOdd` at BCI 9, which is inlined into `main` on BCI 3.
The extended compilation unit is presented in the following graph.

```
   +------------------------------------------------------------------------+
   |         BCI 2    +----------+     BCI 9                                |
   |      +-----------| printEven|<-----------                              |
   |      |           |   ID 3   |            |                             |
   |      V           +----------+            |                             |
   | +-------+                        +----------------+   BCI 3   +------+ |
   | | print |                        | printEvenOrOdd |<----------| main | |
   | | ID 5  |                        |    ID 2        |           | ID 1 | |
   | +-------+                        +----------------+           +------+ |
   +------^-----------------------------------|-----------------------------+
          |           +---------+             |
          +---------- | printOdd|<------------+
             BCI 2    |   ID 4  |    BCI 15
                      +---------+
```

Several partial contexts can now be written down rather concisely, which are very cumbersome to write down in natural language.
Consider the `5:0<3:2<2:2<1:3` partial context.
This is read as "the beginning of `print`, inlined into `printEven` on BCI2, which is inlined into `printEvenOrOdd` at BCI 9,
which is inlined into `main` at BCI 3".
These partial contexts can be arbitrarily long, depending on the inlining decisions that the compiler made during the build of the instrumented image.

Note that this compilation unit does not include `printOdd`.
Now assume `printOdd` is a compilation root and inlined `print` on BCI 2 into it.
Both compilation units superimposed over the call graph look as follows.


```
   +------------------------------------------------------------------------+
   |         BCI 2    +----------+     BCI 9                                |
   |      +-----------| printEven|<-----------                              |
   |      |           |   ID 3   |            |                             |
+--|------V------+    +----------+            |                             |
|  | +-------+   |                    +----------------+   BCI 3   +------+ |
|  | | print |   +-----------------+  | printEvenOrOdd |<----------| main | |
|  | | ID 5  |                     |  |    ID 2        |           | ID 1 | |
|  | +-------+                     |  +----------------+           +------+ |
|  +------^-----------------------------------|-----------------------------+
|         |           +---------+  |          |
|         +---------- | printOdd|<------------+
|            BCI 2    |   ID 4  |  | BCI 15
|                     +---------+  |
+----------------------------------+
```

This will result in two distinct partial profiles for the "beginning of `print`":
One with the context  (`5:0<3:2<2:2<1:3`) shown before, and another with `printOdd` as the rightmost entry in the partial context (`5:0<4:2`).
Note that, if `print` was also compiled as a compilation root (for example, if it was called from another point in the code and wasn't inlined there),
there would be yet another partial context for the begging of `print` which would be simply `5:0`.

### Storing Call Count Profiles

This entry in the _iprof_ file is an array of objects where each object contains a context (stored in a `ctx` field of the object)
as well as the actual numeric values of the profile (stored in the `records` field of the object).
In the case of call-count profiles the only numeric value stored is the number of times the method (at the start of the context, with BCI 0)
was executed in that context.
This is modeled as an array of integers with a single value.

Consider the following example call-count profiles from the first application example.

```json
"callCountProfiles": [
...
        {
            "ctx": "19551:0",
            "records": [
                1
            ]
        },
...
        {
            "ctx": "4669:0<19551:34",
            "records": [
                10
            ]
        },
...
]
```

The first shown object indicates that a method with the ID `19551` was executed only once in that context.
Looking up the method with that ID in the `methods` field of the _iprof_ file shows that it is the `fibonacci` method of the `Fib` class.
This method was indeed executed only once during the run, and was, by chance, not inlined into its only caller (`main`).

The second object shows that a method with the ID `4669` was inlined into `fibonacci` and that the call was on BCI 34.
That method was executed 10 times in that context.
Looking further in the _iprof_ file it can be seen that this is in fact the `java.io.PrintStream#print` method called through
`System.out` which was indeed executed 10 times in that context.
Confirming this is left as an exercise to the reader.

## Conditional Profiles

Conditional profiles contain information about the behavior of conditionals (i.e. branches) in the code.
This includes `if` and `switch` statements as well as all loops,
since they are ultimately bound by a conditional statement.
The profile information is essentially how many times each branch of a conditional statement was taken.

The conditional profiles are stored in a very similar manner to call-count profiles -
an array of objects with a `ctx` and `records` field, whose values are a string and an array of integers respectively.
It is recommended to understand the information in the [Call-Count Profiles](#call-count-profiles) section,
especially the Partial Calling Contexts subsection.

Consider the following selection of the conditional profiles from the Fibonacci example.

```json
"conditionalProfiles": [
...
        {
            "ctx": "19551:11",
            "records": [
                20,
                0,
                10,
                53,
                1,
                1
            ]
        },
...
]
```

The value in the `ctx` field of this object shows that the method in question has the ID `19551` which is `Fib#fibonacci`.
The BCI in question is 11.
Inspecting the bytecode of the method would show that BCI 11 corresponds to the conditional check of the `for` loop in the `fibonacci` method.
This means that this profile is about the `for` loop in the `fibonacci` method.
The `records` entry of this object is an array of 6 values.
This is because the conditional has 2 branches (one to the beginning of the loop, another exiting the loop), and 3 integer values per branch are stored:
the BCI to which the branch jumps, an index of the branch, and a count of how many times that branch was taken.
This means that the length of the `records` array in conditional profiles must always be divisible by 3.
A switch statement with 100 branches will result in an array of 300 values.
The index of the branch is just an ordering of the branches imposed by the compiler.
This is necessary as multiple branches could target the same BCI, but the index is unique.

Going back to the example values (`20`, `0`, `10`, `53`, `1`, `1`)
indicate that a jump to BCI 20 (index 0) happened 10 times (first 3 values)
and the jump to the BCI 53 (index 1) happened once.
Referring back to the source code of `fibonacci`, the loop is executed `n` times, which is 10 for the example.
This is in line with the collected profile - 10 jumps to the beginning of the loop to repeat the loop 10 times,
and 1 jump to the outside of the loop to terminate.

## Virtual Invoke Profiles

Virtual invoke profiles contain information on the run-time types of a receiver of a virtual invoke.
Concretely, it is how many times each recorded type was the type of the receiver of the virtual call.
The current implementation of PGO limits the number of types recorded per location to 8,
but there is no such limit in the _iprof_ format.

The virtual invoke profiles are stored in a very similar manner to call-count profiles -
an array of objects with a `ctx` and `records` field whose values are a string and an array of integers respectively.
It is recommended to understand the information in the [Call-Count Profiles](#call-count-profiles) section,
especially the Partial Calling Contexts subsection.

Consider the following selection of virtual invoke profiles from the Fibonacci example.

```json
...
    "virtualInvokeProfiles": [
...
        {
            "ctx": "3236:11<4669:2<19551:34",
            "records": [
                2280,
                10
            ]
        },
...
        {
            "ctx": "6886:9<6882:23",
            "records": [
                1322,
                2,
                2280,
                60,
                3660,
                56
            ]
        },
...
    ]
...
```

The method at the end of the context has an ID of 19551 (`Fib#fibonacci`).
In that method, on BCI 34, a method with ID 4669 was inlined into `fibonacci`.
Looking at the methods in the _iprof_ file, you can see that it is `java.io.PrintStream#print`,
which is expected based on the source code.
Furthermore, on BCI 2, a method with the ID 3239 was inlined into `print`
and the profile refers to BCI `11` of that method.
Looking at the methods in the _iprof_ file again it can be seen that the method
`java.lang.String#valueOf(java.lang.Object)` has the ID 3236.
This `valueOf` method has a virtual invoke at BCI `11`.
The source code of this method follows, and the virtual invoke in question is the call to `toString` on `Object`.

```java
    public static String valueOf(Object obj) {
        return (obj == null) ? "null" : obj.toString();
    }
```

The `records` array has only 2 values.
The first number is the ID of the type that was recorded (`2280` in this case is `java.lang.String`).
The second number is the count of how many times this type was the receiver for this virtual invoke.
Since the example application only ever passes `java.lang.String` to the `print` method
(note the appending of a space after `num1` which implicitly converts the argument to a `java.lang.String`)
and the `print` method is called 10 times - the count for `java.lang.String` is 10.

The length of the `records` array for virtual invoke profiles is always a multiple of 2,
since the values represent a type ID and count pair.
In the second object of the example, the `records` array has 6 entries,
meaning 3 different types were recorded as the receiver type at run time.

## Monitor Profiles

This section describes the monitor profiles.
In Java, each object has its own monitor,
which can be used to ensure exclusive access to a section of code (using the `synchronized` keyword).
The monitor profiles record which types were used to synchronize code
(either implicitly by adding `synchronized` to a method of the type, or explicitly with `synchronized(obj) {...}`),
as well as how many times this happened for each of those types.

The monitor profiles are stored in a very similar format as call-count profiles -
an array of objects with a `ctx` and `records` field whose values are a string and an array of integers respectively.
It is recommended to understand the information in the [Call-Count Profiles](#call-count-profiles) section,
especially the [Partial Calling Contexts](#partial-calling-contexts) subsection.

It is worth noting that, since monitor profiles are global, i.e. not related to a particular context,
there is only one object in the array and that object has a dummy `0:0` context in the `ctx` field.
This is done for legacy reasons, to keep the format of all the profiles consistent.

See below the entirety of the monitor profiles for the Fibonacci example.

```json
    "monitorProfiles": [
        {
            "ctx": "0:0",
            "records": [
                9,
                4,
                10,
                1,
                579,
                9,
                619,
                10,
                1213,
                1,
                1972,
                1,
                2284,
                2,
                2337,
                1,
                2612,
                2,
                3474,
                3,
                3654,
                61,
                3807,
                3,
                3820,
                7,
                4060,
                2,
                4127,
                3,
                4725,
                6
            ]
        }
    ],
...
]
```

As mentioned before, the value of the `ctx` fields of the single object in the array is a dummy context `0:0`.
The `records` on the other hand are similar to the format used for virtual invoke profiles -
an array of type ID and count pairs.
This means that, as with virtual invoke profiles, the length of `records` array has to be a multiple of 2.

The first two values of the array indicate that the type with ID `9` (`java.lang.Object`) has been used 4 times for synchronization.
Since the example does only one synchronization on the instance of `Fib` (the `fibonacci` method is `synchronized`)
the next two values indicate that the type with ID `10` (`Fib`) has been used once for synchronization
(recall that `fibonacci` method is executed only once).

## Sampling Profiles

This section describes the sampling profiles.
Unlike all the profiles described so far,
which are gathered through instrumentation and only have partial contexts,
sampling profiles are gathered by periodically sampling the call stack,
with no need for instrumentation.
This also means that the contexts contained in the sampling profiles are not partial,
but are in fact the entire call stack at the moment of sampling.
This means that it is normal and expected to see much longer contexts in the sampling profiles when compared to the other profiles.

The sampling profiles are stored in a very similar manner to call-count profiles -
an array of objects with a `ctx` and `records` field whose values are a string and array of integers respectively.
It is recommended to understand the information in the [Call-Count Profiles](#call-count-profiles) section,
especially the Partial Calling Contexts subsection.

The Fibonacci example executes rather quickly for the sampler to collect a useful variety of samples,
so the entirety of the sampling profiles is shown below.

```json
...
    "samplingProfiles": [
        {
            "ctx": "11823:38<12811:1<12810:33<12855:25<19551:17<19547:9<19529:10<6305:105<5998:67<5941:0<5903:50<2684:23<2685:1",
            "records": [
                10
            ]
        },
        {
            "ctx": "22500:23<22353:65<22210:15<22187:246<22032:20<22030:1<22027:22<11795:68<11793:12<43854:2",
            "records": [
                1
            ]
        }
    ],
...
]
...
```

The length of the `ctx` values is much longer in the sampling profiles.
The first object in the sampling profiles has the method with ID `11823` at the top of the context.
Looking at the method entries in the _iprof_ file this is the `com.oracle.svm.core.thread.PlatformThreads#sleep` method,
called from method with ID `12811` (`java.lang.Thread#sleepNanos0`),
called from method with ID `12810` (`java.lang.Thread#sleepNanos`),
called from method with ID `12855` (`java.lang.Thread#sleep`),
called from method with ID `19551` (`Fib#fibonacci`) and so on down to the entry point of the application.
Note again that this is a full context, unlike a partial one that other profiles use.

The `records` array contains a single value which tells us how many times this unique call stack was seen during run-time sampling.
In this case, it means that the context described in the previous paragraph was recorded 10 times.

The other object in the sampling profiles array contains a different context and this sample was seen only once.
Understanding the nature of this sample is left as an exercise to the reader.

## Instance-of Profiles

> Note: Instance-of profiles were added in the _iprof_ file format version 1.1.0. They are not present in version 1.0.0.

Instance-of profiles contain information on the run-time types of values that are checked at `instanceof` operators.
Specifically, they record how many times a particular run-time type was encountered at a given `instanceof` operation.
Thus, instance-of profiling information can be stored in the same format as [Virtual Invoke Profiles](#virtual-invoke-profiles).
The only difference is that the resulting type histogram in the `records` part describes the types seen at `instanceof` checks, rather than the receiver types of virtual method calls.
Consider the following Java code:

```java
    public class A {}
    public class B extends A {}

    public static void doForA(Object obj) {
        if(obj instanceof A) {
            doSomething(obj);
        }
    }
```

The `records` section of the profile will include all types (e.g., `A`, `B`, `Object`, `String`) encountered at run time for the `obj` variable, together with their occurrence counts.
Thus, the length of the `records` array for instance-of profiles is always a multiple of 2,
since the values represent a type ID and count pair.

## Related Documentation
- [PGO](PGO.md)
- [Optimize a Native Executable with Profile-Guided Optimization](guides/optimize-native-executable-with-pgo.md)

