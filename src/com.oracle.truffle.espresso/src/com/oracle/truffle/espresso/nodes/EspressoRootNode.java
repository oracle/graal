/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.nodes;

import java.util.Arrays;

import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.FrameUtil;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.InstrumentableNode.WrapperNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.espresso.impl.ContextAccess;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.vm.InterpreterToVM;

/**
 * The root of all executable bits in Espresso, includes everything that can be called a "method" in
 * Java. Regular (concrete) Java methods, native methods and intrinsics/substitutions.
 */
public abstract class EspressoRootNode extends RootNode implements ContextAccess {

    // must not be of type EspressoMethodNode as it might be wrapped by instrumentation
    @Child protected EspressoInstrumentableNode methodNode;

    private final FrameSlot monitorSlot;
    private final FrameSlot frameIdSlot;

    private final BranchProfile unbalancedMonitorProfile = BranchProfile.create();

    EspressoRootNode(FrameDescriptor frameDescriptor, EspressoMethodNode methodNode, boolean usesMonitors) {
        super(methodNode.getMethod().getEspressoLanguage(), frameDescriptor);
        this.methodNode = methodNode;
        this.monitorSlot = usesMonitors ? frameDescriptor.addFrameSlot("monitor", FrameSlotKind.Object) : null;
        this.frameIdSlot = frameDescriptor.addFrameSlot("frameId", FrameSlotKind.Long);
    }

    private EspressoRootNode(EspressoRootNode split, FrameDescriptor frameDescriptor, EspressoMethodNode methodNode) {
        super(methodNode.getMethod().getEspressoLanguage(), frameDescriptor);
        this.methodNode = methodNode;
        this.monitorSlot = split.monitorSlot;
        this.frameIdSlot = split.frameIdSlot;
    }

    public final Method getMethod() {
        return getMethodNode().getMethod();
    }

    public final Method.MethodVersion getMethodVersion() {
        return getMethodNode().getMethodVersion();
    }

    public abstract EspressoRootNode split();

    @Override
    public boolean isCloningAllowed() {
        return getMethodNode().shouldSplit();
    }

    @Override
    protected boolean isCloneUninitializedSupported() {
        return getMethodNode().shouldSplit();
    }

    @Override
    protected EspressoRootNode cloneUninitialized() {
        return split();
    }

    @Override
    public final EspressoContext getContext() {
        return getMethodNode().getContext();
    }

    @Override
    public final String getName() {
        return getMethod().getDeclaringKlass().getType() + "." + getMethod().getName() + getMethod().getRawSignature();
    }

    @Override
    public final String toString() {
        return getName();
    }

    @Override
    public final SourceSection getSourceSection() {
        return getMethodNode().getSourceSection();
    }

    @Override
    public final SourceSection getEncapsulatingSourceSection() {
        return getMethodNode().getEncapsulatingSourceSection();
    }

    public final boolean isBytecodeNode() {
        return getMethodNode() instanceof BytecodeNode;
    }

    public final BytecodeNode getBytecodeNode() {
        if (isBytecodeNode()) {
            return (BytecodeNode) getMethodNode();
        } else {
            return null;
        }
    }

    protected EspressoMethodNode getMethodNode() {
        Node child = methodNode;
        if (child instanceof WrapperNode) {
            child = ((WrapperNode) child).getDelegateNode();
        }
        assert !(child instanceof WrapperNode);
        return (EspressoMethodNode) child;
    }

    public static EspressoRootNode create(FrameDescriptor descriptor, EspressoMethodNode methodNode) {
        FrameDescriptor desc = descriptor != null ? descriptor : new FrameDescriptor();
        if (methodNode.getMethod().isSynchronized()) {
            return new Synchronized(desc, methodNode);
        } else {
            return new Default(desc, methodNode);
        }
    }

    public final int readBCI(Frame frame) {
        return ((BytecodeNode) getMethodNode()).readBCI(frame);
    }

    public final void setFrameId(Frame frame, long frameId) {
        frame.setLong(frameIdSlot, frameId);
    }

