/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */

/* 
 * This test verifies that CallTargets cannot exceed the TruffleInliningMaxCallerSize limit when inlining.
 */

function test1() {
    testTruffleBoundary01();
}
function main() {
    callUntilOptimized(test1);
    assertTrue(isOptimized(test1), "inlinableFunction must be compiled properly");
}  
