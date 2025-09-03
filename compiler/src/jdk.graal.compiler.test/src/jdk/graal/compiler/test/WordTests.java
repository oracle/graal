/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.test;

import static jdk.graal.compiler.core.common.calc.UnsignedMath.aboveOrEqual;
import static jdk.graal.compiler.core.common.calc.UnsignedMath.aboveThan;
import static jdk.graal.compiler.core.common.calc.UnsignedMath.belowOrEqual;
import static jdk.graal.compiler.core.common.calc.UnsignedMath.belowThan;

import jdk.graal.compiler.word.Word;
import org.graalvm.word.Pointer;
import org.graalvm.word.SignedWord;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.stream.LongStream;
import java.util.stream.Stream;

/**
 * Tests word operations on boxed values produced by {@link WordFactory} and {@link Word}.
 */
public class WordTests {

    static long[] words = {
                    Long.MIN_VALUE,
                    Long.MIN_VALUE + 1,
                    -1L,
                    0L,
                    1L,
                    Long.MAX_VALUE - 1,
                    Long.MAX_VALUE,
                    Integer.MAX_VALUE - 1L,
                    Integer.MAX_VALUE,
                    Integer.MAX_VALUE + 1L,
                    Integer.MIN_VALUE - 1L,
                    Integer.MIN_VALUE,
                    Integer.MIN_VALUE + 1L
    };

    static SignedWord graalSigned(long val) {
        return Word.signed(val);
    }

    static UnsignedWord graalUnsigned(long val) {
        return Word.unsigned(val);
    }

    static Pointer graalPointer(long val) {
        return Word.pointer(val);
    }

    static List<SignedWord> signedWords = Stream.concat(
                    LongStream.of(words).mapToObj(org.graalvm.word.WordFactory::signed),
                    LongStream.of(words).mapToObj(WordTests::graalSigned)).toList();
    static List<UnsignedWord> unsignedWords = Stream.concat(
                    LongStream.of(words).mapToObj(org.graalvm.word.WordFactory::unsigned),
                    LongStream.of(words).mapToObj(WordTests::graalUnsigned)).toList();
    static List<Pointer> pointers = Stream.concat(
                    LongStream.of(words).mapToObj(org.graalvm.word.WordFactory::pointer),
                    LongStream.of(words).mapToObj(WordTests::graalPointer)).toList();

    @Test
    public void testSigned() {
        for (var x : signedWords) {
            Assert.assertEquals(x.not().rawValue(), ~x.rawValue());

            for (var y : signedWords) {
                Assert.assertEquals(x.equal(y), x.rawValue() == y.rawValue());
                Assert.assertEquals(x.notEqual(y), x.rawValue() != y.rawValue());

                Assert.assertEquals(x.add(y).rawValue(), x.rawValue() + y.rawValue());
                Assert.assertEquals(x.add(y).rawValue(), x.rawValue() + y.rawValue());
                Assert.assertEquals(x.subtract(y).rawValue(), x.rawValue() - y.rawValue());
                Assert.assertEquals(x.multiply(y).rawValue(), x.rawValue() * y.rawValue());

                if (y.rawValue() != 0) {
                    Assert.assertEquals(x.signedDivide(y).rawValue(), x.rawValue() / y.rawValue());
                    Assert.assertEquals(x.signedRemainder(y).rawValue(), x.rawValue() % y.rawValue());
                }
                Assert.assertEquals(x.and(y).rawValue(), x.rawValue() & y.rawValue());
                Assert.assertEquals(x.or(y).rawValue(), x.rawValue() | y.rawValue());
                Assert.assertEquals(x.xor(y).rawValue(), x.rawValue() ^ y.rawValue());

                Assert.assertEquals(x.greaterThan(y), x.rawValue() > y.rawValue());
                Assert.assertEquals(x.greaterOrEqual(y), x.rawValue() >= y.rawValue());
                Assert.assertEquals(x.lessThan(y), x.rawValue() < y.rawValue());
                Assert.assertEquals(x.lessOrEqual(y), x.rawValue() <= y.rawValue());

                Assert.assertEquals(x.shiftLeft((UnsignedWord) y).rawValue(), x.rawValue() << y.rawValue());
                Assert.assertEquals(x.signedShiftRight((UnsignedWord) y).rawValue(), x.rawValue() >> y.rawValue());
            }
        }
    }

