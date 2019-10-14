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
package com.oracle.truffle.api;

import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

/**
 * Represents an exception thrown during the execution of a guest language program. All exceptions
 * thrown in a guest language implementation that are not implementing {@link TruffleException} are
 * considered {@link #isInternalError() internal} errors.
 * <p>
 * {@link TruffleException} is most efficient if the {@link Throwable#getCause() cause} is left
 * uninitialized. Implementations of {@link TruffleException} also should not prevent initialization
 * of the exception cause, e.g., by overriding {@link Throwable#initCause(Throwable)}.
 * <p>
 * In order to be efficient, a {@link TruffleException} should override
 * {@link Throwable#fillInStackTrace()} without the {@code synchronized} modifier, so that it
 * returns {@code this}:
 *
 * <pre>
 * &#64;SuppressWarnings("sync-override")
 * &#64;Override
 * public final Throwable fillInStackTrace() {
 *     return this;
 * }
 * </pre>
 *
 * @since 0.27
 * @see TruffleStackTrace TruffleStackTrace to access the stack trace of an exception.
 */
public interface TruffleException {

    /**
     * Returns a node indicating the location where this exception occurred in the AST. This method
     * may return <code>null</code> to indicate that the location is not available.
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
