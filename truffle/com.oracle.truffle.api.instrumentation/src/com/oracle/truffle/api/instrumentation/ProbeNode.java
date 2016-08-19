/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.instrumentation;

import java.io.PrintStream;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.InstrumentableFactory.WrapperNode;
import com.oracle.truffle.api.instrumentation.InstrumentationHandler.InstrumentClientInstrumenter;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.source.SourceSection;

/**
 * <p>
 * Represents an event sink for instrumentation events that is embedded in the AST using wrappers if
 * needed. Instances of this class are provided by
 * {@link InstrumentableFactory#createWrapper(Node, ProbeNode)} to notify the instrumentation API
 * about execution events.
 * </p>
 *
 * The recommended use of this node for implementing {@link WrapperNode wrapper nodes} looks as
 * follows:
 *
 * <pre>
 * &#064;Override
 * public Object execute(VirtualFrame frame) {
 *     try {
 *         probeNode.onEnter(frame);
 *         Object returnValue = delegate.execute(frame);
 *         probeNode.onReturnValue(frame, returnValue);
 *         return returnValue;
 *     } catch (Throwable t) {
 *         probeNode.onReturnExceptional(frame, t);
 *         throw t;
 *     }
 * }
 * </pre>
 *
 * @since 0.12
 */
public final class ProbeNode extends Node {

    private final InstrumentationHandler handler;
    private final EventContext context;

    @Child private volatile ProbeNode.EventChainNode chain;

    /*
     * We cache to ensure that the instrumented tags and source sections are always compilation
     * final for listeners and factories.
     */
    @CompilationFinal private volatile Assumption version;

    /** Instantiated by the instrumentation framework. */
    ProbeNode(InstrumentationHandler handler, SourceSection sourceSection) {
        this.handler = handler;
        this.context = new EventContext(this, sourceSection);
    }

    /**
     * Should get invoked before the node is invoked.
     *
     * @param frame the current frame of the execution.
     * @since 0.12
     */
    public void onEnter(VirtualFrame frame) {
        EventChainNode localChain = lazyUpdate(frame);
        if (localChain != null) {
            localChain.onEnter(context, frame);
        }
    }

    /**
     * Should get invoked after the node is invoked successfully.
     *
     * @param result the result value of the operation
     * @param frame the current frame of the execution.
     * @since 0.12
     */
    public void onReturnValue(VirtualFrame frame, Object result) {
        EventChainNode localChain = lazyUpdate(frame);
        if (localChain != null) {
            localChain.onReturnValue(context, frame, result);
        }
    }

    /**
     * Should get invoked if the node did not complete successfully.
     *
     * @param exception the exception that occurred during the execution
     * @param frame the current frame of the execution.
     * @since 0.12
     */
    public void onReturnExceptional(VirtualFrame frame, Throwable exception) {
        if (exception instanceof ThreadDeath) {
            throw (ThreadDeath) exception;
        }
        EventChainNode localChain = lazyUpdate(frame);
        if (localChain != null) {
            localChain.onReturnExceptional(context, frame, exception);
        }
    }

    EventContext getContext() {
        return context;
    }

    WrapperNode findWrapper() throws AssertionError {
        Node parent = getParent();
        if (!(parent instanceof WrapperNode)) {
            if (parent == null) {
                throw new AssertionError("Probe node disconnected from AST.");
            } else {
                throw new AssertionError("ProbeNodes must have a parent Node that implements NodeWrapper.");
            }
        }
        return (WrapperNode) parent;
    }

    synchronized void invalidate() {
        Assumption localVersion = this.version;
        if (localVersion != null) {
            localVersion.invalidate();
        }
    }

