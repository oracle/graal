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