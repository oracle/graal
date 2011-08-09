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
package com.oracle.max.graal.nodes.loop;

import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.base.*;
import com.oracle.max.graal.nodes.calc.*;
import com.oracle.max.graal.nodes.spi.*;
import com.sun.cri.ci.*;

public abstract class InductionVariableNode extends FloatingNode {

    public InductionVariableNode(CiKind kind, Graph graph) {
        super(kind, graph);
        assert kind.isInt() || kind.isLong();
    }

    public abstract LoopBeginNode loopBegin();

    public abstract void peelOneIteration();

    public void replaceAtNonIVUsages(Node other) {
        for (Node usage : this.usages().snapshot()) {
            if (!(usage instanceof InductionVariableNode)) {
                usage.replaceFirstInput(this, other);
            }
        }
    }

    @Override
    public void accept(ValueVisitor v) {
        // nop
    }
}
