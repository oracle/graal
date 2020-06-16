/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.parser;

import com.oracle.truffle.regex.UnsupportedRegexException;

public class Counter {

    protected int count = 0;

    public int getCount() {
        return count;
    }

    public void reset() {
        count = 0;
    }

    public int inc() {
        return inc(1);
    }

    public int inc(int i) {
        int ret = count;
        count += i;
        return ret;
    }

    public int dec() {
        return dec(1);
    }

    public int dec(int i) {
        int ret = count;
        count -= i;
        return ret;
    }

    public static class ThresholdCounter extends Counter {

        private final int max;
        private final String errorMsg;

        public ThresholdCounter(int max, String errorMsg) {
            this.max = max;
            this.errorMsg = errorMsg;
        }

        @Override
        public int inc(int i) {
            final int ret = super.inc(i);
            if (getCount() > max) {
                throw new UnsupportedRegexException(errorMsg);
            }
            return ret;
        }
    }

    public static class ThreadSafeCounter extends Counter {

        @Override
        public int inc() {
            int c = count;
            if (c < Integer.MAX_VALUE) {
                count = c + 1;
            }
            return count;
        }

        @Override
        public int inc(int i) {
            int c = count;
            if (c <= Integer.MAX_VALUE - i) {
                count = c + i;
            }
            return count;
        }

        @Override
        public int dec() {
            int c = count;
            if (c > Integer.MIN_VALUE) {
                count = c - 1;
            }
            return count;
        }

        @Override
        public int dec(int i) {
            int c = count;
            if (c >= Integer.MIN_VALUE + i) {
                count = c - i;
            }
            return count;
        }
    }
}
