


## Example 1 - Monomorphization of arguments

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
