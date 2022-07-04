/*
 * Copyright (c) 2016, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.debug;

import java.util.Objects;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.debug.DebugValue.HeapValue;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstance.FrameAccess;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.NodeLibrary;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

/**
 * Represents a frame in the guest language stack. A guest language stack frame consists of a
 * {@link #getName() name}, the current {@link #getSourceSection() source location} and
 * {@link #getScope() scopes} containing local variables and arguments. Furthermore it allows to
 * {@link #eval(String) evaluate} guest language expressions in the lexical context of a particular
 * frame.
 * <p>
 * Debug stack frames are only valid as long as {@link SuspendedEvent suspended events} are valid.
 * Suspended events are valid as long while the originating {@link SuspendedCallback} is still
 * executing. All methods of the frame throw {@link IllegalStateException} if they become invalid.
 * <p>
 * Depending on the method, clients may access the stack frame only on the execution thread where
 * the suspended event of the stack frame was created and the notification was received. For some
 * methods, access from other threads may throw {@link IllegalStateException}. Please see the
 * javadoc of the individual method for details.
 *
 * @see SuspendedEvent#getStackFrames()
 * @see SuspendedEvent#getTopStackFrame()
 * @since 0.17
 */
public final class DebugStackFrame {

    final SuspendedEvent event;
    private final FrameInstance currentFrame;
    private final StackTraceElement hostTraceElement;
    private final int depth;    // The frame depth on guest stack. 0 is the top frame
    private final String name;
    private final DebugException nameEx;

    DebugStackFrame(SuspendedEvent session, FrameInstance instance, int depth) {
        this.event = session;
        this.currentFrame = instance;
        this.hostTraceElement = null;
        this.depth = depth;
        String frameName = null;
        DebugException frameNameEx = null;
        try {
            frameName = initName();
        } catch (DebugException ex) {
            frameNameEx = ex;
        }
        this.name = frameName;
        this.nameEx = frameNameEx;
    }

    DebugStackFrame(SuspendedEvent session, StackTraceElement hostElement, int depth) {
        this.event = session;
        this.currentFrame = null;
        this.hostTraceElement = hostElement;
        this.depth = depth;
        this.name = hostElement.getClassName() + '.' + hostElement.getMethodName();
        this.nameEx = null;
    }

    // Initialize the stack frame name while we're on the execution thread
    private String initName() throws DebugException {
        verifyValidState(false);
        Node node;
        if (currentFrame == null) {
            node = getContext().getInstrumentedNode();
        } else {
            node = currentFrame.getCallNode();
            node = InstrumentableNode.findInstrumentableParent(node);
        }
        try {
            if (node != null) {
                Frame frame = findTruffleFrame(FrameAccess.READ_ONLY);
                NodeLibrary nodeLibrary = NodeLibrary.getUncached();
                if (nodeLibrary.hasRootInstance(node, frame)) {
                    Object instance = nodeLibrary.getRootInstance(node, frame);
                    InteropLibrary interop = InteropLibrary.getUncached();
                    if (interop.hasExecutableName(instance)) {
                        return interop.asString(interop.getExecutableName(instance));
                    }
                }
            }
            RootNode root = findCurrentRoot();
            if (root == null) {
                return null;
            }
            return root.getName();
        } catch (ThreadDeath td) {
            throw td;
        } catch (Throwable ex) {
            RootNode root = findCurrentRoot();
            LanguageInfo languageInfo = root != null ? root.getLanguageInfo() : null;
            throw DebugException.create(event.getSession(), ex, languageInfo);
        }
    }

    /**
     * Returns whether this stack frame is a language implementation artifact that should be hidden
     * during normal guest language debugging, for example in stack traces.
     * <p>
     * Language implementations sometimes create method calls internally that do not correspond to
     * anything explicitly written by a programmer, for example when the body of a looping construct
     * is implemented as callable block. Language implementors mark these methods as
     * <em>internal</em>.
     * </p>
     * <p>
     * Clients of the debugging API should assume that displaying <em>internal</em> frames is
     * unlikely to help programmers debug guest language programs and might possibly create
     * confusion. However, clients may choose to display all frames, for example in a special mode
     * to support development of programming language implementations.
     * </p>
     * <p>
     * The decision to mark a method as <em>internal</em> is language-specific, reflects judgments
     * about tool usability, and is subject to change.
     * <p>
     * This method is thread-safe.
     *
     * @since 0.17
     */
    public boolean isInternal() {
        verifyValidState(true);
        if (isHost()) {
            return false;
        }
        RootNode root = findCurrentRoot();
        if (root == null) {
            return true;
        }
        return root.isInternal();
    }

