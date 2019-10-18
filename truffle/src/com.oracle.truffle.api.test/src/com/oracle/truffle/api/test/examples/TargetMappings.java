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
package com.oracle.truffle.api.test.examples;

import org.graalvm.polyglot.HostAccess;

/**
 * This class contains useful lossy coercions from host to guest types.
 */
public class TargetMappings {

    /**
     * Enables coercions from numbers to characters.
     */
    public static HostAccess.Builder enableCharToIntegerCoercion(HostAccess.Builder builder) {
        builder.targetTypeMapping(Integer.class, Character.class, (v) -> v >= 0 && v < 65536, (v) -> (char) (int) v);
        return builder;
    }

    /**
     * Enables lossy coercions between number and string types.
     */
    public static HostAccess.Builder enableStringCoercions(HostAccess.Builder builder) {
        // String to primitive coercion
        builder.targetTypeMapping(String.class, Byte.class, (v) -> parseByteOrThrow(v) != null, (v) -> parseByteOrThrow(v));
        builder.targetTypeMapping(String.class, Short.class, (v) -> parseShortOrThrow(v) != null, (v) -> parseShortOrThrow(v));
        builder.targetTypeMapping(String.class, Integer.class, (v) -> parseIntOrThrow(v) != null, (v) -> parseIntOrThrow(v));
        builder.targetTypeMapping(String.class, Long.class, (v) -> parseLongOrThrow(v) != null, (v) -> parseLongOrThrow(v));
        builder.targetTypeMapping(String.class, Float.class, (v) -> parseFloatOrThrow(v) != null, (v) -> parseFloatOrThrow(v));
        builder.targetTypeMapping(String.class, Double.class, (v) -> parseDoubleOrThrow(v) != null, (v) -> parseDoubleOrThrow(v));
        builder.targetTypeMapping(String.class, Boolean.class, (v) -> parseBooleanOrNull(v) != null, (v) -> parseBooleanOrNull(v));

        // Primitive to String coercion
        builder.targetTypeMapping(Number.class, String.class, null, (v) -> v.toString());
        builder.targetTypeMapping(Boolean.class, String.class, null, (v) -> v.toString());
        return builder;
    }

    private static boolean isValidFloatString(String s) {
        if (s.isEmpty()) {
            return false;
        }
        char first = s.charAt(0);
        char last = s.charAt(s.length() - 1);
        // Disallow leading or trailing whitespace.
        if (first <= ' ' || last <= ' ') {
            return false;
        }
        // Disallow float type suffix.
        switch (last) {
            case 'D':
            case 'F':
            case 'd':
            case 'f':
                return false;
            default:
                return true;
        }
    }

    private static Boolean parseBooleanOrNull(String s) {
        if ("true".equals(s)) {
            return Boolean.TRUE;
        } else if ("false".equals(s)) {
            return Boolean.FALSE;
        } else {
            return null;
        }
    }

    private static Byte parseByteOrThrow(String s) {
        try {
            return Byte.parseByte(s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Short parseShortOrThrow(String s) {
        try {
            return Short.parseShort(s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Integer parseIntOrThrow(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Long parseLongOrThrow(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Double parseDoubleOrThrow(String s) {
        try {
            if (isValidFloatString(s)) {
                return Double.parseDouble(s);
            }
        } catch (NumberFormatException ex) {
            return null;
        }
        return null;
    }

    private static Float parseFloatOrThrow(String s) {
        try {
            if (isValidFloatString(s)) {
                double doubleValue = Double.parseDouble(s);
                float floatValue = (float) doubleValue;
                // The value does not fit into float if:
                // * the float value is zero but the double value is non-zero (too small)
                // * the float value is infinite but the double value is finite (too large)
                if ((floatValue != 0.0 || doubleValue == 0.0) && (Float.isFinite(floatValue) || !Double.isFinite(doubleValue))) {
                    return floatValue;
                }
            }
        } catch (NumberFormatException ex) {
            return null;
        }
        return null;
    }

}
