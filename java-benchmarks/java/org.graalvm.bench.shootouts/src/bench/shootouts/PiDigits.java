/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
/*
 * Copyright (c) 2004-2008 Brent Fulgham, 2005-2020 Isaac Gouy
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted
 * provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions
 * and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided with
 * the distribution.
 *
 * 3. Neither the name "The Computer Language Benchmarks Game" nor the name "The Benchmarks Game" nor
 * the name "The Computer Language Shootout Benchmarks" nor the names of its contributors may be used
 * to endorse or promote products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY
 * WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package bench.shootouts;
/*
 * The Computer Language Benchmarks Game
 * http://benchmarksgame.alioth.debian.org/
 *
 * contributed by Isaac Gouy
 */


import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

import java.math.BigInteger;

@State(Scope.Benchmark)
public class PiDigits {

    static final int L = 10;
    @Param("500")
    static int piDigitsN;

    @Benchmark
    public static void bench(Blackhole blackhole) {
        int n = piDigitsN;
        int j = 0;

        PiDigitSpigot digits = new PiDigitSpigot();

        while (n > 0) {
            if (n >= L) {
                for (int i = 0; i < L; i++) {
                    blackhole.consume(digits.next());
                }
                j += L;
            } else {
                for (int i = 0; i < n; i++) {
                    blackhole.consume(digits.next());
                }
                for (int i = n; i < L; i++) {
                    blackhole.consume(" ");
                }
                j += n;
            }

            blackhole.consume(j);
            n -= L;
        }
    }
}

class PiDigitSpigot {
    Transformation z, x, inverse;

    public PiDigitSpigot() {
        z = new Transformation(1, 0, 0, 1);
        x = new Transformation(0, 0, 0, 0);
        inverse = new Transformation(0, 0, 0, 0);
    }

    public int next() {
        int y = digit();
        if (isSafe(y)) {
            z = produce(y);
            return y;
        } else {
            z = consume(x.next());
            return next();
        }
    }

    public int digit() {
        return z.extract(3);
    }

    public boolean isSafe(int digit) {
        return digit == z.extract(4);
    }

    public Transformation produce(int i) {
        return (inverse.qrst(10, -10 * i, 0, 1)).compose(z);
    }

    public Transformation consume(Transformation a) {
        return z.compose(a);
    }
}

class Transformation {
    BigInteger q, r, s, t;
    int k;

    public Transformation(int q, int r, int s, int t) {
        this.q = BigInteger.valueOf(q);
        this.r = BigInteger.valueOf(r);
        this.s = BigInteger.valueOf(s);
        this.t = BigInteger.valueOf(t);
        k = 0;
    }

    public Transformation(BigInteger q, BigInteger r, BigInteger s, BigInteger t) {
        this.q = q;
        this.r = r;
        this.s = s;
        this.t = t;
        k = 0;
    }

    public Transformation next() {
        k++;
        q = BigInteger.valueOf(k);
        r = BigInteger.valueOf(4 * k + 2);
        s = BigInteger.valueOf(0);
        t = BigInteger.valueOf(2 * k + 1);
        return this;
    }

    public int extract(int j) {
        BigInteger bigj = BigInteger.valueOf(j);
        BigInteger numerator = (q.multiply(bigj)).add(r);
        BigInteger denominator = (s.multiply(bigj)).add(t);
        return (numerator.divide(denominator)).intValue();
    }

    public Transformation qrst(int q, int r, int s, int t) {
        this.q = BigInteger.valueOf(q);
        this.r = BigInteger.valueOf(r);
        this.s = BigInteger.valueOf(s);
        this.t = BigInteger.valueOf(t);
        k = 0;
        return this;
    }

    public Transformation compose(Transformation a) {
        return new Transformation(
                q.multiply(a.q)
                , (q.multiply(a.r)).add((r.multiply(a.t)))
                , (s.multiply(a.q)).add((t.multiply(a.s)))
                , (s.multiply(a.r)).add((t.multiply(a.t))));
    }
}
