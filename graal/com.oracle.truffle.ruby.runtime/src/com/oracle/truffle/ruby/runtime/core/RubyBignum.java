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

import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.core.array.*;
import com.oracle.truffle.ruby.runtime.objects.*;

/**
 * Represents the Ruby {@code Bignum} class.
 */
public class RubyBignum extends RubyObject implements Unboxable {

    private final BigInteger value;

    public RubyBignum(RubyClass bignumClass, BigInteger value) {
        super(bignumClass);

        assert value != null;

        this.value = value;
    }

    public BigInteger getValue() {
        return value;
    }

    public Object unbox() {
        return value;
    }

    public static RubyArray divMod(RubyContext context, BigInteger a, BigInteger b) {
        final BigInteger[] quotientRemainder = a.divideAndRemainder(b);

        final Object quotient = GeneralConversions.fixnumOrBignum(quotientRemainder[0]);
        final Object remainder = GeneralConversions.fixnumOrBignum(quotientRemainder[1]);

        final ObjectImmutablePairArrayStore store = new ObjectImmutablePairArrayStore(quotient, remainder);
        return new RubyArray(context.getCoreLibrary().getArrayClass(), store);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof RubyBignum)) {
            return false;
        }
        RubyBignum other = (RubyBignum) obj;
        if (value == null) {
            if (other.value != null) {
                return false;
            }
        } else if (!value.equals(other.value)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return value.toString();
    }

}
