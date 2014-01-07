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
 * Represents the Ruby {@code FalseClass} class.
 */
public class RubyFalseClass extends RubyObject implements Unboxable {

    public RubyFalseClass(RubyClass objectClass) {
        super(objectClass);
    }

    public Object unbox() {
        return false;
    }

    @Override
    public String toString() {
        return "false";
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof RubyFalseClass || (other instanceof Boolean && !((boolean) other));
    }

    @Override
    public int hashCode() {
        return Boolean.FALSE.hashCode();
    }

}
