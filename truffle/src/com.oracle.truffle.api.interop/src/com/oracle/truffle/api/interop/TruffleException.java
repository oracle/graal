/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.interop;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

/**
 * A base class for an exception thrown during the execution of a guest language program.
 *
 * The following simplified {@code TryCatchNode} shows how the
 * {@link TruffleException#isCatchable()} should be handled by languages.
 *
 * <pre>
 * public class TryCatchNode extends StatementNode {
 *
 *     &#64;Child private BlockNode block;
 *     &#64;Child private BlockNode catchBlock;
 *     &#64;Child private BlockNode finalizerBlock;
 *     &#64;Child private InteropLibrary interop = InteropLibrary.getFactory().createDispatched(5);
 *
 *     public TryCatchNode(BlockNode block, BlockNode catchBlock, BlockNode finalizerBlock) {
 *         this.block = block;
 *         this.catchBlock = catchBlock;
 *         this.finalizerBlock = finalizerBlock;
 *     }
 *
 *     &#64;Override
 *     Object execute(VirtualFrame frame) {
 *         boolean runFinalization = true;
 *         try {
 *             return block.execute(frame);
 *         } catch (Throwable ex) {
 *             if (interop.isException(ex)) {
 *                 try {
 *                     runFinalization = interop.isExceptionCatchable(ex);
 *                     if (runFinalization && catchBlock != null) {
 *                         return catchBlock.execute(frame);
 *                     } else {
 *                         interop.throwException(ex);
 *                     }
 *                 } catch (UnsupportedMessageException ume) {
 *                     CompilerDirectives.shouldNotReachHere(ume);
 *                 }
 *             }
 *             throw ex;
 *         } finally {
 *             if (runFinalization && finalizerBlock != null) {
 *                 finalizerBlock.execute(frame);
 *             }
 *         }
 *     }
 * }
 * </pre>
 *
 * @since 20.2
 */
@SuppressWarnings({"serial", "deprecation"})
public abstract class TruffleException extends RuntimeException implements TruffleObject, com.oracle.truffle.api.TruffleException {

    private final int stackTraceElementLimit;
    private volatile Throwable lazyStackTrace;

    protected TruffleException() {
        this((String) null, -1);
    }

    protected TruffleException(int stackTraceElementLimit) {
        this((String) null, stackTraceElementLimit);
    }

    protected TruffleException(String message) {
        this(message, -1);
    }

    protected TruffleException(String message, int stackTraceElementLimit) {
        super(message);
        this.stackTraceElementLimit = stackTraceElementLimit;
    }

    protected TruffleException(Throwable internalCause) {
        this(internalCause, -1);
    }

    protected TruffleException(Throwable internalCause, int stackTraceElementLimit) {
        super(checkCause(internalCause));
        this.stackTraceElementLimit = stackTraceElementLimit;
    }

    protected TruffleException(String message, Throwable internalCause) {
        this(message, internalCause, -1);
    }

    protected TruffleException(String message, Throwable internalCause, int stackTraceElementLimit) {
        super(message, checkCause(internalCause));
        this.stackTraceElementLimit = stackTraceElementLimit;
    }

    @Override
    @SuppressWarnings("sync-override")
    public final Throwable fillInStackTrace() {
        return this;
    }

    /**
     * Returns a node indicating the location where this exception occurred in the AST. This method
     * may return <code>null</code> to indicate that the location is not available.
     *
     */
    public Node getLocation() {
        return null;
    }

    /**
     * Returns a location where this exception occurred in the AST. This method may return
     * <code>null</code> to indicate that the location is not available.
     *
     * @return the {@link SourceSection} or null
     */
    @Override
    public SourceSection getSourceLocation() {
        final Node node = getLocation();
        return node == null ? null : node.getEncapsulatingSourceSection();
    }

