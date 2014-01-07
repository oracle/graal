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

import com.oracle.truffle.ruby.runtime.objects.*;

/**
 * Represents the Ruby {@code Float} class.
 */
public class RubyFloat extends RubyObject implements Unboxable {

    private final double value;

    public RubyFloat(RubyClass floatClass, double value) {
        super(floatClass);
        this.value = value;
    }

    public double getValue() {
        return value;
    }

    @Override
    public String toString() {
        return Double.toString(value);
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof Integer) {
            return value == (int) other;
        } else if (other instanceof RubyFixnum) {
            return value == ((RubyFixnum) other).getValue();
        } else if (other instanceof Double) {
            return value == (double) other;
        } else if (other instanceof RubyFloat) {
            return value == ((RubyFloat) other).value;
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
