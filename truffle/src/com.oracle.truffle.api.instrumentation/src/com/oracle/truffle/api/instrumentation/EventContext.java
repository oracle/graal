/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.InstrumentableNode.WrapperNode;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

/**
 * Represents the context of an execution event.
 *
 * Instances of {@link EventContext} should be neither stored, cached nor hashed. One exception is
 * when they are stored in {@link ExecutionEventNode} implementations. The equality and hashing
 * behavior is undefined.
 *
 * @see ExecutionEventNodeFactory
 * @see ExecutionEventListener
 * @since 0.12
 */
public final class EventContext {

    private final ProbeNode probeNode;
    private final SourceSection sourceSection;
    @CompilationFinal private volatile Object nodeObject;

    EventContext(ProbeNode probeNode, SourceSection sourceSection) {
        this.sourceSection = sourceSection;
        this.probeNode = probeNode;
    }

    @SuppressWarnings("unchecked")
    boolean validEventContextOnWrapperInsert() {
        Node node = getInstrumentedNode();
        if (node instanceof RootNode) {
            throw new IllegalStateException("Instrumentable node must not be a root node.");
        }
        boolean foundStandardTag = false;
        for (Class<?> clazz : StandardTags.ALL_TAGS) {
            if (hasTag((Class<? extends Tag>) clazz)) {
                assert languageDeclaresTag(probeNode.getRootNode(), clazz);
                foundStandardTag = true;
            }
        }
        if (foundStandardTag) {
            RootNode root = probeNode.getRootNode();
            if (root != null && root.getSourceSection() != null) {
                assert sourceSection != null : "All nodes tagged with a standard tag and with a root node that has a source section must also have a source section.";
            }
        }
        return true;
    }

    boolean validEventContextOnLazyUpdate() {
        Node node = getInstrumentedNode();
        /*
         * The node object can only be accessed at runtime when the context is entered.
         */
        Object object = ((InstrumentableNode) node).getNodeObject();
        if (object != null) {
            assert isValidNodeObject(object);
        }
        return true;
    }

    private static boolean languageDeclaresTag(RootNode root, Class<?> tag) {
        Object polyglotEngine = InstrumentAccessor.nodesAccess().getPolyglotEngine(root);
        if (polyglotEngine == null) {
            return true;
        }
        InstrumentationHandler handler = (InstrumentationHandler) InstrumentAccessor.engineAccess().getInstrumentationHandler(polyglotEngine);
        Set<Class<?>> providedTags = handler.getProvidedTags(root);
        if (!providedTags.contains(tag)) {
            TruffleLanguage<?> language = InstrumentAccessor.nodesAccess().getLanguage(root);
            throw new AssertionError("An instrumentable node returned true for a tag that was not provided by the language '" + root.getLanguageInfo().getId() + "'.\n" +
                            "Add @ProvidedTags with tag  " + tag + " to " + language.getClass().getName() + ".");
        }
        return true;
    }

    ProbeNode getProbeNode() {
        return probeNode;
    }

    /**
     * Returns <code>true</code> if the underlying instrumented AST is tagged with a particular tag.
     * The return value of {@link #hasTag(Class)} always returns the same value for a particular tag
     * and {@link EventContext}. The method may be used on compiled code paths.
     *
     * @param tag the tag to check to check, must not be <code>null</code>.
     * @since 0.33
     */
    public boolean hasTag(Class<? extends Tag> tag) {
        if (tag == null) {
            CompilerDirectives.transferToInterpreter();
            throw new NullPointerException();
        }
        Node node = getInstrumentedNode();
        if (node instanceof InstrumentableNode) {
            boolean has = ((InstrumentableNode) node).hasTag(tag);
            assert !has || languageDeclaresTag(probeNode.getRootNode(), tag);
            return has;
        } else {
            return false;
        }
    }

    /**
     * Returns a language provided object that represents the instrumented node properties. The
     * returned is always a valid interop object. The returned object is never <code>null</code> and
     * always returns <code>true</code> for the HAS_KEYS message. Multiple calls to
     * {@link #getNodeObject()} return the same node object instance.
     *
     * @see InstrumentableNode#getNodeObject()
     * @since 0.33
     */
    public Object getNodeObject() {
        Object object = this.nodeObject;
        if (object == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            Node node = getInstrumentedNode();
            if (node instanceof InstrumentableNode) {
                object = ((InstrumentableNode) node).getNodeObject();
            } else {
                return null;
            }
            if (object == null) {
                object = InstrumentAccessor.interopAccess().createDefaultNodeObject(node);
            } else {
                assert isValidNodeObject(object);
            }
            this.nodeObject = object;
        }
        return object;
    }

