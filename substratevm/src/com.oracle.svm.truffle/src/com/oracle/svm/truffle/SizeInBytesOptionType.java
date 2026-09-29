/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.truffle;

import java.util.function.Function;

import org.graalvm.options.OptionType;

/**
 * Represents an option type descriptor that can parse bytes with unit.
 */
public final class SizeInBytesOptionType {

    public static final OptionType<Long> POSITIVE_AND_NEGATIVE = create();

    private static OptionType<Long> create() {
        return new OptionType<>("sizeinbytes", new Function<String, Long>() {

            @Override
            public Long apply(String s) {
                try {
                    SizeUnit foundUnit = null;
                    for (SizeUnit unit : SizeUnit.values()) {
                        if (s.endsWith(unit.symbol)) {
                            foundUnit = unit;
                            break;
                        }
                    }
                    if (foundUnit == null) {
                        throw invalidValue(s);
                    }
                    String subString = s.substring(0, s.length() - foundUnit.symbol.length());
                    long value = Long.parseLong(subString);
                    return Math.multiplyExact(value, foundUnit.factor);
                } catch (NumberFormatException | ArithmeticException e) {
                    throw invalidValue(s);
                }
            }

            private IllegalArgumentException invalidValue(String value) {
                throw new IllegalArgumentException("Invalid size '" + value + "' specified. " //
                                + "A valid size consists of a positive integer value and a byte-based size unit. " //
                                + "For example '512KB' or '100MB'. Valid size units are " //
                                + "'B' for bytes, " //
                                + "'KB' for kilobytes, " //
                                + "'MB' for megabytes, and " //
                                + "'GB' for gigabytes ");
            }
        });
    }

    private SizeInBytesOptionType() {
    }

    private enum SizeUnit {
        GIGABYTE("GB", 1024 * 1024 * 1024),
        MEGABYTE("MB", 1024 * 1024),
        KILOBYTE("KB", 1024),
        BYTE("B", 1);

        private final String symbol;
        private final long factor;

        SizeUnit(String symbol, long factor) {
            this.symbol = symbol;
            this.factor = factor;
        }
    }

}
