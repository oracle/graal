/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */

function fib(num) {
    if (num < 1) {return 0;}
    if (num <= 2) {return 1;}
    return fib(num - 1) + fib(num - 2);
}

function run() {  
    number = 31;
    fibo_is = 1346269;

    fibo = fib(number);

    if (fibo != fibo_is) {
        1 / 0;  // SL does not have a throw primitive
    }

    return fibo;
}
