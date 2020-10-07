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

package com.oracle.truffle.espresso.analysis.liveness;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.espresso.analysis.liveness.actions.MultiAction;
import com.oracle.truffle.espresso.nodes.BytecodeNode;

public class BCILocalActionRecord {
    @CompilerDirectives.CompilationFinal private LocalVariableAction pre;
    @CompilerDirectives.CompilationFinal private LocalVariableAction post;

    public void pre(VirtualFrame frame, BytecodeNode node) {
        if (pre == null) {
            return;
        }
        pre.execute(frame, node);
    }

    public void post(VirtualFrame frame, BytecodeNode node) {
        if (post == null) {
            return;
        }
        post.execute(frame, node);
    }

    public boolean hasMulti() {
        return (pre instanceof MultiAction.TempMultiAction) || (post instanceof MultiAction.TempMultiAction);
    }

    public void freeze() {
        LocalVariableAction newPre = pre instanceof MultiAction.TempMultiAction ? ((MultiAction.TempMultiAction) pre).freeze() : pre;
        LocalVariableAction newPost = post instanceof MultiAction.TempMultiAction ? ((MultiAction.TempMultiAction) post).freeze() : post;
        pre = newPre;
        post = newPost;
    }

    public void register(LocalVariableAction action, boolean preAction) {
        if (preAction) {
            pre = register(pre, action);
        } else {
            post = register(post, action);
        }
    }

    private LocalVariableAction register(LocalVariableAction old, LocalVariableAction action) {
        if (old == null) {
            return action;
        } else if (old instanceof MultiAction.TempMultiAction) {
            ((MultiAction.TempMultiAction) old).add(action);
            return old;
        } else {
            MultiAction.TempMultiAction multi = new MultiAction.TempMultiAction();
            multi.add(old);
            multi.add(action);
            return multi;
        }
    }

    @Override
    public String toString() {
        return (pre == null ? "" : pre.toString()) + " | " + (post == null ? "" : post.toString());
    }
}
