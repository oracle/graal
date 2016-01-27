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
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.InstrumentableFactory.WrapperNode;
import com.oracle.truffle.api.instrumentation.InstrumentationHandler.InstrumentationInstrumenter;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.source.SourceSection;

/**
 * <p>
 * Class provided by {@link InstrumentableFactory#createWrapper(Node, ProbeNode)} to notify the
 * instrumentation API about execution events.
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
 */
public final class ProbeNode extends Node {

    private final InstrumentationHandler instrumenter;
    private final EventContext context;

    @Child private ProbeNode.EventChainNode chain;

    /*
     * We cache to ensure that the instrumented tags and source sections are always compilation
     * final for listeners and factories.
     */
    @CompilationFinal private Assumption version;

    /** Is instantiated by the instrumentation framework. */
    ProbeNode(InstrumentationHandler impl, SourceSection sourceSection) {
        this.instrumenter = impl;
        this.context = new EventContext(this, sourceSection);
    }

    /**
     * Should get invoked before the node is invoked.
     *
     * @param frame the current frame of the execution.
     */
    public void onEnter(VirtualFrame frame) {
        if (lazyUpdate(frame)) {
            chain.onEnter(context, frame);
        }
    }

    /**
     * Should get invoked after the node is invoked sucessfuly.
     *
     * @param result the result value of the operation
     * @param frame the current frame of the execution.
     */
    public void onReturnValue(VirtualFrame frame, Object result) {
        if (lazyUpdate(frame)) {
            chain.onReturnValue(context, frame, result);
        }
    }

    /**
     * Should get invoked if the node did not complete sucessfully.
     *
     * @param exception the exception that occured during the execution
     * @param frame the current frame of the execution.
     */
    public void onReturnExceptional(VirtualFrame frame, Throwable exception) {
        if (lazyUpdate(frame)) {
            chain.onReturnExceptional(context, frame, exception);
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

    void invalidate() {
        if (version != null) {
            version.invalidate();
        } else {
            assert chain == null;
        }
    }

    private boolean lazyUpdate(VirtualFrame frame) {
        if (version == null || !version.isValid()) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            // i am allowed to pass in the virtual frame as its instances are always materialized
            return lazyUpdatedImpl(frame);
        }
        return true;
    }

    private boolean lazyUpdatedImpl(VirtualFrame frame) {
        Node nextChain = instrumenter.installBindings(ProbeNode.this);
        if (nextChain == null) {
            // chain is null -> remove wrapper;
            // Note: never set child nodes to null, can cause races
            InstrumentationHandler.removeWrapper(ProbeNode.this);
            return false;
        }
        EventChainNode oldChain = this.chain;
        if (oldChain != null) {
            oldChain.onDispose(context, frame);
        }
        this.chain = (EventChainNode) insert(nextChain);
        this.version = Truffle.getRuntime().createAssumption("Instrumentations unchanged");
        return true;
    }

    ProbeNode.EventChainNode createEventChainCallback(EventBinding<?> binding) {
        ProbeNode.EventChainNode next;
        Object element = binding.getElement();
        if (element instanceof EventListener) {
            next = new EventFilterChainNode(binding, (EventListener) element);
        } else {
            assert element instanceof EventNodeFactory;
            EventNode eventNode = createEventNode(binding, element);
            if (eventNode == null) {
                // error occured creating the event node
                return null;
            }
            next = new EventProviderChainNode(binding, eventNode);
        }
        return next;
    }

    private EventNode createEventNode(EventBinding<?> binding, Object element) {
        EventNode eventNode;
        try {
            eventNode = ((EventNodeFactory) element).create(context);
            if (eventNode.getParent() != null) {
                throw new IllegalStateException(String.format("Returned EventNode %s was already adopted by another AST.", eventNode));
            }
        } catch (Throwable t) {
            if (binding.isLanguageBinding()) {
                /* Language bindings can just throw exceptions directly into the AST. */
                throw t;
            } else {
                /* Where as instrumentations are not allowed to do that. */
                failEventForInstrumentation(binding, "ProbeNodeFactory.create", t);
                return null;
            }
        }
        return eventNode;
    }

    static void failEventForInstrumentation(EventBinding<?> b, String eventName, Throwable t) {
        assert !b.isLanguageBinding();
        InstrumentationInstrumenter instrumentationInstrumenter = (InstrumentationInstrumenter) b.getInstrumenter();
        Class<?> instrumentationClass = instrumentationInstrumenter.getInstrumentationClass();

        String message = String.format("Event %s failed for instrumentation class %s and listener/factory %s.", //
                        eventName, instrumentationClass.getName(), b.getElement());

        Exception exception = new Exception(message, t);
        PrintStream stream = new PrintStream(instrumentationInstrumenter.getEnv().err());
        exception.printStackTrace(stream);
    }

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
                    CompilerDirectives.transferToInterpreter();
                    failEventForInstrumentation(binding, "onEnter", t);
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
                    failEventForInstrumentation(binding, "onEnter", t);
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
                    failEventForInstrumentation(binding, "onReturnValue", t);
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
                    failEventForInstrumentation(binding, "onReturnExceptional", t);
                }
            }
            if (next != null) {
                next.onReturnExceptional(context, frame, exception);
            }
        }

        protected abstract void innerOnReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception);

    }

    private static class EventFilterChainNode extends ProbeNode.EventChainNode {

        private final EventListener listener;

        EventFilterChainNode(EventBinding<?> binding, EventListener listener) {
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

        @Child private EventNode eventNode;

        EventProviderChainNode(EventBinding<?> binding, EventNode eventNode) {
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
