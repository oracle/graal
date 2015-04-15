/* 
 * This test verifies that CallTargets cannot exceed the TruffleInliningMaxCallerSize limit when inlining.
 */
function inlinableFunction() { 
    generateDummyNodes(getOption("TruffleInliningMaxCallerSize") - 7);
}

function notInlinableFunction() { 
    generateDummyNodes(getOption("TruffleInliningMaxCallerSize") - 6);
}

function test1() {
    inlinableFunction(); 
}

function test2() {
    notInlinableFunction(); 
}

function main() {
    originalMaxCallerSize = getOption("TruffleInliningMaxCallerSize");
    setOption("TruffleInliningMaxCallerSize", 20);
    callUntilOptimized(test1);
    assertTrue(isInlined(test1, test1, inlinableFunction), "inlinableFunction is not inlined");
    
    callUntilOptimized(test2); 
    assertFalse(isInlined(test2, test2, notInlinableFunction), "notInlinableFunction is inlined"); 
    setOption("TruffleInliningMaxCallerSize", originalMaxCallerSize);
}  
