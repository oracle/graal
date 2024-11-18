/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.polyglot;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.Message;
import com.oracle.truffle.api.library.ReflectionLibrary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;

/**
 * A guest TruffleObject that has been passed from one context to another.
 */
@ExportLibrary(ReflectionLibrary.class)
final class OtherContextGuestObject implements TruffleObject {

    static final Object OTHER_VALUE = new Object();
    static final ReflectionLibrary OTHER_VALUE_UNCACHED = ReflectionLibrary.getFactory().getUncached(OTHER_VALUE);

    final PolyglotContextImpl receiverContext;
    final Object delegate;
    final PolyglotContextImpl delegateContext;

    OtherContextGuestObject(PolyglotContextImpl receiverContext, Object delegate, PolyglotContextImpl delegateContext) {
        assert !(delegate instanceof OtherContextGuestObject) : "recursive host foreign value found";
        assert receiverContext != null && delegateContext != null : "Must have associated contexts.";
        assert receiverContext != delegateContext : "no need for foreign value if contexts match";
        this.delegate = delegate;
        this.receiverContext = receiverContext;
        this.delegateContext = delegateContext;
    }

    static final int CACHE_LIMIT = 5;

    static boolean canCache(PolyglotSharingLayer cachedLayer, PolyglotContextImpl context0, PolyglotContextImpl context1) {
        /*
         * We can only cache if both layers are identical. Otherwise we might cause invalid sharing
         * across layers.
         */
        return cachedLayer != null && cachedLayer.isClaimed() && cachedLayer.shared == context0.layer.shared && cachedLayer.shared == context1.layer.shared;
    }

    static PolyglotSharingLayer getCachedLayer(Node library) {
        RootNode root = library.getRootNode();
        if (root == null) {
            // cannot cache if not adopted
            return null;
        }
        return ((PolyglotSharingLayer) EngineAccessor.NODES.getSharingLayer(root));
    }

    @ExportMessage
    @ImportStatic(OtherContextGuestObject.class)
    static class Send {

        @Specialization(guards = "canCache(cachedLayer, receiver.receiverContext, receiver.delegateContext)", limit = "1")
        static Object doCached(OtherContextGuestObject receiver, Message message, Object[] args,
                        @Bind Node node,
                        @SuppressWarnings("unused") @CachedLibrary("receiver") ReflectionLibrary receiverLibrary,
                        @Cached(value = "getCachedLayer(receiverLibrary)") PolyglotSharingLayer cachedLayer,
                        @CachedLibrary(limit = "CACHE_LIMIT") ReflectionLibrary delegateLibrary,
                        @Cached InlinedBranchProfile seenOther,
                        @Cached InlinedBranchProfile seenError) throws Exception {
            assert cachedLayer != null;
            return sendImpl(node, cachedLayer, receiver.delegate, message, args, receiver.receiverContext, receiver.delegateContext, delegateLibrary, seenOther, seenError);
        }

        @TruffleBoundary
        @Specialization(replaces = "doCached")
        static Object doSlowPath(OtherContextGuestObject receiver, Message message, Object[] args, @Bind Node node) throws Exception {
            return sendImpl(node, receiver.receiverContext.layer, receiver.delegate, message, args, receiver.receiverContext,
                            receiver.delegateContext,
                            ReflectionLibrary.getUncached(receiver.delegate),
                            InlinedBranchProfile.getUncached(), InlinedBranchProfile.getUncached());
        }

    }

    private static final Message IDENTICAL = Message.resolve(InteropLibrary.class, "isIdentical");

    static Object sendImpl(Node node, PolyglotSharingLayer layer, Object receiver, Message message, Object[] args, PolyglotContextImpl receiverContext,
                    PolyglotContextImpl delegateContext,
                    ReflectionLibrary delegateLibrary,
                    InlinedBranchProfile seenOther,
                    InlinedBranchProfile seenError) throws Exception {
        if (message.getLibraryClass() == InteropLibrary.class) {
            try {
                Object[] prev = layer.engine.enter(delegateContext);
                try {
                    Object returnValue;
                    Object[] migratedArgs = migrateArgs(message, args, receiverContext, delegateContext);
                    if ((message == IDENTICAL) && migratedArgs[0] instanceof OtherContextGuestObject) {
                        OtherContextGuestObject foreignCompare = (OtherContextGuestObject) migratedArgs[0];
                        /*
                         * It is guaranteed at this point that host foreign values with the same
                         * context are already unboxed.
                         */
                        assert foreignCompare.delegateContext != delegateContext;
                        /*
                         * If two values of different contexts are compared with each other we end
                         * up in an endless recursion as each invocation performs argument boxing to
                         * hosted values which in turn will call isIdenticalOrUndefined of the other
                         * value.
                         */
                        returnValue = Boolean.FALSE;
                    } else {
                        returnValue = delegateLibrary.send(receiver, message, migratedArgs);
                    }
                    if (message.getReturnType() == void.class) {
                        return null;
                    }
                    return migrateReturn(returnValue, receiverContext, delegateContext);
                } catch (Throwable e) {
                    seenError.enter(node);
                    throw migrateException(receiverContext, e, delegateContext);
                } finally {
                    layer.engine.leave(prev, delegateContext);
                }
            } catch (Throwable t) {
                seenError.enter(node);
                throw toHostOrInnerContextBoundaryException(receiverContext, t, delegateContext);
            }
        } else {
            seenOther.enter(node);
            return fallbackSend(message, args);
        }
    }

