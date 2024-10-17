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

import java.util.Objects;

import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * Represents a guest stack trace element.
 *
 * @since 0.27
 */
public final class TruffleStackTraceElement {

    private final Node location;
    private final RootCallTarget target;
    private final Frame frame;
    private final int bytecodeIndex;

    TruffleStackTraceElement(Node location, RootCallTarget target, Frame frame, int bytecodeIndex) {
        this.location = location;
        this.target = target;
        this.frame = frame;
        this.bytecodeIndex = bytecodeIndex;
    }

    /**
     * Create a new stack trace element.
     *
     * @param location See {@link #getLocation()}
     * @param target See {@link #getTarget()}
     * @param frame See {@link #getFrame()}
     * @since 20.1.0
     */
    public static TruffleStackTraceElement create(Node location, RootCallTarget target, Frame frame) {
        Objects.requireNonNull(target, "RootCallTarget must not be null");
        return new TruffleStackTraceElement(location, target, frame, -1);
    }

    /**
     * Create a new stack trace element.
     *
     * @param location See {@link #getLocation()}
     * @param target See {@link #getTarget()}
     * @param frame See {@link #getFrame()}
     * @param bytecodeIndex See {@link #getBytecodeIndex()}
     * @since 24.1
     */
    public static TruffleStackTraceElement create(Node location, RootCallTarget target, Frame frame, int bytecodeIndex) {
        Objects.requireNonNull(target, "RootCallTarget must not be null");
        return new TruffleStackTraceElement(location, target, frame, bytecodeIndex);
    }

    /**
     * Returns a node representing the callsite on the stack.
     * <p>
     * Returns <code>null</code> if no detailed callsite information is available. This is the case
     * when {@link CallTarget#call(Object...)} is used or for the top-of-the-stack element or if the
     * exception is an internal exception or the
     * {@link com.oracle.truffle.api.exception.AbstractTruffleException#getLocation()} returned
     * <code>null</code>.
     * <p>
     * See {@link FrameInstance#getCallNode()} for the relation between callsite and CallTarget.
     *
     * @since 0.27
     **/
    public Node getLocation() {
        return location;
    }

    /**
     * Returns an instrumentable call node from a frame instance. This method should be preferred
     * over {@link #getLocation()} by tools to find the instrumentable node associated with this
     * stack trace element.
     * <p>
     * In case of bytecode interpreters the instrumentable node needs to be resolved by the language
     * and is not directly accessible from the {@link Node#getParent() parent} chain of the regular
     * {@link #getLocation() location}. Just like {@link #getLocation()} this method may not
     * directly return an instrumentable node. To find the eventual instrumentable node the
     * {@link Node#getParent() parent} chain must be searched. There is no guarantee that an
     * instrumentable node can be found, e.g. if the language does not support instrumentation.
     *
     * @see RootNode#findInstrumentableCallNode
     * @see FrameInstance#getInstrumentableCallNode()
     * @see com.oracle.truffle.api.instrumentation.InstrumentableNode#findInstrumentableParent(Node)
     * @since 24.2
     */
    public Node getInstrumentableLocation() {
        return LanguageAccessor.NODES.findInstrumentableCallNode(target.getRootNode(), getLocation(), getFrame(), getBytecodeIndex());
    }

    /**
     * Returns the call target on the stack. Never returns <code>null</code>.
     * <p>
     * See {@link FrameInstance#getCallNode()} for the relation between callsite and CallTarget.
     *
     * @since 0.27
     **/
    public RootCallTarget getTarget() {
        return target;
    }

    /**
     * Returns the read-only frame. Returns <code>null</code> if the initial {@link RootNode} that
     * filled in the stack trace did not request frames to be captured by overriding
     * {@link RootNode#isCaptureFramesForTrace(Node)}.
     *
     * @since 0.31
     */
    public Frame getFrame() {
        return frame;
    }

    /**
     * Returns <code>true</code> if the stack trace element contains a valid bytecode index, else
     * <code>false</code>. If this method returns <code>false</code> then
     * {@link #getBytecodeIndex()} will return a negative <code>int</code> value.
     *
     * @since 24.1
     */
    public boolean hasBytecodeIndex() {
        return getBytecodeIndex() >= 0;
    }

    /**
     * Returns the bytecode index at the time when this frame was created. If no bytecode index was
     * captured then a negative <code>int</code> value is returned. When the stack trace element is
     * captured {@link RootNode#findBytecodeIndex} is invoked to resolve the byte code index
     * returned by this method.
     *
     * @see RootNode#findBytecodeIndex
     * @since 24.1
     */
    public int getBytecodeIndex() {
        return bytecodeIndex;
    }

    /**
     * Returns an interop object representing this {@linkplain TruffleStackTraceElement} supporting
     * the {@code hasExecutableName} and potentially {@code hasDeclaringMetaObject} and
     * {@code hasSourceLocation} messages.
     * <p>
     * This method must only be called on an interpreter thread with a valid
     * {@link TruffleContext#isEntered() entered}. The current entered context can be accessed
     * through the language or instrument environment.
     * <p>
     *
     * @since 20.3
     */
    public Object getGuestObject() {
        assert LanguageAccessor.engineAccess().getCurrentCreatorTruffleContext() != null : "The TruffleContext must be entered.";
        Object guestObject = LanguageAccessor.nodesAccess().translateStackTraceElement(this);
        assert LanguageAccessor.EXCEPTIONS.assertGuestObject(guestObject);
        return guestObject;
    }

    /**
     * {@inheritDoc}
     *
     * @since 24.0
     */
    @Override
    public String toString() {
        return "TruffleStackTraceElement[rootNode=" + target.getRootNode() + ", callNode=" + location + ", bytecodeIndex=" + bytecodeIndex + ", frame=" + frame + "]";
    }

}
