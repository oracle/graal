/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.interop.NodeLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.espresso.EspressoScope;
import com.oracle.truffle.espresso.classfile.attributes.Local;
import com.oracle.truffle.espresso.descriptors.ByteSequence;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Utf8ConstantTable;
import com.oracle.truffle.espresso.impl.ContextAccess;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.runtime.EspressoContext;

@GenerateWrapper
@ExportLibrary(NodeLibrary.class)
public abstract class EspressoInstrumentableNode extends Node implements BciProvider, InstrumentableNode, ContextAccess {

    public abstract Object execute(VirtualFrame frame);

    public abstract Method getMethod();

    @Override
    public final EspressoContext getContext() {
        /*
         * WARNING: this returns the **current**, thread-local, context; not a context associated
         * with this node.
         */
        return EspressoContext.get(this);
    }

    @Override
    public final boolean isInstrumentable() {
        return true;
    }

    @Override
    public WrapperNode createWrapper(ProbeNode probeNode) {
        return new EspressoInstrumentableNodeWrapper(this, probeNode);
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public final boolean hasScope(@SuppressWarnings("unused") Frame frame) {
        return true;
    }

    @ExportMessage
    @TruffleBoundary
    @SuppressWarnings("static-method")
    public final Object getScope(Frame frame, @SuppressWarnings("unused") boolean nodeEnter) {
        // construct the current scope with valid local variables information
        Method method = getMethod();
        Local[] liveLocals = method.getLocalVariableTable().getLocalsAt(getBci(frame));
        if (liveLocals.length == 0) {
            // class was compiled without a local variable table
            // include "this" in method arguments throughout the method
            boolean hasReceiver = !method.isStatic();
            int localCount = hasReceiver ? 1 : 0;
            localCount += method.getParameterCount();
            liveLocals = new Local[localCount];
            Klass[] parameters = (Klass[]) method.getParameters();
            Utf8ConstantTable utf8Constants = getContext().getLanguage().getUtf8ConstantTable();
            int startslot = 0;

            if (hasReceiver) {
                // include 'this' and method arguments
                liveLocals[0] = new Local(utf8Constants.getOrCreate(Symbol.Name.thiz), utf8Constants.getOrCreate(method.getDeclaringKlass().getType()), 0, 65536, 0);
                startslot++;
            }

            // include method parameters
            for (int i = startslot; i < localCount; i++) {
                Klass param = hasReceiver ? parameters[i - 1] : parameters[i];
                liveLocals[i] = new Local(utf8Constants.getOrCreate(ByteSequence.create("param_" + (i))), utf8Constants.getOrCreate(param.getType()), 0, 65536, i);
            }
        }
        return EspressoScope.createVariables(liveLocals, frame, method.getName());
    }
}
