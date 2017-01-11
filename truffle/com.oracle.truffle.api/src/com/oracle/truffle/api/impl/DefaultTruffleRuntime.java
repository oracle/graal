/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.impl;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerOptions;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RepeatingNode;
import com.oracle.truffle.api.nodes.RootNode;
import java.util.Iterator;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/**
 * Default implementation of the Truffle runtime if the virtual machine does not provide a better
 * performing alternative.
 * <p>
 * This is an implementation-specific class. Do not use or instantiate it. Instead, use
 * {@link Truffle#getRuntime()} to retrieve the current {@link TruffleRuntime}.
 */
public final class DefaultTruffleRuntime implements TruffleRuntime {

    private final ThreadLocal<DefaultFrameInstance> stackTraces = new ThreadLocal<>();
    private final Map<RootCallTarget, Void> callTargets = Collections.synchronizedMap(new WeakHashMap<RootCallTarget, Void>());
    private final DefaultTVMCI tvmci = new DefaultTVMCI();

    public DefaultTruffleRuntime() {
    }

    public DefaultTVMCI getTvmci() {
        return tvmci;
    }

    @Override
    public String getName() {
        return "Default Truffle Runtime";
    }

    @SuppressWarnings("deprecation")
    @Override
    public RootCallTarget createCallTarget(RootNode rootNode) {
        DefaultCallTarget target = new DefaultCallTarget(rootNode);
        rootNode.setCallTarget(target);
        getTvmci().onLoad(target);
        callTargets.put(target, null);
        return target;
    }

    @Override
    public DirectCallNode createDirectCallNode(CallTarget target) {
        Objects.requireNonNull(target);
        return new DefaultDirectCallNode(target);
    }

    @Override
    public IndirectCallNode createIndirectCallNode() {
        return new DefaultIndirectCallNode();
    }

    @Override
    public VirtualFrame createVirtualFrame(Object[] arguments, FrameDescriptor frameDescriptor) {
        return new DefaultVirtualFrame(frameDescriptor, arguments);
    }

    @Override
    public MaterializedFrame createMaterializedFrame(Object[] arguments) {
        return createMaterializedFrame(arguments, new FrameDescriptor());
    }

    @Override
    public MaterializedFrame createMaterializedFrame(Object[] arguments, FrameDescriptor frameDescriptor) {
        return new DefaultMaterializedFrame(new DefaultVirtualFrame(frameDescriptor, arguments));
    }

    @Override
    public CompilerOptions createCompilerOptions() {
        return new DefaultCompilerOptions();
    }

    @Override
    public Assumption createAssumption() {
        return createAssumption(null);
    }

    @Override
    public Assumption createAssumption(String name) {
        return new DefaultAssumption(name);
    }

    @Override
    public <T> T iterateFrames(FrameInstanceVisitor<T> visitor) {
        T result = null;
        DefaultFrameInstance frameInstance = getThreadLocalStackTrace();
        while (frameInstance != null) {
            result = visitor.visitFrame(frameInstance);
            if (result != null) {
                return result;
            }
            frameInstance = frameInstance.callerFrame;
        }
        return result;
    }

    @Override
    public FrameInstance getCallerFrame() {
        DefaultFrameInstance currentFrame = getThreadLocalStackTrace();
        if (currentFrame != null) {
            return currentFrame.callerFrame;
        } else {
            return null;
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public Collection<RootCallTarget> getCallTargets() {
        return Collections.unmodifiableSet(callTargets.keySet());
    }

    @Override
    public FrameInstance getCurrentFrame() {
        return getThreadLocalStackTrace();
    }

    private DefaultFrameInstance getThreadLocalStackTrace() {
        return stackTraces.get();
    }

    private void setThreadLocalStackTrace(DefaultFrameInstance topFrame) {
        stackTraces.set(topFrame);
    }

    void pushFrame(VirtualFrame frame, CallTarget target) {
        setThreadLocalStackTrace(new DefaultFrameInstance(frame, target, null, getThreadLocalStackTrace()));
    }

    void pushFrame(VirtualFrame frame, CallTarget target, Node parentCallNode) {
        DefaultFrameInstance currentFrame = getThreadLocalStackTrace();
        // we need to ensure that frame instances are immutable so we need to recreate the parent
        // frame
        if (currentFrame != null) {
            currentFrame = new DefaultFrameInstance(currentFrame.frame, currentFrame.target, parentCallNode, currentFrame.callerFrame);
        }
        setThreadLocalStackTrace(new DefaultFrameInstance(frame, target, null, currentFrame));
    }

    void popFrame() {
        DefaultFrameInstance callerFrame = getThreadLocalStackTrace().callerFrame;
        if (callerFrame != null) {
            setThreadLocalStackTrace(new DefaultFrameInstance(callerFrame.frame, callerFrame.target, null, callerFrame.callerFrame));
        } else {
            setThreadLocalStackTrace(null);
        }
    }

    public <T> T getCapability(Class<T> capability) {
        final Iterator<T> it = ServiceLoader.load(capability).iterator();
        try {
            return it.hasNext() ? it.next() : null;
        } catch (ServiceConfigurationError e) {
            return null;
        }
    }

    public void notifyTransferToInterpreter() {
    }

    public LoopNode createLoopNode(RepeatingNode repeating) {
        if (!(repeating instanceof Node)) {
            throw new IllegalArgumentException("Repeating node must be of type Node.");
        }
        return new DefaultLoopNode(repeating);
    }

    public boolean isProfilingEnabled() {
        return false;
    }

    private static class DefaultFrameInstance implements FrameInstance {

        private final CallTarget target;
        private final VirtualFrame frame;
        private final Node callNode;
        private final DefaultFrameInstance callerFrame;

        DefaultFrameInstance(VirtualFrame frame, CallTarget target, Node callNode, DefaultFrameInstance callerFrame) {
            this.target = target;
            this.frame = frame;
            this.callNode = callNode;
            this.callerFrame = callerFrame;
        }

        public final Frame getFrame(FrameAccess access, boolean slowPath) {
            if (access == FrameAccess.NONE) {
                return null;
            }
            if (access == FrameAccess.MATERIALIZE) {
                return frame.materialize();
            }
            return frame;
        }

        public final boolean isVirtualFrame() {
            return false;
        }

        public final CallTarget getCallTarget() {
            return target;
        }

        public Node getCallNode() {
            return callNode;
        }

    }
}
