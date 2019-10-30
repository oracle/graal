/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.espresso.impl.ContextAccess;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.runtime.EspressoContext;

/**
 * The root of all executable bits in Espresso, includes everything that can be called a "method" in
 * Java. Regular (concrete) Java methods, native methods and intrinsics/substitutions.
 */
public final class EspressoRootNode extends RootNode implements ContextAccess {
    private final Method method;

    @Child EspressoBaseNode childNode;

    public final Method getMethod() {
        return method;
    }

    public EspressoRootNode(Method method, EspressoBaseNode childNode) {
        super(method.getEspressoLanguage());
        this.method = method;
        this.childNode = childNode;
    }

    public EspressoRootNode(Method method, FrameDescriptor frameDescriptor, EspressoBaseNode childNode) {
        super(method.getEspressoLanguage(), frameDescriptor);
        this.method = method;
        this.childNode = childNode;
    }

    @Override
    public EspressoContext getContext() {
        return method.getContext();
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return childNode.execute(frame);
    }

    @Override
    public String getName() {
        return getMethod().getDeclaringKlass().getType() + "." + getMethod().getName() + getMethod().getRawSignature();
    }

    @Override
    public String toString() {
        return getName();
    }

    @Override
    public SourceSection getSourceSection() {
        return childNode.getSourceSection();
    }

    public boolean isBytecodeNode() {
        return childNode instanceof BytecodeNode;
    }

    public int readBCI(FrameInstance frameInstance) {
        assert childNode instanceof BytecodeNode;
        return ((BytecodeNode) childNode).readBCI(frameInstance);
    }
}
