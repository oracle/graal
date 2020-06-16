/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */

function main() {
  t = 10 == 10; // true
  f = 10 != 10; // false
  println(left(f) && right(f));
  println(left(f) && right(t));
  println(left(t) && right(f));
  println(left(t) && right(t));
  println("");
  println(left(f) || right(f));
  println(left(f) || right(t));
  println(left(t) || right(f));
  println(left(t) || right(t));
}

function left(x) {
  println("left");
  return x;
}

function right(x) {
  println("right");
  return x;
}