    /**
     * Returns the {@link SourceSection} that is being instrumented. The returned source section is
     * final for each {@link EventContext} instance. The returned source section may be null if the
     * node does not provide sources section.
     *
     * <p>
     * <b>Performance note:</b> this is method may be invoked in compiled code and is guaranteed to
     * always return a compilation constant .
     * </p>
     *
     * @since 0.12
     */
    public SourceSection getInstrumentedSourceSection() {
        return sourceSection;
    }

    /**
     * Accessor to the instrumented node at which the event occurred. The returned AST must not be
     * mutated by the user.
     * <p>
     * <b>Performance note:</b> this is method may be invoked in compiled code and is guaranteed to
     * always return a compilation constant .
     * </p>
     *
     * @since 0.12
     */
    @SuppressWarnings("deprecation")
    public Node getInstrumentedNode() {
        WrapperNode wrapper = probeNode.findWrapper();
        return wrapper != null ? wrapper.getDelegateNode() : null;
    }

    /**
     * Test if language context of the source of the event is initialized.
     *
     * @since 0.26
     */
    public boolean isLanguageContextInitialized() {
        CompilerAsserts.neverPartOfCompilation();
        Node node = getInstrumentedNode();
        if (node == null) {
            return true;
        }
        RootNode root = node.getRootNode();
        if (root == null) {
            return true;
        }
        LanguageInfo languageInfo = root.getLanguageInfo();
        Env env = InstrumentAccessor.engineAccess().getEnvForInstrument(languageInfo);
        return InstrumentAccessor.langAccess().isContextInitialized(env);
    }

    /**
     * Returns the execution event node that was inserted at this location given an event binding.
     * This is useful to disambiguate multiple bindings from each other when installed at the same
     * source location.
     *
     * @param binding the binding to lookup
     * @since 0.17
     */
    @SuppressWarnings("cast")
    public ExecutionEventNode lookupExecutionEventNode(EventBinding<? extends ExecutionEventNodeFactory> binding) {
        if (!(binding.getElement() instanceof ExecutionEventNodeFactory)) {
            // security check for unsafe generics casts
            throw new IllegalArgumentException(String.format("Binding is not a subtype of %s.", ExecutionEventNodeFactory.class.getSimpleName()));
        }
        return probeNode.lookupExecutionEventNode(binding);
    }

    /**
     * Returns all execution event nodes in the insertion order at this location, whose event
     * bindings are contained in the given collection. This is useful to be able to sort out
     * multiple bindings when installed at the same source location.
     *
     * @param bindings a collection of bindings to find the event nodes for at this context location
     * @since 19.0
     */
    public Iterator<ExecutionEventNode> lookupExecutionEventNodes(Collection<EventBinding<? extends ExecutionEventNodeFactory>> bindings) {
        return probeNode.lookupExecutionEventNodes(bindings);
    }

    /**
     * Create an unwind throwable, that when thrown, abruptly breaks execution of a node and unwinds
     * it off the execution stack. This is a a shortcut for
     * {@link #createUnwind(Object, EventBinding)} with the current binding, only the event listener
     * instance that threw the unwind throwable gets called <code>onUnwind</code>.
     *
     * @param info an info that is passed into
     *            {@link ExecutionEventListener#onUnwind(EventContext, VirtualFrame, Object)} or
     *            {@link ExecutionEventNode#onUnwind(VirtualFrame, Object)}. It can be used for
     *            arbitrary client data that help to control the unwind process.
     * @see #createUnwind(Object, EventBinding)
     * @since 0.31
     */
    public ThreadDeath createUnwind(Object info) {
        return createUnwind(info, null);
    }

