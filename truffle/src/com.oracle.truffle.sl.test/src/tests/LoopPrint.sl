/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */

function loop(n) {
  i = 0;  
  while (i < n) {  
    i = i + 1;  
  }  
  return i;
}  

function main() {
  i = 0;
  while (i < 20) {
    loop(1000);
    i = i + 1;
  }
  println(loop(1000));  
}  
