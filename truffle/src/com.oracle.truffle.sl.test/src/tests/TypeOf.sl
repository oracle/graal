/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */

function main() {
  println(typeOf(42));
  println(typeOf(42000000000000000000000000000000000000000));
  println(typeOf("42"));
  println(typeOf(42 == 42));
  println(typeOf(new()));
  println(typeOf(main));
  println(typeOf(null()));
}
function null() {
}
