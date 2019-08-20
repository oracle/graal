/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.instrumentation;

import java.io.PrintStream;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.locks.Lock;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.InstrumentationHandler.EngineInstrumenter;
import com.oracle.truffle.api.instrumentation.InstrumentationHandler.InstrumentClientInstrumenter;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.NodeVisitor;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.source.SourceSection;

/**
 * <p>
 * Represents an event sink for instrumentation events that is embedded in the AST using wrappers if
 * needed. Instances of this class are provided by
 * {@link InstrumentableFactory#createWrapper(Node, ProbeNode)} to notify the instrumentation API
 * about execution events.
 * </p>
 *
 * It is strongly recommended to use {@link GenerateWrapper} to generate implementations of wrapper
 * nodes. If needed to be done manually then the recommended implementation of an execute method
 * looks as follows:
 *
 * <pre>
 * &#064;Override
 * public Object execute(VirtualFrame frame) {
 *     Object returnValue;
 *     for (;;) {
 *         boolean wasOnReturnExecuted = false;
 *         try {
 *             probeNode.onEnter(frame);
 *             returnValue = delegateNode.executeGeneric(frame);
 *             wasOnReturnExecuted = true;
 *             probeNode.onReturnValue(frame, returnValue);
 *             break;
 *         } catch (Throwable t) {
 *             Object result = probeNode.onReturnExceptionalOrUnwind(frame, t, wasOnReturnExecuted);
 *             if (result == ProbeNode.UNWIND_ACTION_REENTER) {
 *                 continue;
 *             } else if (result != null) {
 *                 returnValue = result;
 *                 break;
 *             }
 *             throw t;
 *         }
 *     }
 *     return returnValue;
 * }
 * </pre>
 *
 * @since 0.12
 */
public final class ProbeNode extends Node {

    /**
     * A constant that performs reenter of the current node when returned from
     * {@link ExecutionEventListener#onUnwind(EventContext, VirtualFrame, Object)} or
     * {@link ExecutionEventNode#onUnwind(VirtualFrame, Object)}.
     *
     * @since 0.31
     */
    public static final Object UNWIND_ACTION_REENTER = new Object();

    // returned from chain nodes whose bindings ignore the unwind
    private static final Object UNWIND_ACTION_IGNORED = new Object();

    private final InstrumentationHandler handler;
    @CompilationFinal private volatile EventContext context;

    @Child private volatile ProbeNode.EventChainNode chain;

    /*
     * We cache to ensure that the instrumented tags and source sections are always compilation
     * final for listeners and factories.
     */
    @CompilationFinal private volatile Assumption version;

    @CompilationFinal private volatile byte seen = 0;
    private final BranchProfile unwindHasNext = BranchProfile.create();

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
     * @param result the result value of the operation, must be an interop type (i.e. either
     *            implementing TruffleObject or be a primitive value), or <code>null</code>.
     * @param frame the current frame of the execution.
     * @since 0.12
     */
    public void onReturnValue(VirtualFrame frame, Object result) {
        EventChainNode localChain = lazyUpdate(frame);
        assert isNullOrInteropValue(result);
        if (localChain != null) {
            localChain.onReturnValue(context, frame, result);
        }
    }

    private boolean isNullOrInteropValue(Object result) {
        if (!(context.getInstrumentedNode() instanceof InstrumentableNode)) {
            // legacy support
            return true;
        }
        if (result == null) {
            return true;
        }
        InstrumentAccessor.interopAccess().checkInteropType(result);
        return true;
    }

    /**
     * Should get invoked if the node did not complete successfully.
     *
     * @param exception the exception that occurred during the execution
     * @param frame the current frame of the execution.
     * @since 0.12
     * @deprecated Use {@link #onReturnExceptionalOrUnwind(VirtualFrame, Throwable, boolean)}
     *             instead and adjust the wrapper node implementation accordingly.
     */
    @Deprecated
    public void onReturnExceptional(VirtualFrame frame, Throwable exception) {
        if (exception instanceof ThreadDeath) {
            throw (ThreadDeath) exception;
        }
        EventChainNode localChain = lazyUpdate(frame);
        if (localChain != null) {
            localChain.onReturnExceptional(context, frame, exception);
        }
    }

    /**
     * Creates a shallow copy of this node.
     *
     * @return the new copy
     * @since 0.31
     */
    @Override
    public Node copy() {
        ProbeNode pn = (ProbeNode) super.copy();
        pn.context = new EventContext(pn, context.getInstrumentedSourceSection());
        return pn;
    }