    @Test
    public void testUnsigned() {
        for (var x : unsignedWords) {
            Assert.assertEquals(x.not().rawValue(), ~x.rawValue());

            for (var y : unsignedWords) {
                Assert.assertEquals(x.equal(y), x.rawValue() == y.rawValue());
                Assert.assertEquals(x.notEqual(y), x.rawValue() != y.rawValue());

                Assert.assertEquals(x.add(y).rawValue(), x.rawValue() + y.rawValue());
                Assert.assertEquals(x.subtract(y).rawValue(), x.rawValue() - y.rawValue());
                Assert.assertEquals(x.multiply(y).rawValue(), x.rawValue() * y.rawValue());

                if (y.rawValue() != 0) {
                    Assert.assertEquals(x.unsignedDivide(y).rawValue(), Long.divideUnsigned(x.rawValue(), y.rawValue()));
                    Assert.assertEquals(x.unsignedRemainder(y).rawValue(), Long.remainderUnsigned(x.rawValue(), y.rawValue()));
                }
                Assert.assertEquals(x.and(y).rawValue(), x.rawValue() & y.rawValue());
                Assert.assertEquals(x.or(y).rawValue(), x.rawValue() | y.rawValue());
                Assert.assertEquals(x.xor(y).rawValue(), x.rawValue() ^ y.rawValue());

                Assert.assertEquals(x.aboveThan(y), aboveThan(x.rawValue(), y.rawValue()));
                Assert.assertEquals(x.aboveOrEqual(y), aboveOrEqual(x.rawValue(), y.rawValue()));
                Assert.assertEquals(x.belowThan(y), belowThan(x.rawValue(), y.rawValue()));
                Assert.assertEquals(x.belowOrEqual(y), belowOrEqual(x.rawValue(), y.rawValue()));

                Assert.assertEquals(x.shiftLeft(y).rawValue(), x.rawValue() << y.rawValue());
                Assert.assertEquals(x.unsignedShiftRight(y).rawValue(), x.rawValue() >>> y.rawValue());
            }
        }
    }

    @Test
    public void testPointer() {
        for (var x : pointers) {
            Assert.assertEquals(x.not().rawValue(), ~x.rawValue());
            Assert.assertEquals(x.isNull(), x.rawValue() == 0);
            Assert.assertEquals(x.isNonNull(), x.rawValue() != 0);

            for (var y : pointers) {
                Assert.assertEquals(x.equal(y), x.rawValue() == y.rawValue());
                Assert.assertEquals(x.notEqual(y), x.rawValue() != y.rawValue());

                Assert.assertEquals(x.add(y).rawValue(), x.rawValue() + y.rawValue());
                Assert.assertEquals(x.subtract(y).rawValue(), x.rawValue() - y.rawValue());
                Assert.assertEquals(x.multiply(y).rawValue(), x.rawValue() * y.rawValue());

                if (y.rawValue() != 0) {
                    Assert.assertEquals(x.unsignedDivide(y).rawValue(), Long.divideUnsigned(x.rawValue(), y.rawValue()));
                    Assert.assertEquals(x.unsignedRemainder(y).rawValue(), Long.remainderUnsigned(x.rawValue(), y.rawValue()));
                }
                Assert.assertEquals(x.and(y).rawValue(), x.rawValue() & y.rawValue());
                Assert.assertEquals(x.or(y).rawValue(), x.rawValue() | y.rawValue());
                Assert.assertEquals(x.xor(y).rawValue(), x.rawValue() ^ y.rawValue());

                Assert.assertEquals(x.aboveThan(y), aboveThan(x.rawValue(), y.rawValue()));
                Assert.assertEquals(x.aboveOrEqual(y), aboveOrEqual(x.rawValue(), y.rawValue()));
                Assert.assertEquals(x.belowThan(y), belowThan(x.rawValue(), y.rawValue()));
                Assert.assertEquals(x.belowOrEqual(y), belowOrEqual(x.rawValue(), y.rawValue()));

                Assert.assertEquals(x.shiftLeft(y).rawValue(), x.rawValue() << y.rawValue());
                Assert.assertEquals(x.unsignedShiftRight(y).rawValue(), x.rawValue() >>> y.rawValue());
            }
        }
    }
}
