/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.oracle.svm.interpreter;

import com.oracle.svm.configure.ClassNameSupport;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.guest.staging.jdk.InternalVMMethod;
import com.oracle.svm.shared.AlwaysInline;
import com.oracle.svm.shared.NeverInline;

/**
 * Wraps exceptions thrown by the interpreter or by compiled code. This is a way to
 * differentiate between exceptions caused by the interpreter itself vs. the code
 * being interpreted.
 * <p>
 * This exception must only be used within the interpreter loop ({@code Interpreter.Root#executeBodyFromBCI})
 * and its helper methods. Exceptions escaping the interpreter loop (e.g., during exception unwinding)
 * should be unwrapped. Conversely, exceptions raised by the runtime support routines called from the
 * interpreter loop should be wrapped.
 * <p>
 * GR-74439 will replace this class with a new mechanism for better separating exceptions
 * caused by the interpreter itself vs. the code being interpreted.
 */
@InternalVMMethod
public final class SemanticJavaException extends RuntimeException {
    @java.io.Serial static final long serialVersionUID = 8271499373291031203L;

    private SemanticJavaException(Throwable cause) {
        /*
         * RuntimeException(Throwable) derives the wrapper message from cause.toString(), which can
         * itself throw for guest exceptions with lazy or hostile message formatting. Preserve only
         * the cause so semantic guest exceptions cannot turn into host-side wrapper failures.
         */
        super(null, cause);
    }

    @Override
    @SuppressWarnings("sync-override")
    public Throwable fillInStackTrace() {
        return this;
    }

    @NeverInline("Exception construction")
    public static RuntimeException raise(Throwable cause) {
        InterpreterUtil.assertion(cause != null && !(cause instanceof SemanticJavaException), "bad SemanticJavaException nesting");
        throw new SemanticJavaException(cause);
    }

    @AlwaysInline("Inlined variant of raise")
    static RuntimeException raiseInlined(Throwable cause) {
        InterpreterUtil.assertion(cause != null && !(cause instanceof SemanticJavaException), "bad SemanticJavaException nesting");
        throw new SemanticJavaException(cause);
    }

    @NeverInline("Exception construction")
    static RuntimeException raiseNullPointerException() {
        throw new SemanticJavaException(new NullPointerException());
    }

    @NeverInline("Exception construction")
    static RuntimeException raiseArrayIndexOutOfBoundsException(int index, int length) {
        throw new SemanticJavaException(new ArrayIndexOutOfBoundsException("Index " + index + " out of bounds for length " + length));
    }

    @NeverInline("Exception construction")
    static RuntimeException raiseArrayStoreException(DynamicHub hub) {
        throw new SemanticJavaException(new ArrayStoreException(ClassNameSupport.reflectionNameToTypeName(hub.getName())));
    }

    @NeverInline("Exception construction")
    static RuntimeException raiseIllegalMonitorStateException() {
        throw new SemanticJavaException(new IllegalMonitorStateException());
    }

    private static String cannotCastMsg(Object instance, Class<?> clazz) {
        return "Cannot cast " + instance.getClass().getName() + " to " + clazz.getName();
    }

    @NeverInline("Keep class-cast exception construction out of bytecode-handler stubs")
    static SemanticJavaException raiseClassCastException(Object instance, Class<?> clazz) {
        throw raiseInlined(new ClassCastException(cannotCastMsg(instance, clazz)));
    }

    @NeverInline("Exception construction")
    static RuntimeException raiseInstantiationError(String message) {
        throw new SemanticJavaException(new InstantiationError(message));
    }

    @NeverInline("Exception construction")
    static RuntimeException raiseNegativeArraySizeException(int length) {
        throw new SemanticJavaException(new NegativeArraySizeException(String.valueOf(length)));
    }

    @NeverInline("Exception construction")
    static RuntimeException raiseIncompatibleClassChangeError(String message) {
        throw new SemanticJavaException(new IncompatibleClassChangeError(message));
    }
}
