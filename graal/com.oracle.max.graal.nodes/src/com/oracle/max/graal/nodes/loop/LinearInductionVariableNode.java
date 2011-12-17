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
import com.oracle.max.graal.nodes.*;
import com.sun.cri.ci.*;

/**
 * InductionVariable of the form a*x+b.
 */
public abstract class LinearInductionVariableNode extends InductionVariableNode {

    @Input private ValueNode a;
    @Input private ValueNode b;

    public LinearInductionVariableNode(CiKind kind, ValueNode a, ValueNode b) {
        super(kind);
        this.a = a;
        this.b = b;
    }

    protected ValueNode a() {
        return a;
    }

    protected ValueNode b() {
        return b;
    }

    protected void setA(ValueNode a) {
        updateUsages(this.a, a);
        this.a = a;
    }

    protected void setB(ValueNode b) {
        updateUsages(this.b, b);
        this.b = b;
    }

    public boolean isLinearInductionVariableInput(Node n) {
        return n == a() || n == b();
    }
}
