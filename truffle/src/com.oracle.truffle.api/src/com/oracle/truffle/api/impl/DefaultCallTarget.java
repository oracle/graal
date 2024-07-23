/*
 * Copyright (c) 2012, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.impl;

import static com.oracle.truffle.api.impl.DefaultTruffleRuntime.getRuntime;

import java.util.concurrent.atomic.AtomicLong;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.impl.DefaultTruffleRuntime.DefaultFrameInstance;
import com.oracle.truffle.api.nodes.EncapsulatingNodeReference;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * This is an implementation-specific class. Do not use or instantiate it. Instead, use
 * {@link RootNode#getCallTarget()} to get the {@link RootCallTarget} of a root node.
 */
public final class DefaultCallTarget implements RootCallTarget {

    public static final String CALL_BOUNDARY_METHOD = "callDirectOrIndirect";
    private final RootNode rootNode;
    private volatile boolean initialized;
    private volatile boolean loaded;

    public final long id;

    private static final AtomicLong ID_COUNTER = new AtomicLong(0);

    DefaultCallTarget(RootNode function) {
        this.rootNode = function;
        this.rootNode.adoptChildren();
        this.id = ID_COUNTER.incrementAndGet();
    }

    @Override
    public String toString() {
        return rootNode.toString();
    }

    public RootNode getRootNode() {
        return rootNode;
    }

    Object callDirectOrIndirect(final Node callNode, Object... args) {
        if (!this.initialized) {
            initialize();
        }
        final VirtualFrame frame = new FrameWithoutBoxing(rootNode.getFrameDescriptor(), args);
        DefaultFrameInstance callerFrame = getRuntime().pushFrame(frame, this, callNode);
        try {
            Object toRet = rootNode.execute(frame);
            TruffleSafepoint.poll(rootNode);
            return toRet;
        } catch (Throwable t) {
            DefaultRuntimeAccessor.LANGUAGE.addStackFrameInfo(callNode, this, t, frame);
            throw t;
        } finally {
            getRuntime().popFrame(callerFrame);
        }
    }

    @Override
    public Object call(Object... args) {
        // Use the encapsulating node as call site and clear it inside as we cross the call boundary
        EncapsulatingNodeReference encapsulating = EncapsulatingNodeReference.getCurrent();
        Node parent = encapsulating.set(null);
        try {
            return callDirectOrIndirect(parent, args);
        } finally {
            encapsulating.set(parent);
        }
    }

    private void initialize() {
        synchronized (this) {
            if (!this.initialized) {
                DefaultRuntimeAccessor.INSTRUMENT.onFirstExecution(getRootNode(), true);
                this.initialized = true;
            }
        }
    }

    boolean isLoaded() {
        return loaded;
    }

    void setLoaded() {
        this.loaded = true;
    }
}