    public final long readFrameIdOrZero(Frame frame) {
        try {
            return frame.isLong(frameIdSlot) ? frame.getLong(frameIdSlot) : 0L;
        } catch (FrameSlotTypeException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    public boolean usesMonitors() {
        return monitorSlot != null;
    }

    final void initMonitorStack(VirtualFrame frame) {
        frame.setObject(monitorSlot, new MonitorStack());
    }

    final void monitorExit(VirtualFrame frame, StaticObject monitor) {
        InterpreterToVM.monitorExit(monitor);
        unregisterMonitor(frame, monitor);
    }

    final void unregisterMonitor(VirtualFrame frame, StaticObject monitor) {
        getMonitorStack(frame).exit(monitor, this);
    }

    final void monitorEnter(VirtualFrame frame, StaticObject monitor) {
        InterpreterToVM.monitorEnter(monitor);
        registerMonitor(frame, monitor);
    }

    private void registerMonitor(VirtualFrame frame, StaticObject monitor) {
        getMonitorStack(frame).enter(monitor);
    }

    private MonitorStack getMonitorStack(Frame frame) {
        Object frameResult = FrameUtil.getObjectSafe(frame, monitorSlot);
        assert frameResult instanceof MonitorStack;
        return (MonitorStack) frameResult;
    }

    public final StaticObject[] getMonitorsOnFrame(Frame frame) {
        MonitorStack monitorStack = getMonitorStack(frame);
        return monitorStack != null ? monitorStack.getMonitors() : StaticObject.EMPTY_ARRAY;
    }

    public final void abortMonitor(VirtualFrame frame) {
        if (usesMonitors()) {
            getMonitorStack(frame).abort();
        }
    }

    static final class Synchronized extends EspressoRootNode {

        Synchronized(FrameDescriptor frameDescriptor, EspressoMethodNode methodNode) {
            super(frameDescriptor, methodNode, true);
        }

        Synchronized(Synchronized split) {
            super(split, split.getFrameDescriptor(), split.getMethodNode());
        }

        @Override
        public EspressoRootNode split() {
            return new Synchronized(this);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Method method = getMethod();
            assert method.isSynchronized();
            StaticObject monitor = method.isStatic()
                            ? /* class */ method.getDeclaringKlass().mirror()
                            : /* receiver */ (StaticObject) frame.getArguments()[0];
            // No owner checks in SVM. Manual monitor accesses is a safeguard against unbalanced
            // monitor accesses until Espresso has its own monitor handling.
            //
            // synchronized (monitor) {
            initMonitorStack(frame);
            monitorEnter(frame, monitor);
            Object result;
            try {
                result = methodNode.execute(frame);
            } finally {
                // force early return has already released the monitor on the frame, so don't
                // do an unbalanced monitor exit here
                if (getContext().getJDWPListener().getAndRemoveEarlyReturnValue() == null) {
                    monitorExit(frame, monitor);
                }
            }
            return result;
        }
    }

    static final class Default extends EspressoRootNode {

        Default(FrameDescriptor frameDescriptor, EspressoMethodNode methodNode) {
            super(frameDescriptor, methodNode, methodNode.getMethod().usesMonitors());
        }

        Default(Default split) {
            super(split, split.getFrameDescriptor(), split.getMethodNode());
        }

        @Override
        public EspressoRootNode split() {
            return new Default(this);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            if (usesMonitors()) {
                initMonitorStack(frame);
            }
            return methodNode.execute(frame);
        }
    }

    private static final class MonitorStack {
        private static final int DEFAULT_CAPACITY = 4;

        private StaticObject[] monitors = new StaticObject[DEFAULT_CAPACITY];
        private int top = 0;
        private int capacity = DEFAULT_CAPACITY;

        private void enter(StaticObject monitor) {
            if (top >= capacity) {
                monitors = Arrays.copyOf(monitors, capacity <<= 1);
            }
            monitors[top++] = monitor;
        }

        private void exit(StaticObject monitor, EspressoRootNode node) {
            if (top > 0 && monitor == monitors[top - 1]) {
                // Balanced locking: simply pop.
                monitors[--top] = null;
            } else {
                node.unbalancedMonitorProfile.enter();
                // Unbalanced locking: do the linear search.
                int i = top - 1;
                for (; i >= 0; i--) {
                    if (monitors[i] == monitor) {
                        System.arraycopy(monitors, i + 1, monitors, i, top - 1 - i);
                        monitors[--top] = null;
                        return;
                    }
                }
                // monitor not found. Not against the specs.
            }
        }

        private void abort() {
            for (int i = 0; i < top; i++) {
                StaticObject monitor = monitors[i];
                try {
                    InterpreterToVM.monitorExit(monitor);
                } catch (Throwable e) {
                    /* ignore */
                }
            }
        }

        private StaticObject[] getMonitors() {
            return monitors;
        }
    }
}
