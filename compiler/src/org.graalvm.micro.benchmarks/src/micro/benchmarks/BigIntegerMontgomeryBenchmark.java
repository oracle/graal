/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package micro.benchmarks;

import java.math.BigInteger;
import java.util.Random;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/**
 * Focused benchmark for comparing BigInteger Montgomery intrinsics against the Java fallback.
 */
@State(Scope.Thread)
public class BigIntegerMontgomeryBenchmark extends BenchmarkBase {

    @Param({"1024", "2048", "4096"}) private int bits;

    private BigInteger base;
    private BigInteger modulus;
    private BigInteger multiplyHeavyExponent;
    private BigInteger squareHeavyExponent;

    @Setup
    public void setup() {
        Random random = new Random(0x4d4f4e54L + bits);
        modulus = new BigInteger(bits, random).setBit(bits - 1).setBit(0);
        base = new BigInteger(bits - 1, random).mod(modulus.subtract(BigInteger.ONE)).add(BigInteger.ONE);

        multiplyHeavyExponent = new BigInteger(bits, random).setBit(bits - 1).setBit(0);
        squareHeavyExponent = BigInteger.ONE.shiftLeft(bits - 1);
    }

    @Benchmark
    public BigInteger multiplyHeavyModPow() {
        return base.modPow(multiplyHeavyExponent, modulus);
    }

    @Benchmark
    public BigInteger squareHeavyModPow() {
        return base.modPow(squareHeavyExponent, modulus);
    }
}
