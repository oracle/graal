/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */



function testConstantValue1() {
    return isCompilationConstant(42);
}

function testConstantValue2() {
    return isCompilationConstant(21 + 21);
}

function testConstantSequence() {
    40;
    return isCompilationConstant(42);
}

function testConstantLocalVariable() {
    x = 42;
    return isCompilationConstant(x);
}

function testNonConstantAdd() {
    return isCompilationConstant(42 + "42");
}


function main() {
    callFunctionsWith("testConstant", harnessTrue);
    callFunctionsWith("testNonConstant", harnessFalse);
}  

function harnessTrue(testFunction) {
    callUntilOptimized(testFunction);
    assertTrue(testFunction(), "test function " + testFunction + " is not constant");
}


function harnessFalse(testFunction) {
    callUntilOptimized(testFunction);
    assertFalse(testFunction(), "test function " + testFunction + " is constant");
}
