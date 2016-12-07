/*
 * This test verifies the compilation threshold property.
 */
function test() {
}  

function main() {  
    /* TODO disableSplitting is required because otherwise it needs more calls to warm up. This still needs to be fixed. */
    disableSplitting(test); 
    threshold = getOption("TruffleCompilationThreshold");
    i = 0;
    while (i < threshold -1) {
        test();
        i = i + 1;
    }
    assertFalse(isOptimized(waitForOptimization(test)));
    test();                         // triggers compilation
    assertTrue(isOptimized(waitForOptimization(test)));
}  
