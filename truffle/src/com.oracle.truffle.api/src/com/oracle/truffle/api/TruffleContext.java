/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.InputStream;
import java.io.OutputStream;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.graalvm.polyglot.PolyglotException;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleSafepoint.Interrupter;
import com.oracle.truffle.api.TruffleSafepoint.InterruptibleFunction;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;

/**
 * A handle on a context of a set of Truffle languages. This context handle is designed to be used
 * by Truffle guest language implementations. The Truffle context can be used to create inner
 * contexts for isolated execution of guest language code.
 * <p>
 * A {@link TruffleContext context} consists of a {@link TruffleLanguage#createContext(Env) language
 * context} instance for each {@link Env#getInternalLanguages() installed language}. The current
 * language context is {@link TruffleLanguage#createContext(Env) created} eagerly and can be
 * accessed using a {@link ContextReference context reference} after the context was
 * {@link TruffleContext#enter(Node) entered}.
 * <p>
 * The configuration for each language context is inherited from its parent/creator context. In
 * addition to that {@link Builder#config(String, Object) config} parameters can be passed to new
 * language context instance of the current language. The configuration of other installed languages
 * cannot be modified. To run guest language code in a context, the context needs to be first
 * {@link #enter(Node) entered} and then {@link #leave(Node, Object) left}. The context should be
 * {@link #close() closed} when it is no longer needed. If the context is not closed explicitly,
 * then it is automatically closed together with the parent context.
 * <p>
 * Example usage: {@link TruffleContextSnippets.MyNode#executeInContext}
 *
 * @since 0.27
 */
public final class TruffleContext implements AutoCloseable {

    static final TruffleContext EMPTY = new TruffleContext();

    private static final ThreadLocal<List<Object>> CONTEXT_ASSERT_STACK;

    static {
        boolean assertions = false;
        assert (assertions = true) == true;
        CONTEXT_ASSERT_STACK = assertions ? new ThreadLocal<>() {
            @Override
            protected List<Object> initialValue() {
                return new ArrayList<>();
            }
        } : null;
    }
    final Object polyglotContext;
    final boolean creator;

    TruffleContext(Object polyglotContext, boolean creator) {
        this.polyglotContext = polyglotContext;
        this.creator = creator;
    }

    /*
     * Constructor necessary for inner builder.
     */
    private TruffleContext() {
        this.polyglotContext = null;
        this.creator = false;
    }

    /**
     * {@inheritDoc}
     *
     * @since 20.3
     */
    @Override
    @TruffleBoundary
    public boolean equals(Object obj) {
        if (!(obj instanceof TruffleContext)) {
            return false;
        }
        TruffleContext c = (TruffleContext) obj;
        return polyglotContext.equals(c.polyglotContext);
    }

    /**
     * {@inheritDoc}
     *
     * @since 20.3
     */
    @Override
    public int hashCode() {
        return polyglotContext.hashCode();
    }

    /**
     * Get a parent context of this context, if any. This provides the hierarchy of inner contexts.
     *
     * @return a parent context, or <code>null</code> if there is no parent
     * @since 0.30
     */
    @TruffleBoundary
    public TruffleContext getParent() {
        try {
            return LanguageAccessor.engineAccess().getParentContext(polyglotContext);
        } catch (Throwable t) {
            throw Env.engineToLanguageException(t);
        }
    }

    /**
     * Enters this context and returns an object representing the previous context. Calls to enter
     * must be followed by a call to {@link #leave(Node, Object)} in a finally block and the
     * previous context must be passed as an argument. It is allowed to enter a context multiple
     * times from the same thread. If the context is currently not entered by any thread then it is
     * allowed be entered by an arbitrary thread. Entering the context from two or more different
     * threads at the same time is possible, unless one of the loaded languages denies access to the
     * thread, in which case an {@link IllegalStateException} is thrown.
     * <p>
     * If the current thread was not previously entered in any context, the enter function returns
     * {@code null}. If the return value is not {@code null}, the result of the enter function is
     * unspecified and must only be passed to {@link #leave(Node, Object)}. The result value must
     * not be stored permanently.
     * <p>
     * An adopted node may be passed to allow perform optimizations on the fast-path. If a
     * <code>null</code> node is passed then entering a context will result in a
     * {@link TruffleBoundary boundary} call in compiled code. If the provided node is not adopted
     * an {@link IllegalArgumentException} is thrown.
     * <p>
     * Entering a language context is designed for compilation and is most efficient if the
     * {@link TruffleContext context} instance is compilation final.
     *
     * <p>
     * Example usage: {@link TruffleContextSnippets.MyNode#executeInContext}
     *
     * @see #leave(Node, Object)
     * @since 20.3
     */
    public Object enter(Node node) {
        try {
            CompilerAsserts.partialEvaluationConstant(node);
            Object prev = LanguageAccessor.engineAccess().enterInternalContext(node, polyglotContext);
            if (CONTEXT_ASSERT_STACK != null) {
                verifyEnter(prev);
            }
            return prev;
        } catch (Throwable t) {
            throw Env.engineToLanguageException(t);
        }
    }

