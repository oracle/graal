/* 
 * This tests that simple arithmetic gets inlined.
 */
function add(a, b) {
    deoptimizeWhenCompiled(a == 50); 
    return a + b;
}


function test() {
    i = 0;
    while (i < 100) {
        i = add(i, 1);
    }
    return i;
}

function main() {
    waitForOptimization(callUntilOptimized(test, 1 == 2));
    test();
}  
