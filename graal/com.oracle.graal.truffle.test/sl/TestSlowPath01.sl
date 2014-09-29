/* 
 * This test verifies that CallTargets cannot exceed the TruffleInliningMaxCallerSize limit when inlining.
 */

function test1() {
    testSlowPath01();
}
function main() {
    waitForOptimization(callUntilOptimized(test1));
    assertTrue(isOptimized(test1), "inlinableFunction must be compiled properly");
}  
