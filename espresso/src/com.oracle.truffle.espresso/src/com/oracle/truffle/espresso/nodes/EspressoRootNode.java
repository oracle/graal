/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.espresso.impl.ContextAccess;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.ForeignStackTraceElementObject;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.CallableFromNative;
import com.oracle.truffle.espresso.substitutions.JavaSubstitution;
import com.oracle.truffle.espresso.vm.FrameCookie;
import com.oracle.truffle.espresso.vm.InterpreterToVM;

/**
 * The root of all executable bits in Espresso, includes everything that can be called a "method" in
 * Java. Regular (concrete) Java methods, native methods and intrinsics/substitutions.
 */
public abstract class EspressoRootNode extends RootNode implements ContextAccess {

    // must not be of type EspressoMethodNode as it might be wrapped by instrumentation
    @Child protected EspressoInstrumentableRootNode methodNode;

    private static final int SLOT_UNUSED = -2;
    private static final int SLOT_UNINITIALIZED = -1;

    private static final Object MONITOR_SLOT_KEY = new Object();
    private static final Object COOKIE_SLOT_KEY = new Object();

    @CompilationFinal private int monitorSlot;
    /**
     * Shared slot for some VM method implementations that needs to leave a mark of passage in a
     * particular frame. See {@link FrameCookie}.
     */
    @CompilationFinal private int cookieSlot;

    private final BranchProfile unbalancedMonitorProfile = BranchProfile.create();

    private EspressoRootNode(FrameDescriptor frameDescriptor, EspressoInstrumentableRootNode methodNode, boolean usesMonitors) {
        super(methodNode.getMethodVersion().getMethod().getLanguage(), frameDescriptor);
        this.methodNode = methodNode;
        this.monitorSlot = usesMonitors ? SLOT_UNINITIALIZED : SLOT_UNUSED;
        this.cookieSlot = SLOT_UNINITIALIZED;
    }

    // Splitting constructor
    private EspressoRootNode(EspressoRootNode split, FrameDescriptor frameDescriptor, EspressoInstrumentableRootNode methodNode) {
        super(methodNode.getMethodVersion().getMethod().getLanguage(), frameDescriptor);
        this.methodNode = methodNode.split();
        this.monitorSlot = split.monitorSlot;
        this.cookieSlot = split.cookieSlot;
    }

    public final Method getMethod() {
        return getMethodVersion().getMethod();
    }

    public final Method.MethodVersion getMethodVersion() {
        return getMethodNode().getMethodVersion();
    }

    public abstract EspressoRootNode split();

    @Override
    public boolean isCloningAllowed() {
        return getMethodNode().canSplit();
    }

    @Override
    protected boolean isCloneUninitializedSupported() {
        return getMethodNode().canSplit();
    }

    @Override
    protected EspressoRootNode cloneUninitialized() {
        return split();
    }

    @Override
    public final EspressoContext getContext() {
        return getMethodNode().getContext();
    }

    /*
     * Needed to prevent svm analysis from including other versions of getMeta.
     */
    @Override
    public Meta getMeta() {
        return getContext().getMeta();
    }

    @Override
    public final String getName() {
        return getMethod().getName().toString() + getMethod().getRawSignature();
    }

    @Override
    public String getQualifiedName() {
        return getMethod().getDeclaringKlass().getType() + "." + getMethod().getName() + getMethod().getRawSignature();
    }

    @Override
    protected Object translateStackTraceElement(TruffleStackTraceElement element) {
        Node location = element.getLocation();
        return new ForeignStackTraceElementObject(getMethod(), location != null ? location.getEncapsulatingSourceSection() : getEncapsulatingSourceSection());
    }

    @Override
    public final String toString() {
        return getQualifiedName();
    }

    @Override
    public final SourceSection getSourceSection() {
        return getMethodNode().getSourceSection();
    }

    @Override
    public final SourceSection getEncapsulatingSourceSection() {
        return getMethodNode().getEncapsulatingSourceSection();
    }

    public EspressoInstrumentableRootNode getMethodNode() {
        return methodNode;
    }

    private static EspressoRootNode create(FrameDescriptor descriptor, EspressoInstrumentableRootNode methodNode) {
        FrameDescriptor desc = descriptor != null ? descriptor : new FrameDescriptor();

        EspressoRootNode result = null;
        if (methodNode.getMethodVersion().isSynchronized()) {
            result = new Synchronized(desc, methodNode);
        } else {
            result = new Default(desc, methodNode);
        }
        assert hasExactlyOneRootBodyTag(result.getMethodNode()) : result;
        return result;
    }

