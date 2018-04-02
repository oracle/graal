/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativeimage;

import java.util.concurrent.TimeUnit;

import org.graalvm.nativeimage.impl.ThreadingSupport;

/**
 * Functionality related to execution in threads.
 *
 * @since 1.0
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
     * @since 1.0
     */
    public static void registerRecurringCallback(long interval, TimeUnit unit, RecurringCallback callback) {
        ImageSingletons.lookup(ThreadingSupport.class).registerRecurringCallback(interval, unit, callback);
    }

    /**
     * Interface that a callback handler needs to implement.
     *
     * @since 1.0
     */
    @FunctionalInterface
    public interface RecurringCallback {
        /**
         * Method that is called recurringly when the callback handler is installed.
         *
         * @since 1.0
         */
        void run(RecurringCallbackAccess access);
    }

    /**
     * Provides methods that are available during the execution of a {@link RecurringCallback}.
     *
     * @since 1.0
     */
    public interface RecurringCallbackAccess {
        /**
         * Throws an exception from the recurring callback to the code that is regularly executing
         * in the thread.
         *
         * @since 1.0
         */
        void throwException(Throwable t);
    }
}
