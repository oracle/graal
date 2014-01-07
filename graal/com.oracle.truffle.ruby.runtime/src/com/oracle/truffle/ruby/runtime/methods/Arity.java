/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.runtime.methods;

import com.oracle.truffle.api.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.control.*;

/**
 * Represents the arity, or parameter contract, of a method.
 */
public class Arity {

    private final int minimum;
    public static final int NO_MINIMUM = 0;

    private final int maximum;
    public static final int NO_MAXIMUM = Integer.MAX_VALUE;

    public static final Arity NO_ARGS = new Arity(0, 0);
    public static final Arity ONE_ARG = new Arity(1, 1);

    public Arity(int minimum, int maximum) {
        this.minimum = minimum;
        this.maximum = maximum;
    }

    public void checkArguments(RubyContext context, Object[] arguments) {
        if (arguments.length < minimum || arguments.length > maximum) {
            CompilerDirectives.transferToInterpreter();
            throw new RaiseException(context.getCoreLibrary().argumentError(arguments.length, minimum));
        }
    }

    public int getMinimum() {
        return minimum;
    }

    public int getMaximum() {
        return maximum;
    }

    @Override
    public String toString() {
        return String.format("Arity(%d, %d)", minimum, maximum);
    }

}