    /**
     * Forces initialization of an internal or public language. If the context is not an inner
     * context and e.g. accessed using {@link Env#getContext()} an {@link IllegalStateException} is
     * thrown. In such a case {@link Env#initializeLanguage(LanguageInfo)} should be used instead to
     * initialize contexts.
     * <p>
     * No context or the parent creator context must be entered to initialize languages in a
     * {@link TruffleContext} otherwise an {@link IllegalStateException} will be thrown.
     *
     * @throws IllegalStateException if an invalid context is entered or the context is already
     *             closed.
     * @throws IllegalArgumentException if the given language of the source cannot be accessed.
     * @param languageId the id of the language to initialize
     * @param node a partial evaluation constant node context used to optimize this operation. Can
     *            be <code>null</code> if not available.
     * @return <code>true</code> if the language was initialized, else <code>false</code>, e.g. if
     *         the language as already initialized.
     *
     * @since 22.3
     */
    public boolean initializeInternal(Node node, String languageId) {
        Objects.requireNonNull(languageId);
        CompilerAsserts.partialEvaluationConstant(node);
        try {
            return LanguageAccessor.engineAccess().initializeInnerContext(node, polyglotContext, languageId, true);
        } catch (Throwable t) {
            throw Env.engineToLanguageException(t);
        }
    }

    /**
     * The same as {@link #initializeInternal(Node, String)}, but only public languages are
     * accessible.
     *
     * @throws IllegalStateException if an invalid context is entered or the context is already
     *             closed.
     * @throws IllegalArgumentException if the given language of the source cannot be accessed.
     * @param languageId the id of the language to initialize
     * @param node a partial evaluation constant node context used to optimize this operation. Can
     *            be <code>null</code> if not available.
     * @return <code>true</code> if the language was initialized, else <code>false</code>, e.g. if
     *         the language as already initialized.
     *
     * @since 22.3
     */
    public boolean initializePublic(Node node, String languageId) {
        Objects.requireNonNull(languageId);
        CompilerAsserts.partialEvaluationConstant(node);
        try {
            return LanguageAccessor.engineAccess().initializeInnerContext(node, polyglotContext, languageId, false);
        } catch (Throwable t) {
            throw Env.engineToLanguageException(t);
        }
    }

    /**
     * Evaluates a source in an inner context and returns the result. If the context is not an inner
     * context and e.g. accessed using {@link Env#getContext()} an {@link IllegalStateException} is
     * thrown. In such a case {@link Env#parseInternal(Source, String...)} should be used instead to
     * evaluate sources.
     * <p>
     * No context or the parent creator context must be entered to evaluate sources in a
     * {@link TruffleContext} otherwise an {@link IllegalStateException} will be thrown. In order to
     * ensure that all values are accessed from their respective contexts only, any non-primitive
     * value returned by the evaluation will be wrapped and enter this context for each interop
     * message sent. Parameters to interop messages will enter and leave the parent context when
     * they are accessed. If the result is a primitive value, then the value is directly returned.
     * <p>
     * This method has access to all public and internal languages the creator context has access
     * to. This corresponds to the set of languages returned by {@link Env#getInternalLanguages()}.
     * If a language cannot be accessed then an {@link IllegalArgumentException} is thrown. If a
     * language is not yet initialized in the inner context, it will get automatically initialized.
     * <p>
     * This method is designed to be used in compiled code paths. This method may be used from
     * multiple threads at the same time. The result of this method must not be cached, instead the
     * {@link Source} object should be cached.
     * <p>
     *
     * @throws IllegalArgumentException if the given language of the source cannot be accessed.
     * @throws IllegalStateException if an invalid context is entered or the context is already
     *             closed.
     * @param node a partial evaluation constant node context used to optimize this operation. Can
     *            be <code>null</code> if not available.
     * @param source the source to evaluate
     * @since 21.3
     */
    public Object evalInternal(Node node, Source source) {
        CompilerAsserts.partialEvaluationConstant(node);
        try {
            return LanguageAccessor.engineAccess().evalInternalContext(node, polyglotContext, source, true);
        } catch (Throwable t) {
            throw Env.engineToLanguageException(t);
        }
    }

    /**
     * The same as {@link #evalInternal(Node, Source)}, but only public languages are accessible.
     *
     * @throws IllegalArgumentException if the given language of the source cannot be accessed.
     * @throws IllegalStateException if an invalid context is entered or the context is already
     *             closed.
     * @param node a partial evaluation constant node context used to optimize this operation. Can
     *            be <code>null</code> if not available.
     * @param source the source to evaluate
     * @since 21.3
     */
    public Object evalPublic(Node node, Source source) {
        CompilerAsserts.partialEvaluationConstant(node);
        try {
            return LanguageAccessor.engineAccess().evalInternalContext(node, polyglotContext, source, false);
        } catch (Throwable t) {
            throw Env.engineToLanguageException(t);
        }
    }

    /**
     * Checks whether the context is entered on the current thread and the context is the currently
     * active context on this thread. There can be multiple contexts {@link #isActive() active} on a
     * single thread, but this method only returns <code>true</code> if it is the top-most context.
     * This method is thread-safe and may be used from multiple threads.
     *
     * @since 20.0
     * @return {@code true} if the context is active, {@code false} otherwise
     */
    public boolean isEntered() {
        try {
            return LanguageAccessor.engineAccess().isContextEntered(polyglotContext);
        } catch (Throwable t) {
            throw Env.engineToLanguageException(t);
        }
    }

    /**
     * Returns <code>true</code> if the context is currently active on the current thread, else
     * <code>false</code>. Checks whether the context has been previously entered by this thread
     * with {@link #enter(Node)} and hasn't been left yet with
     * {@link #leave(Node, java.lang.Object)} methods. Multiple contexts can be active on a single
     * thread. See {@link #isEntered()} for checking whether it is the top-most entered context.
     * This method is thread-safe and may be used from multiple threads.
     *
     * @since 20.3
     */
    public boolean isActive() {
        try {
            return LanguageAccessor.engineAccess().isContextActive(polyglotContext);
        } catch (Throwable t) {
            throw Env.engineToLanguageException(t);
        }
    }

