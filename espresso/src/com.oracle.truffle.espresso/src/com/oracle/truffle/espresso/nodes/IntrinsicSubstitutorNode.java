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

import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.perf.DebugCounter;
import com.oracle.truffle.espresso.substitutions.JavaSubstitution;

public final class IntrinsicSubstitutorNode extends EspressoMethodNode {
    @Child private JavaSubstitution substitution;

    @CompilerDirectives.CompilationFinal //
    int callState = 0;

    // Truffle does not want to report split on first call. Delay until the second.
    private final DebugCounter nbSplits;

    public IntrinsicSubstitutorNode(JavaSubstitution.Factory factory, Method method) {
        super(method.getMethodVersion());
        this.substitution = factory.create(getContext().getMeta());

        EspressoError.guarantee(!substitution.isTrivial() || !method.isSynchronized(),
                        "Substitution for synchronized method '%s' cannot be marked as trivial", method);

        if (substitution.shouldSplit()) {
            this.nbSplits = DebugCounter.create("Splits for: " + Arrays.toString(factory.getMethodNames()));
        } else {
            this.nbSplits = null;
        }
    }

    private IntrinsicSubstitutorNode(IntrinsicSubstitutorNode toSplit) {
        super(toSplit.getMethodVersion());
        assert toSplit.substitution.shouldSplit();
        this.substitution = toSplit.substitution.split();
        this.nbSplits = toSplit.nbSplits;
        this.callState = 3;
    }

    @Override
    void initializeBody(VirtualFrame frame) {
        // nop
    }

    @Override
    public Object executeBody(VirtualFrame frame) {
        if (CompilerDirectives.inInterpreter() && callState <= 1) {
            callState++;
        }
        if (CompilerDirectives.inInterpreter() && callState == 2 && !substitution.uninitialized()) {
            // Hints to the truffle runtime that it should split this node on every new call site
            reportPolymorphicSpecialize();
            callState = 3;
        }
        return substitution.invoke(frame.getArguments());
    }

    @Override
    public boolean shouldSplit() {
        return substitution.shouldSplit();
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
    public int getBci(@SuppressWarnings("unused") Frame frame) {
        return -2;
    }

    @Override
    protected boolean isTrivial() {
        return substitution.isTrivial();
    }
}
