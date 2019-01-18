/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.library.test.examples;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.GenerateLibrary.DefaultExport;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.nodes.Node;

/**
 * Shows:
 * <ul>
 * <li>How to use default types
 * <li>Reuse of default types in boxed values (see SmallNumber)
 * <li>Non trivial specialization in BigNumber on AddNode.
 * <li>How to design big number abstraction that is extensible.
 * </ul>
 */
@SuppressWarnings("unused")
public class NumberDemo {

    static final int LIMIT = 3;

    @GenerateLibrary
    @DefaultExport(IntegerNumber.class)
    public abstract static class NumberLibrary extends Library {

        public abstract boolean isFixedNumber(Object receiver);

        public abstract Object add(Object receiver, Object value);

        public abstract boolean fitsInInt(Object receiver);

        public abstract int asInt(Object receiver);

        public abstract int getSignNumber(Object receiver);

        public abstract int[] getMagnitude(Object receiver);

    }

    @ExportLibrary(value = NumberLibrary.class, receiverClass = Integer.class)
    public static class IntegerNumber {
        @ExportMessage
        static boolean isFixedNumber(Integer receiver) {
            return true;
        }

        @ExportMessage
        @ImportStatic(NumberDemo.class)
        static class Add {

            @Specialization(guards = "targetLib.fitsInInt(target)", rewriteOn = ArithmeticException.class, limit = "LIMIT")
            static SmallNumber doSmallNoOverflow(Integer receiver, Object target,
                            @CachedLibrary("target") NumberLibrary targetLib) throws ArithmeticException {
                return new SmallNumber(Math.addExact(receiver, targetLib.asInt(target)));
            }

            @Specialization(replaces = "doSmallNoOverflow", limit = "LIMIT")
            static BigNumber doSmallOverflow(Integer receiver, Object target,
                            @CachedLibrary("receiver") NumberLibrary receiverLib,
                            @CachedLibrary("target") NumberLibrary targetLib,
                            @Cached AddImplNode add) {
                int receiverSign = receiverLib.getSignNumber(receiver);
                int[] receiverMagnitude = receiverLib.getMagnitude(receiver);
                int targetSign = targetLib.getSignNumber(target);
                int[] targetMagnitude = targetLib.getMagnitude(target);
                return add.execute(receiverSign, receiverMagnitude, targetSign, targetMagnitude);
            }

        }

        @ExportMessage
        static boolean fitsInInt(Integer receiver) {
            return true;
        }

        @ExportMessage
        static int asInt(Integer receiver) {
            return receiver;
        }

        @ExportMessage
        static class GetSignNumber {

            @Specialization(guards = "receiver.intValue() < 0")
            static int doNegative(Integer receiver) {
                return -1;
            }

            @Specialization(guards = "receiver.intValue() >= 0")
            static int doPositive(Integer receiver) {
                return 1;
            }

        }

        @ExportMessage
        static class GetMagnitude {

            @Specialization(guards = "receiver.intValue() == 0")
            static int[] doZero(Integer receiver) {
                return BigNumber.ZERO_MAG;
            }

            @Specialization(guards = "receiver.intValue() < 0")
            static int[] doNegative(Integer receiver) {
                return new int[]{-receiver};
            }

            @Specialization(guards = "receiver.intValue() > 0")
            static int[] doPositive(Integer receiver) {
                return new int[]{receiver};
            }

        }

    }

    @ExportLibrary(NumberLibrary.class)
    public static class BigNumber {

        private static final int[] ZERO_MAG = new int[0];

        // TODO should not need to be public
        public final int signum;
        public final int[] magnitude;

        BigNumber(int signum, int[] mag) {
            this.signum = signum;
            this.magnitude = mag;
        }

        @ExportMessage
        boolean isFixedNumber() {
            return true;
        }

        @ExportMessage
        @ImportStatic(NumberDemo.class)
        static class Add {

            @Specialization(limit = "LIMIT")
            static BigNumber doSmallOverflow(BigNumber receiver, Object target,
                            @CachedLibrary("receiver") NumberLibrary receiverLib,
                            @CachedLibrary("target") NumberLibrary targetLib,
                            @Cached AddImplNode add) {
                int receiverSign = receiverLib.getSignNumber(receiver);
                int[] receiverMagnitude = receiverLib.getMagnitude(receiver);
                int targetSign = targetLib.getSignNumber(target);
                int[] targetMagnitude = targetLib.getMagnitude(target);
                return add.execute(receiverSign, receiverMagnitude, targetSign, targetMagnitude);
            }
        }

