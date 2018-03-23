/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api;

import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

/**
 * Represents an exception thrown during the execution of a guest language program. All exceptions
 * thrown in a guest language implementation that are not implementing {@link TruffleException} are
 * considered {@link #isInternalError() internal} errors.
 *
 * {@link TruffleException} is most efficient if the {@link Throwable#getCause() cause} is left
 * uninitialized. Implementations of {@link TruffleException} also should not prevent initialization
 * of the exception cause, e.g., by overriding {@link Throwable#initCause(Throwable)}.
 *
 * In order to be efficient, a {@link TruffleException} should override
 * {@link Throwable#fillInStackTrace()} so that it returns {@code null}.
 *
 * @since 0.27
 * @see TruffleStackTraceElement#getStackTrace(Throwable) To access the stack trace of an exception.
 */
public interface TruffleException {

    /**
     * Returns a node indicating the location where this exception occurred in the AST. This method
     * may <code>null</code> to indicate that the location is not available.
     *
     * @since 0.27
     */
    Node getLocation();

    /**
     * Returns an additional guest language object. The return object must be an interop type so it
     * must be either implementing TruffleObject or be a primitive value. The default implementation
     * returns <code>null</code> to indicate that no object is available for this exception.
     *
     * @since 0.27
     */
    default Object getExceptionObject() {
        return null;
    }

    /**
     * Returns <code>true</code> if this exception indicates a parser or syntax error. Syntax errors
     * typically occur while
     * {@link TruffleLanguage#parse(com.oracle.truffle.api.TruffleLanguage.ParsingRequest) parsing}
     * of guest language source code.
     *
     * @since 0.27
     */
    default boolean isSyntaxError() {
        return false;
    }

    /**
     * Returns <code>true</code> if this exception indicates a syntax error that is indicating that
     * the syntax is incomplete. This allows guest language programmers to find out if more code is
     * expected from a given source. For example an incomplete JavaScript program could look like
     * this:
     *
     * <pre>
     * function incompleteFunction(arg) {
     * </pre>
     *
     * A shell might react to this exception and prompt for additional source code, if this method
     * returns <code>true</code>.
     *
     * @since 0.27
     */
    default boolean isIncompleteSource() {
        return false;
    }

    /**
     * Returns <code>true</code> if this exception indicates an internal error. Note that all
     * exceptions thrown in a guest language implementation that are not implementing
     * {@link TruffleException} are considered internal.
     *
     * @since 0.27
     */
    default boolean isInternalError() {
        return false;
    }

    /**
     * Returns <code>true</code> if this exception indicates that guest language application was
     * cancelled during its execution.
     *
     * @since 0.27
     */
    default boolean isCancelled() {
        return false;
    }

    /**
     * @since 0.27
     * @deprecated in 0.27
     */
    @Deprecated
    default boolean isTimeout() {
        return isCancelled();
    }

    /**
     * Returns <code>true</code> if the exception indicates that the application was exited within
     * the guest language program. If {@link #isExit()} returns <code>true</code> also
     * {@link #getExitStatus()} should be implemented.
     *
     * @see #getExitStatus()
     * @since 0.27
     */
    default boolean isExit() {
        return false;
    }

    /**
     * Returns the exit status if this exception indicates that the application was {@link #isExit()
     * exited}. The exit status is intended to be passed to {@link System#exit(int)}.
     *
     * @see #isExit()
     * @since 0.27
     */
    default int getExitStatus() {
        return 0;
    }

    /**
     * Returns the number of guest language frames that should be collected for this exception.
     * Returns a negative integer by default for unlimited guest language frames. This is intended
     * to be used by guest languages to limit the number of guest language stack frames. Languages
     * might want to limit the number of frames for performance reasons. Frames that point to
     * {@link RootNode#isInternal() internal} internal root nodes are not counted when the stack
     * trace limit is computed.
     *
     * @since 0.27
     */
    default int getStackTraceElementLimit() {
        return -1;
    }

    /**
     * Returns a location where this exception occurred in the AST. This method may return
     * <code>null</code> to indicate that the location is not available.
     *
     * @return the {@link SourceSection} or null
     * @since 0.33
     */
    default SourceSection getSourceLocation() {
        final Node node = getLocation();
        return node == null ? null : node.getEncapsulatingSourceSection();
    }
}
