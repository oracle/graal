/* 
 * This tests that simple arithmetic gets inlined.
 */
function add(a, b) { 
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
    waitForOptimization(callUntilOptimized(test));
    assertTrue(isInlined(test, test, add), "add is not inlined");
}  
