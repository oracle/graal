/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */


function a() {return 42;}
function b() {return a();}
function c() {return b();}
function d() {return c();}
function e() {return c();}
function f() {return c();}
function g() {return d() + e() + f();}

function main() {
    i = 0;
    result = 0;
    while (i < 10000) {
        result = result + g();
        i = i + 1;
    }
    return result;
}