        @ExportMessage
        final int getSignNumber() {
            return signum;
        }

        @ExportMessage
        final int[] getMagnitude() {
            return magnitude;
        }

        @ExportMessage
        boolean fitsInInt() {
            return magnitude.length <= 1;
        }

        @ExportMessage
        static class AsInt {

            @Specialization(guards = {"receiver.signum > 0", "receiver.magnitude.length == 1"})
            static int doSingleDigitPositive(BigNumber receiver) {
                return receiver.magnitude[0];
            }

            @Specialization(replaces = "doSingleDigitPositive", guards = {"receiver.magnitude.length == 1"})
            static int doSingleDigit(BigNumber receiver) {
                return receiver.magnitude[0] * receiver.signum;
            }

            @Specialization(guards = {"receiver.magnitude.length == 0"})
            static int doZero(BigNumber receiver) {
                return 0;
            }

            @Fallback
            static int fallback(BigNumber receiver) {
                CompilerDirectives.transferToInterpreter();
                throw new UnsupportedOperationException();
            }
        }

    }

    @GenerateUncached
    abstract static class AddImplNode extends Node {

        /**
         * This mask is used to obtain the value of an int as if it were unsigned.
         */
        static final long LONG_MASK = 0xffffffffL;

        abstract BigNumber execute(int signLeft, int[] magnLeft, int signRight, int[] magnRight);

        @Specialization
        static BigNumber doAdd(int signLeft, int[] magnLeft, int signRight, int[] magnRight) {
            return null;
        }

        /**
         * Adds the contents of the int arrays x and y. This method allocates a new int array to
         * hold the answer and returns a reference to that array.
         */
        private static int[] add(int[] x, int[] y) {
            // If x is shorter, swap the two arrays
            if (x.length < y.length) {
                int[] tmp = x;
                x = y;
                y = tmp;
            }

            int xIndex = x.length;
            int yIndex = y.length;
            int[] result = new int[xIndex];
            long sum = 0;
            if (yIndex == 1) {
                sum = (x[--xIndex] & LONG_MASK) + (y[0] & LONG_MASK);
                result[xIndex] = (int) sum;
            } else {
                // Add common parts of both numbers
                while (yIndex > 0) {
                    sum = (x[--xIndex] & LONG_MASK) +
                                    (y[--yIndex] & LONG_MASK) + (sum >>> 32);
                    result[xIndex] = (int) sum;
                }
            }
            // Copy remainder of longer number while carry propagation is required
            boolean carry = (sum >>> 32 != 0);
            while (xIndex > 0 && carry) {
                carry = ((result[--xIndex] = x[xIndex] + 1) == 0);
            }

            // Copy remainder of longer number
            while (xIndex > 0) {
                result[--xIndex] = x[xIndex];
            }

            // Grow result if necessary
            if (carry) {
                int[] bigger = new int[result.length + 1];
                System.arraycopy(result, 0, bigger, 1, result.length);
                bigger[0] = 0x01;
                return bigger;
            }
            return result;
        }

    }

    @ExportLibrary(NumberLibrary.class)
    public static class SmallNumber {

        // TODO should not need to be public
        final int value;

        SmallNumber(int value) {
            this.value = value;
        }

        @ExportMessage
        boolean isFixedNumber() {
            return true;
        }

        @ExportMessage
        boolean fitsInInt() {
            return true;
        }

        @ExportMessage
        int asInt() {
            return value;
        }

        @ExportMessage
        final Object add(Object value) {
            return null;
        }

        @ExportMessage
        static class GetSignNumber {

            @Specialization(guards = "receiver.value < 0")
            static int doNegative(SmallNumber receiver) {
                return -1;
            }

            @Specialization(guards = "receiver.value >= 0")
            static int doPositive(SmallNumber receiver) {
                return 1;
            }

        }

        @ExportMessage
        static class GetMagnitude {

            @Specialization(guards = "receiver.value == 0")
            static int[] doZero(SmallNumber receiver) {
                return BigNumber.ZERO_MAG;
            }

            @Specialization(guards = "receiver.value < 0")
            static int[] doNegative(SmallNumber receiver) {
                return new int[]{-receiver.value};
            }

            @Specialization(guards = "receiver.value > 0")
            static int[] doPositive(SmallNumber receiver) {
                return new int[]{receiver.value};
            }

        }

    }

}
