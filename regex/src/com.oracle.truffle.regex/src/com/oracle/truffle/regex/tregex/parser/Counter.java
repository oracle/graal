/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
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
        return count--;
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
        public int dec() {
            int c = count;
            if (c > Integer.MIN_VALUE) {
                count = c - 1;
            }
            return count;
        }
    }
}
