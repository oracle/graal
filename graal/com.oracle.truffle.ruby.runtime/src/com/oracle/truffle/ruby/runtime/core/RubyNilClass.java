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

import com.oracle.truffle.ruby.runtime.*;

/**
 * Represents the Ruby {@code NilClass} class.
 */
public class RubyNilClass extends RubyObject {

    public RubyNilClass(RubyClass rubyClass) {
        super(rubyClass);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof RubyNilClass || other instanceof NilPlaceholder;
    }

    @Override
    public int hashCode() {
        return 0;
    }

    public static boolean isNil(Object block) {
        return block instanceof NilPlaceholder || block instanceof RubyNilClass;
    }

    @Override
    public String toString() {
        return "";
    }

}