    /**
     * Should get invoked if the node did not complete successfully and handle a possible unwind.
     * When a non-<code>null</code> value is returned, a change of the execution path was requested
     * by an {@link EventContext#createUnwind(Object) unwind}.
     *
     * @param exception the exception that occurred during the execution
     * @param frame the current frame of the execution.
     * @param isReturnCalled <code>true</code> when {@link #onReturnValue(VirtualFrame, Object)} was
     *            called already for this node's execution, <code>false</code> otherwise. This helps
     *            to assure correct pairing of enter/return notifications.
     * @return <code>null</code> to proceed to throw of the exception,
     *         {@link #UNWIND_ACTION_REENTER} to reenter the current node, or an interop value to
     *         return that value early from the current node (void nodes just return, ignoring the
     *         return value).
     * @since 0.31
     */
    public Object onReturnExceptionalOrUnwind(VirtualFrame frame, Throwable exception, boolean isReturnCalled) {
        UnwindException unwind = null;
        if (exception instanceof UnwindException) {
            if (!isSeenUnwind()) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                setSeenUnwind();
            }
            unwind = (UnwindException) exception;
        } else if (exception instanceof ThreadDeath) {
            throw (ThreadDeath) exception;
        }
        EventChainNode localChain = lazyUpdate(frame);
        if (localChain != null) {
            if (!isReturnCalled) {
                try {
                    localChain.onReturnExceptional(context, frame, exception);
                } catch (UnwindException ex) {
                    if (!isSeenUnwind()) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        setSeenUnwind();
                    }
                    if (unwind != null && unwind != ex) {
                        unwindHasNext.enter();
                        unwind.addNext(ex);
                    } else {
                        unwind = ex;
                    }
                }
            }
            if (unwind != null) { // seenUnwind must be true here
                Object ret = localChain.onUnwind(context, frame, unwind);
                if (ret == UNWIND_ACTION_REENTER) {
                    if (!isSeenReenter()) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        setSeenReenter();
                    }
                    return UNWIND_ACTION_REENTER;
                } else if (ret != null && ret != UNWIND_ACTION_IGNORED) {
                    if (!isSeenReturn()) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        setSeenReturn();
                    }
                    assert isNullOrInteropValue(ret);
                    return ret;
                }
                throw unwind;
            }
        }
        return null;
    }

    private boolean isSeenUnwind() {
        return (seen & 0b1) != 0;
    }

    private void setSeenUnwind() {
        CompilerAsserts.neverPartOfCompilation();
        seen = (byte) (seen | 0b1);
    }

    private boolean isSeenReenter() {
        return (seen & 0b10) != 0;
    }

    private void setSeenReenter() {
        CompilerAsserts.neverPartOfCompilation();
        seen = (byte) (seen | 0b10);
    }

    private boolean isSeenReturn() {
        return (seen & 0b100) != 0;
    }

    private void setSeenReturn() {
        CompilerAsserts.neverPartOfCompilation();
        seen = (byte) (seen | 0b100);
    }

    void onInputValue(VirtualFrame frame, EventBinding<?> targetBinding, EventContext inputContext, int inputIndex, Object inputValue) {
        EventChainNode localChain = lazyUpdate(frame);
        if (localChain != null) {
            localChain.onInputValue(context, frame, targetBinding, inputContext, inputIndex, inputValue);
        }
    }

    EventContext getContext() {
        return context;
    }

    @SuppressWarnings("deprecation")
    com.oracle.truffle.api.instrumentation.InstrumentableFactory.WrapperNode findWrapper() throws AssertionError {
        Node parent = getParent();
        if (!(parent instanceof com.oracle.truffle.api.instrumentation.InstrumentableFactory.WrapperNode)) {
            CompilerDirectives.transferToInterpreter();
            if (parent == null) {
                throw new AssertionError("Probe node disconnected from AST.");
            } else {
                throw new AssertionError("ProbeNodes must have a parent Node that implements NodeWrapper.");
            }
        }
        return (com.oracle.truffle.api.instrumentation.InstrumentableFactory.WrapperNode) parent;
    }

    synchronized void invalidate() {
        Assumption localVersion = this.version;
        if (localVersion != null) {
            localVersion.invalidate();
        }
    }

    EventChainNode lazyUpdate(VirtualFrame frame) {
        Assumption localVersion = this.version;
        if (localVersion == null || !localVersion.isValid()) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            // Ok to pass in the virtual frame as its instances are always materialized
            return lazyUpdatedImpl(frame);
        }
        return this.chain;
    }

    private EventChainNode lazyUpdatedImpl(VirtualFrame frame) {
        EventChainNode oldChain;
        EventChainNode nextChain;
        Lock lock = getLock();
        lock.lock();
        try {
            Assumption localVersion = this.version;
            if (localVersion != null && localVersion.isValid()) {
                return this.chain;
            }
            nextChain = handler.createBindings(frame, ProbeNode.this);
            if (nextChain == null) {
                // chain is null -> remove wrapper;
                // Note: never set child nodes to null, can cause races
                InstrumentationHandler.removeWrapper(ProbeNode.this);
                return null;
            }

            oldChain = this.chain;
            this.chain = insert(nextChain);
            this.version = Truffle.getRuntime().createAssumption("Instruments unchanged");
        } finally {
            lock.unlock();
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

    Iterator<ExecutionEventNode> lookupExecutionEventNodes(Collection<EventBinding<? extends ExecutionEventNodeFactory>> bindings) {
        return new Iterator<ExecutionEventNode>() {

            private EventChainNode chainNode = ProbeNode.this.chain;
            private EventProviderChainNode nextNode;

            @Override
            public boolean hasNext() {
                if (nextNode == null) {
                    while (chainNode != null) {
                        if (chainNode instanceof EventProviderChainNode && bindings.contains(chainNode.binding)) {
                            nextNode = (EventProviderChainNode) chainNode;
                            chainNode = chainNode.next;
                            break;
                        }
                        chainNode = chainNode.next;
                    }
                }
                return nextNode != null;
            }

            @Override
            public ExecutionEventNode next() {
                EventProviderChainNode node = nextNode;
                if (node == null) {
                    throw new NoSuchElementException();
                }
                nextNode = null;
                return node.eventNode;
            }
        };
    }

    EventChainNode createParentEventChainCallback(VirtualFrame frame, EventBinding.Source<?> binding, RootNode rootNode, Set<Class<?>> providedTags) {
        EventChainNode parent = findParentChain(frame, binding);
        if (!(parent instanceof EventProviderWithInputChainNode)) {
            // this event is unreachable because nobody is listening to it.
            return null;
        }

        EventContext parentContext = parent.findProbe().getContext();
        EventProviderWithInputChainNode parentChain = (EventProviderWithInputChainNode) parent;
        int index = indexOfChild(binding, rootNode, providedTags, parentContext.getInstrumentedNode(), parentContext.getInstrumentedSourceSection(), context.getInstrumentedNode());
        if (index < 0 || index >= parentChain.inputCount) {
            // not found. a child got replaced?
            // probe should have been notified about this with notifyInserted
            assert throwIllegalASTAssertion(parentChain, parentContext, binding, rootNode, providedTags, index);
            return null;
        }
        ProbeNode probe = parent.findProbe();
        return new InputValueChainNode(binding, probe, context, index);
    }

    @SuppressWarnings("deprecation")
    private static boolean throwIllegalASTAssertion(EventProviderWithInputChainNode parentChain, EventContext parentContext, EventBinding.Source<?> binding, RootNode rootNode,
                    Set<Class<?>> providedTags, int index) {
        StringBuilder msg = new StringBuilder();
        try {
            // number of additional children that will be looked up from the current index
            // might not be enough depending on the violation.
            final int lookupChildrenCount = 10;

            SourceSection parentSourceSection = parentContext.getInstrumentedSourceSection();
            EventContext[] contexts = findChildContexts(binding, rootNode, providedTags, parentContext.getInstrumentedNode(), parentContext.getInstrumentedSourceSection(),
                            Math.max(parentChain.inputCount, index + lookupChildrenCount));

            int contextCount = 0;
            for (int i = 0; i < contexts.length; i++) {
                EventContext eventContext = contexts[i];
                if (eventContext != null) {
                    contextCount++;
                }
            }

            msg.append("Stable AST assumption violated.  " + parentChain.inputCount + " children expected got " + contextCount);
            msg.append("\n Parent: " + parentSourceSection);

            for (int i = 0; i < contexts.length; i++) {
                EventContext eventContext = contexts[i];
                if (eventContext == null) {
                    continue;
                }
                msg.append("\nChild[" + i + "] = " + eventContext.getInstrumentedSourceSection());
                Node node = eventContext.getInstrumentedNode();
                String indent = "  ";
                while (node != null) {
                    msg.append("\n");
                    msg.append(indent);
                    if (node == parentContext.getInstrumentedNode()) {
                        msg.append("Parent");
                        break;
                    }
                    if (node.getParent() == null) {
                        msg.append("null parent = ");
                    } else {
                        String fieldName = NodeUtil.findChildField(node.getParent(), node).getName();
                        msg.append(node.getParent().getClass().getSimpleName() + "." + fieldName + " = ");
                    }

                    msg.append(node.getClass().getSimpleName() + "#" + System.identityHashCode(node));
                    indent += "  ";
                    node = node.getParent();
                }
            }

        } catch (Throwable e) {
            // if assertion computation fails we need to fallback to some simplerm essage
            AssertionError error = new AssertionError("Stable AST assumption violated");
            error.addSuppressed(e);
            throw error;
        }
        throw new AssertionError(msg.toString());
    }

    ProbeNode.EventChainNode createEventChainCallback(VirtualFrame frame, EventBinding.Source<?> binding, RootNode rootNode, Set<Class<?>> providedTags, Node instrumentedNode,
                    SourceSection instrumentedNodeSourceSection) {
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
            if (binding.getInputFilter() != null) {
                EventChainNode parent = findParentChain(frame, binding);
                EventProviderWithInputChainNode parentChain = ((EventProviderWithInputChainNode) parent);

                int baseInput;
                if (parentChain == null) {
                    baseInput = 0;
                } else {
                    EventContext parentContext = parentChain.findProbe().getContext();
                    int childIndex = indexOfChild(binding, rootNode, providedTags, parentContext.getInstrumentedNode(), parentContext.getInstrumentedSourceSection(), instrumentedNode);
                    int inputBaseIndex = parentChain.inputBaseIndex;
                    if (childIndex < 0) {
                        // be conservative if child could not be identified
                        baseInput = inputBaseIndex + parentChain.inputCount;
                    } else {
                        // we can reuse frame slots next silbing nodes for child nodes.
                        baseInput = inputBaseIndex + childIndex;
                    }
                }
                int inputCount = countChildren(binding, rootNode, providedTags, instrumentedNode, instrumentedNodeSourceSection);
                next = new EventProviderWithInputChainNode(binding, eventNode, baseInput, inputCount);
            } else {
                next = new EventProviderChainNode(binding, eventNode);
            }
        }
        return next;
    }

    static EventContext[] findChildContexts(EventBinding.Source<?> binding, RootNode rootNode, Set<Class<?>> providedTags, Node instrumentedNode, SourceSection instrumentedNodeSourceSection,
                    int inputCount) {
        InputChildContextLookup visitor = new InputChildContextLookup(binding, rootNode, providedTags, instrumentedNode, instrumentedNodeSourceSection, inputCount);
        NodeUtil.forEachChild(instrumentedNode, visitor);
        return visitor.foundContexts;
    }

    private static int indexOfChild(EventBinding.Source<?> binding, RootNode rootNode, Set<Class<?>> providedTags, Node instrumentedNode, SourceSection instrumentedNodeSourceSection,
                    Node lookupChild) {
        InputChildIndexLookup visitor = new InputChildIndexLookup(binding, rootNode, providedTags, instrumentedNode, instrumentedNodeSourceSection, lookupChild);
        NodeUtil.forEachChild(instrumentedNode, visitor);
        return visitor.found ? visitor.index : -1;
    }

    private static int countChildren(EventBinding.Source<?> binding, RootNode rootNode, Set<Class<?>> providedTags, Node instrumentedNode, SourceSection instrumentedNodeSourceSection) {
        InputChildIndexLookup visitor = new InputChildIndexLookup(binding, rootNode, providedTags, instrumentedNode, instrumentedNodeSourceSection, null);
        NodeUtil.forEachChild(instrumentedNode, visitor);
        return visitor.index;
    }

    @SuppressWarnings("deprecation")
    private EventChainNode findParentChain(VirtualFrame frame, EventBinding<?> binding) {
        Node node = getParent().getParent();
        while (node != null) {
            // TODO we should avoid materializing the source section here
            if (node instanceof com.oracle.truffle.api.instrumentation.InstrumentableFactory.WrapperNode) {
                ProbeNode probe = ((com.oracle.truffle.api.instrumentation.InstrumentableFactory.WrapperNode) node).getProbeNode();
                EventChainNode c = probe.lazyUpdate(frame);
                if (c != null) {
                    c = c.find(binding);
                }
                if (c != null) {
                    return c;
                }
            } else if (node instanceof RootNode) {
                break;
            }
            node = node.getParent();
        }
        if (node == null) {
            throw new IllegalStateException("The AST node is not yet adopted. ");
        }
        return null;

    }

    private ExecutionEventNode createEventNode(EventBinding.Source<?> binding, Object element) {
        ExecutionEventNode eventNode;
        try {
            eventNode = ((ExecutionEventNodeFactory) element).create(context);
            if (eventNode != null && eventNode.getParent() != null) {
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
    static void exceptionEventForClientInstrument(EventBinding.Source<?> b, String eventName, Throwable t) {
        assert !b.isLanguageBinding();
        if (t instanceof ThreadDeath) {
            // Terminates guest language execution immediately
            throw (ThreadDeath) t;
        }
        final Object currentVm = InstrumentAccessor.engineAccess().getCurrentVM();
        if (b.getInstrumenter() instanceof EngineInstrumenter || (currentVm != null && InstrumentAccessor.engineAccess().isInstrumentExceptionsAreThrown(currentVm))) {
            throw sthrow(RuntimeException.class, t);
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

    private static boolean checkInteropType(Object value, EventBinding.Source<?> binding) {
        if (value != null && value != UNWIND_ACTION_REENTER && value != UNWIND_ACTION_IGNORED && !InstrumentAccessor.ACCESSOR.isTruffleObject(value)) {
            Class<?> clazz = value.getClass();
            if (!(clazz == Byte.class ||
                            clazz == Short.class ||
                            clazz == Integer.class ||
                            clazz == Long.class ||
                            clazz == Float.class ||
                            clazz == Double.class ||
                            clazz == Character.class ||
                            clazz == Boolean.class ||
                            clazz == String.class)) {
                CompilerDirectives.transferToInterpreter();
                ClassCastException ccex = new ClassCastException(clazz.getName() + " isn't allowed Truffle interop type!");
                if (binding.isLanguageBinding()) {
                    throw ccex;
                } else {
                    exceptionEventForClientInstrument(binding, "onUnwind", ccex);
                    return false;
                }
            }
        }
        return true;
    }

    private static Object mergePostUnwindReturns(Object r1, Object r2) {
        // Prefer unwind
        if (r1 == null || r2 == null) {
            return null;
        }
        if (r1 == UNWIND_ACTION_IGNORED) {
            return r2;
        }
        if (r2 == UNWIND_ACTION_IGNORED) {
            return r1;
        }
        // Prefer reenter over return
        if (r1 == UNWIND_ACTION_REENTER || r2 == UNWIND_ACTION_REENTER) {
            return UNWIND_ACTION_REENTER;
        }
        return r1; // The first one wins
    }

    @SuppressWarnings({"unchecked", "unused"})
    private static <T extends Throwable> T sthrow(Class<T> type, Throwable t) throws T {
        throw (T) t;
    }

    private static class InputChildContextLookup extends InstrumentableChildVisitor {

        EventContext[] foundContexts;
        int index;

        InputChildContextLookup(EventBinding.Source<?> binding, RootNode rootNode, Set<Class<?>> providedTags, Node instrumentedNode, SourceSection instrumentedNodeSourceSection, int childrenCount) {
            super(binding, rootNode, providedTags, instrumentedNode, instrumentedNodeSourceSection);
            this.foundContexts = new EventContext[childrenCount];
        }

        @SuppressWarnings("deprecation")
        @Override
        protected boolean visitChild(Node child) {
            Node parent = child.getParent();
            if (parent instanceof com.oracle.truffle.api.instrumentation.InstrumentableFactory.WrapperNode) {
                ProbeNode probe = ((com.oracle.truffle.api.instrumentation.InstrumentableFactory.WrapperNode) parent).getProbeNode();
                if (index < foundContexts.length) {
                    foundContexts[index] = probe.context;
                } else {
                    assert false;
                    foundContexts = null;
                    return false;
                }
            } else {
                // not yet materialized
                assert false;
                foundContexts = null;
                return false;
            }
            index++;
            return true;
        }
    }

    private static class InputChildIndexLookup extends InstrumentableChildVisitor {

        private final Node lookupNode;

        boolean found = false;
        int index;

        InputChildIndexLookup(EventBinding.Source<?> binding, RootNode rootNode, Set<Class<?>> providedTags, Node instrumentedNode, SourceSection instrumentedNodeSourceSection, Node lookupNode) {
            super(binding, rootNode, providedTags, instrumentedNode, instrumentedNodeSourceSection);
            this.lookupNode = lookupNode;
        }

        @Override
        protected boolean visitChild(Node child) {
            if (found) {
                return false;
            }
            if (lookupNode == child) {
                found = true;
                return false;
            }
            index++;
            return true;
        }
    }

    private abstract static class InstrumentableChildVisitor implements NodeVisitor {

        private final EventBinding.Source<?> binding;
        private final Set<Class<?>> providedTags;
        private final RootNode rootNode;
        private final Node instrumentedNode;
        private final SourceSection instrumentedNodeSourceSection;

        InstrumentableChildVisitor(EventBinding.Source<?> binding, RootNode rootNode, Set<Class<?>> providedTags, Node instrumentedNode, SourceSection instrumentedNodeSourceSection) {
            this.binding = binding;
            this.providedTags = providedTags;
            this.rootNode = rootNode;
            this.instrumentedNode = instrumentedNode;
            this.instrumentedNodeSourceSection = instrumentedNodeSourceSection;
        }

        public final boolean visit(Node node) {
            SourceSection sourceSection = node.getSourceSection();
            if (InstrumentationHandler.isInstrumentableNode(node, sourceSection)) {
                if (binding.isChildInstrumentedFull(providedTags, rootNode, instrumentedNode, instrumentedNodeSourceSection, node, sourceSection)) {
                    if (!visitChild(node)) {
                        return false;
                    }
                }
                return true;
            }
            NodeUtil.forEachChild(node, this);
            return true;
        }

        protected abstract boolean visitChild(Node child);
    }

    abstract static class EventChainNode extends Node {

        @Child private ProbeNode.EventChainNode next;
        private final EventBinding.Source<?> binding;
        @CompilationFinal private byte seen = 0;

        EventChainNode(EventBinding.Source<?> binding) {
            this.binding = binding;
        }

        final ProbeNode findProbe() {
            Node parent = this;
            while (parent != null && !(parent instanceof ProbeNode)) {
                parent = parent.getParent();
            }
            return (ProbeNode) parent;
        }

        final void setNext(ProbeNode.EventChainNode next) {
            this.next = insert(next);
        }

        EventBinding.Source<?> getBinding() {
            return binding;
        }

        ProbeNode.EventChainNode getNext() {
            return next;
        }

        @Override
        public final NodeCost getCost() {
            return NodeCost.NONE;
        }

        private boolean isSeenException() {
            return (seen & 0b1) != 0;
        }

        private void setSeenException() {
            CompilerAsserts.neverPartOfCompilation();
            seen = (byte) (seen | 0b1);
        }

        private boolean isSeenUnwind() {
            return (seen & 0b10) != 0;
        }

        private void setSeenUnwind() {
            CompilerAsserts.neverPartOfCompilation();
            seen = (byte) (seen | 0b10);
        }

        private boolean isSeenUnwindOnInputValue() {
            return (seen & 0b100) != 0;
        }

        private void setSeenUnwindOnInputValue() {
            CompilerAsserts.neverPartOfCompilation();
            seen = (byte) (seen | 0b100);
        }

        private boolean isSeenHasNext() {
            return (seen & 0b1000) != 0;
        }

        private void setSeenHasNext() {
            CompilerAsserts.neverPartOfCompilation();
            seen = (byte) (seen | 0b1000);
        }

        final void onDispose(EventContext context, VirtualFrame frame) {
            try {
                innerOnDispose(context, frame);
            } catch (Throwable t) {
                if (!isSeenException()) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    setSeenException();
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
            UnwindException unwind = null;
            try {
                innerOnEnter(context, frame);
            } catch (UnwindException ex) {
                if (!isSeenUnwind()) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    setSeenUnwind();
                }
                ex.thrownFromBinding(binding);
                unwind = ex;
            } catch (Throwable t) {
                if (!isSeenException()) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    setSeenException();
                }
                if (binding.isLanguageBinding()) {
                    throw t;
                } else {
                    CompilerDirectives.transferToInterpreter();
                    exceptionEventForClientInstrument(binding, "onEnter", t);
                }
            }
            if (next != null) {
                try {
                    next.onEnter(context, frame);
                } catch (UnwindException ex) {
                    if (!isSeenUnwind()) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        setSeenUnwind();
                    }
                    if (unwind != null && unwind != ex) {
                        if (!isSeenHasNext()) {
                            CompilerDirectives.transferToInterpreterAndInvalidate();
                            setSeenHasNext();
                        }
                        unwind.addNext(ex);
                    } else {
                        unwind = ex;
                    }
                }
            }
            if (unwind != null) {
                throw unwind;
            }
        }

        protected abstract void innerOnEnter(EventContext context, VirtualFrame frame);

        final void onInputValue(EventContext context, VirtualFrame frame, EventBinding<?> inputBinding, EventContext inputContext, int inputIndex, Object inputValue) {
            UnwindException unwind = null;
            if (next != null) {
                try {
                    next.onInputValue(context, frame, inputBinding, inputContext, inputIndex, inputValue);
                } catch (UnwindException ex) {
                    if (!isSeenUnwindOnInputValue()) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        setSeenUnwindOnInputValue();
                    }
                    unwind = ex;
                }
            }
            try {
                if (binding == inputBinding) {
                    innerOnInputValue(context, frame, binding, inputContext, inputIndex, inputValue);
                }
            } catch (UnwindException ex) {
                if (!isSeenUnwindOnInputValue()) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    setSeenUnwindOnInputValue();
                }
                ex.thrownFromBinding(binding);
                unwind = mergeUnwind(unwind, ex);
            } catch (Throwable t) {
                if (!isSeenException()) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    setSeenException();
                }
                if (binding.isLanguageBinding()) {
                    throw t;
                } else {
                    CompilerDirectives.transferToInterpreter();
                    exceptionEventForClientInstrument(binding, "onInputValue", t);
                }
            }
            if (unwind != null) {
                throw unwind;
            }
        }

        private UnwindException mergeUnwind(UnwindException unwind, UnwindException other) {
            if (unwind != null && unwind != other) {
                if (!isSeenHasNext()) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    setSeenHasNext();
                }
                unwind.addNext(other);
                return unwind;
            } else {
                return other;
            }
        }

        protected abstract void innerOnInputValue(EventContext context, VirtualFrame frame, EventBinding<?> targetBinding, EventContext inputContext, int inputIndex, Object inputValue);

        final void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
            UnwindException unwind = null;
            if (next != null) {
                try {
                    next.onReturnValue(context, frame, result);
                } catch (UnwindException ex) {
                    if (!isSeenUnwind()) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        setSeenUnwind();
                    }
                    unwind = ex;
                }
            }
            try {
                innerOnReturnValue(context, frame, result);
            } catch (UnwindException ex) {
                if (!isSeenUnwind()) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    setSeenUnwind();
                }
                ex.thrownFromBinding(binding);
                unwind = mergeUnwind(unwind, ex);
            } catch (Throwable t) {
                if (!isSeenException()) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    setSeenException();
                }
                if (binding.isLanguageBinding()) {
                    throw t;
                } else {
                    CompilerDirectives.transferToInterpreter();
                    exceptionEventForClientInstrument(binding, "onReturnValue", t);
                }
            }
            if (unwind != null) {
                throw unwind;
            }
        }

        protected abstract void innerOnReturnValue(EventContext context, VirtualFrame frame, Object result);

        final void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
            UnwindException unwind = null;
            if (exception instanceof UnwindException) {
                if (!isSeenUnwind()) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    setSeenUnwind();
                }
                unwind = (UnwindException) exception;
                assert unwind.getBinding() != null;
            }
            if (next != null) {
                try {
                    next.onReturnExceptional(context, frame, exception);
                } catch (UnwindException ex) {
                    if (!isSeenUnwind()) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        setSeenUnwind();
                    }
                    unwind = mergeUnwind(unwind, ex);
                }
            }
            try {
                innerOnReturnExceptional(context, frame, exception);
            } catch (UnwindException ex) {
                if (!isSeenUnwind()) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    setSeenUnwind();
                }
                ex.thrownFromBinding(binding);
                unwind = mergeUnwind(unwind, ex);
            } catch (Throwable t) {
                if (!isSeenException()) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    setSeenException();
                }
                if (binding.isLanguageBinding()) {
                    exception.addSuppressed(t);
                } else {
                    CompilerDirectives.transferToInterpreter();
                    exceptionEventForClientInstrument(binding, "onReturnExceptional", t);
                }
            }
            if (unwind != null) {
                throw unwind;
            }
        }

        protected abstract void innerOnReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception);

        private boolean containsBinding(UnwindException unwind) {
            if (unwind.getBinding() == binding) {
                return true;
            } else {
                UnwindException nextUnwind = unwind.getNext();
                if (nextUnwind != null) {
                    if (!isSeenHasNext()) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        setSeenHasNext();
                    }
                    return containsBindingBoundary(nextUnwind);
                } else {
                    return false;
                }
            }
        }

        @TruffleBoundary
        private boolean containsBindingBoundary(UnwindException unwind) {
            return containsBinding(unwind);
        }

        private Object getInfo(UnwindException unwind) {
            if (unwind.getBinding() == binding) {
                return unwind.getInfo();
            } else {
                UnwindException nextUnwind = unwind.getNext();
                if (nextUnwind != null) {
                    if (!isSeenHasNext()) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        setSeenHasNext();
                    }
                    return getInfoBoundary(nextUnwind);
                } else {
                    return false;
                }
            }
        }

        @TruffleBoundary
        private Object getInfoBoundary(UnwindException unwind) {
            return getInfo(unwind);
        }

        private void reset(UnwindException unwind) {
            if (unwind.getBinding() == binding) {
                unwind.resetThread();
            } else {
                UnwindException nextUnwind = unwind.getNext();
                if (nextUnwind != null) {
                    if (!isSeenHasNext()) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        setSeenHasNext();
                    }
                    unwind.resetBoundary(binding);
                }
            }
        }

        final Object onUnwind(EventContext context, VirtualFrame frame, UnwindException unwind) {
            Object ret = null;
            if (containsBinding(unwind)) {
                try {
                    ret = innerOnUnwind(context, frame, getInfo(unwind));
                } catch (Throwable t) {
                    if (!isSeenException()) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        setSeenException();
                    }
                    if (binding.isLanguageBinding()) {
                        throw t;
                    } else {
                        CompilerDirectives.transferToInterpreter();
                        exceptionEventForClientInstrument(binding, "onUnwind", t);
                    }
                }
                if (ret != null) {
                    assert checkInteropType(ret, binding);
                    reset(unwind);
                }
            } else {
                ret = UNWIND_ACTION_IGNORED;
            }
            if (next != null) {
                Object nextRet = next.onUnwind(context, frame, unwind);
                ret = mergePostUnwindReturns(ret, nextRet);
            }
            return ret;
        }

        protected abstract Object innerOnUnwind(EventContext context, VirtualFrame frame, Object info);

        EventChainNode find(EventBinding<?> b) {
            if (binding == b) {
                assert next == null || next.find(b) == null : "only one chain entry per binding allowed";
                return this;
            }
            return next != null ? next.find(b) : null;
        }
    }

    private static class EventFilterChainNode extends ProbeNode.EventChainNode {

        private final ExecutionEventListener listener;

        EventFilterChainNode(EventBinding.Source<?> binding, ExecutionEventListener listener) {
            super(binding);
            this.listener = listener;
        }

        @Override
        protected void innerOnInputValue(EventContext context, VirtualFrame frame, EventBinding<?> binding, EventContext inputContext, int inputIndex, Object inputValue) {
            listener.onInputValue(context, frame, inputContext, inputIndex, inputValue);
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
        protected Object innerOnUnwind(EventContext context, VirtualFrame frame, Object info) {
            return listener.onUnwind(context, frame, info);
        }

        @Override
        protected void innerOnDispose(EventContext context, VirtualFrame frame) {
        }

    }

    static class EventProviderWithInputChainNode extends EventProviderChainNode {

        static final Object[] EMPTY_ARRAY = new Object[0];
        @CompilationFinal(dimensions = 1) private volatile FrameSlot[] inputSlots;
        private volatile FrameDescriptor sourceFrameDescriptor;
        final int inputBaseIndex;
        final int inputCount;
        @CompilationFinal(dimensions = 1) volatile EventContext[] inputContexts;

        EventProviderWithInputChainNode(EventBinding.Source<?> binding, ExecutionEventNode eventNode, int inputBaseIndex, int inputCount) {
            super(binding, eventNode);
            this.inputBaseIndex = inputBaseIndex;
            this.inputCount = inputCount;
        }

        final int getInputCount() {
            return inputCount;
        }

        final EventContext getInputContext(int index) {
            EventContext[] contexts = inputContexts;
            if (contexts == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                ProbeNode probe = findProbe();
                EventContext thisContext = probe.context;
                RootNode rootNode = getRootNode();
                Set<Class<?>> providedTags = probe.handler.getProvidedTags(rootNode);
                inputContexts = contexts = findChildContexts(getBinding(), rootNode, providedTags, thisContext.getInstrumentedNode(), thisContext.getInstrumentedSourceSection(), inputCount);
            }
            if (contexts == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException("Input event context not yet available. They are only available during event notifications.");
            }
            return contexts[index];
        }

        final void saveInputValue(VirtualFrame frame, int inputIndex, Object value) {
            verifyIndex(inputIndex);
            if (inputSlots == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                initializeSlots(frame);
            }
            assert sourceFrameDescriptor == frame.getFrameDescriptor() : "Unstable frame descriptor used by the language.";
            frame.setObject(inputSlots[inputIndex], value);
        }

        private void initializeSlots(VirtualFrame frame) {
            Lock lock = getLock();
            lock.lock();
            try {
                if (this.inputSlots == null) {
                    if (InstrumentationHandler.TRACE) {
                        InstrumentationHandler.trace("SLOTS: Adding %s save slots for binding %s%n", inputCount, getBinding().getElement());
                    }
                    FrameDescriptor frameDescriptor = frame.getFrameDescriptor();
                    FrameSlot[] slots = new FrameSlot[inputCount];
                    for (int i = 0; i < inputCount; i++) {
                        int slotIndex = inputBaseIndex + i;
                        slots[i] = frameDescriptor.findOrAddFrameSlot(new SavedInputValueID(getBinding(), slotIndex));
                    }
                    this.sourceFrameDescriptor = frameDescriptor;
                    this.inputSlots = slots;
                }
            } finally {
                lock.unlock();
            }
        }

        private void verifyIndex(int inputIndex) {
            if (inputIndex >= inputCount || inputIndex < 0) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalArgumentException("Invalid input index.");
            }
        }

        @Override
        protected void innerOnDispose(EventContext context, VirtualFrame frame) {
            Lock lock = getLock();
            lock.lock();
            try {
                if (inputSlots != null) {
                    FrameSlot[] slots = inputSlots;
                    inputSlots = null;

                    RootNode rootNode = context.getInstrumentedNode().getRootNode();
                    if (rootNode == null) {
                        return;
                    }
                    FrameDescriptor descriptor = rootNode.getFrameDescriptor();
                    assert descriptor != null;

                    for (FrameSlot slot : slots) {
                        FrameSlot resolvedSlot = descriptor.findFrameSlot(slot.getIdentifier());
                        if (resolvedSlot != null) {
                            descriptor.removeFrameSlot(slot.getIdentifier());
                        } else {
                            // slot might be shared and already removed by another event provider
                            // node.
                        }
                    }
                }
            } finally {
                lock.unlock();
            }
            super.innerOnDispose(context, frame);
        }

        @Override
        protected void innerOnReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
            super.innerOnReturnExceptional(context, frame, exception);
            clearSlots(frame);
        }

        @Override
        protected void innerOnReturnValue(EventContext context, VirtualFrame frame, Object result) {
            super.innerOnReturnValue(context, frame, result);
            clearSlots(frame);
        }

        @ExplodeLoop
        private void clearSlots(VirtualFrame frame) {
            FrameSlot[] slots = inputSlots;
            if (slots != null) {
                if (frame.getFrameDescriptor() == sourceFrameDescriptor) {
                    for (int i = 0; i < slots.length; i++) {
                        frame.setObject(slots[i], null);
                    }
                }
            }
        }

        protected final Object getSavedInputValue(VirtualFrame frame, int inputIndex) {
            try {
                verifyIndex(inputIndex);
                if (inputSlots == null) {
                    // never saved any value
                    return null;
                }
                return frame.getObject(inputSlots[inputIndex]);
            } catch (FrameSlotTypeException e) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError(e);
            }
        }

        @ExplodeLoop
        protected final Object[] getSavedInputValues(VirtualFrame frame) {
            FrameSlot[] slots = inputSlots;
            if (slots == null) {
                return EMPTY_ARRAY;
            }
            Object[] inputValues;
            if (frame.getFrameDescriptor() == sourceFrameDescriptor) {
                inputValues = new Object[slots.length];
                for (int i = 0; i < slots.length; i++) {
                    try {
                        inputValues[i] = frame.getObject(slots[i]);
                    } catch (FrameSlotTypeException e) {
                        CompilerDirectives.transferToInterpreter();
                        throw new AssertionError(e);
                    }
                }
            } else {
                inputValues = new Object[inputSlots.length];
            }
            return inputValues;
        }

        static final class SavedInputValueID {

            private final EventBinding<?> binding;
            private final int index;

            SavedInputValueID(EventBinding<?> binding, int index) {
                this.binding = binding;
                this.index = index;
            }

            @Override
            public int hashCode() {
                return (31 * binding.hashCode()) * 31 + index;
            }

            @Override
            public String toString() {
                return "SavedInputValue(binding=" + binding.hashCode() + ":" + index + ")";
            }

            @Override
            public boolean equals(Object obj) {
                if (this == obj) {
                    return true;
                } else if (obj == null || getClass() != obj.getClass()) {
                    return false;
                }
                SavedInputValueID other = (SavedInputValueID) obj;
                return binding == other.binding && index == other.index;
            }
        }

    }

    static class EventProviderChainNode extends ProbeNode.EventChainNode {

        @Child private ExecutionEventNode eventNode;

        EventProviderChainNode(EventBinding.Source<?> binding, ExecutionEventNode eventNode) {
            super(binding);
            this.eventNode = eventNode;
        }

        @Override
        protected final void innerOnInputValue(EventContext context, VirtualFrame frame, EventBinding<?> binding, EventContext inputContext, int inputIndex, Object inputValue) {
            eventNode.onInputValue(frame, inputContext, inputIndex, inputValue);
        }

        @Override
        protected final void innerOnEnter(EventContext context, VirtualFrame frame) {
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
        protected Object innerOnUnwind(EventContext context, VirtualFrame frame, Object info) {
            return eventNode.onUnwind(frame, info);
        }

        @Override
        protected void innerOnDispose(EventContext context, VirtualFrame frame) {
            eventNode.onDispose(frame);
        }

    }

    private static class InputValueChainNode extends ProbeNode.EventChainNode {

        private final EventBinding<?> targetBinding;
        private final ProbeNode parentProbe;
        private final int inputIndex;
        private final EventContext inputContext;

        InputValueChainNode(EventBinding.Source<?> binding, ProbeNode parentProbe, EventContext inputContext, int inputIndex) {
            super(binding);
            this.targetBinding = binding;
            this.parentProbe = parentProbe;
            this.inputContext = inputContext;
            this.inputIndex = inputIndex;
        }

        @Override
        EventChainNode find(EventBinding<?> b) {
            EventChainNode next = getNext();
            if (next == null) {
                return null;
            } else {
                return next.find(b);
            }
        }

        @Override
        protected Object innerOnUnwind(EventContext context, VirtualFrame frame, Object info) {
            return UNWIND_ACTION_IGNORED;
        }

        @Override
        @SuppressWarnings("hiding")
        protected void innerOnInputValue(EventContext context, VirtualFrame frame, EventBinding<?> binding, EventContext inputContext, int inputIndex, Object inputValue) {
        }

        @Override
        protected void innerOnEnter(EventContext context, VirtualFrame frame) {
        }

        @Override
        protected void innerOnDispose(EventContext context, VirtualFrame frame) {
        }

        @Override
        protected void innerOnReturnValue(EventContext context, VirtualFrame frame, Object result) {
            parentProbe.onInputValue(frame, targetBinding, inputContext, inputIndex, result);
        }

        @Override
        protected void innerOnReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
        }
    }
}
