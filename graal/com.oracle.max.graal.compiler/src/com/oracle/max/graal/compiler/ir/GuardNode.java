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

import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.debug.*;
import com.oracle.max.graal.compiler.phases.CanonicalizerPhase.NotifyReProcess;
import com.oracle.max.graal.compiler.phases.CanonicalizerPhase.*;
import com.oracle.max.graal.graph.*;
import com.sun.cri.ci.*;

public final class GuardNode extends FloatingNode implements Canonicalizable {

    @Input private FixedNode anchor;
    @Input private BooleanNode node;

    public FixedNode anchor() {
        return anchor;
    }

    public void setAnchor(FixedNode x) {
        updateUsages(anchor, x);
        anchor = x;
    }

    /**
     * The instruction that produces the tested boolean value.
     */
    public BooleanNode node() {
        return node;
    }

    public void setNode(BooleanNode x) {
        updateUsages(node, x);
        node = x;
    }

    public GuardNode(BooleanNode node, Graph graph) {
        super(CiKind.Illegal, graph);
        setNode(node);
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitGuardNode(this);
    }

    @Override
    public void print(LogStream out) {
        out.print("guard node ").print(node());
    }

    @Override
    public Node canonical(NotifyReProcess reProcess) {
        if (node() instanceof Constant) {
            Constant c = (Constant) node();
            if (c.asConstant().asBoolean()) {
                if (GraalOptions.TraceCanonicalizer) {
                    TTY.println("Removing redundant floating guard " + this);
                }
                return Node.Null;
            }
        }
        return this;
    }
}