    /**
     * Create an unwind throwable, that when thrown, abruptly breaks execution of a node and unwinds
     * it off the execution stack. It's to be thrown in <code>onEnter</code>,
     * <code>onReturnValue</code> or <code>onReturnExceptional</code> methods of
     * {@link ExecutionEventListener} or {@link ExecutionEventNode}, to initiate the unwind process.
     * It acts in connection with
     * {@link ExecutionEventListener#onUnwind(EventContext, VirtualFrame, Object)} or
     * {@link ExecutionEventNode#onUnwind(VirtualFrame, Object)}. Only the event listener instance
     * that is associated with the provided <code>unwindBinding</code> gets called
     * <code>onUnwind</code>, use {@link #createUnwind(java.lang.Object)} to have the current event
     * listener called <code>onUnwind</code>. Other bindings that happen to instrument the unwound
     * nodes get called <code>onReturnExceptional</code>.
     * <p>
     * The returned throwable can be kept and thrown again later to repeat the unwind process. A
     * repeating unwind process is possible without deoptimization. A single throwable instance
     * cannot be used on multiple threads concurrently. It can be thrown on a different thread only
     * after the unwind finishes on the last thread.
     * <p>
     * Usage example of forced return: {@link UnwindInstrumentationReturnSnippets#onCreate}
     * <p>
     * Usage example of reenter: {@link UnwindInstrumentationReenterSnippets#onCreate}
     *
     * @param info an info that is passed into
     *            {@link ExecutionEventListener#onUnwind(EventContext, VirtualFrame, Object)} or
     *            {@link ExecutionEventNode#onUnwind(VirtualFrame, Object)}. It can be used for
     *            arbitrary client data that help to control the unwind process.
     * @param unwindBinding the binding whose listener's <code>onUnwind</code> is to be called, or
     *            <code>null</code> to call the current listener that throws the returned throwable.
     * @since 0.31
     */
    @SuppressWarnings("static-method")
    public ThreadDeath createUnwind(Object info, EventBinding<?> unwindBinding) {
        CompilerAsserts.neverPartOfCompilation();
        return new UnwindException(info, unwindBinding);
    }

    /**
     * Creates a runtime exception that when thrown is observable to the guest language application.
     * Be aware that errors propagated to the guest application may significantly alter the behavior
     * of the guest application influencing other instruments which may limit them ability of them
     * to be composed. If not wrapped using this method any exception caused by an execution event
     * instrumentation is printed to the {@link TruffleInstrument.Env#out() error stream}.
     * <p>
     * Propagating runtime errors is supported in the following events:
     * <ul>
     * <li>{@link ExecutionEventNode#onEnter(VirtualFrame) onEnter}
     * <li>{@link ExecutionEventNode#onInputValue(VirtualFrame, EventContext, int, Object)
     * onInputValue}
     * <li>{@link ExecutionEventNode#onReturnExceptional(VirtualFrame, Throwable)
     * onReturnExceptional}
     * <li>{@link ExecutionEventNode#onReturnValue(VirtualFrame, Object) onReturnValue}
     * <li>{@link ExecutionEventNode#onUnwind(VirtualFrame, Object) onUnwind}
     * <li>{@link ExecutionEventNode#onDispose(VirtualFrame) onDispose}
     * </ul>
     * Errors may not be propagated in {@link ExecutionEventNodeFactory#create(EventContext)} as
     * this may lead to unstable ASTs.
     * <p>
     * If an error is propagated all other installed execution event listeners will continue to be
     * notified. If multiple listeners propagate errors then the first error will be propagated and
     * later errors will be attached to the first as {@link Exception#addSuppressed(Throwable)
     * suppressed} exception. The notification order relates to the order the bindings were
     * installed.
     * <p>
     * Example usage: {@link PropagateErrorSnippets#onCreate}
     *
     * @param e the exception to propagate.
     * @since 20.0
     */
    public RuntimeException createError(RuntimeException e) {
        return new InstrumentException(this, e);
    }

    /** @since 0.12 */
    @Override
    public String toString() {
        return "EventContext[source=" + getInstrumentedSourceSection() + "]";
    }

