/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.compiler.ir;

import com.oracle.max.graal.compiler.debug.*;
import com.oracle.max.graal.graph.*;
import com.sun.cri.ci.*;

public class LoopBegin extends Instruction {

    private static final int INPUT_COUNT = 0;
    private static final int SUCCESSOR_COUNT = 0;

    public LoopBegin(Graph graph) {
        super(CiKind.Illegal, INPUT_COUNT, SUCCESSOR_COUNT, graph);
    }

    public LoopEnd loopEnd() {
        for (Node usage : usages()) {
            if (usage instanceof LoopEnd) {
                LoopEnd end = (LoopEnd) usage;
                if (end.loopBegin() == this) {
                    return end;
                }
            }
        }
        assert false : "LoopBegin should always have a LoopEnd";
        return null;
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitLoopBegin(this);
    }

    @Override
    public void print(LogStream out) {
        out.print("loopBegin");
    }

    @Override
    public String shortName() {
        return "LoopBegin";
    }

    @Override
    public Node copy(Graph into) {
        LoopBegin x = new LoopBegin(into);
        return x;
    }
}
