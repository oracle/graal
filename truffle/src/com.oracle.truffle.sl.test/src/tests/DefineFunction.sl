/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */

function foo() {
  println(test(40, 2));
}

function main() {
  defineFunction("function test(a, b) { return a + b; }");
  foo();

  defineFunction("function test(a, b) { return a - b; }");
  foo();
}  