    private boolean isValidNodeObject(Object obj) {
        CompilerAsserts.neverPartOfCompilation();
        InteropLibrary interop = InteropLibrary.getFactory().getUncached(obj);

        if (!interop.hasMembers(obj)) {
            throw new AssertionError("Invalid node object: must return true for the hasMembers message.");
        }
        Object members;
        try {
            members = interop.getMembers(obj);
        } catch (UnsupportedMessageException e) {
            throw new AssertionError("Invalid node object: must support the getMembers message.", e);
        }
        InteropLibrary membersInterop = InteropLibrary.getFactory().getUncached(members);
        if (!membersInterop.hasArrayElements(members)) {
            throw new AssertionError("Invalid node object: the returned members object must support hasArrayElements.");
        }
        long size;
        try {
            size = membersInterop.getArraySize(members);
        } catch (UnsupportedMessageException e) {
            throw new AssertionError("Invalid node object: the returned members object must have a size.");
        }
        for (long i = 0; i < size; i++) {
            Object key;
            try {
                key = membersInterop.readArrayElement(members, i);
            } catch (InvalidArrayIndexException | UnsupportedMessageException e) {
                throw new AssertionError("Invalid node object: the returned members object must be readable at number index " + i);
            }
            InteropLibrary keyInterop = InteropLibrary.getFactory().getUncached(key);
            if (!keyInterop.isString(key)) {
                throw new AssertionError("Invalid node object: the returned member must return a string at index " + i + ". But was " + key.getClass().getName() + ".");
            }
            String member;
            try {
                member = keyInterop.asString(key);
            } catch (UnsupportedMessageException e1) {
                throw new AssertionError("Invalid node object: the returned member must return a string  ");
            }
            try {
                interop.readMember(obj, member);
            } catch (UnknownIdentifierException | UnsupportedMessageException e) {
                throw new AssertionError("Invalid node object: the returned member must be readable with identifier " + member);
            }

            if (interop.isMemberWritable(obj, member)) {
                throw new AssertionError("Invalid node object: The member " + member + " is marked as writable but node objects must not be writable.");
            }
        }
        if (interop.hasArrayElements(obj)) {
            throw new AssertionError("Invalid node object: the node object must not return true for hasArrayElements.");
        }

        return isValidTaggedNodeObject(obj);
    }

    private boolean isValidTaggedNodeObject(Object obj) {
        if (hasTag(StandardTags.ReadVariableTag.class)) {
            isValidVarsNodeObject(obj, StandardTags.ReadVariableTag.NAME);
        }
        if (hasTag(StandardTags.WriteVariableTag.class)) {
            isValidVarsNodeObject(obj, StandardTags.WriteVariableTag.NAME);
        }
        return true;
    }

    private static void isValidVarsNodeObject(Object obj, String varNameProperty) {
        InteropLibrary interop = InteropLibrary.getFactory().getUncached(obj);
        if (!interop.isMemberReadable(obj, varNameProperty)) {
            throw new AssertionError("Invalid node object " + obj + ", does not have " + varNameProperty + " member.");
        }
        Object varName;
        try {
            varName = interop.readMember(obj, varNameProperty);
        } catch (UnsupportedMessageException | UnknownIdentifierException ex) {
            throw new AssertionError("Invalid node object " + obj + ", can not read " + varNameProperty + " member.", ex);
        }
        if (varName instanceof String) {
            return;
        }
        interop = InteropLibrary.getFactory().getUncached(varName);
        if (interop.hasArrayElements(varName)) {
            long size;
            try {
                size = interop.getArraySize(varName);
            } catch (UnsupportedMessageException e) {
                throw new AssertionError("Invalid node object: the returned variable name object must have a size when it's an array.");
            }
            for (long i = 0; i < size; i++) {
                Object var;
                try {
                    var = interop.readArrayElement(varName, i);
                } catch (InvalidArrayIndexException | UnsupportedMessageException e) {
                    throw new AssertionError("Invalid node object: the returned variable name object must be readable at number index " + i);
                }
                isValidVarObject(var);
            }
        } else {
            isValidVarObject(varName);
        }
    }

    private static void isValidVarObject(Object var) {
        InteropLibrary interop = InteropLibrary.getFactory().getUncached(var);
        if (!interop.isString(var)) {
            throw new AssertionError("Invalid variable object " + var + ", must be interop String.");
        }
        try {
            interop.asString(var);
        } catch (UnsupportedMessageException ex) {
            throw new AssertionError("Invalid variable object " + var + ", must be interop String.", ex);
        }
        // May have location:
        boolean hasLocation = interop.hasSourceLocation(var);
        try {
            interop.getSourceLocation(var);
            assert hasLocation : String.format("Invalid variable object %s, provides source location that should not have.", var);
        } catch (UnsupportedMessageException ex) {
            assert !hasLocation : String.format("Invalid variable object %s, missing source location.", var);
        }
    }

}

