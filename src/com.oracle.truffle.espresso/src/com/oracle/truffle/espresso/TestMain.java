package com.oracle.truffle.espresso;

public class TestMain {

    static long fib(long n) {
        return (n < 2) ? n : fib(n - 1) + fib(n - 2);
    }

    static long factorial(long n) {
        return (n == 0) ? 1 : n * factorial(n - 1);
    }

    static void primeSieve(int n) {

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
                //System.out.println(i);
            }
        }
        System.out.println("Found " + count + " primes < " + n);
    }

    public static void main(String[] args) {
        long ticks = System.currentTimeMillis();
        System.out.println(factorial(8));
        primeSieve(1000);
        System.out.println("Elapsed: " + (System.currentTimeMillis() - ticks) + " ms");
    }
}