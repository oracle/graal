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

import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.espresso.analysis.frame.EspressoFrameDescriptor.Builder;
import com.oracle.truffle.espresso.analysis.liveness.LocalVariableAction;
import com.oracle.truffle.espresso.nodes.EspressoFrame;

public final class MultiAction extends LocalVariableAction {
    @CompilationFinal(dimensions = 1) private final int[] actions;

    public MultiAction(int[] actions) {
        this.actions = actions;
    }

    @Override
    @ExplodeLoop
    public void execute(VirtualFrame frame) {
        for (int local : actions) {
            EspressoFrame.clearLocal(frame, local);
        }
    }

    @Override
    public void execute(Builder frame) {
        for (int local : actions) {
            frame.clear(local);
        }
    }

    @Override
    public String toString() {
        return Arrays.toString(actions);
    }

    @Override
    public LocalVariableAction merge(LocalVariableAction other) {
        int[] locals;
        if (other instanceof MultiAction) {
            MultiAction multi = (MultiAction) other;
            locals = new int[this.actions.length + multi.actions.length];
            System.arraycopy(this.actions, 0, locals, 0, this.actions.length);
            System.arraycopy(multi.actions, 0, locals, this.actions.length, multi.actions.length);
            return new MultiAction(locals);
        } else {
            assert other instanceof NullOutAction;
            locals = new int[this.actions.length + 1];
            System.arraycopy(this.actions, 0, locals, 0, this.actions.length);
            locals[locals.length - 1] = ((NullOutAction) other).local();
            return new MultiAction(locals);
        }
    }
}
