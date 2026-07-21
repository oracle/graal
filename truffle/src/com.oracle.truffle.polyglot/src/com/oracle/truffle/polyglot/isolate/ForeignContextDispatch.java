/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.polyglot.isolate;

import static com.oracle.truffle.polyglot.isolate.ForeignIsolateSourceCache.isSourceRemotelyCacheable;

import org.graalvm.nativebridge.CustomDispatchAccessor;
import org.graalvm.nativebridge.CustomReceiverAccessor;
import org.graalvm.nativebridge.GenerateHotSpotToNativeBridge;
import org.graalvm.nativebridge.GenerateNativeToNativeBridge;
import org.graalvm.nativebridge.GenerateProcessToProcessBridge;
import org.graalvm.nativebridge.Isolate;
import org.graalvm.nativebridge.IsolateDeathException;
import org.graalvm.nativebridge.IsolateDeathHandler;
import org.graalvm.nativebridge.IsolateThread;
import org.graalvm.nativebridge.Peer;
import org.graalvm.nativebridge.ReceiverMethod;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.APIAccess;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractContextDispatch;

import com.oracle.truffle.polyglot.isolate.PolyglotMarshallerConfig.ValueReceiver;

import java.lang.ref.Reference;

@GenerateHotSpotToNativeBridge(factory = PolyglotIsolateForeignFactory.class)
@GenerateNativeToNativeBridge(factory = PolyglotIsolateForeignFactory.class)
@GenerateProcessToProcessBridge(factory = PolyglotIsolateForeignFactory.class)
@IsolateDeathHandler(ForeignContextDispatch.AsCancelledPolyglotException.class)
abstract class ForeignContextDispatch extends AbstractContextDispatch {

    private final APIAccess apiAccess;
    private final ThreadLocal<ExplicitlyEnteredIsolate> explicitIsolateStack;

    ForeignContextDispatch(AbstractPolyglotImpl impl) {
        super(impl);
        this.apiAccess = impl.getAPIAccess();
        this.explicitIsolateStack = new ThreadLocal<>();
    }

    @Override
    public final String toString(Object receiver, int identityHash, String isolateDescription) {
        ForeignContext foreignContext = (ForeignContext) receiver;
        Peer peer = foreignContext.getPeer();
        synchronized (foreignContext) {
            if (!foreignContext.isDisposed()) {
                Isolate<?> isolate = peer.getIsolate();
                try {
                    IsolateThread isolateThread = isolate.tryEnter();
                    if (isolateThread != null) {
                        try {
                            return toStringImpl(receiver, identityHash, ForeignEngineDispatch.formatIsolate(peer, false));
                        } finally {
                            isolateThread.leave();
                        }
                    }
                } catch (IsolateDeathException isolateDeath) {
                    // Fall through and return unavailable.
                }
            }
            return ForeignEngineDispatch.unavailableToString("Context", identityHash, peer);
        }
    }

    @ReceiverMethod("toString")
    @IsolateDeathHandler(IsolateDeathHandlerSupport.KeepIsolateDeathException.class)
    abstract String toStringImpl(Object receiver, int identityHash, String isolate);

    @Override
    public final void setContextAPIReference(Object receiver, Reference<Context> key) {
        ((ForeignContext) receiver).setContextAPIReference(key);
    }

    @Override
    public final Object asValue(Object receiver, Object hostValue) {
        return apiAccess.contextAsValue(((ForeignContext) receiver).getLocalContext(), hostValue);
    }

    @Override
    public final Object eval(Object receiver, String language, Object source) {
        return parseEval(receiver, language, source, true);
    }

    @Override
    public final Object parse(Object receiver, String language, Object source) {
        return parseEval(receiver, language, source, false);
    }

    private Object parseEval(Object receiver, String language, Object source, boolean eval) {
        ForeignContext foreignContext = (ForeignContext) receiver;
        Context localContext = foreignContext.getLocalContext();
        Object prev = enterIfNeeded(localContext);
        try {
            long sourceHandle = foreignContext.getSourceCache().translate(source);
            try {
                return foreignContext.getPolyglotIsolateServices().parseEval(foreignContext, language, sourceHandle, eval);
            } finally {
                /*
                 * It is very important that the source is accessed here, especially for those
                 * sources that ARE remotely cacheable, because otherwise the source could be
                 * collected and removed from the remote cache during the parseEval method execution
                 * that uses just the remote handle.
                 */
                if (!isSourceRemotelyCacheable(source, apiAccess)) {
                    foreignContext.getSourceCache().release(sourceHandle);
                }
            }
        } catch (IsolateDeathException isolateDeath) {
            throw IsolateDeathHandlerSupport.createCancelledPolyglotException(foreignContext, isolateDeath);
        } finally {
            leaveIfNeeded(localContext, prev);
        }
    }

