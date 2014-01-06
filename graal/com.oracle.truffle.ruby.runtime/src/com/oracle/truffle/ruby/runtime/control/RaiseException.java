/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.runtime.control;

import com.oracle.truffle.ruby.runtime.objects.*;

/**
 * Ruby exceptions are just Ruby objects, so they cannot also be exceptions unless we made all Ruby
 * objects exceptions. A simpler approach is to wrap Ruby exceptions in Java exceptions when we want
 * to throw them. The error messages match MRI. Note that throwing is different to raising in Ruby,
 * which is the reason we have both {@link ThrowException} and {@link RaiseException}.
 */
public class RaiseException extends RuntimeException {

    private final RubyBasicObject rubyException;

    public RaiseException(RubyBasicObject rubyException) {
        this.rubyException = rubyException;
    }

    @Override
    public String toString() {
        return rubyException.toString();
    }

    @Override
    public String getMessage() {
        return rubyException.toString();
    }

    public RubyBasicObject getRubyException() {
        return rubyException;
    }

    private static final long serialVersionUID = 7501185855599094740L;

}
