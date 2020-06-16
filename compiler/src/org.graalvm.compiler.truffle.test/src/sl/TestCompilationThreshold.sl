/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */

/*
 * This test verifies the compilation threshold property.
 */
function test() {
}  

function main() {  
    /* TODO disableSplitting is required because otherwise it needs more calls to warm up. This still needs to be fixed. */
    disableSplitting(test); 
    threshold = getOption("engine.CompilationThreshold");
    i = 0;
    while (i < threshold -1) {
        test();
        i = i + 1;
    }
    assertFalse(isOptimized(test));
    test();                         // triggers compilation
    assertTrue(isOptimized(test));
}  
