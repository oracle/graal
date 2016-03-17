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
import java.util.LinkedList;
import java.util.Map;
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

/**
 * Default implementation of the Truffle runtime if the virtual machine does not provide a better
 * performing alternative.
 * <p>
 * This is an implementation-specific class. Do not use or instantiate it. Instead, use
 * {@link Truffle#getRuntime()} to retrieve the current {@link TruffleRuntime}.
 */
public final class DefaultTruffleRuntime implements TruffleRuntime {

    private final ThreadLocal<LinkedList<FrameInstance>> stackTraces = new ThreadLocal<>();
    private final Map<RootCallTarget, Void> callTargets = Collections.synchronizedMap(new WeakHashMap<RootCallTarget, Void>());

    public DefaultTruffleRuntime() {
    }

    @Override
    public String getName() {
        return "Default Truffle Runtime";
    }

    @Override
    public RootCallTarget createCallTarget(RootNode rootNode) {
        DefaultCallTarget target = new DefaultCallTarget(rootNode);
        rootNode.setCallTarget(target);
        callTargets.put(target, null);
        return target;
    }

    @Override
    public DirectCallNode createDirectCallNode(CallTarget target) {
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
        for (FrameInstance frameInstance : getThreadLocalStackTrace()) {
            result = visitor.visitFrame(frameInstance);
            if (result != null) {
                return result;
            }
        }
        return result;
    }

    @Override
    public FrameInstance getCallerFrame() {
        LinkedList<FrameInstance> result = getThreadLocalStackTrace();
        if (result.size() > 1) {
            return result.get(1);
        } else {
            return null;
        }
    }

    @Override
    public Collection<RootCallTarget> getCallTargets() {
        return Collections.unmodifiableSet(callTargets.keySet());
    }

    @Override
    public FrameInstance getCurrentFrame() {
        return getThreadLocalStackTrace().peekFirst();
    }

    private LinkedList<FrameInstance> getThreadLocalStackTrace() {
        LinkedList<FrameInstance> result = stackTraces.get();
        if (result == null) {
            result = new LinkedList<>();
            stackTraces.set(result);
        }
        return result;
    }

    void pushFrame(VirtualFrame frame, CallTarget target) {
        LinkedList<FrameInstance> threadLocalStackTrace = getThreadLocalStackTrace();
        threadLocalStackTrace.addFirst(new DefaultFrameInstance(frame, target, null));
    }

    void pushFrame(VirtualFrame frame, CallTarget target, Node parentCallNode) {
        LinkedList<FrameInstance> threadLocalStackTrace = getThreadLocalStackTrace();
        // we need to ensure that frame instances are immutable so we need to recreate the parent
        // frame
        if (threadLocalStackTrace.size() > 0) {
            DefaultFrameInstance oldInstance = (DefaultFrameInstance) threadLocalStackTrace.removeFirst();
            threadLocalStackTrace.addFirst(new DefaultFrameInstance(oldInstance.getFrame(), oldInstance.getCallTarget(), parentCallNode));
        }
        threadLocalStackTrace.addFirst(new DefaultFrameInstance(frame, target, null));
    }

    void popFrame() {
        LinkedList<FrameInstance> threadLocalStackTrace = getThreadLocalStackTrace();
        threadLocalStackTrace.removeFirst();

        if (threadLocalStackTrace.size() > 0) {
            DefaultFrameInstance parent = (DefaultFrameInstance) threadLocalStackTrace.removeFirst();
            threadLocalStackTrace.addFirst(new DefaultFrameInstance(parent.getFrame(), parent.getCallTarget(), null));
        }
    }

    public <T> T getCapability(Class<T> capability) {
        return null;
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

        DefaultFrameInstance(VirtualFrame frame, CallTarget target, Node callNode) {
            this.target = target;
            this.frame = frame;
            this.callNode = callNode;
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

        final VirtualFrame getFrame() {
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