    /**
     * Returns <code>true</code> if the context was closed else <code>false</code>. A context may be
     * closed if {@link #close()}, {@link #closeCancelled(Node, String)}, or
     * {@link #closeExited(Node, int)} was called previously.
     *
     * @since 20.3
     */
    @TruffleBoundary
    public boolean isClosed() {
        try {
            return LanguageAccessor.engineAccess().isContextClosed(polyglotContext);
        } catch (Throwable t) {
            throw Env.engineToLanguageException(t);
        }
    }

    /**
     * Returns <code>true</code> if the context is being cancelled else <code>false</code>. A
     * context may be in the process of cancelling if {@link #closeCancelled(Node, String)} was
     * called previously.
     *
     * @since 21.1
     */
    @TruffleBoundary
    public boolean isCancelling() {
        try {
            return LanguageAccessor.engineAccess().isContextCancelling(polyglotContext);
        } catch (Throwable t) {
            throw Env.engineToLanguageException(t);
        }
    }

    /**
     * Returns <code>true</code> if the context is being hard-exited else <code>false</code>. A
     * context may be in the process of exit if {@link #closeExited(Node, int)} was called
     * previously.
     *
     * @since 22.0
     */
    @TruffleBoundary
    public boolean isExiting() {
        try {
            return LanguageAccessor.engineAccess().isContextExiting(polyglotContext);
        } catch (Throwable t) {
            throw Env.engineToLanguageException(t);
        }
    }

    /**
     * Pause execution on all threads for this context. This call does not wait for the threads to
     * be actually paused. Instead, a future is returned that can be used to wait for the execution
     * to be paused. The future is completed when all active threads are paused. New threads entered
     * after this point are paused immediately after entering until
     * {@link TruffleContext#resume(Future)} is called.
     *
     * @return a future that can be used to wait for the execution to be paused. Also, the future is
     *         used to resume execution by passing it to the {@link TruffleContext#resume(Future)}
     *         method.
     *
     * @since 21.2
     */
    @TruffleBoundary
    public Future<Void> pause() {
        try {
            return LanguageAccessor.engineAccess().pause(polyglotContext);
        } catch (Throwable t) {
            throw Env.engineToLanguageException(t);
        }
    }

    /**
     * Resume previously paused execution on all threads for this context. The execution will not
     * resume if {@link TruffleContext#pause()} was called multiple times and for some of the other
     * calls resume was not called yet.
     *
     * @param pauseFuture pause future returned by a previous call to
     *            {@link TruffleContext#pause()}.
     *
     * @throws IllegalArgumentException in case the passed pause future was not obtained by a
     *             previous call to {@link TruffleContext#pause()} on this context.
     *
     * @since 21.2
     */
    @TruffleBoundary
    public void resume(Future<Void> pauseFuture) {
        try {
            LanguageAccessor.engineAccess().resume(polyglotContext, pauseFuture);
        } catch (Throwable t) {
            throw Env.engineToLanguageException(t);
        }
    }

    /**
     * Leaves this context and sets the previous context as the new current context.
     * <p>
     * An adopted node may be passed to allow perform optimizations on the fast-path. If a
     * <code>null</code> node is passed then entering a context will result in a
     * {@link TruffleBoundary boundary} call in compiled code. If the node is not adopted an
     * {@link IllegalArgumentException} is thrown.
     * <p>
     * Leaving a language context is designed for compilation and is most efficient if the
     * {@link TruffleContext context} instance is compilation final.
     *
     * @param prev the previous context returned by {@link #enter(Node)}
     * @see #enter(Node)
     * @since 20.3
     */
    public void leave(Node node, Object prev) {
        try {
            if (CONTEXT_ASSERT_STACK != null) {
                verifyLeave(prev);
            }
            LanguageAccessor.engineAccess().leaveInternalContext(node, polyglotContext, prev);
        } catch (Throwable t) {
            throw Env.engineToLanguageException(t);
        }
    }

    /**
     * Leaves this context, runs the passed supplier and reenters the context. This is useful when
     * the current thread must wait for another thread (and does not need to access the context to
     * do so) and triggering multithreading is not desired, for instance when implementing
     * coroutines with threads. The supplier cannot access the context and must not run any guest
     * language code or invoke interoperability messages.
     * <p>
     * The supplier will typically notify another thread that it can now enter the context without
     * triggering multithreading and then wait for some thread to leave the context before exiting
     * the supplier and reentering the context (again to avoid triggering multithreading).
     * <p>
     * An adopted node may be passed to allow perform optimizations on the fast-path. If a
     * <code>null</code> node is passed then entering a context will result in a
     * {@link TruffleBoundary boundary} call in compiled code. If the provided node is not adopted
     * an {@link IllegalArgumentException} is thrown.
     * <p>
     * Entering a language context is designed for compilation and is most efficient if the
     * {@link TruffleContext context} instance is compilation final.
     *
     * @param node an adopted node or {@code null}
     * @param runWhileOutsideContext the supplier to run while having left this context
     * @since 21.1
     * @deprecated Use {@link #leaveAndEnter(Node, Interrupter, InterruptibleFunction, Object)}
     *             instead.
     */
    @Deprecated
    public <T> T leaveAndEnter(Node node, Supplier<T> runWhileOutsideContext) {
        CompilerAsserts.partialEvaluationConstant(node);
        try {
            LanguageAccessor.engineAccess().leaveInternalContext(node, polyglotContext, null);
            try {
                return callSupplier(runWhileOutsideContext);
            } finally {
                LanguageAccessor.engineAccess().enterInternalContext(node, polyglotContext);
            }
        } catch (Throwable t) {
            throw Env.engineToLanguageException(t);
        }
    }