    @SuppressWarnings("unchecked")
    @TruffleBoundary
    static <T extends Throwable> RuntimeException migrateException(PolyglotContextImpl receiverContext, Throwable e, PolyglotContextImpl valueContext) throws T {
        if (e instanceof OtherContextException) {
            OtherContextException other = (OtherContextException) e;
            if (other.receiverContext == receiverContext && other.delegateContext == valueContext) {
                throw other;
            } else {
                throw new OtherContextException(receiverContext, other.delegate, other.delegateContext);
            }
        } else if (InteropLibrary.getUncached().isException(e)) {
            if (e instanceof AbstractTruffleException) {
                throw new OtherContextException(receiverContext, (AbstractTruffleException) e, valueContext);
            } else {
                throw new OtherContextException(receiverContext, (Exception) e, valueContext);
            }
        } else {
            throw (T) e;
        }
    }

    @SuppressWarnings("unchecked")
    @TruffleBoundary
    static <T extends Throwable> RuntimeException toHostOrInnerContextBoundaryException(PolyglotContextImpl receiverContext, Throwable e, PolyglotContextImpl delegateContext) throws T {
        if (e instanceof PolyglotEngineException || e instanceof PolyglotEngineImpl.CancelExecution || e instanceof PolyglotContextImpl.ExitException) {
            try {
                if (e instanceof PolyglotEngineImpl.CancelExecution) {
                    if (delegateContext.parent != null) {
                        PolyglotContextImpl.State receiverContextState = receiverContext.state;
                        if (!receiverContextState.isCancelling() && receiverContextState != PolyglotContextImpl.State.CLOSED_CANCELLED) {
                            delegateContext.runOnCancelled();
                            throw new IllegalStateException("Context cancel exception of inner context leaks outside to a non-cancelled context!");
                        }
                        throw (PolyglotEngineImpl.CancelExecution) e;
                    } else {
                        throw PolyglotImpl.guestToHostException(delegateContext.getHostContext(), e, false);
                    }
                } else if (e instanceof PolyglotContextImpl.ExitException) {
                    if (delegateContext.parent != null) {
                        PolyglotContextImpl.State receiverContextState = receiverContext.state;
                        if (!receiverContextState.isExiting() && receiverContextState != PolyglotContextImpl.State.CLOSED_EXITED) {
                            delegateContext.runOnExited(((PolyglotContextImpl.ExitException) e).getExitCode());
                            throw new IllegalStateException("Context exit exception of inner context leaks outside to a non-exited context!");
                        }
                        throw (PolyglotContextImpl.ExitException) e;
                    } else {
                        throw PolyglotImpl.guestToHostException(delegateContext.getHostContext(), e, false);
                    }
                } else {
                    if (delegateContext.parent != null && e instanceof PolyglotEngineException && ((PolyglotEngineException) e).closedException) {
                        PolyglotContextImpl.State enclosingState = receiverContext != null ? receiverContext.state : PolyglotContextImpl.State.DEFAULT;
                        boolean enclosingDisposing = receiverContext != null && receiverContext.disposing;
                        if (enclosingState != PolyglotContextImpl.State.CLOSED && !enclosingDisposing) {
                            delegateContext.runOnClosed();
                            throw new IllegalStateException("Context close exception of inner context leaks outside to a non-closed context!");
                        }
                    }
                    // [GR-35549] Truffle isolate enters the guest context as result of
                    // delegateLibrary#send(). Exceptions thrown by enter are wrapped as
                    // PolyglotEngineException. We need to unwrap them and throw as host exception.
                    throw PolyglotImpl.engineToLanguageException(e);
                }
            } catch (Throwable ex) {
                if (delegateContext.parent != null) {
                    /*
                     * Note that the parent context might be different from the receiverContext. In
                     * any case, if the delegateContext has parent, we don't wrap the resulting
                     * exception with HostException.
                     */
                    throw ex;
                } else {
                    throw receiverContext.engine.host.toHostException(receiverContext.getHostContextImpl(), ex);
                }
            }
        } else {
            throw (T) e;
        }
    }

    @TruffleBoundary
    private static Object fallbackSend(Message message, Object[] args) throws Exception {
        /*
         * This is a convenient way to trigger the default implementation for all other libraries.
         * We do not want to redirect anything other than interop.
         */
        return OTHER_VALUE_UNCACHED.send(OTHER_VALUE, message, args);
    }