    /**
     * Returns the number of guest language frames that should be collected for this exception.
     * Returns a negative integer by default for unlimited guest language frames. This is intended
     * to be used by guest languages to limit the number of guest language stack frames. Languages
     * might want to limit the number of frames for performance reasons. Frames that point to
     * {@link RootNode#isInternal() internal} internal root nodes are not counted when the stack
     * trace limit is computed.
     *
     */
    @Override
    public final int getStackTraceElementLimit() {
        return stackTraceElementLimit;
    }

    /**
     * Returns an additional guest language object. The return object must be an interop exception
     * type, the {@link @link com.oracle.truffle.api.interop.InteropLibrary#isException(Object)} has
     * to return {@code true}. The default implementation returns <code>null</code> to indicate that
     * no object is available for this exception.
     */
    @Deprecated
    @Override
    public final Object getExceptionObject() {
        return this;
    }

    /**
     * Returns <code>true</code> if this exception indicates a parser or syntax error. Syntax errors
     * typically occur while
     * {@link TruffleLanguage#parse(com.oracle.truffle.api.TruffleLanguage.ParsingRequest) parsing}
     * of guest language source code.
     *
     */
    @Deprecated
    @Override
    public final boolean isSyntaxError() {
        return getExceptionType() == ExceptionType.SYNTAX_ERROR;
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
     */
    @Deprecated
    @Override
    public final boolean isIncompleteSource() {
        return getExceptionType() == ExceptionType.INCOMPLETE_SOURCE;
    }

    /**
     * Returns <code>true</code> if this exception indicates an internal error. Note that all
     * exceptions thrown in a guest language implementation that are not implementing
     * {@link TruffleException} are considered internal.
     *
     * @since 0.27
     */
    @Deprecated
    @Override
    public final boolean isInternalError() {
        return getExceptionType() == ExceptionType.INTERNAL_ERROR;
    }

    /**
     * Returns <code>true</code> if this exception indicates that guest language application was
     * cancelled during its execution. If {@code isCancelled} returns {@code true} languages should
     * not catch this exception, they must just rethrow it.
     *
     */
    @Deprecated
    @Override
    public final boolean isCancelled() {
        return getExceptionType() == ExceptionType.CANCEL;
    }

    /**
     * Returns <code>true</code> if the exception indicates that the application was exited within
     * the guest language program. If {@link #isExit()} returns <code>true</code> also
     * {@link #getExitStatus()} should be implemented.
     *
     * @see #getExitStatus()
     */
    @Deprecated
    @Override
    public final boolean isExit() {
        return getExceptionType() == ExceptionType.EXIT;
    }

    /**
     * Returns the exit status if this exception indicates that the application was {@link #isExit()
     * exited}. The exit status is intended to be passed to {@link System#exit(int)}.
     *
     * @see #isExit()
     */
    @Deprecated
    @Override
    public final int getExitStatus() {
        try {
            return InteropLibrary.getUncached().getExceptionExitStatus(this);
        } catch (UnsupportedMessageException um) {
            throw CompilerDirectives.shouldNotReachHere(um);
        }
    }

    /**
     * @inheritDoc
     */
    public Throwable initCause(Throwable cause) {
        return super.initCause(checkCause(cause));
    }

    Throwable getLazyStackTrace() {
        Throwable res = lazyStackTrace;
        if (res == null) {
            synchronized (this) {
                res = lazyStackTrace;
                if (res == null) {
                    res = InteropAccessor.LANGUAGE.createLazyStackTrace();
                    lazyStackTrace = res;
                }
            }
        }
        return res;
    }

    private ExceptionType getExceptionType() {
        try {
            return InteropLibrary.getUncached().getExceptionType(this);
        } catch (UnsupportedMessageException um) {
            throw CompilerDirectives.shouldNotReachHere(um);
        }
    }

    @SuppressWarnings("deprecation")
    private static Throwable checkCause(Throwable t) {
        if (t != null && !(t instanceof com.oracle.truffle.api.TruffleException)) {
            throw new IllegalArgumentException("The " + t + " must be TruffleException subclass.");
        }
        return t;
    }
}