    /**
     * Leaves this context, runs the passed interruptible function and reenters the context. This is
     * useful when the current thread must wait for another thread (and does not need to access the
     * context to do so) and triggering multithreading is not desired, for instance when
     * implementing coroutines with threads. The function cannot access the context and must not run
     * any guest language code or invoke interoperability messages.
     * <p>
     * The function will typically notify another thread that it can now enter the context without
     * triggering multithreading and then wait for some thread to leave the context before exiting
     * the function and reentering the context (again to avoid triggering multithreading).
     * <p>
     * An adopted node may be passed to allow performing optimizations on the fast-path.
     * <p>
     *
     * @param node an adopted node or {@code null}
     * @param interrupter an interrupter used to interrupt the interruptible function when, e.g.,
     *            the context is cancelled. The interrupter is also used to
     *            {@link Interrupter#resetInterrupted() reset} the interrupted state when the
     *            context is reentered.
     * @param interruptible the interruptible function to run while having left this context. This
     *            function is run at most once.
     * @return return value of the interruptible, or <code>null</code> if the interruptibe throws an
     *         {@link InterruptedException} without the context being cancelled or exited.
     * 
     * @since 23.1
     */
    @SuppressWarnings("unused")
    @TruffleBoundary
    public <T, R> R leaveAndEnter(Node node, Interrupter interrupter, InterruptibleFunction<T, R> interruptible, T object) {
        try {
            return LanguageAccessor.engineAccess().leaveAndEnter(polyglotContext, interrupter, interruptible, object);
        } catch (Throwable t) {
            throw Env.engineToLanguageException(t);
        }
    }

    @TruffleBoundary
    private static <T> T callSupplier(Supplier<T> supplier) {
        return supplier.get();
    }

    @TruffleBoundary
    private static void verifyEnter(Object prev) {
        assert CONTEXT_ASSERT_STACK != null;
        CONTEXT_ASSERT_STACK.get().add(prev);
    }

    @TruffleBoundary
    private static void verifyLeave(Object prev) {
        assert CONTEXT_ASSERT_STACK != null;
        List<Object> list = CONTEXT_ASSERT_STACK.get();
        assert !list.isEmpty() : "Assert stack is empty.";
        Object expectedPrev = list.get(list.size() - 1);
        assert prev == expectedPrev : "Invalid prev argument provided in TruffleContext.leave(Object).";
        list.remove(list.size() - 1); // pop
    }

    /**
     * Closes this context and disposes its resources. Closes this context and disposes its
     * resources. A context cannot be closed if it is currently {@link #enter(Node) entered} or
     * active by any thread. If a closed context is attempted to be accessed or entered, then an
     * {@link IllegalStateException} is thrown. If the context is not closed explicitly, then it is
     * automatically closed together with the parent context. If an attempt to close a context was
     * successful then consecutive calls to close have no effect.
     *
     * @throws UnsupportedOperationException if the close operation is not supported on this context
     * @throws IllegalStateException if the context is {@link #isActive() active}.
     * @since 0.27
     * @see #closeCancelled(Node, String)
     * @see #closeResourceExhausted(Node, String)
     */
    @Override
    @TruffleBoundary
    public void close() {
        if (!creator) {
            throw new UnsupportedOperationException("This context instance has no permission to close. " +
                            "Only the original creator of the truffle context or instruments can close.");
        }
        try {
            LanguageAccessor.engineAccess().closeContext(polyglotContext, false, null, false, null);
        } catch (Throwable t) {
            throw Env.engineToLanguageException(t);
        }
    }

    /**
     * Force closes the context as cancelled and stops all the execution on all active threads using
     * a special {@link ThreadDeath} cancel exception. If this context is not currently
     * {@link #isEntered() entered} on the current thread then this method waits until the close
     * operation is complete and {@link #isClosed()} returns <code>true</code>, else it throws the
     * cancelled exception upon its completion of this method. If an attempt to close a context was
     * successful then consecutive calls to close have no effect.
     * <p>
     * The throwing of the special {@link ThreadDeath} cancel exception also applies to any guest
     * code run during {@link TruffleLanguage#finalizeContext(Object)} which means that
     * {@link TruffleLanguage#finalizeContext(Object)} cannot run guest code during the cancel
     * operation.
     * <p>
     * If forced and this context currently {@link #isEntered() entered} on the current thread and
     * no other context is entered on the current thread then this method directly throws a
     * {@link ThreadDeath} error instead of completing to indicate that the current thread should be
     * stopped. The thrown {@link ThreadDeath} must not be caught by the guest language and freely
     * propagated to the guest application to cancel the execution on the current thread. Please
     * note that this means that the guest language's finally blocks must not be executed.
     * <p>
     * If a context is {@link #isActive() active} on the current thread, but not {@link #isEntered()
     * entered}, then an {@link IllegalStateException} is thrown, as parent contexts that are active
     * on the current thread cannot be cancelled.
     *
     * @param closeLocation the node where the close occurred. If the context is currently entered
     *            on the thread, then this node will be used as location, for the exception thrown.
     *            If the context is not entered, then the closeLocation parameter will be ignored.
     * @param message exception text for humans provided to the embedder and tools that observe the
     *            cancellation.
     * @throws UnsupportedOperationException if the close operation is not supported on this context
     * @throws IllegalStateException if the context is {@link #isActive() active} but not
     *             {@link #isEntered() entered} on the current thread.
     * @see #close()
     * @see #closeResourceExhausted(Node, String)
     * @since 20.3
     */
    @TruffleBoundary
    public void closeCancelled(Node closeLocation, String message) {
        if (!creator) {
            throw new UnsupportedOperationException("This context instance has no permission to close. " +
                            "Only the original creator of the truffle context or instruments can close.");
        }
        try {
            LanguageAccessor.engineAccess().closeContext(polyglotContext, true, closeLocation, false, message);
        } catch (Throwable t) {
            throw Env.engineToLanguageException(t);
        }
    }

