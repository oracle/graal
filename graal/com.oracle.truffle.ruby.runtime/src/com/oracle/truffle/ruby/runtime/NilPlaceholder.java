/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.runtime;

/**
 * Represents the Ruby {@code Nil} object, but without being a full Ruby object. This allows us to
 * have a simple values that is {@code nil}, but more readily available than the particular instance
 * for a context.
 */
public final class NilPlaceholder {

    public static final NilPlaceholder INSTANCE = new NilPlaceholder();

    private NilPlaceholder() {
    }

    @Override
    public String toString() {
        return "";
    }

}
