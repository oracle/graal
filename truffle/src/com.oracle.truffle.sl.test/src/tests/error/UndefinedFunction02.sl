/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */

function invoke(f) {
  f("hello");
}

function f1() {
  println("f1");
}

function main() {
  invoke(f1);
  invoke(foo);  
}  
