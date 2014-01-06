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
 * Represents the Ruby {@code TrueClass} class.
 */
public class RubyTrueClass extends RubyObject implements Unboxable {

    public RubyTrueClass(RubyClass objectClass) {
        super(objectClass);
    }

    public Object unbox() {
        return true;
    }

    @Override
    public String toString() {
        return "true";
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof RubyTrueClass || (other instanceof Boolean && (boolean) other);
    }

    @Override
    public int hashCode() {
        return Boolean.TRUE.hashCode();
    }

}