    private EventChainNode lazyUpdate(VirtualFrame frame) {
        Assumption localVersion = this.version;
        if (localVersion == null || !localVersion.isValid()) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            // Ok to pass in the virtual frame as its instances are always materialized
            return lazyUpdatedImpl(frame);
        }
        return this.chain;
    }

    private EventChainNode lazyUpdatedImpl(VirtualFrame frame) {
        EventChainNode nextChain = handler.createBindings(ProbeNode.this);
        if (nextChain == null) {
            // chain is null -> remove wrapper;
            // Note: never set child nodes to null, can cause races
            InstrumentationHandler.removeWrapper(ProbeNode.this);
            return null;
        }

        EventChainNode oldChain;
        synchronized (this) {
            oldChain = this.chain;
            this.chain = insert(nextChain);
            this.version = Truffle.getRuntime().createAssumption("Instruments unchanged");
        }

        if (oldChain != null) {
            oldChain.onDispose(context, frame);
        }

        return nextChain;
    }

    ExecutionEventNode lookupExecutionEventNode(EventBinding<?> binding) {
        if (binding.isDisposed()) {
            return null;
        }
        EventChainNode chainNode = this.chain;
        while (chainNode != null) {
            if (chainNode.binding == binding) {
                if (chainNode instanceof EventProviderChainNode) {
                    return ((EventProviderChainNode) chainNode).eventNode;
                }
            }
            chainNode = chainNode.next;
        }
        return null;
    }

    ProbeNode.EventChainNode createEventChainCallback(EventBinding<?> binding) {
        ProbeNode.EventChainNode next;
        Object element = binding.getElement();
        if (element instanceof ExecutionEventListener) {
            next = new EventFilterChainNode(binding, (ExecutionEventListener) element);
        } else {
            assert element instanceof ExecutionEventNodeFactory;
            ExecutionEventNode eventNode = createEventNode(binding, element);
            if (eventNode == null) {
                // error occurred creating the event node
                return null;
            }
            next = new EventProviderChainNode(binding, eventNode);
        }
        return next;
    }

    private ExecutionEventNode createEventNode(EventBinding<?> binding, Object element) {
        ExecutionEventNode eventNode;
        try {
            eventNode = ((ExecutionEventNodeFactory) element).create(context);
            if (eventNode.getParent() != null) {
                throw new IllegalStateException(String.format("Returned EventNode %s was already adopted by another AST.", eventNode));
            }
        } catch (Throwable t) {
            if (binding.isLanguageBinding()) {
                /* Language bindings can just throw exceptions directly into the AST. */
                throw t;
            } else {
                /*
                 * Client Instruments are not allowed to disrupt program execution by throwing
                 * exceptions into the AST.
                 */
                exceptionEventForClientInstrument(binding, "ProbeNodeFactory.create", t);
                return null;
            }
        }
        return eventNode;
    }

    /**
     * Handles exceptions from non-language instrumentation code that must not be allowed to alter
     * guest language execution semantics. Normal response is to log and continue.
     */
    @TruffleBoundary
    static void exceptionEventForClientInstrument(EventBinding<?> b, String eventName, Throwable t) {
        assert !b.isLanguageBinding();
        if (t instanceof ThreadDeath) {
            // Terminates guest language execution immediately
            throw (ThreadDeath) t;
        }
        // Exception is a failure in (non-language) instrumentation code; log and continue
        InstrumentClientInstrumenter instrumenter = (InstrumentClientInstrumenter) b.getInstrumenter();
        Class<?> instrumentClass = instrumenter.getInstrumentClass();

        String message = String.format("Event %s failed for instrument class %s and listener/factory %s.", //
                        eventName, instrumentClass.getName(), b.getElement());

        Exception exception = new Exception(message, t);
        PrintStream stream = new PrintStream(instrumenter.getEnv().err());
        exception.printStackTrace(stream);
    }

    /** @since 0.12 */
    @Override
    public NodeCost getCost() {
        return NodeCost.NONE;
    }

    abstract static class EventChainNode extends Node {

        @Child private ProbeNode.EventChainNode next;
        private final EventBinding<?> binding;
        @CompilationFinal private boolean seenException;

        EventChainNode(EventBinding<?> binding) {
            this.binding = binding;
        }

        final void setNext(ProbeNode.EventChainNode next) {
            this.next = insert(next);
        }

        EventBinding<?> getBinding() {
            return binding;
        }

        ProbeNode.EventChainNode getNext() {
            return next;
        }

        @Override
        public final NodeCost getCost() {
            return NodeCost.NONE;
        }

        final void onDispose(EventContext context, VirtualFrame frame) {
            try {
                innerOnDispose(context, frame);
            } catch (Throwable t) {
                if (!seenException) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    seenException = true;
                }
                if (binding.isLanguageBinding()) {
                    throw t;
                } else {
                    exceptionEventForClientInstrument(binding, "onEnter", t);
                }
            }
            if (next != null) {
                next.onDispose(context, frame);
            }
        }

        protected abstract void innerOnDispose(EventContext context, VirtualFrame frame);

        final void onEnter(EventContext context, VirtualFrame frame) {
            try {
                innerOnEnter(context, frame);
            } catch (Throwable t) {
                if (!seenException) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    seenException = true;
                }
                if (binding.isLanguageBinding()) {
                    throw t;
                } else {
                    CompilerDirectives.transferToInterpreter();
                    exceptionEventForClientInstrument(binding, "onEnter", t);
                }
            }
            if (next != null) {
                next.onEnter(context, frame);
            }
        }

        protected abstract void innerOnEnter(EventContext context, VirtualFrame frame);

        final void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
            try {
                innerOnReturnValue(context, frame, result);
            } catch (Throwable t) {
                if (!seenException) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    seenException = true;
                }
                if (binding.isLanguageBinding()) {
                    throw t;
                } else {
                    CompilerDirectives.transferToInterpreter();
                    exceptionEventForClientInstrument(binding, "onReturnValue", t);
                }
            }
            if (next != null) {
                next.onReturnValue(context, frame, result);
            }
        }

        protected abstract void innerOnReturnValue(EventContext context, VirtualFrame frame, Object result);

        final void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
            try {
                innerOnReturnExceptional(context, frame, exception);
            } catch (Throwable t) {
                if (!seenException) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    seenException = true;
                }
                if (binding.isLanguageBinding()) {
                    exception.addSuppressed(t);
                } else {
                    CompilerDirectives.transferToInterpreter();
                    exceptionEventForClientInstrument(binding, "onReturnExceptional", t);

                }
            }
            if (next != null) {
                next.onReturnExceptional(context, frame, exception);
            }
        }

        protected abstract void innerOnReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception);

    }

    private static class EventFilterChainNode extends ProbeNode.EventChainNode {

        private final ExecutionEventListener listener;

        EventFilterChainNode(EventBinding<?> binding, ExecutionEventListener listener) {
            super(binding);
            this.listener = listener;
        }

        @Override
        protected void innerOnEnter(EventContext context, VirtualFrame frame) {
            listener.onEnter(context, frame);
        }

        @Override
        protected void innerOnReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
            listener.onReturnExceptional(context, frame, exception);
        }

        @Override
        protected void innerOnReturnValue(EventContext context, VirtualFrame frame, Object result) {
            listener.onReturnValue(context, frame, result);
        }

        @Override
        protected void innerOnDispose(EventContext context, VirtualFrame frame) {
        }

    }

    private static class EventProviderChainNode extends ProbeNode.EventChainNode {

        @Child private ExecutionEventNode eventNode;

        EventProviderChainNode(EventBinding<?> binding, ExecutionEventNode eventNode) {
            super(binding);
            this.eventNode = eventNode;
        }

        @Override
        protected void innerOnEnter(EventContext context, VirtualFrame frame) {
            eventNode.onEnter(frame);
        }

        @Override
        protected void innerOnReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
            eventNode.onReturnExceptional(frame, exception);
        }

        @Override
        protected void innerOnReturnValue(EventContext context, VirtualFrame frame, Object result) {
            eventNode.onReturnValue(frame, result);
        }

        @Override
        protected void innerOnDispose(EventContext context, VirtualFrame frame) {
            eventNode.onDispose(frame);
        }

    }
}
