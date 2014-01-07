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

import com.oracle.truffle.api.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.core.*;
import com.oracle.truffle.ruby.runtime.objects.*;

public final class ExceptionTranslator {

    /**
     * Translate a Java exception into a Ruby exception.
     */
    public static RubyBasicObject translateException(RubyContext context, Throwable exception) {
        assert context != null;
        assert exception != null;

        CompilerAsserts.neverPartOfCompilation();

        // RaiseException already includes the Ruby exception

        if (exception instanceof RaiseException) {
            return ((RaiseException) exception).getRubyException();
        }

        // Translate divide by zero into ZeroDivisionError

        if (exception instanceof ArithmeticException && (exception.getMessage().endsWith("divide by zero") || exception.getMessage().endsWith("/ by zero"))) {
            return new RubyException(context.getCoreLibrary().getZeroDivisionErrorClass(), "divided by 0");
        }

        /*
         * If we can't translate the exception into a Ruby exception, then the error is ours and we
         * report it as as RubyTruffleError. If a programmer sees this then it's a bug in our
         * implementation.
         */

        if (context.getConfiguration().getPrintJavaExceptions()) {
            exception.printStackTrace();
        }

        String message;

        if (exception.getMessage() == null) {
            message = exception.getClass().getSimpleName();
        } else {
            message = exception.getMessage();
        }

        return new RubyException(context.getCoreLibrary().getRubyTruffleErrorClass(), message);
    }

}