    private static Object migrateReturn(Object arg, PolyglotContextImpl receiverContext, PolyglotContextImpl delegateContext) {
        if (arg instanceof TruffleObject) {
            return receiverContext.migrateValue(arg, delegateContext);
        } else {
            assert InteropLibrary.isValidProtocolValue(arg) : "unexpected interop primitive";
            return arg;
        }
    }

    private static Object migrateArg(Object arg, PolyglotContextImpl receiverContext, PolyglotContextImpl delegateContext) {
        if (arg instanceof TruffleObject) {
            return delegateContext.migrateValue(arg, receiverContext);
        } else if (arg instanceof Object[]) {
            return migrateArgs(null, (Object[]) arg, receiverContext, delegateContext);
        } else if (arg instanceof InteropLibrary) {
            return InteropLibrary.getUncached();
        } else {
            assert InteropLibrary.isValidProtocolValue(arg);
            return arg;
        }
    }

    private static Object[] migrateArgs(Message message, Object[] args, PolyglotContextImpl receiverContext, PolyglotContextImpl delegateContext) {
        if (message != null) {
            return migrateArgsExplode(message, args, receiverContext, delegateContext);
        } else {
            Object[] newArgs = new Object[args.length];
            for (int i = 0; i < args.length; i++) {
                newArgs[i] = migrateArg(args[i], receiverContext, delegateContext);
            }
            return newArgs;
        }
    }

    @ExplodeLoop
    private static Object[] migrateArgsExplode(Message message, Object[] args, PolyglotContextImpl receiverContext, PolyglotContextImpl delegateContext) {
        int length = message.getParameterCount();
        Object[] newArgs = new Object[length - 1];
        for (int i = 0; i < length - 1; i++) {
            newArgs[i] = migrateArg(args[i], receiverContext, delegateContext);
        }
        return newArgs;
    }

    @Override
    public String toString() {
        return "OtherContextGuestObject[" + //
                        "targetContext=0x" + Integer.toHexString(System.identityHashCode(receiverContext)) + //
                        ", delegate=(" + delegate.getClass().getSimpleName() + "(0x" + Integer.toHexString(System.identityHashCode(delegate)) + ")" + //
                        ", delegateContext=0x" + Integer.toHexString(System.identityHashCode(delegateContext));
    }

    @SuppressWarnings("serial")
    @ExportLibrary(ReflectionLibrary.class)
    static class OtherContextException extends AbstractTruffleException {

        final PolyglotContextImpl receiverContext;
        final Exception delegate;
        final PolyglotContextImpl delegateContext;

        OtherContextException(PolyglotContextImpl receiverContext, AbstractTruffleException delegate, PolyglotContextImpl delegateContext) {
            super(delegate);
            assert !(delegate instanceof OtherContextException) : "recursive host foreign value found";
            assert receiverContext != null && delegateContext != null : "Must have associated contexts.";
            assert receiverContext != delegateContext : "no need for foreign value if contexts match";
            this.delegate = delegate;
            this.receiverContext = receiverContext;
            this.delegateContext = delegateContext;
        }

        @TruffleBoundary
        OtherContextException(PolyglotContextImpl thisContext, Exception delegate, PolyglotContextImpl delegateContext) {
            super(delegate.getMessage());
            assert !(delegate instanceof OtherContextException) : "recursive host foreign value found";
            assert thisContext != null && delegateContext != null : "Must have associated contexts.";
            assert thisContext != delegateContext : "no need for foreign value if contexts match";
            this.delegate = delegate;
            this.receiverContext = thisContext;
            this.delegateContext = delegateContext;
        }

        @ExportMessage
        @ImportStatic(OtherContextGuestObject.class)
        static class Send {

            @Specialization(guards = "canCache(cachedLayer, receiver.receiverContext, receiver.delegateContext)", limit = "1")
            static Object doCached(OtherContextException receiver, Message message, Object[] args,
                            @Bind Node node,
                            @SuppressWarnings("unused") @CachedLibrary("receiver") ReflectionLibrary receiverLibrary,
                            @Cached(value = "getCachedLayer(receiverLibrary)") PolyglotSharingLayer cachedLayer,
                            @CachedLibrary(limit = "CACHE_LIMIT") ReflectionLibrary delegateLibrary,
                            @Cached InlinedBranchProfile seenOther,
                            @Cached InlinedBranchProfile seenError) throws Exception {
                assert cachedLayer != null;
                return sendImpl(node, cachedLayer, receiver.delegate, message, args, receiver.receiverContext, receiver.delegateContext, delegateLibrary, seenOther, seenError);
            }

            @TruffleBoundary
            @Specialization(replaces = "doCached")
            static Object doSlowPath(OtherContextException receiver, Message message, Object[] args,
                            @Bind Node node) throws Exception {
                return sendImpl(node, receiver.receiverContext.layer, receiver.delegate, message, args, receiver.receiverContext,
                                receiver.delegateContext,
                                ReflectionLibrary.getUncached(receiver.delegate),
                                InlinedBranchProfile.getUncached(), InlinedBranchProfile.getUncached());
            }

        }
    }

}