    /**
     * Initiates force close of the context as exited -
     * {@link com.oracle.truffle.api.TruffleLanguage.ExitMode#HARD hard exit}. Requires the context
     * to be entered on the current thread. Languages are first notified by calling
     * {@link TruffleLanguage#exitContext(Object, TruffleLanguage.ExitMode, int)} and then the
     * closing of the context is initiated. Execution on all active threads including the current
     * thread is stopped by throwing a special {@link ThreadDeath} exit exception. This method does
     * not wait for the execution on other threads to be stopped, it throws the {@link ThreadDeath}
     * exception as soon as possible. To exit threads reliably, guest languages need to ensure that
     * the {@link ThreadDeath} is always immediately rethrown and guest language exception handlers
     * and finally blocks are not run.
     * <p>
     * The throwing of the special {@link ThreadDeath} exit exception also applies to any guest code
     * run during {@link TruffleLanguage#finalizeContext(Object)} which means that
     * {@link TruffleLanguage#finalizeContext(Object)} cannot run guest code during hard exit.
     * <p>
     * The exit code can be specified only once and the first call to this method also executes the
     * {@link TruffleLanguage#exitContext(Object, TruffleLanguage.ExitMode, int) exit notifications}
     * on the same thread. Further calls to this method will ensure that the following guest code on
     * the calling thread is not executed by throwing the {@link ThreadDeath} exit exception, and
     * the exit location is used as the stopping point of the thread, but the passed exit code is
     * ignored and exit notifications are not run.
     * <p>
     * In case the context is in one of the following states
     * <ul>
     * <li>the context is being closed and the finalization stage has already begun
     * ({@link TruffleLanguage#finalizeContext(Object)} is being executed for all language
     * contexts).
     * <li>the context is already closed
     * <li>the context threads are being unwound as a part of the cancelling process
     * <li>the context threads are being unwound as a part of the hard exit process that comes after
     * exit notifications
     * </ul>
     * then calling this method has no effect.
     *
     * @param exitLocation the node where the exit occurred.
     * @param exitCode exitCode provided to the embedder and tools that observe the exit.
     * @throws IllegalStateException if the context is not {@link #isEntered() entered} on the
     *             current thread.
     * @see <a href= "https://github.com/oracle/graal/blob/master/truffle/docs/Exit.md">Context
     *      Exit</a>
     *
     * @since 22.0
     */
    @TruffleBoundary
    public void closeExited(Node exitLocation, int exitCode) {
        if (!isEntered()) {
            throw new IllegalStateException("Exit cannot be initiated for this context because it is not currently entered.");
        }
        try {
            LanguageAccessor.engineAccess().exitContext(polyglotContext, exitLocation, exitCode);
        } catch (Throwable t) {
            throw Env.engineToLanguageException(t);
        }
    }

    /**
     * Force closes the context due to resource exhaustion. This method is equivalent to calling
     * {@link #closeCancelled(Node, String) closeCancelled(location, message)} except in addition
     * the thrown {@link PolyglotException} returns <code>true</code> for
     * {@link PolyglotException#isResourceExhausted()}.
     *
     * @see PolyglotException#isResourceExhausted()
     * @see #close()
     * @see #closeCancelled(Node, String)
     * @since 20.3
     */
    @TruffleBoundary
    public void closeResourceExhausted(Node location, String message) {
        if (!creator) {
            throw new UnsupportedOperationException("This context instance has no permission to cancel. " +
                            "Only the original creator of the truffle context or instruments can close.");
        }
        try {
            LanguageAccessor.engineAccess().closeContext(polyglotContext, true, location, true, message);
        } catch (Throwable t) {
            throw Env.engineToLanguageException(t);
        }
    }

    /**
     * Builder class to create new {@link TruffleContext} instances.
     *
     * @since 0.27
     */
    public final class Builder {

        private final Env sourceEnvironment;
        private Map<String, Object> config;
        private Map<String, String[]> arguments;
        private boolean initializeCreatorContext;
        private Runnable onCancelled;
        private Consumer<Integer> onExited;
        private Runnable onClosed;

        private Boolean sharingEnabled;
        private Map<String, String> options;
        private Map<String, String> environment;
        private String[] permittedLanguages;
        private OutputStream out;
        private OutputStream err;
        private InputStream in;
        private boolean inheritAccess;
        private Boolean allowCreateThread;
        private Boolean allowNativeAccess;
        private Boolean allowIO;
        private Boolean allowHostLookup;
        private Boolean allowHostClassLoading;
        private Boolean allowCreateProcess;
        private Boolean allowInnerContextOptions;
        private Boolean allowPolyglotAccess;
        private Boolean allowEnvironmentAccess;
        private ZoneId timeZone;

        Builder(Env env) {
            this.sourceEnvironment = env;
        }

        @SuppressWarnings("hiding")
        Builder permittedLanguages(String... permittedLanguages) {
            this.permittedLanguages = permittedLanguages;
            return this;
        }

