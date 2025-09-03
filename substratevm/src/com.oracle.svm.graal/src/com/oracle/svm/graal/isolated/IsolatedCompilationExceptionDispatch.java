/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.graal.isolated;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;

import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.threadlocal.FastThreadLocalObject;
import com.oracle.svm.hosted.code.CEntryPointCallStubMethod;
import com.oracle.svm.hosted.code.CEntryPointJavaCallStubMethod;

import jdk.graal.compiler.core.common.GraalBailoutException;

/**
 * Mechanism for dispatching exceptions across isolates with isolated compilation.
 * <p>
 * It relies on entry points having a {@link CEntryPoint#exceptionHandler} that invokes
 * {@link #handleException}, which then calls into the other isolate (the entry point's caller
 * isolate) to record that an exception has occurred. The entry point must further have a
 * {@link CEntryPointOptions#callerEpilogue()} which executes in the caller isolate after the
 * (original) call has returned and calls {@link #throwPendingException} to check whether an
 * exception has been recorded, and if so, throw it. When no exception occurs, this comes at almost
 * no extra cost.
 * <p>
 * Because objects cannot transcend isolate boundaries, exceptions are "rethrown" using a generic
 * exception type with most information preserved in string form in their message.
 */
public abstract class IsolatedCompilationExceptionDispatch {
    private static final RuntimeException EXCEPTION_WITHOUT_MESSAGE = new GraalBailoutException("[no details because exception allocation failed]");

    /**
     * An exception to be thrown in the current isolate as a result of it calling another isolate
     * during which an exception has been caught.
     */
    private static final FastThreadLocalObject<RuntimeException> pendingException = FastThreadLocalFactory.createObject(RuntimeException.class,
                    "IsolatedCompilationExceptionDispatch.pendingException");

    protected static void throwPendingException() {
        RuntimeException pending = pendingException.get();
        if (pending != null) {
            pendingException.set(null);
            throw pending;
        }
    }

    /** Provides the isolate to which an exception in the current isolate should be dispatched. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected abstract IsolateThread getOtherIsolate();

    /**
     * Dispatches an exception that was caught in an entry point to the isolate which called that
     * entry point.
     * <p>
     * Note that the caller isolate cannot have called from uninterruptible code because
     * {@link CEntryPointJavaCallStubMethod} does thread state transitions that require a safepoint
     * check, so this method calling it back to dispatch the exception in interruptible code is
     * considered acceptable.
     * <p>
     * Our (callee) entry point might intend to execute only uninterruptible code save for this
     * exception handler, but as of writing this, isolated compilation nowhere requires relying on
     * that and {@link CEntryPointCallStubMethod} also does state transitions and safepoint checks.
     * <p>
     * Also note that an exception's stack trace contains all its isolate's frames up until the last
     * entry frame, but not another isolate's frames in between. When an exception is propagated
     * through several entry points, this can make the output look confusing at first, but it is not
     * too difficult to make sense of it.
     */
    @Uninterruptible(reason = "Called in exception handler.", calleeMustBe = false)
    protected final int handleException(Throwable t) {
        boolean done;
        try {
            done = dispatchExceptionToOtherIsolate(t);
        } catch (Throwable another) {
            done = false;
        }
        if (!done) {
            // Being uninterruptible, this should never fail:
            dispatchExceptionWithoutMessage(getOtherIsolate());
        }
        return 0;
    }

    @NeverInline("Ensure that an exception thrown from this method can always be caught.")
    private boolean dispatchExceptionToOtherIsolate(Throwable t) {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        try (PrintWriter pw = new PrintWriter(os)) {
            pw.print("{ ");
            t.printStackTrace(pw); // trailing newline
            pw.print("}");
        }
        try (CTypeConversion.CCharPointerHolder cstr = CTypeConversion.toCString(os.toString())) {
            return dispatchException(getOtherIsolate(), cstr.get());
        }
    }

    @CEntryPoint(exceptionHandler = ReturnFalseExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    private static boolean dispatchException(@SuppressWarnings("unused") IsolateThread other, CCharPointer cstr) {
        String message = CTypeConversion.toJavaString(cstr);
        GraalBailoutException exception = new GraalBailoutException(message);
        pendingException.set(exception);
        return true;
    }

    private static final class ReturnFalseExceptionHandler implements CEntryPoint.ExceptionHandler {
        @Uninterruptible(reason = "Exception handler")
        @SuppressWarnings("unused")
        static boolean handle(Throwable t) {
            return false;
        }
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @Uninterruptible(reason = "Called from exception handler, should not raise an exception.")
    private static void dispatchExceptionWithoutMessage(@SuppressWarnings("unused") IsolateThread other) {
        pendingException.set(EXCEPTION_WITHOUT_MESSAGE);
    }
}
