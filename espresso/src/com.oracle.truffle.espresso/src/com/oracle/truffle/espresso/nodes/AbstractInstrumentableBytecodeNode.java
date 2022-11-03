/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.HashMap;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.interop.NodeLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.espresso.EspressoScope;
import com.oracle.truffle.espresso.classfile.attributes.Local;
import com.oracle.truffle.espresso.descriptors.ByteSequence;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Utf8ConstantTable;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;

@GenerateWrapper
@ExportLibrary(NodeLibrary.class)
abstract class AbstractInstrumentableBytecodeNode extends EspressoInstrumentableNode {

    abstract Object execute(VirtualFrame frame);

    abstract void initializeFrame(VirtualFrame frame);

    abstract Method.MethodVersion getMethodVersion();

    public final Method getMethod() {
        return getMethodVersion().getMethod();
    }

    protected abstract boolean isTrivial();

    @Override
    public WrapperNode createWrapper(ProbeNode probeNode) {
        return new AbstractInstrumentableBytecodeNodeWrapper(this, probeNode);
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public final boolean hasScope(@SuppressWarnings("unused") Frame frame) {
        return true;
    }

    @ExportMessage
    public final Object getScope(Frame frame, @SuppressWarnings("unused") boolean nodeEnter) {
        return getScopeSlowPath(frame != null ? frame.materialize() : null);
    }

    @TruffleBoundary
    private Object getScopeSlowPath(MaterializedFrame frame) {
        // construct the current scope with valid local variables information
        Method method = getMethodVersion().getMethod();
        Local[] liveLocals = method.getLocalVariableTable().getLocalsAt(getBci(frame));
        boolean allParamsIncluded = checkLocals(liveLocals, method);
        if (!allParamsIncluded) {
            ArrayList<Local> constructedLiveLocals = new ArrayList<>();
            HashMap<Integer, Local> slotToLocal = new HashMap<>(liveLocals.length);
            for (Local liveLocal : liveLocals) {
                slotToLocal.put(liveLocal.getSlot(), liveLocal);
            }
            // class was compiled without a full local variable table
            // include "this" in method arguments throughout the method
            boolean hasReceiver = !method.isStatic();
            int localCount = hasReceiver ? 1 : 0;
            localCount += method.getParameterCount();

            Klass[] parameters = (Klass[]) method.getParameters();
            Utf8ConstantTable utf8Constants = getContext().getLanguage().getUtf8ConstantTable();
            int startslot = 0;

            if (hasReceiver) {
                // include 'this' and method arguments if not already included
                if (!slotToLocal.containsKey(startslot)) {
                    constructedLiveLocals.add(new Local(utf8Constants.getOrCreate(Symbol.Name.thiz), utf8Constants.getOrCreate(method.getDeclaringKlass().getType()), 0, 65536, 0));
                } else {
                    constructedLiveLocals.add(slotToLocal.get(startslot));
                }
                slotToLocal.remove(startslot);
                startslot++;
            }

            // include method parameters if not already included
            for (int i = startslot; i < localCount; i++) {
                Klass param = hasReceiver ? parameters[i - 1] : parameters[i];
                if (!slotToLocal.containsKey(i)) {
                    constructedLiveLocals.add(new Local(utf8Constants.getOrCreate(ByteSequence.create("param_" + (i))), utf8Constants.getOrCreate(param.getType()), 0, 65536, i));
                    slotToLocal.remove(i);
                }
            }
            for (Local local : slotToLocal.values()) {
                // add non-parameters last
                constructedLiveLocals.add(local);
            }
            liveLocals = constructedLiveLocals.toArray(new Local[constructedLiveLocals.size()]);
        }
        return EspressoScope.createVariables(liveLocals, frame, method.getName());
    }

    private static boolean checkLocals(Local[] liveLocals, Method method) {
        if (liveLocals.length == 0) {
            return false;
        }
        int expectedParameterSlots = !method.isStatic() ? 1 + method.getParameterCount() : method.getParameterCount();
        boolean[] localPresent = new boolean[expectedParameterSlots];
        if (liveLocals.length < expectedParameterSlots) {
            return false;
        }
        for (Local liveLocal : liveLocals) {
            if (liveLocal.getSlot() < expectedParameterSlots) {
                localPresent[liveLocal.getSlot()] = true;
            }
        }
        for (boolean present : localPresent) {
            if (!present) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean hasTag(Class<? extends Tag> tag) {
        return tag == StandardTags.RootBodyTag.class;
    }
}