        /**
         * Sets a config parameter that the child context of this language can access using
         * {@link Env#getConfig()}.
         *
         * @since 0.27
         */
        @TruffleBoundary
        public Builder config(String key, Object value) {
            if (config == null) {
                config = new HashMap<>();
            }
            config.put(key, value);
            return this;
        }

        /**
         * Specifies whether the creating language context should be initialized in the new context.
         * By default the creating language won't get initialized.
         *
         * @since 21.3
         */
        public Builder initializeCreatorContext(boolean enabled) {
            this.initializeCreatorContext = enabled;
            return this;
        }

        /**
         * Sets the standard output stream to be used for the context. If not set or set to
         * <code>null</code>, then the standard output stream is inherited from the outer context.
         *
         * @since 22.3
         */
        @SuppressWarnings("hiding")
        public Builder out(OutputStream out) {
            this.out = out;
            return this;
        }

        /**
         * Sets the error output stream to be used for the context. If not set or set to
         * <code>null</code>, then the standard error stream is inherited from the outer context. is
         * used.
         *
         * @since 22.3
         */
        @SuppressWarnings("hiding")
        public Builder err(OutputStream err) {
            this.err = err;
            return this;
        }

        /**
         * Sets the input stream to be used for the context. If not set or set to <code>null</code>,
         * then the standard input stream is inherited from the outer context.
         *
         * @since 22.3
         */
        @SuppressWarnings("hiding")
        public Builder in(InputStream in) {
            this.in = in;
            return this;
        }

        /**
         * Force enables or disables code sharing for this inner context. By default this option is
         * set to <code>null</code> which instructs this context to inherit sharing configuration
         * from the outer context.
         * <p>
         * If sharing is explicitly set to <code>true</code> then code may be shared with all
         * contexts compatible with the configuration of this inner context. Code may also be shared
         * with the outer context if the outer context supports code sharing and its sharing layer
         * is compatible. This may be useful to force sharing for multiple created inner contexts if
         * the outer context configuration disabled sharing.
         * <p>
         * If sharing is explicitly set to <code>false</code> then no code will be shared even if
         * the outer context has code sharing enabled and supported by the language. This may be
         * useful to avoid runtime profile pollution through code run in an inner context.
         * <p>
         * If multiple languages are potentially used it is recommended but not required to use
         * {@link Env#newInnerContextBuilder(String...) permitted languages} to specify all used
         * languages ahead of time. This allows to validate sharing layer compatibility eagerly and
         * avoids late failures when a language is initialized lazily and is not compatible with the
         * sharing layer. Use <code>--engine.TraceSharing</code> for details on whether the sharing
         * configuration is compatible.
         *
         * @since 22.3
         */
        public Builder forceSharing(Boolean enabled) {
            this.sharingEnabled = enabled;
            return this;
        }

        /**
         * Overrides an option of the inner context. Only language options may be changed. Engine or
         * instrument options cannot be modified after their initial configuration in the outer
         * context. All options for inner contexts are inherited from the outer context, but options
         * set for the inner context have precedence over options set in the outer context.
         * <p>
         * This method is currently only supported if the outer context is configured with
         * {@link org.graalvm.polyglot.Context.Builder#allowInnerContextOptions(boolean)}. If all
         * privileges are denied then an {@link IllegalArgumentException} will be thrown when
         * building the inner context. To find out whether the inner context option privilege has
         * been granted languages may use
         * {@link TruffleLanguage.Env#isInnerContextOptionsAllowed()}.
         *
         * @since 22.3
         */
        @TruffleBoundary
        public Builder option(String key, String value) {
            Objects.requireNonNull(key);
            Objects.requireNonNull(value);
            if (this.options == null) {
                this.options = new HashMap<>();
            }
            this.options.put(key, value);
            return this;
        }

        /**
         * If set to <code>true</code> allows all access privileges that were also granted to the
         * outer context. If set to <code>false</code> then all privileges are denied in the created
         * context independent of the outer context. By default all privileges are denied and not
         * inherited.
         * <p>
         * This privilege enables inheritance for the following privileges:
         * <ul>
         * <li>{@link #allowCreateThread(boolean)}
         * <li>{@link #allowNativeAccess(boolean)}
         * <li>{@link #allowIO(boolean)}
         * <li>{@link #allowHostClassLookup(boolean)}
         * <li>{@link #allowHostClassLoading(boolean)}
         * <li>{@link #allowCreateProcess(boolean)}
         * <li>{@link #allowPolyglotAccess(boolean)}
         * <li>{@link #allowInheritEnvironmentAccess(boolean)}
         * <li>{@link #allowInnerContextOptions(boolean)}
         * </ul>
         * <p>
         * If an individual privilege was set explicitly then the value of
         * {@link #inheritAllAccess(boolean)} is ignored for that privilege. For example it is
         * possible to set {@link #inheritAllAccess(boolean)} to <code>true</code> but set
         * {@link #allowNativeAccess(boolean)} to <code>false</code> to allow inheritance of all but
         * native access rights from the outer to the inner context.
         * <p>
         * In case new privileges are added in the future, then this method will allow or disallow
         * inheritance for the new privileges as well. For security sensitive use-cases it is
         * recommended to keep the default value of {@link #inheritAllAccess(boolean)} and
         * individually specify allowed privileges for this context. For non security sensitive
         * use-cases it is recommended to set this flag to <code>true</code>. Following this
         * recommendation is important in case new privileges are added to the platform.
         *
         * @since 22.3
         */
        public Builder inheritAllAccess(boolean b) {
            this.inheritAccess = b;
            return this;
        }

