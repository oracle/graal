/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.NodeLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.espresso.classfile.perf.DebugCounter;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.runtime.EspressoThreadLocalState;
import com.oracle.truffle.espresso.substitutions.JavaSubstitution;
import com.oracle.truffle.espresso.threads.ThreadState;
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
        EspressoThreadLocalState tls = getContext().getLanguage().getThreadLocalState();
        tls.blockContinuationSuspension();
        try {
            // We consider substitutions non-native, as they are in Espresso's control.
            assert ThreadState.currentThreadInEspresso(getContext());
            return substitution.invoke(frame.getArguments());
        } finally {
            tls.unblockContinuationSuspension();
        }
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
        if (getMethodVersion().getMethod().isMethodNative()) {
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
    @SuppressWarnings("static-method")
    public Object getScope(Frame frame, @SuppressWarnings("unused") boolean nodeEnter) {
        return new SubstitutionScope(frame.getArguments(), getMethodVersion());
    }
}
