/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */

function add(a, b) {
  return a + b;
}

function sub(a, b) {
  return a - b;
}

function foo(f) {
  println(f(40, 2));
}

function main() {
  foo(add);
  foo(sub);
}  
