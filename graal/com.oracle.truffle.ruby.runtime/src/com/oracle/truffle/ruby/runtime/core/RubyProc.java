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
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.control.*;
import com.oracle.truffle.ruby.runtime.methods.*;
import com.oracle.truffle.ruby.runtime.objects.*;

/**
 * Represents the Ruby {@code Proc} class.
 */
public class RubyProc extends RubyObject {

    /**
     * The class from which we create the object that is {@code Proc}. A subclass of
     * {@link RubyClass} so that we can override {@link #newInstance} and allocate a
     * {@link RubyProc} rather than a normal {@link RubyBasicObject}.
     */
    public static class RubyProcClass extends RubyClass {

        public RubyProcClass(RubyClass objectClass) {
            super(null, objectClass, "Proc");
        }

        @Override
        public RubyBasicObject newInstance() {
            return new RubyProc(this);
        }

    }

    public static enum Type {
        PROC, LAMBDA
    }

    @CompilationFinal private Type type;
    @CompilationFinal private Object self;
    @CompilationFinal private RubyProc block;
    @CompilationFinal private RubyMethod method;

    public RubyProc(RubyClass procClass) {
        super(procClass);
    }

    public RubyProc(RubyClass procClass, Type type, Object self, RubyProc block, RubyMethod method) {
        super(procClass);
        initialize(type, self, block, method);
    }

    public void initialize(Type setType, Object setSelf, RubyProc setBlock, RubyMethod setMethod) {
        assert setSelf != null;
        assert RubyContext.shouldObjectBeVisible(setSelf);
        type = setType;
        self = setSelf;
        block = setBlock;
        method = setMethod;
    }

    public Object getSelf() {
        return self;
    }

    @CompilerDirectives.SlowPath
    public Object call(PackedFrame caller, Object... args) {
        return callWithModifiedSelf(caller, self, args);
    }

    public Object callWithModifiedSelf(PackedFrame caller, Object modifiedSelf, Object... args) {
        assert modifiedSelf != null;

        try {
            return method.call(caller, modifiedSelf, block, args);
        } catch (ReturnException e) {
            switch (type) {
                case PROC:
                    throw e;
                case LAMBDA:
                    return e.getValue();
                default:
                    throw new IllegalStateException();
            }
        }
    }

    public RubyMethod getMethod() {
        return method;
    }

    public Type getType() {
        return type;
    }

    public RubyProc getBlock() {
        return block;
    }

}