    /**
     * Returns <code>true</code> if this frame is a host frame. Host frames provide
     * {@link #getHostTraceElement() stack trace element}, have no {@link #getScope() scope}, no
     * {@link #getSourceSection() source section} and can not {@link #eval(String) evaluate} code.
     * <p>
     * Host frames are provided only when {@link DebuggerSession#setShowHostStackFrames(boolean)
     * host info} is set to <code>true</code>.
     *
     * @since 20.3
     * @see DebuggerSession#setShowHostStackFrames(boolean)
     */
    public boolean isHost() {
        return hostTraceElement != null;
    }

    /**
     * Provides a host frame. Returns the host stack trace element if and only if this is
     * {@link #isHost() host} frame.
     *
     * @return the host stack trace element, or <code>null</code> when not a host frame.
     * @since 20.3
     * @see #isHost()
     */
    public StackTraceElement getHostTraceElement() {
        return hostTraceElement;
    }

    /**
     * A description of the AST (expected to be a method or procedure name in most languages) that
     * identifies the AST for the benefit of guest language programmers using tools; it might
     * appear, for example in the context of a stack dump or trace and is not expected to be called
     * often. If the language does not provide such a description then <code>null</code> is
     * returned.
     *
     * <p>
     * This method is thread-safe.
     *
     * @since 0.17
     */
    public String getName() throws DebugException {
        verifyValidState(true);
        if (nameEx != null) {
            throw nameEx;
        }
        return name;
    }

    /**
     * Returns the source section of the location where the debugging session was suspended. The
     * source section is <code>null</code> if the source location is not available.
     *
     * <p>
     * This method is thread-safe.
     *
     * @since 0.17
     */
    public SourceSection getSourceSection() {
        verifyValidState(true);
        if (isHost()) {
            return null;
        }
        if (currentFrame == null) {
            SuspendedContext context = getContext();
            return event.getSession().resolveSection(context.getInstrumentedSourceSection());
        } else {
            Node callNode = currentFrame.getCallNode();
            if (callNode != null) {
                return event.getSession().resolveSection(callNode);
            }
            return null;
        }
    }

    /**
     * Returns public information about the language of this frame.
     *
     * @return the language info, or <code>null</code> when no language is associated with this
     *         frame.
     * @since 19.0
     */
    public LanguageInfo getLanguage() {
        verifyValidState(true);
        if (isHost()) {
            return null;
        }
        RootNode root = findCurrentRoot();
        if (root == null) {
            return null;
        }
        return root.getLanguageInfo();
    }

    /**
     * Get the current inner-most scope. The scope remain valid as long as the current stack frame
     * remains valid.
     * <p>
     * Use {@link DebuggerSession#getTopScope(java.lang.String)} to get scopes with global validity.
     * <p>
     * This method is not thread-safe and will throw an {@link IllegalStateException} if called on
     * another thread than it was created with.
     *
     * @return the scope, or <code>null</code> when no language is associated with this frame
     *         location, or when no local scope exists.
     * @throws DebugException when guest language code throws an exception
     * @since 0.26
     */
    public DebugScope getScope() throws DebugException {
        verifyValidState(false);
        if (isHost()) {
            return null;
        }
        SuspendedContext context = getContext();
        RootNode root = findCurrentRoot();
        if (root == null) {
            return null;
        }
        Node node;
        if (currentFrame == null) {
            node = context.getInstrumentedNode();
        } else {
            node = currentFrame.getCallNode();
            if (node == null) {
                return null;
            }
            node = InstrumentableNode.findInstrumentableParent(node);
        }

        DebuggerSession session = event.getSession();
        Frame frame = findTruffleFrame(FrameAccess.READ_WRITE);
        try {
            if (!NodeLibrary.getUncached().hasScope(node, frame)) {
                return null;
            }
            Object scope = NodeLibrary.getUncached().getScope(node, frame, isEnter());
            return new DebugScope(scope, session, event, node, frame, root);
        } catch (ThreadDeath td) {
            throw td;
        } catch (Throwable ex) {
            throw DebugException.create(session, ex, root.getLanguageInfo());
        }
    }

    private boolean isEnter() {
        return depth == 0 && SuspendAnchor.BEFORE.equals(event.getSuspendAnchor());
    }

    /**
     * Returns the current node for this stack frame, or <code>null</code> if the requesting
     * language class does not match the root node guest language.
     *
     * This method is permitted only if the guest language class is available. This is the case if
     * you want to utilize the Debugger API directly from within a guest language, or if you are an
     * instrument bound/dependent on a specific language.
     *
     * @param languageClass the Truffle language class for a given guest language
     * @return the node associated with the frame
     *
     * @since 20.1
     */
    public Node getRawNode(Class<? extends TruffleLanguage<?>> languageClass) {
        Objects.requireNonNull(languageClass);
        RootNode rootNode = findCurrentRoot();
        if (rootNode == null) {
            return null;
        }
        // check if language class of the root node corresponds to the input language
        TruffleLanguage<?> language = Debugger.ACCESSOR.nodeSupport().getLanguage(rootNode);
        return language != null && language.getClass() == languageClass ? getCurrentNode() : null;
    }

