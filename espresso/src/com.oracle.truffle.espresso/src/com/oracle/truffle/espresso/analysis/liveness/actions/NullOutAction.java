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

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.espresso.analysis.liveness.LocalVariableAction;
import com.oracle.truffle.espresso.nodes.BytecodeNode;

public final class NullOutAction extends LocalVariableAction {
    private static final int MAX_CACHE = 256;

    private static final LocalVariableAction[] CACHE = new LocalVariableAction[MAX_CACHE];

    private final int local;

    public static LocalVariableAction get(int local) {
        if (local < MAX_CACHE) {
            LocalVariableAction res = CACHE[local];
            if (res == null) {
                synchronized (CACHE) {
                    res = CACHE[local];
                    if (res == null) {
                        res = CACHE[local] = new NullOutAction(local);
                    }
                }
            }
            return res;
        } else {
            return new NullOutAction(local);
        }
    }

    private NullOutAction(int local) {
        this.local = local;
    }

    @Override
    public void execute(VirtualFrame frame) {
        BytecodeNode.freeLocal(frame, local);
    }

    @Override
    public String toString() {
        return local + "";
    }

    @Override
    public LocalVariableAction merge(LocalVariableAction other) {
        if (other instanceof MultiAction) {
            return other.merge(this);
        }
        assert other instanceof NullOutAction;
        return new MultiAction(new int[]{this.local, ((NullOutAction) other).local});
    }

    public int local() {
        return local;
    }
}
