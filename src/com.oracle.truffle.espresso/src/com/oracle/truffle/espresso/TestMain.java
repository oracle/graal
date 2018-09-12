/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.espresso;

public class TestMain {

    static long fib(long n) {
        return (n < 2) ? n : fib(n - 1) + fib(n - 2);
    }

    static long factorial(long n) {
        return (n == 0) ? 1 : n * factorial(n - 1);
    }

    static int primeSieve(int n) {

        boolean[] mark = new boolean[n];
        for (int i = 2; i * i < n; ++i) {
            if (!mark[i]) {
                for (int j = i * i; j < n; j += i) {
                    mark[j] = true;
                }
            }
        }

        int count = 0;
        for (int i = 2; i < n; ++i) {
            if (!mark[i]) {
                count++;
            }
        }

        return count;
    }

    public static void main(String[] args) {
        int n = 10;
        System.out.println(n + "! = " + factorial(n));

        // Warmup the sieve
        for (int i = 0; i < 3000; ++i) {
            primeSieve(100 + i); // argument should not be constant
        }

        System.out.println("Warmup done!");

        n = 100000000;
        System.out.println("Computing primes < " + n);

        long ticks = System.currentTimeMillis();
        System.out.println("Found " + primeSieve(n) + " primes");
        System.out.println("Elapsed: " + (System.currentTimeMillis() - ticks) + " ms");
    }
}