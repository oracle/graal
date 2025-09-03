/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.graalvm.word.test;

import static org.graalvm.word.WordFactory.unsigned;
import static org.graalvm.word.WordFactory.signed;
import static org.graalvm.word.WordFactory.pointer;

import org.graalvm.word.Pointer;
import org.graalvm.word.SignedWord;
import org.graalvm.word.UnsignedWord;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.stream.LongStream;

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

    static SignedWord signedWord(long val) {
        return signed(val);
    }

    static UnsignedWord unsignedWord(long val) {
        return unsigned(val);
    }

    static Pointer asPointer(long val) {
        return pointer(val);
    }

    static List<SignedWord> signedWords = LongStream.of(words).mapToObj(WordTests::signedWord).toList();
    static List<UnsignedWord> unsignedWords = LongStream.of(words).mapToObj(WordTests::unsignedWord).toList();
    static List<Pointer> pointers = LongStream.of(words).mapToObj(WordTests::asPointer).toList();

    @Test
    public void testSigned() {
        for (var x : signedWords) {
            Assert.assertEquals(x.not().rawValue(), ~x.rawValue());

            for (var y : signedWords) {
                Assert.assertEquals(x.equal(y), x == y);
                Assert.assertEquals(x.notEqual(y), x != y);

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

                Assert.assertEquals(x.equal(y), x == y);
                Assert.assertEquals(x.notEqual(y), x != y);

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
                Assert.assertEquals(x.equal(y), x == y);
                Assert.assertEquals(x.notEqual(y), x != y);

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

                Assert.assertEquals(x.equal(y), x == y);
                Assert.assertEquals(x.notEqual(y), x != y);

                Assert.assertEquals(x.aboveThan(y), longAboveThan(x.rawValue(), y.rawValue()));
                Assert.assertEquals(x.aboveOrEqual(y), longAboveOrEqual(x.rawValue(), y.rawValue()));
                Assert.assertEquals(x.belowThan(y), longBelowThan(x.rawValue(), y.rawValue()));
                Assert.assertEquals(x.belowOrEqual(y), longBelowOrEqual(x.rawValue(), y.rawValue()));

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
                Assert.assertEquals(x.equal(y), x == y);
                Assert.assertEquals(x.notEqual(y), x != y);

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

                Assert.assertEquals(x.equal(y), x == y);
                Assert.assertEquals(x.notEqual(y), x != y);

                Assert.assertEquals(x.aboveThan(y), longAboveThan(x.rawValue(), y.rawValue()));
                Assert.assertEquals(x.aboveOrEqual(y), longAboveOrEqual(x.rawValue(), y.rawValue()));
                Assert.assertEquals(x.belowThan(y), longBelowThan(x.rawValue(), y.rawValue()));
                Assert.assertEquals(x.belowOrEqual(y), longBelowOrEqual(x.rawValue(), y.rawValue()));

                Assert.assertEquals(x.shiftLeft(y).rawValue(), x.rawValue() << y.rawValue());
                Assert.assertEquals(x.unsignedShiftRight(y).rawValue(), x.rawValue() >>> y.rawValue());
            }
        }
    }

    @Test
    public void testWords() {
        for (long x : words) {
            Assert.assertEquals(signed(x).not().rawValue(), ~x);
            Assert.assertEquals(pointer(x).isNull(), x == 0);
            Assert.assertEquals(pointer(x).isNonNull(), x != 0);

            for (long y : words) {
                Assert.assertEquals(signed(x).equal(signed(y)), x == y);
                Assert.assertEquals(signed(x).notEqual(signed(y)), x != y);

                Assert.assertEquals(signed(x).add(signed(y)).rawValue(), x + y);
                Assert.assertEquals(signed(x).subtract(signed(y)).rawValue(), x - y);
                Assert.assertEquals(signed(x).multiply(signed(y)).rawValue(), x * y);

                Assert.assertEquals(unsigned(x).add(unsigned(y)).rawValue(), x + y);
                Assert.assertEquals(unsigned(x).subtract(unsigned(y)).rawValue(), x - y);
                Assert.assertEquals(unsigned(x).multiply(unsigned(y)).rawValue(), x * y);

                if (y != 0) {
                    Assert.assertEquals(unsigned(x).unsignedDivide(unsigned(y)).rawValue(), Long.divideUnsigned(x, y));
                    Assert.assertEquals(unsigned(x).unsignedRemainder(unsigned(y)).rawValue(), Long.remainderUnsigned(x, y));
                    Assert.assertEquals(signed(x).signedDivide(signed(y)).rawValue(), x / y);
                    Assert.assertEquals(signed(x).signedRemainder(signed(y)).rawValue(), x % y);
                }
                Assert.assertEquals(signed(x).and(signed(y)).rawValue(), x & y);
                Assert.assertEquals(signed(x).or(signed(y)).rawValue(), x | y);
                Assert.assertEquals(signed(x).xor(signed(y)).rawValue(), x ^ y);

                Assert.assertEquals(unsigned(x).and(unsigned(y)).rawValue(), x & y);
                Assert.assertEquals(unsigned(x).or(unsigned(y)).rawValue(), x | y);
                Assert.assertEquals(unsigned(x).xor(unsigned(y)).rawValue(), x ^ y);

                Assert.assertEquals(signed(x).equal(signed(y)), x == y);
                Assert.assertEquals(signed(x).notEqual(signed(y)), x != y);
                Assert.assertEquals(unsigned(x).equal(unsigned(y)), x == y);
                Assert.assertEquals(unsigned(x).notEqual(unsigned(y)), x != y);

                Assert.assertEquals(signed(x).greaterThan(signed(y)), x > y);
                Assert.assertEquals(signed(x).greaterOrEqual(signed(y)), x >= y);
                Assert.assertEquals(signed(x).lessThan(signed(y)), x < y);
                Assert.assertEquals(signed(x).lessOrEqual(signed(y)), x <= y);

                Assert.assertEquals(unsigned(x).aboveThan(unsigned(y)), longAboveThan(x, y));
                Assert.assertEquals(unsigned(x).aboveOrEqual(unsigned(y)), longAboveOrEqual(x, y));
                Assert.assertEquals(unsigned(x).belowThan(unsigned(y)), longBelowThan(x, y));
                Assert.assertEquals(unsigned(x).belowOrEqual(unsigned(y)), longBelowOrEqual(x, y));

                Assert.assertEquals(signed(x).shiftLeft(signed(y)).rawValue(), x << y);
                Assert.assertEquals(signed(x).signedShiftRight(signed(y)).rawValue(), x >> y);
                Assert.assertEquals(unsigned(x).shiftLeft(unsigned(y)).rawValue(), x << y);
                Assert.assertEquals(unsigned(x).unsignedShiftRight(unsigned(y)).rawValue(), x >>> y);
            }
        }
    }

    static boolean longAboveThan(long a, long b) {
        return Long.compareUnsigned(a, b) > 0;
    }

    static boolean longAboveOrEqual(long a, long b) {
        return Long.compareUnsigned(a, b) >= 0;
    }

    static boolean longBelowThan(long a, long b) {
        return Long.compareUnsigned(a, b) < 0;
    }

    static boolean longBelowOrEqual(long a, long b) {
        return Long.compareUnsigned(a, b) <= 0;
    }
}
