/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.analysis.liveness.actions;

import java.util.ArrayList;
import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.espresso.analysis.Util;
import com.oracle.truffle.espresso.analysis.liveness.LocalVariableAction;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.nodes.BytecodeNode;

public final class MultiAction extends LocalVariableAction {
    @CompilerDirectives.CompilationFinal(dimensions = 1) private final int[] actions;

    public MultiAction(int[] actions) {
        this.actions = actions;
    }

    @Override
    @ExplodeLoop
    public void execute(VirtualFrame frame, BytecodeNode node) {
        for (int local : actions) {
            node.nullOutLocalObject(frame, local);
        }
    }

    @Override
    public String toString() {
        return Arrays.toString(actions);
    }

    public static class TempMultiAction extends LocalVariableAction {
        private final ArrayList<Integer> actions;

        @Override
        public void execute(VirtualFrame frame, BytecodeNode node) {
            throw EspressoError.shouldNotReachHere();
        }

        public void add(LocalVariableAction action) {
            assert action instanceof NullOutAction;
            actions.add(((NullOutAction) action).local());
        }

        public TempMultiAction() {
            this.actions = new ArrayList<>();
        }

        public TempMultiAction(ArrayList<LocalVariableAction> actions) {
            this.actions = new ArrayList<>();
            for (LocalVariableAction action : actions) {
                add(action);
            }
        }

        public MultiAction freeze() {
            return new MultiAction(Util.toIntArray(actions));
        }
    }
}
