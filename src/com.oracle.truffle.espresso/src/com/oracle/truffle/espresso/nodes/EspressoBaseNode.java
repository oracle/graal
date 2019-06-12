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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.espresso.impl.ContextAccess;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.vm.InterpreterToVM;

@GenerateWrapper
public abstract class EspressoBaseNode extends Node implements ContextAccess, InstrumentableNode {

    private final Method method;

    public final Method getMethod() {
        return method;
    }

    protected EspressoBaseNode(Method method) {
        this.method = method;
    }

    protected EspressoBaseNode(EspressoBaseNode copy) {
        this.method = copy.method;
    }

    @Override
    public EspressoContext getContext() {
        return method.getContext();
    }

    public abstract Object invokeNaked(VirtualFrame frame);

    public Object execute(VirtualFrame frame) {
        if (method.isSynchronized()) {
            Object monitor = method.isStatic()
                            ? /* class */ method.getDeclaringKlass().mirror()
                            : /* receiver */ frame.getArguments()[0];
            // No owner checks in SVM. Manual monitor accesses is a safeguard against unbalanced
            // monitor accesses until Espresso has its own monitor handling.
            //
            // synchronized (monitor) {
            InterpreterToVM.monitorEnter(monitor);
            Object result;
            try {
                result = invokeNaked(frame);
            } finally {
                InterpreterToVM.monitorExit(monitor);
            }
            return result;
            // }
        } else {
            return invokeNaked(frame);
        }
    }

    @TruffleBoundary
    @Override
    public SourceSection getSourceSection() {
        String methodName = method.getName().toString();
        Source source = Source.newBuilder("java", methodName, methodName).build();
        return source.createSection(1);
    }

    @Override
    public boolean isInstrumentable() {
        return true;
    }

    public WrapperNode createWrapper(ProbeNode probe) {
        return new EspressoBaseNodeWrapper(this, this, probe);
    }

    public boolean hasTag(Class<? extends Tag> tag) {
        return tag == StandardTags.RootTag.class;
    }

}
