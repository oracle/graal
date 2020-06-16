/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.tests.debug;

import com.oracle.truffle.api.debug.DebugValue;

import java.math.BigInteger;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

abstract class LLVMDebugValue {

    public static final String UNAVAILABLE = "<unavailable>";

    private static String getActualType(DebugValue value) {
        final DebugValue typeValue = value.getMetaObject();
        return typeValue != null ? typeValue.getMetaQualifiedName() : null;
    }

    private final String kind;
    private final String expectedType;

    // known bugs in LLVM debug information may cause values to be impossible to retrieve
    private final boolean isBuggy;

    private LLVMDebugValue(String kind, String expectedType, boolean isBuggy) {
        this.kind = kind;
        this.expectedType = expectedType;
        this.isBuggy = isBuggy;
    }

    String getKind() {
        return kind;
    }

    String getExpectedType() {
        return expectedType;
    }

    boolean isBuggy() {
        return isBuggy;
    }

    void checkType(DebugValue value) {
        final String actualType = getActualType(value);
        assertEquals("Unexpected type!", expectedType, actualType);
    }

    void check(DebugValue actualValue) {
        checkType(actualValue);
        if (!isBuggy()) {
            checkValue(actualValue);
        }
    }

    abstract String getExpectedDisplayValue();

    abstract void checkValue(DebugValue value);

    static final class Any extends LLVMDebugValue {

        Any(String expectedType) {
            // the value can be anything, it does not matter if it is buggy or not
            super(Trace.KEYWORD_KIND_ANY, expectedType, false);
        }

        @Override
        String getExpectedDisplayValue() {
            return null;
        }

        @Override
        void checkValue(DebugValue value) {
        }
    }

    static final class Unavailable extends LLVMDebugValue {

        Unavailable(String expectedType, boolean isBuggy) {
            super(Trace.KEYWORD_KIND_ANY, expectedType, isBuggy);
        }

        @Override
        String getExpectedDisplayValue() {
            return UNAVAILABLE;
        }

        @Override
        void checkValue(DebugValue value) {
            assertEquals(UNAVAILABLE, value.toDisplayString());
        }
    }

    static final class Char extends LLVMDebugValue {

        private final char expected;

        Char(String expectedType, char expected, boolean isBuggy) {
            super(Trace.KEYWORD_KIND_CHAR, expectedType, isBuggy);
            this.expected = expected;
        }

        @Override
        String getExpectedDisplayValue() {
            return String.valueOf(expected);
        }

        @Override
        void checkValue(DebugValue value) {
            final String val = value.toDisplayString();
            if (val == null || val.length() != 1) {
                throw new AssertionError(String.format("Expected character '%s', but was %s", expected, val));
            }
            assertEquals(expected, val.charAt(0));
        }
    }

    static final class Int extends LLVMDebugValue {

        private final BigInteger expected;

        @Override
        String getExpectedDisplayValue() {
            return String.valueOf(expected);
        }

        Int(String expectedType, BigInteger expected, boolean isBuggy) {
            super(Trace.KEYWORD_KIND_INT, expectedType, isBuggy);
            this.expected = expected;
        }

        @Override
        void checkValue(DebugValue value) {
            try {
                final String val = value.toDisplayString();
                final BigInteger actual = new BigInteger(val);
                assertEquals(expected, actual);
            } catch (NumberFormatException nfe) {
                throw new AssertionError(String.format("Failed to read value \'%s\'", expected), nfe);
            }
        }
    }

    static final class Float_32 extends LLVMDebugValue {

        private final float expected;

        @Override
        String getExpectedDisplayValue() {
            return String.valueOf(expected);
        }

        Float_32(String expectedType, float expected, boolean isBuggy) {
            super(Trace.KEYWORD_KIND_FLOAT_32, expectedType, isBuggy);
            this.expected = expected;
        }

        @Override
        void checkValue(DebugValue value) {
            try {
                final String val = value.toDisplayString();
                final float actual = Float.parseFloat(val);
                assertEquals(expected, actual, 0.000001);
            } catch (NumberFormatException nfe) {
                throw new AssertionError(String.format("Failed to read value \'%s\'", expected), nfe);
            }
        }
    }

    static final class Float_64 extends LLVMDebugValue {

        private final double expected;

        Float_64(String expectedType, double expected, boolean isBuggy) {
            super(Trace.KEYWORD_KIND_FLOAT_64, expectedType, isBuggy);
            this.expected = expected;
        }

        @Override
        String getExpectedDisplayValue() {
            return String.valueOf(expected);
        }

        @Override
        void checkValue(DebugValue value) {
            try {
                final String val = value.toDisplayString();
                final double actual = Double.parseDouble(val);
                assertEquals(expected, actual, 0.000001);
            } catch (NumberFormatException nfe) {
                throw new AssertionError(String.format("Failed to read value \'%s\'", expected), nfe);
            }
        }
    }

    static final class Address extends LLVMDebugValue {

        private final String expected;

        Address(String expectedType, String expected, boolean isBuggy) {
            super(Trace.KEYWORD_KIND_ADDRESS, expectedType, isBuggy);
            this.expected = expected.toLowerCase();
        }

        @Override
        String getExpectedDisplayValue() {
            return String.valueOf(expected);
        }

        @Override
        void checkValue(DebugValue value) {
            final String actual = value.toDisplayString().toLowerCase();
            assertEquals(expected, actual);
        }
    }

    static final class Exact extends LLVMDebugValue {

        private final String expected;

        Exact(String expectedType, String expected, boolean isBuggy) {
            super(Trace.KEYWORD_KIND_EXACT, expectedType, isBuggy);
            this.expected = expected;
        }

        @Override
        String getExpectedDisplayValue() {
            return String.valueOf(expected);
        }

        @Override
        void checkValue(DebugValue value) {
            final String actual = value.toDisplayString();
            assertEquals(expected, actual);
        }
    }

    static final class Structured extends LLVMDebugValue {

        private final Map<String, LLVMDebugValue> expectedMembers;

        Structured(String expectedType, boolean isBuggy) {
            super(Trace.KEYWORD_KIND_STRUCTURED, expectedType, isBuggy);
            expectedMembers = new HashMap<>();
        }

        void addMember(String name, LLVMDebugValue value) {
            expectedMembers.put(name, value);
        }

        Map<String, LLVMDebugValue> getExpectedMembers() {
            return expectedMembers;
        }

        @Override
        String getExpectedDisplayValue() {
            return "";
        }

        @Override
        void check(DebugValue actualValue) {
            checkType(actualValue);

            final Collection<DebugValue> actualMembers = actualValue.getProperties();
            if (actualMembers == null) {
                if (expectedMembers.isEmpty()) {
                    return;
                } else {
                    throw new AssertionError(String.format("Unexpected number of members: expected %d, but got 0", expectedMembers.size()));
                }
            }

            for (DebugValue actual : actualMembers) {
                final String name = actual.getName();
                final LLVMDebugValue expected = expectedMembers.get(name);

                if (expected == null) {
                    throw new AssertionError(String.format("Unexpected member: %s", name));
                }

                try {
                    expected.check(actual);
                } catch (Throwable t) {
                    throw new AssertionError(String.format("Error in member %s", name), t);
                }
            }

            assertEquals("Unexpected number of members", expectedMembers.size(), actualMembers.size());
        }

        @Override
        void checkValue(DebugValue value) {
            // the value of a structured variable is expected to be its address in memory, it is
            // unimportant as long as the members and their values are correct
        }
    }
}
