/* 
 * This test verifies that CallTargets cannot exceed the TruffleInliningMaxCallerSize limit when inlining.
 */
function inlinableFunction() { 
    generateDummyNodes(getOption("TruffleInliningMaxCallerSize") - 8);
}

function notInlinableFunction() { 
    generateDummyNodes(getOption("TruffleInliningMaxCallerSize") - 7);
}

function test1() {
    inlinableFunction(); 
}

function test2() {
    notInlinableFunction(); 
}

function main() {
    waitForOptimization(callUntilOptimized(test1));
    assertTrue(isInlined(test1, inlinableFunction), "inlinableFunction is not inlined");
    
    waitForOptimization(callUntilOptimized(test2));
    assertFalse(isInlined(test2, notInlinableFunction), "notInlinableFunction is inlined");
}  