class PropagateErrorSnippets extends TruffleInstrument {

    // Checkstyle: stop
    // @formatter:off
    @Override
    // BEGIN: PropagateErrorSnippets#onCreate
    protected void onCreate(TruffleInstrument.Env env) {
        env.getInstrumenter().attachExecutionEventListener(
            SourceSectionFilter.newBuilder().
                                tagIs(StandardTags.CallTag.class).build(),
            new ExecutionEventListener() {
                public void onEnter(EventContext context, VirtualFrame f) {
                    throw context.createError(
                          new RuntimeException("propagated to the guest"));
                }
                public void onReturnValue(EventContext context,
                                          VirtualFrame f, Object result) {
                }
                public void onReturnExceptional(EventContext context,
                                                VirtualFrame f, Throwable ex) {}
            });
    }
    // END: PropagateErrorSnippets#onCreate
    // @formatter:on
}

class UnwindInstrumentationReturnSnippets extends TruffleInstrument {

    // @formatter:off
    @Override
    // BEGIN: UnwindInstrumentationReturnSnippets#onCreate
    protected void onCreate(TruffleInstrument.Env env) {
        // Register a listener that checks the return value to all call nodes
        // If the return value is not 42, it forces to return 42.
        env.getInstrumenter().attachExecutionEventListener(
            SourceSectionFilter.newBuilder().
                                tagIs(StandardTags.CallTag.class).build(),
            new ExecutionEventListener() {
                public void onEnter(EventContext context, VirtualFrame f) {}
                public void onReturnValue(EventContext context,
                                          VirtualFrame f, Object result) {
                    if (!Objects.equals(result, 42)) {
                        CompilerDirectives.transferToInterpreter();
                        throw context.createUnwind(42);
                    }
                }
                public Object onUnwind(EventContext context,
                                       VirtualFrame f, Object info) {
                    // return 42 on unwind
                    return info;
                }
                public void onReturnExceptional(EventContext context,
                                                VirtualFrame f, Throwable ex) {}
            });
    }
    // END: UnwindInstrumentationReturnSnippets#onCreate
    // @formatter:on
}

class UnwindInstrumentationReenterSnippets extends TruffleInstrument {

    // @formatter:off
    @Override
    // BEGIN: UnwindInstrumentationReenterSnippets#onCreate
    protected void onCreate(TruffleInstrument.Env env) {
        // Two event bindings are created: one for reenter, one for unwind

        // Listener that reenters on unwind, attached to root nodes.
        EventBinding<ExecutionEventListener> functionReenter =
            env.getInstrumenter().attachExecutionEventListener(
                SourceSectionFilter.newBuilder().
                                    tagIs(StandardTags.RootTag.class).build(),
            new ExecutionEventListener() {
                public Object onUnwind(EventContext context,
                                       VirtualFrame f, Object info) {
                    // Reenters on unwind.
                    return ProbeNode.UNWIND_ACTION_REENTER;
                }
                public void onEnter(EventContext context, VirtualFrame f) {}
                public void onReturnValue(EventContext context,
                                          VirtualFrame f, Object result) {}
                public void onReturnExceptional(EventContext context,
                                                VirtualFrame f, Throwable ex) {}
            });

        // Listener that initiates unwind at line 20, attached to statements.
        env.getInstrumenter().attachExecutionEventListener(
            SourceSectionFilter.newBuilder().
                                tagIs(StandardTags.StatementTag.class).build(),
            new ExecutionEventListener() {
                public void onEnter(EventContext context, VirtualFrame f) {
                    SourceSection ss = context.getInstrumentedSourceSection();
                    if (ss.getStartLine() == 20) {
                        CompilerDirectives.transferToInterpreter();
                        // Unwind to nodes instrumented by functionReenter
                        throw context.createUnwind(null, functionReenter);
                    }
                }
                public void onReturnValue(EventContext context,
                                          VirtualFrame f, Object result) {}
                public void onReturnExceptional(EventContext context,
                                                VirtualFrame f, Throwable ex) {}
            });
    }
    // END: UnwindInstrumentationReenterSnippets#onCreate
    // @formatter:on
    // Checkstyle: resume
}
