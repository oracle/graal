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
import com.oracle.truffle.ruby.runtime.control.*;
import com.oracle.truffle.ruby.runtime.core.array.*;

/**
 * Represents the Ruby {@code Continuation} class. We only support continuations that just move up
 * the stack and are one-shot.
 */
public class RubyContinuation extends RubyObject {

    /*
     * A continuation is dead if we have already resumed it once. We will not be able to resume it
     * again due to the current implementation being an exception thrown to go back up the stack.
     */
    private boolean dead = false;

    public RubyContinuation(RubyClass rubyClass) {
        super(rubyClass);
    }

    /**
     * To enter a continuation means to remember the execution state at this point, reify that into
     * an object, and then call the passed block. For our implementation, the continuation will be
     * dead when this method resumes.
     */
    public Object enter(RubyProc block) {
        try {
            return block.call(null, this);
        } catch (ContinuationReturnException e) {
            // Thrown in call

            // Check the exception is for this continuation

            if (e.getContinuation() == this) {
                return e.getValue();
            } else {
                throw e;
            }
        } finally {
            dead = true;
        }
    }

    /**
     * To call a continuation means to go back to the execution state when it was created. For our
     * implementation we can only do this once, and only if that means jumping back up the stack.
     */
    public void call(Object... args) {
        if (dead) {
            throw new UnsupportedOperationException("Only continuations that just move up the stack and are one-shot are supported");
        }

        Object returnValue;

        if (args.length == 0) {
            returnValue = NilPlaceholder.INSTANCE;
        } else if (args.length == 1) {
            returnValue = args[0];
        } else {
            returnValue = RubyArray.specializedFromObjects(getRubyClass().getContext().getCoreLibrary().getArrayClass(), args);
        }

        // Caught in enter

        throw new ContinuationReturnException(this, returnValue);
    }

}
