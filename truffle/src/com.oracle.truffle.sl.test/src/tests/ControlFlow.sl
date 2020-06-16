/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */

function foo() {}
function bar() {}

function main() {  
  foo();
  if (1 < 2) {
    bar();
    return 1;
  }
}  
