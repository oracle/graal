/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */

function recursion(n) {
  local = 42;
  
  if (n > 0) {
    recursion(n - 1);
  } else {
    local = "abc";
  }
  
  println(local);
}

function main() {
  recursion(3);
}  
