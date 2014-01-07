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
 * Represents the Ruby {@code Exception} class.
 */
public class RubyException extends RubyObject {

    /**
     * The class from which we create the object that is {@code Exception}. A subclass of
     * {@link RubyClass} so that we can override {@link #newInstance} and allocate a
     * {@link RubyException} rather than a normal {@link RubyBasicObject}.
     */
    public static class RubyExceptionClass extends RubyClass {

        public RubyExceptionClass(RubyClass superClass, String name) {
            super(null, superClass, name);
        }

        @Override
        public RubyBasicObject newInstance() {
            return new RubyException(this);
        }

    }

    private RubyString message;

    public RubyException(RubyClass rubyClass) {
        super(rubyClass);
        message = rubyClass.getContext().makeString("(object uninitialized)");
    }

    public RubyException(RubyClass rubyClass, String message) {
        this(rubyClass, rubyClass.getContext().makeString(message));
    }

    public RubyException(RubyClass rubyClass, RubyString message) {
        this(rubyClass);
        initialize(message);
    }

    public void initialize(RubyString setMessage) {
        assert setMessage != null;
        message = setMessage;
    }

    public RubyString getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return message.toString();
    }

}