    /**
     * Returns the underlying frame for this debug stack frame or <code>null</code> if the
     * requesting language class does not match the root node guest language.
     *
     * This method is permitted only if the guest language class is available. This is the case if
     * you want to utilize the Debugger API directly from within a guest language, or if you are an
     * instrument bound/dependent on a specific language.
     *
     * @param languageClass the Truffle language class for a given guest language
     * @param access the frame access mode
     * @return the frame
     *
     * @since 20.1
     */
    public Frame getRawFrame(Class<? extends TruffleLanguage<?>> languageClass, FrameAccess access) {
        Objects.requireNonNull(languageClass);
        RootNode rootNode = findCurrentRoot();
        if (rootNode == null) {
            return null;
        }
        // check if language class of the root node corresponds to the input language
        TruffleLanguage<?> language = Debugger.ACCESSOR.nodeSupport().getLanguage(rootNode);
        return language != null && language.getClass() == languageClass ? findTruffleFrame(access) : null;
    }

    DebugValue wrapHeapValue(Object result) {
        assert !isHost() : "Can not wrap values in host frames.";
        LanguageInfo language;
        RootNode root = findCurrentRoot();
        if (root != null) {
            language = root.getLanguageInfo();
        } else {
            language = null;
        }
        return new HeapValue(event.getSession(), language, null, result);
    }

    /**
     * Evaluates the given code in the state of the current execution and in the same guest language
     * as the current language is defined in. Returns a heap value that remains valid even if this
     * stack frame becomes invalid.
     *
     * <p>
     * This method is not thread-safe and will throw an {@link IllegalStateException} if called on
     * another thread than it was created with.
     *
     * @param code the code to evaluate
     * @return the return value of the expression
     * @throws DebugException when guest language code throws an exception
     * @throws IllegalStateException if called on another thread than this frame was created with,
     *             or if {@link #getLanguage() language} of this frame is not
     *             {@link LanguageInfo#isInteractive() interactive}.
     * @since 0.17
     */
    public DebugValue eval(String code) throws DebugException {
        verifyValidState(false);
        if (isHost()) {
            throw new IllegalStateException("Can not evaluate code in host frames.");
        }
        Object result = DebuggerSession.evalInContext(event, code, currentFrame);
        return wrapHeapValue(result);
    }

    /**
     * @since 19.0
     */
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof DebugStackFrame) {
            DebugStackFrame other = (DebugStackFrame) obj;
            return event == other.event &&
                            hostTraceElement == other.hostTraceElement &&
                            (currentFrame == other.currentFrame ||
                                            currentFrame != null && other.currentFrame != null && currentFrame.getFrame(FrameAccess.READ_ONLY) == other.currentFrame.getFrame(FrameAccess.READ_ONLY));
        }
        return false;
    }

    /**
     * @since 19.0
     */
    @Override
    public int hashCode() {
        return Objects.hash(event, currentFrame);
    }

    Frame findTruffleFrame(FrameAccess access) {
        assert !isHost() : "No Truffle frame in host stack frame";
        if (currentFrame == null) {
            // The top frame has already been materialized
            // so we can safely return that frame
            return event.getMaterializedFrame();
        } else {
            return currentFrame.getFrame(access);
        }
    }

    int getDepth() {
        return depth;
    }

    private SuspendedContext getContext() {
        SuspendedContext context = event.getContext();
        if (context == null) {
            // there is a race condition here if the event
            // got disposed between the parent verifyValidState and getContext.
            // if the context is null we assume the event got disposed so we re-check
            // the disposed flag. return null should therefore not be reachable.
            verifyValidState(true);
            assert false : "should not be reachable";
        }
        return context;
    }

    RootNode findCurrentRoot() {
        if (isHost()) {
            return null;
        }
        SuspendedContext context = getContext();
        if (currentFrame == null) {
            return context.getInstrumentedNode().getRootNode();
        } else {
            return ((RootCallTarget) currentFrame.getCallTarget()).getRootNode();
        }
    }

    RootCallTarget getCallTarget() {
        if (isHost()) {
            return null;
        }
        SuspendedContext context = getContext();
        if (currentFrame == null) {
            return context.getInstrumentedNode().getRootNode().getCallTarget();
        } else {
            return (RootCallTarget) currentFrame.getCallTarget();
        }
    }

    Node getCurrentNode() {
        if (isHost()) {
            return null;
        }
        if (currentFrame == null) {
            return getContext().getInstrumentedNode();
        } else {
            Node callNode = currentFrame.getCallNode();
            if (callNode != null) {
                return callNode;
            }
            CallTarget target = currentFrame.getCallTarget();
            if (target instanceof RootCallTarget) {
                return ((RootCallTarget) target).getRootNode();
            }
            return null;
        }
    }

    void verifyValidState(boolean allowDifferentThread) {
        event.verifyValidState(allowDifferentThread);
    }

}
