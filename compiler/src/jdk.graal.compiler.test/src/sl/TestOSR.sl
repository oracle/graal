/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */

function test() {
  i = 0;
  sum = 0;  
  while (i < 300000) { 
    sum = sum +  i;
    i = i + 1;  
  }
  return sum;
}

function main() {  
  test();
}  
