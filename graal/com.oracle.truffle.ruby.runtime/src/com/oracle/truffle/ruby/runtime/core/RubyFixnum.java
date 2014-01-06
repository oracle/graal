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

import com.oracle.truffle.ruby.runtime.objects.*;

/**
 * Represents the Ruby {@code Fixnum} class.
 */
public class RubyFixnum extends RubyObject implements Unboxable {

    public static final int MIN_VALUE = Integer.MIN_VALUE;
    public static final int MAX_VALUE = Integer.MAX_VALUE;

    public static final BigInteger MIN_VALUE_BIG = BigInteger.valueOf(MIN_VALUE);
    public static final BigInteger MAX_VALUE_BIG = BigInteger.valueOf(MAX_VALUE);

    public static final int SIZE = Integer.SIZE;

    private final int value;

    public RubyFixnum(RubyClass fixnumClass, int value) {
        super(fixnumClass);
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    @Override
    public String toString() {
        return Integer.toString(value);
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof Integer) {
            return value == (int) other;
        } else if (other instanceof RubyFixnum) {
            return value == ((RubyFixnum) other).value;
        } else if (other instanceof BigInteger) {
            return ((BigInteger) other).equals(value);
        } else if (other instanceof RubyBignum) {
            return ((RubyBignum) other).getValue().equals(value);
        } else if (other instanceof Double) {
            return value == (double) other;
        } else if (other instanceof RubyFloat) {
            return value == ((RubyFloat) other).getValue();
        } else {
            return super.equals(other);
        }
    }

    @Override
    public int hashCode() {
        throw new UnsupportedOperationException();
    }

    public Object unbox() {
        return value;
    }

}