    public static boolean hasExactlyOneRootBodyTag(EspressoNode body) {
        return NodeUtil.countNodes(body, node -> node instanceof EspressoInstrumentableNode && ((EspressoInstrumentableNode) node).hasTag(StandardTags.RootBodyTag.class)) == 1;
    }

    /**
     * Creates a root node that can execute the Java bytecodes of the given method. The given method
     * must be a concrete, non-native Java method.
     */
    public static EspressoRootNode createForBytecodes(Method.MethodVersion methodVersion) {
        BytecodeNode bytecodeNode = new BytecodeNode(methodVersion);
        return create(bytecodeNode.getFrameDescriptor(), new MethodWithBytecodeNode(bytecodeNode));
    }

    /**
     * Creates a root node that can execute a native Java method.
     */
    public static EspressoRootNode createNative(Method.MethodVersion methodVersion, TruffleObject nativeMethod) {
        return create(null, new NativeMethodNode(nativeMethod, methodVersion));
    }

    /**
     * Creates a root node that can execute a native Java method, implemented in Java for Espresso,
     * without going to native code.
     *
     * Used to link native calls implemented in Java that are bound with JNI's
     * {@code registerNatives} e.g. JVM_IHashCode, JVM_IsNaN, JVM_ArrayCopy.
     */
    public static EspressoRootNode createIntrinsifiedNative(Method.MethodVersion methodVersion, CallableFromNative.Factory factory, Object env) {
        return create(null, new IntrinsifiedNativeMethodNode(methodVersion, factory, env));
    }

    /**
     * Creates a root node that can execute a substitution e.g. an implementation of the method in
     * host Java, instead of the original givenmethod.
     */
    public static EspressoRootNode createSubstitution(Method.MethodVersion methodVersion, JavaSubstitution.Factory factory) {
        return create(null, new IntrinsicSubstitutorNode(methodVersion, factory));
    }

    public final int readBCI(Frame frame) {
        return methodNode.getBci(frame);
    }

    @Override
    public Node getLeafNodeByFrame(FrameInstance frameInstance) {
        Frame frame = frameInstance.getFrame(FrameInstance.FrameAccess.READ_ONLY);
        Node leafNode = methodNode.getLeafNode(readBCI(frame));
        if (leafNode != null) {
            assert leafNode.getRootNode() == this;
            return leafNode;
        }
        return this;
    }

    public final void setFrameId(Frame frame, long frameId) {
        initCookieSlot(frame);
        frame.setAuxiliarySlot(cookieSlot, FrameCookie.createPrivilegedCookie(frameId));
    }

    public final void setStackWalkAnchor(Frame frame, long anchor) {
        initCookieSlot(frame);
        frame.setAuxiliarySlot(cookieSlot, FrameCookie.createStackWalkCookie(anchor));
    }

    private FrameCookie getCookie(Frame frame) {
        initCookieSlot(frame);
        return (FrameCookie) frame.getAuxiliarySlot(cookieSlot);
    }