        /**
         * Allows or denies creating threads for this context. Set to <code>true</code> to inherit
         * access privilege from the outer context or <code>false</code> to deny access for this
         * context.
         *
         * @see #inheritAllAccess(boolean)
         * @see org.graalvm.polyglot.Context.Builder#allowCreateThread(boolean)
         * @since 22.3
         */
        public Builder allowCreateThread(boolean b) {
            this.allowCreateThread = b;
            return this;
        }

        /**
         * Allows or denies using native access in this context. Set to <code>true</code> to inherit
         * access privilege from the outer context or <code>false</code> to deny access for this
         * context.
         *
         * @see #inheritAllAccess(boolean)
         * @see org.graalvm.polyglot.Context.Builder#allowNativeAccess(boolean)
         * @since 22.3
         */
        public Builder allowNativeAccess(boolean b) {
            this.allowNativeAccess = b;
            return this;
        }

        /**
         * Allows or denies using IO in this context. Set to <code>true</code> to inherit access
         * privilege from the outer context or <code>false</code> to deny access for this context.
         *
         * @see #inheritAllAccess(boolean)
         * @see org.graalvm.polyglot.Context.Builder#allowIO(org.graalvm.polyglot.io.IOAccess)
         * @since 22.3
         */
        public Builder allowIO(boolean b) {
            this.allowIO = b;
            return this;
        }

        /**
         * Allows or denies loading new host classes in this context. Set to <code>true</code> to
         * inherit access privilege from the outer context or <code>false</code> to deny access for
         * this context.
         *
         * @see #inheritAllAccess(boolean)
         * @see org.graalvm.polyglot.Context.Builder#allowHostAccess(org.graalvm.polyglot.HostAccess)
         * @since 22.3
         */
        public Builder allowHostClassLoading(boolean b) {
            this.allowHostClassLoading = b;
            return this;
        }

        /**
         * Allows or denies loading new host classes in this context. Set to <code>true</code> to
         * inherit access privilege from the outer context or <code>false</code> to deny access for
         * this context.
         *
         * @see #inheritAllAccess(boolean)
         * @see org.graalvm.polyglot.Context.Builder#allowHostAccess(org.graalvm.polyglot.HostAccess)
         * @since 22.3
         */
        public Builder allowHostClassLookup(boolean b) {
            this.allowHostLookup = b;
            return this;
        }

        /**
         * Allows or denies creating processes in this context. Set to <code>true</code> to inherit
         * access privilege from the outer context or <code>false</code> to deny access for this
         * context.
         *
         * @see #inheritAllAccess(boolean)
         * @see org.graalvm.polyglot.Context.Builder#allowCreateProcess(boolean)
         * @since 22.3
         */
        public Builder allowCreateProcess(boolean b) {
            this.allowCreateProcess = b;
            return this;
        }

        /**
         * Allows or denies options for inner contexts created by this context. Set to
         * <code>true</code> to inherit access privilege from the outer context or
         * <code>false</code> to deny access for this context.
         *
         * @see #inheritAllAccess(boolean)
         * @see org.graalvm.polyglot.Context.Builder#allowCreateProcess(boolean)
         * @since 22.3
         */
        public Builder allowInnerContextOptions(boolean b) {
            this.allowInnerContextOptions = b;
            return this;
        }

        /**
         * Allows or denies access to polyglot languages in this context. Set to <code>true</code>
         * to inherit access privilege from the outer context or <code>false</code> to deny access
         * for this context.
         *
         * @see #inheritAllAccess(boolean)
         * @see org.graalvm.polyglot.Context.Builder#allowPolyglotAccess(org.graalvm.polyglot.PolyglotAccess)
         * @since 22.3
         */
        public Builder allowPolyglotAccess(boolean b) {
            this.allowPolyglotAccess = b;
            return this;
        }

        /**
         * Allows or denies access to the parent context's environment in this context. Set to
         * <code>true</code> to inherit variables from the outer context or <code>false</code> to
         * deny inheritance for this context. Environment variables can be set for the inner context
         * with {@link #environment(String, String)} even if inheritance is disabled. If inheritance
         * is enabled, environment variables specified for the inner context override environment
         * variables of the outer context.
         *
         * @see #inheritAllAccess(boolean)
         * @see org.graalvm.polyglot.Context.Builder#allowEnvironmentAccess(org.graalvm.polyglot.EnvironmentAccess)
         * @since 22.3
         */
        public Builder allowInheritEnvironmentAccess(boolean b) {
            this.allowEnvironmentAccess = b;
            return this;
        }

        /**
         * Sets an environment variable.
         *
         * @param name the environment variable name
         * @param value the environment variable value
         * @since 22.3
         */
        public Builder environment(String name, String value) {
            Objects.requireNonNull(name, "Name must be non null.");
            Objects.requireNonNull(value, "Value must be non null.");
            if (this.environment == null) {
                this.environment = new HashMap<>();
            }
            this.environment.put(name, value);
            return this;
        }

        /**
         * Shortcut for setting multiple {@link #environment(String, String) environment variables}
         * using a map. All values of the provided map must be non-null.
         *
         * @param env environment variables
         * @see #environment(String, String) To set a single environment variable.
         * @since 22.3
         */
        public Builder environment(Map<String, String> env) {
            Objects.requireNonNull(env, "Env must be non null.");
            for (Map.Entry<String, String> e : env.entrySet()) {
                environment(e.getKey(), e.getValue());
            }
            return this;
        }

        /**
         * Sets the default time zone to be used for this context. If not set, or explicitly set to
         * <code>null</code> then the time zone of the outer context will be used.
         *
         * @since 22.3
         */
        public Builder timeZone(ZoneId zone) {
            this.timeZone = zone;
            return this;
        }

