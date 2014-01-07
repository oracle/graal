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

import com.oracle.truffle.api.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.control.*;
import com.oracle.truffle.ruby.runtime.objects.*;

/**
 * Represents the Ruby {@code Object} class.
 */
public class RubyObject extends RubyBasicObject {

    public boolean frozen = false;

    public RubyObject(RubyClass rubyClass) {
        super(rubyClass);
    }

    public void checkFrozen() {
        if (frozen) {
            CompilerDirectives.transferToInterpreter();
            throw new RaiseException(getRubyClass().getContext().getCoreLibrary().frozenError(getRubyClass().getName().toLowerCase()));
        }
    }

    public Object dup() {
        final RubyObject newObject = new RubyObject(rubyClass);
        newObject.setInstanceVariables(getInstanceVariables());
        return newObject;
    }

    public static String checkInstanceVariableName(RubyContext context, String name) {
        if (!name.startsWith("@")) {
            throw new RaiseException(context.getCoreLibrary().nameErrorInstanceNameNotAllowable(name));
        }

        return name.substring(1);
    }

}
