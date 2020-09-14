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

public interface LogHandlerExtension extends LogHandler {

    /**
     * This method gets called if the VM finds itself in a fatal, non-recoverable error situation.
     * The callee receives arguments that describes the error. Based on these arguments the
     * implementor can decide if it wants to get more specific error related information via
     * subsequent calls to {@link #log(CCharPointer, UnsignedWord)}. This is requested by returning
     * {@code true}. Returning {@code false} on the other hand will let the VM know that it can skip
     * providing this information and immediately proceed with calling {@link #fatalError()} from
     * where it is expected to never return to the VM.
     * <p>
     * Providing this method allows to implement flood control for fatal errors. The implementor can
     * rely on {@link #fatalError()} getting called soon after this method is called.
     *
     * @param callerIP the address of the call-site where the fatal error occurred
     * @param msg provides optional text that was passed to the fatal error call
     * @param ex provides optional exception object that was passed to the fatal error call
     *
     * @return if {@code false} is returned the VM will skip providing more specific error related
     *         information before calling {@link #fatalError()}.
     *
     * @since 20.3
     */
    default boolean fatalContext(CodePointer callerIP, String msg, Throwable ex) {
        return true;
    }
}
