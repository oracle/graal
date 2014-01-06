/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.runtime.core;

import java.math.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.ruby.runtime.*;

public class GeneralConversions {

    /**
     * Convert a value to a boolean, without doing any lookup.
     */
    public static boolean toBoolean(Object value) {
        assert value != null;

        if (value instanceof NilPlaceholder) {
            return false;
        }

        if (value instanceof Boolean) {
            return (boolean) value;
        }

        if (value instanceof RubyTrueClass) {
            return true;
        }

        if (value instanceof RubyFalseClass) {
            return false;
        }

        return true;
    }

    /**
     * Convert a value to a {@code Fixnum}, without doing any lookup.
     */
    public static int toFixnum(Object value) {
        assert value != null;

        if (value instanceof NilPlaceholder || value instanceof RubyNilClass) {
            return 0;
        }

        if (value instanceof Integer) {
            return (int) value;
        }

        if (value instanceof RubyFixnum) {
            return ((RubyFixnum) value).getValue();
        }

        if (value instanceof BigInteger) {
            throw new UnsupportedOperationException();
        }

        if (value instanceof RubyBignum) {
            throw new UnsupportedOperationException();
        }

        if (value instanceof Double) {
            return (int) (double) value;
        }

        if (value instanceof RubyFloat) {
            return (int) ((RubyFloat) value).getValue();
        }

        CompilerDirectives.transferToInterpreter();

        throw new UnsupportedOperationException(value.getClass().toString());
    }

    /**
     * Convert a value to a {@code Float}, without doing any lookup.
     */
    public static double toFloat(Object value) {
        assert value != null;

        if (value instanceof NilPlaceholder || value instanceof RubyNilClass) {
            return 0;
        }

        if (value instanceof Integer) {
            return (int) value;
        }

        if (value instanceof RubyFixnum) {
            return ((RubyFixnum) value).getValue();
        }

        if (value instanceof BigInteger) {
            return ((BigInteger) value).doubleValue();
        }

        if (value instanceof RubyBignum) {
            return ((RubyBignum) value).getValue().doubleValue();
        }

        if (value instanceof Double) {
            return (double) value;
        }

        if (value instanceof RubyFloat) {
            return ((RubyFloat) value).getValue();
        }

        CompilerDirectives.transferToInterpreter();

        throw new UnsupportedOperationException();
    }

    /**
     * Given a {@link BigInteger} value, produce either a {@code Fixnum} or {@code Bignum} .
     */
    public static Object fixnumOrBignum(BigInteger value) {
        assert value != null;

        if (value.compareTo(RubyFixnum.MIN_VALUE_BIG) >= 0 && value.compareTo(RubyFixnum.MAX_VALUE_BIG) <= 0) {
            return value.intValue();
        } else {
            return value;
        }
    }

    /**
     * Given a {@code long} value, produce either a {@code Fixnum} or {@code Bignum} .
     */
    public static Object fixnumOrBignum(long value) {
        if (value >= RubyFixnum.MIN_VALUE && value <= RubyFixnum.MAX_VALUE) {
            return (int) value;
        } else {
            return BigInteger.valueOf(value);
        }
    }

    /**
     * Given a reference, produce either {@code nil} or the object. .
     */
    public static Object instanceOrNil(Object object) {
        if (object == null) {
            return NilPlaceholder.INSTANCE;
        } else {
            return object;
        }
    }

}
