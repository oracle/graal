/* 
 * Test recursive calls do not get inlined and do not crash.
 */
function fib(a) { 
    if (a == 2 || a == 1) {
        return 1;
    }
    return fib(a-1) + fib(a-2);
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
    callUntilOptimized(test);
}  
