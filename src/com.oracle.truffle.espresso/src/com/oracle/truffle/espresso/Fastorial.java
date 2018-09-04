package com.oracle.truffle.espresso;

import java.math.BigInteger;

public class Fastorial {

    static BigInteger mul(int from, int to) {
        if (from == to) {
            return BigInteger.valueOf(to);
        }
        int mid = from + (from - to) / 2;
        return mul(from, mid).multiply(mul(mid + 1, to));
    }

    public static void main(String[] args) {
        int n = 100;
        BigInteger f = BigInteger.ONE;
        for (int i = 2; i <= n; ++i) {
            f = f.multiply(BigInteger.valueOf(i));
        }
        System.out.println(n + "! = " + f);
    }
}
