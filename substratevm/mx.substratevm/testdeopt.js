function add(a, b, test) {
    if (test) {
        a += b;
    }
    return a + b;
}

// trigger compilation add for ints and test = true
for (let i = 0; i < 1000 * 1000; i++) {
    add(i, i, true);
}

// deoptimize with failed assumption in compiled method
// then trigger compilation again
console.log("deopt1")
for (let i = 0; i < 1000 * 1000; i++) {
    add(i, i, false);
}

// deoptimize with different parameter types
console.log("deopt2");
add({f1: "test1", f2: 2}, {x: "x", y: {test: 42}}, false);