    @Override
    public final boolean initializeLanguage(Object receiver, String languageId) {
        Object localContext = ((ForeignContext) receiver).getLocalContext();
        Object prev = enterIfNeeded(localContext);
        try {
            return initializeLanguageImpl(receiver, languageId);
        } finally {
            leaveIfNeeded(localContext, prev);
        }
    }

    @ReceiverMethod("initializeLanguage")
    abstract boolean initializeLanguageImpl(Object receiver, String languageId);

    @Override
    public final void close(Object receiver, boolean cancelIfExecuting) {
        ForeignContext foreignContext = (ForeignContext) receiver;
        Isolate<?> isolate = foreignContext.getPeer().getIsolate();
        /*
         * 1) Call close if the context is still in {@code
         * polyglot.isolatedContextsByLocalContext.containsKey(foreignContext.getLocalContext())
         */
        boolean contextRegistered = PolyglotIsolateHostSupport.requireContextRegistered(foreignContext);
        if (contextRegistered) {
            boolean foreignCloseSuccessful = false;
            try {
                IsolateThread isolateThread = isolate.tryEnter();
                if (isolateThread != null) {
                    try {
                        /*
                         * A2) Close isolated context
                         */
                        closeImpl(receiver, cancelIfExecuting);
                        foreignCloseSuccessful = true;
                    } finally {
                        isolateThread.leave();
                        clearExplicitContextStack(foreignContext);
                    }
                } else {
                    /*
                     * B2) In case the isolated context is not accessible, close just the local
                     * context. It should be already closed as the isolated context close operation
                     * notifies the local context. However, context close might not be a no-op even
                     * if the context is already closed, so we have to execute it.
                     */
                    apiAccess.contextClose(foreignContext.getLocalContext(), cancelIfExecuting);
                }
            } finally {
                /*
                 * A3/B3) Remove from {@code isolatedContextsByHandle.Lazy#isolatedContextsByHandle}
                 * if the isolate is not active and the context is not required elsewhere
                 */
                PolyglotIsolateHostSupport.releaseContextRegisteredRequirement(foreignContext, !isolate.isActive() && foreignCloseSuccessful);
            }
        } else {
            /*
             * C2) In case the isolated context is not accessible, close just the local context. It
             * should be already closed as the isolated context close operation notifies the local
             * context. However, context close might not be a no-op even if the context is already
             * closed, so we have to execute it.
             */
            apiAccess.contextClose(foreignContext.getLocalContext(), cancelIfExecuting);
            clearExplicitContextStack(foreignContext);
        }
    }

    private void clearExplicitContextStack(ForeignContext currentForeignContext) {
        ExplicitlyEnteredIsolate explicitlyEnteredIsolate = explicitIsolateStack.get();
        if (explicitlyEnteredIsolate != null && explicitlyEnteredIsolate.foreignContext == currentForeignContext) {
            if (!PolyglotIsolateAccessor.ENGINE.isContextEntered(apiAccess.getContextReceiver(currentForeignContext.getLocalContext()))) {
                while (explicitlyEnteredIsolate != null && explicitlyEnteredIsolate.foreignContext == currentForeignContext) {
                    explicitlyEnteredIsolate.isolateThread.leave();
                    explicitlyEnteredIsolate = explicitlyEnteredIsolate.previous;
                }
                explicitIsolateStack.set(explicitlyEnteredIsolate);
            }
        }
    }

    @ReceiverMethod("close")
    abstract void closeImpl(Object receiver, boolean cancelIfExecuting);

    @Override
    public final void explicitEnter(Object receiver) {
        ForeignContext foreignContext = (ForeignContext) receiver;
        // 1) Enter local context
        apiAccess.contextEnter(foreignContext.getLocalContext());
        try {
            // 2) Enter isolate
            IsolateThread isolateThread = foreignContext.getPeer().getIsolate().enter();
            explicitIsolateStack.set(new ExplicitlyEnteredIsolate(foreignContext, isolateThread, explicitIsolateStack.get()));
            // 3) Enter isolated context
            explicitEnterImpl(receiver);
        } catch (Throwable t) {
            apiAccess.contextLeave(foreignContext.getLocalContext());
            throw t;
        }
    }

    @ReceiverMethod("explicitEnter")
    abstract void explicitEnterImpl(Object receiver);