    private void initCookieSlot(Frame frame) {
        if (cookieSlot == SLOT_UNINITIALIZED) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            cookieSlot = frame.getFrameDescriptor().findOrAddAuxiliarySlot(COOKIE_SLOT_KEY);
        }
    }

    public final long readFrameIdOrZero(Frame frame) {
        FrameCookie cookie = getCookie(frame);
        if (cookie != null && cookie.isPrivileged()) {
            return cookie.getData();
        }
        return 0L;
    }

    public final long readStackAnchorOrZero(Frame frame) {
        FrameCookie cookie = getCookie(frame);
        if (cookie != null && cookie.isStackWalk()) {
            return cookie.getData();
        }
        return 0L;
    }

    public boolean usesMonitors() {
        return monitorSlot != SLOT_UNUSED;
    }

    final void initMonitorStack(VirtualFrame frame, MonitorStack monitorStack) {
        initMonitorSlot(frame);
        assert monitorStack != null;
        frame.setAuxiliarySlot(monitorSlot, monitorStack);
    }

    private void initMonitorSlot(VirtualFrame frame) {
        if (monitorSlot == SLOT_UNINITIALIZED) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            monitorSlot = frame.getFrameDescriptor().findOrAddAuxiliarySlot(MONITOR_SLOT_KEY);
        }
    }

    final void monitorExit(VirtualFrame frame, StaticObject monitor) {
        InterpreterToVM.monitorExit(monitor, getMeta());
        unregisterMonitor(frame, monitor);
    }

    final void unregisterMonitor(VirtualFrame frame, StaticObject monitor) {
        getMonitorStack(frame).exit(monitor, this);
    }

    final void monitorEnter(VirtualFrame frame, StaticObject monitor) {
        InterpreterToVM.monitorEnter(monitor, getMeta());
        registerMonitor(frame, monitor);
    }

    private void registerMonitor(VirtualFrame frame, StaticObject monitor) {
        getMonitorStack(frame).enter(monitor);
    }

    protected MonitorStack getMonitorStack(Frame frame) {
        assert monitorSlot >= 0;
        return (MonitorStack) frame.getAuxiliarySlot(monitorSlot);
    }

    public final StaticObject[] getMonitorsOnFrame(Frame frame) {
        MonitorStack monitorStack = getMonitorStack(frame);
        return monitorStack != null ? monitorStack.getMonitors() : StaticObject.EMPTY_ARRAY;
    }

    public final void abortMonitor(VirtualFrame frame) {
        if (usesMonitors()) {
            getMonitorStack(frame).abort(getMeta());
        }
    }

    public void abortInternalMonitors(Frame frame) {
        getMonitorStack(frame).exitInternalMonitors(getMeta());
    }

    static final class Synchronized extends EspressoRootNode {

        Synchronized(FrameDescriptor frameDescriptor, EspressoInstrumentableRootNode methodNode) {
            super(frameDescriptor, methodNode, true);
        }

        private Synchronized(Synchronized split) {
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
            assert method.getDeclaringKlass().isInitializedOrInitializing() : method.getDeclaringKlass();
            methodNode.beforeInstumentation(frame);
            StaticObject monitor = method.isStatic()
                            ? /* class */ method.getDeclaringKlass().mirror()
                            : /* receiver */ (StaticObject) frame.getArguments()[0];
            enterSynchronized(frame, monitor);
            Object result;
            try {
                result = methodNode.execute(frame);
            } finally {
                InterpreterToVM.monitorExit(monitor, getMeta());
            }
            return result;
        }

        private void enterSynchronized(VirtualFrame frame, StaticObject monitor) {
            MonitorStack monitorStack = new MonitorStack();
            monitorStack.synchronizedMethodMonitor = monitor;
            initMonitorStack(frame, monitorStack);
            InterpreterToVM.monitorEnter(monitor, getMeta());
        }
    }

    static final class Default extends EspressoRootNode {

        Default(FrameDescriptor frameDescriptor, EspressoInstrumentableRootNode methodNode) {
            super(frameDescriptor, methodNode, methodNode.getMethodVersion().usesMonitors());
        }

        private Default(Default split) {
            super(split, split.getFrameDescriptor(), split.getMethodNode());
        }

        @Override
        public EspressoRootNode split() {
            return new Default(this);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            assert getMethod().getDeclaringKlass().isInitializedOrInitializing() || getContext().anyHierarchyChanged() : getMethod().toString() +
                            (getMethod().isStatic() ? "" : " recv: " + frame.getArguments()[0].toString());
            if (usesMonitors()) {
                initMonitorStack(frame, new MonitorStack());
            }
            methodNode.beforeInstumentation(frame);
            return methodNode.execute(frame);
        }
    }

    private static final class MonitorStack {
        private static final int DEFAULT_CAPACITY = 4;

        private StaticObject synchronizedMethodMonitor;
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

        private void abort(Meta meta) {
            for (int i = 0; i < top; i++) {
                StaticObject monitor = monitors[i];
                try {
                    InterpreterToVM.monitorExit(monitor, meta);
                } catch (Throwable e) {
                    /* ignore */
                }
            }
        }

        public void exitInternalMonitors(Meta meta) {
            for (int i = 0; i < top; i++) {
                InterpreterToVM.monitorExit(monitors[i], meta);
            }
        }

        private StaticObject[] getMonitors() {
            if (synchronizedMethodMonitor == null) {
                return monitors;
            } else {
                StaticObject[] result = new StaticObject[monitors.length + 1];
                result[0] = synchronizedMethodMonitor;
                System.arraycopy(monitors, 0, result, 1, monitors.length);
                return result;
            }
        }
    }

    @Override
    protected final boolean isTrivial() {
        return !methodNode.getMethodVersion().isSynchronized() && methodNode.isTrivial();
    }
}
