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
package com.oracle.truffle.api.debug;

import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.debug.DebugValue.HeapValue;
import com.oracle.truffle.api.debug.DebugValue.StackValue;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstance.FrameAccess;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

/**
 * Represents a frame in the guest language stack. A guest language stack frame consists of a
 * {@link #getName() name}, the current {@link #getSourceSection() source location} and a set of
 * iterable {@link #iterator() local variables}. Furthermore it allows the {@link #eval(String)
 * evaluate} guest language expressions in the lexical context of a particular frame.
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
public final class DebugStackFrame implements Iterable<DebugValue> {

    final SuspendedEvent event;
    private final FrameInstance currentFrame;

    DebugStackFrame(SuspendedEvent session, FrameInstance instance) {
        this.event = session;
        this.currentFrame = instance;
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
        return findCurrentRoot().getSourceSection().getSource().isInternal();
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
    public String getName() {
        verifyValidState(true);
        RootNode root = findCurrentRoot();
        if (root == null) {
            return null;
        }
        try {
            return root.getName();
        } catch (Throwable e) {
            /* Throw error if assertions are enabled. */
            try {
                assert false;
            } catch (AssertionError e1) {
                throw e;
            }
            return null;
        }
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
        EventContext context = getContext();
        if (context == null) {
            // there is a race condition here if the event
            // got disposed between the parent verifyValidState and getContext.
            // if the context is null we assume the event got disposed so we re-check
            // the disposed flag. return null should therefore not be reachable.
            verifyValidState(true);
            assert false : "should not be reachable";
            return null;
        }
        if (currentFrame == null) {
            return context.getInstrumentedSourceSection();
        } else {
            Node callNode = currentFrame.getCallNode();
            if (callNode != null) {
                return callNode.getEncapsulatingSourceSection();
            }
            return null;
        }
    }

    /**
     * Lookup a stack value with a given name. If no value is available in the current stack frame
     * with that name <code>null</code> is returned. Stack values are only accessible as as long as
     * the {@link DebugStackFrame debug stack frame} is valid. Debug stack frames are only valid as
     * long as the source {@link SuspendedEvent suspended event} is valid.
     * <p>
     * This method is thread-safe.
     *
     * @param name the name of the local variable to query.
     * @return the value from the stack
     * @since 0.17
     */
    public DebugValue getValue(String name) {
        verifyValidState(false);
        RootNode root = findCurrentRoot();
        if (root == null) {
            return null;
        }
        FrameSlot slot = root.getFrameDescriptor().findFrameSlot(name);
        if (slot == null) {
            return null;
        }
        MaterializedFrame frame = findTruffleFrame();
        if (frame.getValue(slot) == null) {
            return null;
        }
        return new StackValue(this, slot);
    }

    DebugValue wrapHeapValue(Object result) {
        return new HeapValue(event.getSession().getDebugger(), findCurrentRoot(), result);
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
     * @since 0.17
     */
    public DebugValue eval(String code) {
        verifyValidState(false);
        Object result;
        try {
            result = DebuggerSession.evalInContext(event, code, currentFrame);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return wrapHeapValue(result);
    }

    /**
     * Returns an {@link Iterator iterator} for all stack values available in this frame. The
     * returned stack values remain valid as long as the current stack frame remains valid.
     *
     * <p>
     * This method is not thread-safe and will throw an {@link IllegalStateException} if called on
     * another thread than it was created with.
     *
     * @since 0.17
     */
    public Iterator<DebugValue> iterator() {
        verifyValidState(false);
        RootNode root = findCurrentRoot();
        if (root == null) {
            return Collections.<DebugValue> emptyList().iterator();
        }

        final FrameDescriptor descriptor = root.getFrameDescriptor();
        return new Iterator<DebugValue>() {

            private final Iterator<? extends FrameSlot> slots = descriptor.getSlots().iterator();

            private StackValue nextValue;

            public boolean hasNext() {
                if (nextValue == null) {
                    nextValue = getNext();
                }
                return nextValue != null;
            }

            private StackValue getNext() {
                while (slots.hasNext()) {
                    FrameSlot slot = slots.next();
                    StackValue value = new StackValue(DebugStackFrame.this, slot);
                    if (value.get() != null) {
                        return value;
                    }
                }
                return null;
            }

            public StackValue next() {
                StackValue next = nextValue;
                if (next == null) {
                    return getNext();
                }
                nextValue = null;
                return next;
            }

            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    MaterializedFrame findTruffleFrame() {
        if (currentFrame == null) {
            return event.getMaterializedFrame();
        } else {
            return currentFrame.getFrame(FrameAccess.MATERIALIZE).materialize();
        }
    }

    private EventContext getContext() {
        return event.getContext();
    }

    @SuppressWarnings("rawtypes")
    Class<? extends TruffleLanguage> findCurrentLanguage() {
        RootNode root = findCurrentRoot();
        if (root != null) {
            return Debugger.ACCESSOR.findLanguage(root);
        }
        return null;
    }

    RootNode findCurrentRoot() {
        EventContext context = getContext();
        if (context == null) {
            // there is a race condition here if the event
            // got disposed between the parent verifyValidState and getContext.
            // if the context is null we assume the event got disposed so we re-check
            // the disposed flag. return null should therefore not be reachable.
            verifyValidState(true);
            assert false : "should not be reachable";
            return null;
        }
        if (currentFrame == null) {
            return context.getInstrumentedNode().getRootNode();
        } else {
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
