/* 
 * Tests that indirect recursions are not inlined.
 */
function fib(a) { 
    if (a == 2 || a == 1) {
        return 1;
    }
    return call(fib, a-1) + call(fib, a-2) + call(void, 0);
}

function call(f, a) {
    return f(a);
}

function void(a) {
    return a;
}

function test() {
    i = 0;
    sum = 0;
    while (i < 100) {
        sum = sum + fib(7);
        i = i + 1;
    }
    return sum;
}

function main() {
    waitForOptimization(callUntilOptimized(test));
    assertTrue(isInlined(test, test, fib), "not inlined: test -> fib");
    if (getOption("TruffleContextSensitiveInlining")) {
      assertTrue(isInlined(test, fib, call), "not inlined: fib -> call");
      assertFalse(isInlined(test, call, fib), "inlined: call -> fib"); 
      assertTrue(isInlined(test, call, void), "inlined: call -> void");
    }
}  
