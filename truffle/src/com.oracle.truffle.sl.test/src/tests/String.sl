/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */

function null() {
}

function foo() {
  return "bar";
}

function f(a, b) {
  return a + " < " + b + ": " + (a < b);
}

function main() {  
  println("s" + null());  
  println("s" + null);  
  println("s" + foo());  
  println("s" + foo);
    
  println(null() + "s");  
  println(null() + "s");  
  println(foo() + "s");  
  println(foo + "s");

  println(f(2, 4));
  println(f(2, "4"));
}  