        /**
         * Sets the guest language application arguments for a language context. Application
         * arguments are typically made available to guest language implementations. Passing no
         * arguments to a language is equivalent to providing an empty arguments array.
         *
         * @param language the language id of the primary language.
         * @param args an array of arguments passed to the guest language program.
         * @throws IllegalArgumentException if an invalid language id was specified.
         * @since 22.3
         */
        public Builder arguments(String language, String[] args) {
            Objects.requireNonNull(language);
            Objects.requireNonNull(args);
            String[] newArgs = args;
            if (args.length > 0) {
                newArgs = new String[args.length];
                for (int i = 0; i < args.length; i++) { // defensive copy
                    newArgs[i] = Objects.requireNonNull(args[i]);
                }
            }
            if (arguments == null) {
                arguments = new HashMap<>();
            }
            arguments.put(language, newArgs);
            return this;
        }

        /**
         * Sets multiple options using a map. See {@link #option(String, String)} for details.
         *
         * @since 22.3
         */
        @TruffleBoundary
        @SuppressWarnings("hiding")
        public Builder options(Map<String, String> options) {
            for (var entry : options.entrySet()) {
                option(entry.getKey(), entry.getValue());
            }
            return this;
        }

        /**
         * Specifies a {@link Runnable} that will be executed when the new context is
         * {@link TruffleContext#closeCancelled(Node, String) cancelled} and the cancel exception is
         * about to reach the outer context, or when some operation on the new context is attempted
         * while the context is already cancelled. However, the runnable will only be executed if
         * the outer context is not cancelled.
         *
         * The purpose of the runnable is to allow throwing a custom guest exception before the
         * cancel exception reaches the outer context, so that the outer context can properly handle
         * the exception. In case the runnable does not throw any exception, an internal error is
         * thrown (should not be caught).
         *
         * @since 22.1
         */
        public Builder onCancelled(Runnable r) {
            this.onCancelled = r;
            return this;
        }

        /**
         * Specifies a {@link Consumer} that will be executed when the new context is
         * {@link TruffleContext#closeExited(Node, int) hard-exited} and the exit exception is about
         * to reach the outer context, or when some operation on the new context is attempted while
         * the context is already hard-exited. However, the consumer will only be executed if the
         * outer context is not hard-exited.
         *
         * The purpose of the consumer is to allow throwing a custom guest exception before the exit
         * exception reaches the outer context, so that the outer context can properly handle the
         * exception. In case the consumer does not throw any exception, an internal error is thrown
         * (should not be caught).
         *
         * The single input argument to the {@link Consumer} is the exit code.
         *
         * @since 22.1
         * @see <a href= "https://github.com/oracle/graal/blob/master/truffle/docs/Exit.md">Context
         *      Exit</a>
         */
        public Builder onExited(Consumer<Integer> r) {
            this.onExited = r;
            return this;
        }

        /**
         * Specifies a {@link Runnable} that will be executed when some operation on the new context
         * attenmpted while the context is already {@link TruffleContext#close()} closed}. However,
         * the runnable will only be executed if the outer context is not closed.
         *
         * The purpose of the runnable is to allow throwing a custom guest exception before the
         * close exception reaches the outer context, so that the outer context can properly handle
         * the exception. In case the runnable does not throw any exception, an internal error is
         * thrown (should not be caught).
         *
         * @since 22.1
         */
        public Builder onClosed(Runnable r) {
            this.onClosed = r;
            return this;
        }

        /**
         * Builds the new context instance.
         *
         * @since 0.27
         */
        @TruffleBoundary
        public TruffleContext build() {
            try {
                return LanguageAccessor.engineAccess().createInternalContext(
                                sourceEnvironment.getPolyglotLanguageContext(), this.out, this.err, this.in, this.timeZone,
                                this.permittedLanguages, this.config, this.options, this.arguments, this.sharingEnabled, this.initializeCreatorContext, this.onCancelled, this.onExited,
                                this.onClosed, this.inheritAccess, this.allowCreateThread, this.allowNativeAccess, this.allowIO, this.allowHostLookup, this.allowHostClassLoading,
                                this.allowCreateProcess, this.allowPolyglotAccess, this.allowEnvironmentAccess, this.environment, this.allowInnerContextOptions);
            } catch (Throwable t) {
                throw Env.engineToLanguageException(t);
            }
        }
    }

}

class TruffleContextSnippets {
    // @formatter:off
    abstract class MyContext {
    }
    abstract class MyLanguage extends TruffleLanguage<MyContext> {
    }
    static
    // BEGIN: TruffleContextSnippets.MyNode#executeInContext
    final class MyNode extends Node {
        void executeInContext(Env env) {
            MyContext outerLangContext = getContext(this);

            TruffleContext innerContext = env.newInnerContextBuilder().
                            initializeCreatorContext(true).build();
            Object p = innerContext.enter(this);
            try {
                /*
                 * Here the this node cannot be passed otherwise an
                 * invalid sharing error would occur.
                 */
                MyContext innerLangContext = getContext(null);

                assert outerLangContext != innerLangContext;
            } finally {
                innerContext.leave(this, p);
            }
            assert outerLangContext == getContext(this);
            innerContext.close();
        }
    }
    private static ContextReference<MyContext> REFERENCE
                 = ContextReference.create(MyLanguage.class);

    private static MyContext getContext(Node node) {
        return REFERENCE.get(node);
    }
    // END: TruffleContextSnippets.MyNode#executeInContext
    // @formatter:on

}
