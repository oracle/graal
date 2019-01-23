# Monomorphization Use Cases

This document illustrates through examples how monomorphization can improve
performance of dynamic languages without going into any detail on how
monomorphization is implemented (described in ["Splitting"](Splitting.md)) or
how to leverage monomorphization in your language implementation (described in
["Reporting Polymorphism"](ReportingPolymorphism.md))

## Monomorphization in a Nutshell

To better illustrate the benefits of monomorphization, let us first consider a
small example written in JavaScript:

```
function add(arg1, arg2) {
    return arg1 + arg2;
}

function callsAdd() {
    add(1, 2);
    add("foo", "bar");
}

var i = 0;
while (i < 1000) {
    callsAdd();
    i++;
}
```

As we can see in this example, the `add` function is called from `callsAdd` once
with integer arguments and once with string arguments.  Once `add` is executed
enough times to be compiled its execution profile will show that the `+`
operator has been executed with both integers and strings and thus handlers
(i.e. type checks and execution) for both types will be compiled which has a
performance impact. This can be avoided by rewriting the example as follows:

```
function addInt(arg1, arg2) {
    return arg1 + arg2;
}

function addString(arg1, arg2) {
    return arg1 + arg2;

}
function callsAdd() {
    addInt(1, 2);
    addString("foo", "bar");
}

i = 0;
while (i < 1000) {
    callsAdd();
    i++;
}
```

In this example the `add` has been duplicated (split) in such a way that each
type profile is contained in a separate copy of the function (`addInt` and
`addString`). The result is that, come compilation time, only a single type
profile is available for each function avoiding potentially costly type checks
in the compiled code.

Automating the detection suitable candidates, as well as their duplication,
performed at run time is what we call monomorphization. It is, in other words,
automated run-time monomorphization of polymorphic nodes through AST
duplication.

## Example 1 - Monomorphization of arguments

This example is an extended version of the illustration example from the
previous section. The `add` function is still the target for monomorphization
and is called from the `action` function 3 times with 3 sets of different
arguments (numbers, strings and arrays). We execute the `action` function for 15
seconds in order to have enough time for warmup, and afterwards execute it for
60 seconds keeping track of how long each execution took, reporting finally the
average. We execute this code twice: once with and once without monomorphization
enabled, and report the output of these two runs as well as the speedup.

```
function add(arg1, arg2) {
    return arg1 + arg2;
}

var global = 0;

function action() {
    for (var i = 0; i < 10000; i++) {
        global = add(1, 2);
        global = add("foo", "bar");
        global = add([1,2,3], [4,5,6]);
    }
}


// Warm up.
var start = Date.now();
while ((Date.now() - start) < 15000 /* 15 seconds */) {
    action();
}
// Benchmark
var iterations = 0;
var sum = 0;
var start = Date.now();
while ((Date.now() - start) < 60000 /* 60 seconds */) {
    var thisIterationStart = Date.now();
    action();
    var thisIterationTime = Date.now() - thisIterationStart;
    iterations++;
    sum += thisIterationTime;
}
console.log(sum / iterations);

```
Output **without** monomorphization: 4.494225288735564

Output **with** monomorphization: 4.2421633923

Speedup: ~5%

## Example 2 - Monomorphization of indirect calls

This example is slightly more complicated and illustrates how monomorphization
benefits higher order functions. In the example, we define the `insertionSort`
function, which - given an array of items and a function for comparing these
items - sorts the array using insertion sort. We define an array of 1000 random
double values between 0 and 1 and sort it four times using 4 different sorting
methods (in the `action` function). Finally, as with the previous example, we
warm up the `action` function for 15 second, and report the average execution
time of this function over the next 60 seconds with and without
monomorphization.

```
function insertionSort (items, comparator) {
    for (var i = 0; i < items.length; i++) {
        let value = items[i];
        for (var j = i - 1; j >= 0 && comparator(items[j], value); j--) {
            items[j + 1] = items[j]
        }
        items[j + 1] = value
    }
}

// Random values in an array
var array = new Array(1000);
for (i = 0; i < array.length; i++) {
    array[i] = Math.random();
}


function action() {
    insertionSort(array, function (a, b) { return a < b                                      });
    insertionSort(array, function (a, b) { return a > b                                      });
    insertionSort(array, function (a, b) { return a.toString().length < b.toString().length; });
    insertionSort(array, function (a, b) { return a.toString().length > b.toString().length; });
}

// Warm up.
var start = Date.now();
while ((Date.now() - start) < 15000 /* 15 seconds */) {
    action();
}
// Benchmark
var iterations = 0;
var sum = 0;
var start = Date.now();
while ((Date.now() - start) < 60000 /* 60 seconds */) {
    var thisIterationStart = Date.now();
    action();
    var thisIterationTime = Date.now() - thisIterationStart;
    iterations++;
    sum += thisIterationTime;
}
console.log(sum / iterations);
```
Output **without** monomorphization: 194.05161290322582

Output **with** monomorphization: 175.41071428571428

Speedup: ~10%
