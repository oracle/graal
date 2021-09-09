/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.core.log;

import org.graalvm.nativeimage.LogHandler;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.UnsignedWord;

/**
 * Extensions to {@link LogHandler} for supporting suppression of fatal error logging (e.g.,
 * implementing flood control) and redirection of fatal error logging (e.g., redirect fatal error
 * logging to a {@code hs_err_pid<NNNN>_libjvmci.log} file).
 */
public interface LogHandlerExtension extends LogHandler {

    /**
     * This method is called by the default implementation of {@link #enterFatalContext}.
     *
     * The implementor returns {@code true} if fatal error information should be sent to
     * {@link #log(CCharPointer, UnsignedWord)}. Returning {@code false} specifies that no further
     * information is to be logged and the VM must immediately call {@link #fatalError()}.
     *
     * @param callerIP the address of the call-site where the fatal error occurred
     * @param msg provides optional text that was passed to the fatal error call
     * @param ex provides optional exception object that was passed to the fatal error call
     *
     * @return {@code false} if the VM must not log error information before calling
     *         {@link #fatalError()}
     *
     * @since 20.3
     * @see #enterFatalContext
     */
    default boolean fatalContext(CodePointer callerIP, String msg, Throwable ex) {
        return true;
    }

    /**
     * This method gets called if the VM finds itself in a fatal, non-recoverable error situation.
     *
     * The implementor returns a non-null {@link Log} object if fatal error information should be
     * sent to the log object. Returning {@code null} specifies that no further information is to be
     * logged and the VM must immediately call {@link #fatalError()}.
     *
     * @param callerIP the address of the call-site where the fatal error occurred
     * @param msg provides optional text that was passed to the fatal error call
     * @param ex provides optional exception object that was passed to the fatal error call
     *
     * @return the {@link Log} object to which fatal logging is sent or {@code null} if the VM must
     *         not do any further logging before calling {@link #fatalError()}
     *
     * @since 21.3
     */
    default Log enterFatalContext(CodePointer callerIP, String msg, Throwable ex) {
        if (fatalContext(callerIP, msg, ex)) {
            return Log.log();
        }
        return null;
    }
}