    @Override
    public final void explicitLeave(Object receiver) {
        ForeignContext foreignContext = (ForeignContext) receiver;
        try {
            if (foreignContext.getPeer().getIsolate().isActive()) {
                /*
                 * Explicit leave should be a no-op if the context is already closed, but we have no
                 * easy way to determine whether the isolated context is closed as the whole isolate
                 * might be closed, so we rather test whether the isolate is active in the current
                 * thread.
                 */
                // 1) Leave isolated context
                explicitLeaveImpl(receiver);
                // 2) Leave isolate
                ExplicitlyEnteredIsolate current = explicitIsolateStack.get();
                explicitIsolateStack.set(current.previous);
                current.isolateThread.leave();
            }
        } finally {
            // 3) Leave local context
            apiAccess.contextLeave(foreignContext.getLocalContext());
        }
    }

    @ReceiverMethod("explicitLeave")
    abstract void explicitLeaveImpl(Object receiver);

    @Override
    public final Object getBindings(Object receiver, String language) {
        Object localContext = ((ForeignContext) receiver).getLocalContext();
        Object prev = enterIfNeeded(localContext);
        try {
            return getBindingsImpl(receiver, language);
        } finally {
            leaveIfNeeded(localContext, prev);
        }
    }

    @ValueReceiver
    @ReceiverMethod("getBindings")
    abstract Object getBindingsImpl(Object receiver, String language);

    @Override
    @ValueReceiver
    public abstract Object getPolyglotBindings(Object receiver);

    private Object enterIfNeeded(Object localContext) {
        Object localContextReceiver = apiAccess.getContextReceiver(localContext);
        return PolyglotIsolateAccessor.ENGINE.enterIfNeeded(localContextReceiver);
    }

    private void leaveIfNeeded(Object localContext, Object prev) {
        Object localContextReceiver = apiAccess.getContextReceiver(localContext);
        PolyglotIsolateAccessor.ENGINE.leaveIfNeeded(localContextReceiver, prev);
    }

    @Override
    public final void onContextCollected(Object contextReceiver) {
        ForeignContext foreignContext = (ForeignContext) contextReceiver;
        Isolate<?> isolate = foreignContext.getPeer().getIsolate();
        IsolateThread isolateThread = isolate.tryEnter();
        /*
         * The order of references enqueued in the ReferenceQueue is not deterministic. As a result,
         * the engine can be polled before the context and the engine and isolate may already be
         * closed. We need to use tryEnter to prevent entering a disposed isolate.
         */
        boolean isolateValid = isolateThread != null;
        if (isolateValid) {
            try {
                onContextCollectedImpl(contextReceiver);
            } catch (IsolateDeathException isolateDeath) {
                isolateValid = false;
            } finally {
                isolateThread.leave();
            }
        }
        if (!isolateValid) {
            // The isolate is no more available try to clean the local context.
            Context localContext = foreignContext.getLocalContext();
            apiAccess.getContextDispatch(localContext).onContextCollected(apiAccess.getContextReceiver(localContext));
            if (PolyglotIsolateHostSupport.requireContextRegistered(foreignContext)) {
                PolyglotIsolateHostSupport.releaseContextRegisteredRequirement(foreignContext, true);
            }
        }
    }

    @ReceiverMethod("onContextCollected")
    @IsolateDeathHandler(IsolateDeathHandlerSupport.KeepIsolateDeathException.class)
    abstract void onContextCollectedImpl(Object engine);

    @CustomDispatchAccessor
    static AbstractContextDispatch resolveForeignDelegate(GuestContext guestContext) {
        return PolyglotIsolateHostSupport.getPolyglot().getAPIAccess().getContextDispatch(guestContext.context);
    }

    @CustomReceiverAccessor
    static Object resolveReceiver(GuestContext guestContext) {
        return PolyglotIsolateHostSupport.getPolyglot().getAPIAccess().getContextReceiver(guestContext.context);
    }

    static final class AsCancelledPolyglotException {

        private AsCancelledPolyglotException() {
        }

        static void handleIsolateDeath(Object receiver, IsolateDeathException isolateDeath) {
            throw IsolateDeathHandlerSupport.createCancelledPolyglotException((ForeignContext) receiver, isolateDeath);
        }
    }

    private static final class ExplicitlyEnteredIsolate {

        final ForeignContext foreignContext;
        final IsolateThread isolateThread;
        final ExplicitlyEnteredIsolate previous;

        ExplicitlyEnteredIsolate(ForeignContext foreignContext, IsolateThread value, ExplicitlyEnteredIsolate previous) {
            this.foreignContext = foreignContext;
            this.isolateThread = value;
            this.previous = previous;
        }
    }
}
