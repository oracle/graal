/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */

function propRead(obj) {
    i = 0;
    while(i < 10000000) {
      i = i + obj.foo;
    }
    return i;
}

function run() {
    obj = new();
    obj.foo = 1;
    return propRead(obj);
}

function main() {
    return run();
}
