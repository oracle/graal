/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.graalvm.nativeimage;

import java.util.concurrent.TimeUnit;

import org.graalvm.nativeimage.impl.ThreadingSupport;

/**
 * Functionality related to execution in threads.
 *
 * @since 19.0
 */
public final class Threading {

    private Threading() {
    }

    /**
     * Registers a {@link RecurringCallback callback handler} that is called by the current thread
     * approximately at the provided interval. Only one callback can be active per thread. Each
     * thread can have its own callback with a different interval (or none at all). No guarantees
     * are made about the actual interval. For example, when the thread is waiting for a lock or
     * executing native code, no callback can be done. Exceptions that are thrown during the
     * execution of the callback are caught and ignored, unless they are thrown via a call to
     * {@link RecurringCallbackAccess#throwException(Throwable)}.
     * <p>
     * Specifying {@code null} for {@code callback} clears the current thread's callback (in which
     * case, the values of {@code interval} and {@code unit} are ignored).
     *
     * @since 19.0
     */
    public static void registerRecurringCallback(long interval, TimeUnit unit, RecurringCallback callback) {
        ImageSingletons.lookup(ThreadingSupport.class).registerRecurringCallback(interval, unit, callback);
    }

    /**
     * Interface that a callback handler needs to implement.
     *
     * @since 19.0
     */
    @FunctionalInterface
    public interface RecurringCallback {
        /**
         * Method that is called recurringly when the callback handler is installed.
         *
         * @since 19.0
         */
        void run(RecurringCallbackAccess access);
    }

    /**
     * Provides methods that are available during the execution of a {@link RecurringCallback}.
     *
     * @since 19.0
     */
    public interface RecurringCallbackAccess {
        /**
         * Throws an exception from the recurring callback to the code that is regularly executing
         * in the thread.
         *
         * @since 19.0
         */
        void throwException(Throwable t);
    }
}
