package com.oracle.truffle.espresso;

import java.math.BigInteger;

public class Fastorial {

    static BigInteger mul(int from, int to) {
        if (from == to) {
            return BigInteger.valueOf(to);
        }
        int mid = from + (to - from) / 2;
        return mul(from, mid).multiply(mul(mid + 1, to));
    }

    static BigInteger fastorial(int n) {
        assert n > 0;
        return mul(1, n);
    }

    static BigInteger bruteFactorial(int n) {
        BigInteger f = BigInteger.ONE;
        for (int i = 2; i <= n; ++i) {
            f = f.multiply(BigInteger.valueOf(i));
        }
        return f;
    }

    public static void main(String[] args) {
        long ticks = System.currentTimeMillis();
        int n = Integer.parseInt(args[0]);
        BigInteger f = fastorial(n);
        System.out.println(n + "! = " + f);
        System.out.println("Elapsed: " + (System.currentTimeMillis() - ticks) + " ms");
    }
}
