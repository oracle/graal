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

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.InstrumentableNode.WrapperNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.espresso.impl.ContextAccess;
import com.oracle.truffle.espresso.impl.Method;
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

    EspressoRootNode(FrameDescriptor frameDescriptor, EspressoMethodNode methodNode) {
        super(methodNode.getMethod().getEspressoLanguage(), frameDescriptor);
        this.methodNode = methodNode;
    }

    public final Method getMethod() {
        return getMethodNode().getMethod();
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
    public Object execute(VirtualFrame frame) {
        return methodNode.execute(frame);
    }

    @Override
    public final SourceSection getSourceSection() {
        return getMethodNode().getSourceSection();
    }

    @Override
    public SourceSection getEncapsulatingSourceSection() {
        return getMethodNode().getEncapsulatingSourceSection();
    }

    public final boolean isBytecodeNode() {
        return getMethodNode() instanceof BytecodeNode;
    }

    private EspressoMethodNode getMethodNode() {
        Node child = methodNode;
        if (child instanceof WrapperNode) {
            child = ((WrapperNode) child).getDelegateNode();
        }
        assert !(child instanceof WrapperNode);
        return (EspressoMethodNode) child;
    }

    public static EspressoRootNode create(FrameDescriptor descriptor, EspressoMethodNode methodNode) {
        if (methodNode.getMethod().isSynchronized()) {
            return new Synchronized(descriptor, methodNode);
        } else {
            return new Default(descriptor, methodNode);
        }
    }

    public int readBCI(FrameInstance frameInstance) {
        return ((BytecodeNode) getMethodNode()).readBCI(frameInstance);
    }

    static final class Synchronized extends EspressoRootNode {

        Synchronized(FrameDescriptor frameDescriptor, EspressoMethodNode methodNode) {
            super(frameDescriptor, methodNode);
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
            if (isBytecodeNode()) {
                ((BytecodeNode) getMethodNode()).synchronizedMethodMonitorEnter(frame, monitor);
            } else {
                InterpreterToVM.monitorEnter(monitor);
            }
            Object result;
            try {
                result = methodNode.execute(frame);
            } finally {
                InterpreterToVM.monitorExit(monitor);
            }
            return result;
        }

        private EspressoMethodNode getMethodNode() {
            Node child = methodNode;
            if (child instanceof WrapperNode) {
                child = ((WrapperNode) child).getDelegateNode();
            }
            assert !(child instanceof WrapperNode);
            return (EspressoMethodNode) child;
        }
    }

    static final class Default extends EspressoRootNode {

        Default(FrameDescriptor frameDescriptor, EspressoMethodNode methodNode) {
            super(frameDescriptor, methodNode);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return methodNode.execute(frame);
        }
    }
}
