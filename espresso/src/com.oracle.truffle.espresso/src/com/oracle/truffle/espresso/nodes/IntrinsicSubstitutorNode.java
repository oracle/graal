/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Arrays;
import java.util.HashMap;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.NodeLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.espresso.EspressoScope;
import com.oracle.truffle.espresso.classfile.attributes.Local;
import com.oracle.truffle.espresso.descriptors.ByteSequence;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Utf8ConstantTable;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.perf.DebugCounter;
import com.oracle.truffle.espresso.substitutions.JavaSubstitution;
import com.oracle.truffle.espresso.vm.VM;

@ExportLibrary(NodeLibrary.class)
public final class IntrinsicSubstitutorNode extends EspressoInstrumentableRootNodeImpl {
    @Child private JavaSubstitution substitution;

    // Truffle does not want to report split on first call. Delay until the second.
    private final DebugCounter nbSplits;

    IntrinsicSubstitutorNode(Method.MethodVersion methodVersion, JavaSubstitution.Factory factory) {
        super(methodVersion);
        this.substitution = factory.create();

        EspressoError.guarantee(!substitution.isTrivial() || !methodVersion.isSynchronized(),
                        "Substitution for synchronized method cannot be marked as trivial", methodVersion);

        if (substitution.canSplit()) {
            this.nbSplits = DebugCounter.create("Splits for: " + Arrays.toString(factory.getMethodNames()));
        } else {
            this.nbSplits = null;
        }
    }

    private IntrinsicSubstitutorNode(IntrinsicSubstitutorNode toSplit) {
        super(toSplit.getMethodVersion());
        assert toSplit.substitution.canSplit();
        this.substitution = toSplit.substitution.split();
        this.nbSplits = toSplit.nbSplits;
    }

    @Override
    Object execute(VirtualFrame frame) {
        return substitution.invoke(frame.getArguments());
    }

    @Override
    public boolean canSplit() {
        return substitution.canSplit();
    }

    @Override
    public IntrinsicSubstitutorNode split() {
        nbSplits.inc();
        return new IntrinsicSubstitutorNode(this);
    }

    @Override
    public Node copy() {
        return split();
    }

    @Override
    boolean isTrivial() {
        return substitution.isTrivial();
    }

    @Override
    public int getBci(Frame frame) {
        if (getMethodVersion().isMethodNative()) {
            return VM.EspressoStackElement.NATIVE_BCI;
        } else {
            return 0;
        }
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public boolean hasScope(@SuppressWarnings("unused") Frame frame) {
        return true;
    }

    @ExportMessage
    public Object getScope(Frame frame, @SuppressWarnings("unused") boolean nodeEnter) {
        return getScopeSlowPath(frame != null ? frame.materialize() : null);
    }

    @TruffleBoundary
    private Object getScopeSlowPath(MaterializedFrame frame) {
        // Local variable information for an intrinsified Java method
        // consists of ´this´(if instance method) and all method arg locals
        Method method = getMethodVersion().getMethod();
        ArrayList<Local> constructedLiveLocals = new ArrayList<>();

        boolean hasReceiver = !method.isStatic();
        int localCount = hasReceiver ? 1 : 0;
        localCount += method.getParameterCount();

        Klass[] parameters = (Klass[]) method.getParameters();
        Utf8ConstantTable utf8Constants = method.getLanguage().getUtf8ConstantTable();
        int startslot = 0;

        if (hasReceiver) {
            // include 'this'
            constructedLiveLocals.add(new Local(utf8Constants.getOrCreate(Symbol.Name.thiz), utf8Constants.getOrCreate(method.getDeclaringKlass().getType()), 0, 65536, 0));
            startslot++;
        }

        // include method parameters
        for (int i = startslot; i < localCount; i++) {
            Klass param = hasReceiver ? parameters[i - 1] : parameters[i];
            constructedLiveLocals.add(new Local(utf8Constants.getOrCreate(ByteSequence.create("param_" + (i))), utf8Constants.getOrCreate(param.getType()), 0, 65536, i));
        }
        Local[] liveLocals = constructedLiveLocals.toArray(Local.EMPTY_ARRAY);
        return EspressoScope.createVariables(liveLocals, frame, method.getName());
    }
}
