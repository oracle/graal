/*
 * Copyright (c) 2015, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.graal.compiler.nodes.loop;

import jdk.graal.compiler.core.common.util.CompilationAlarm;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;

/**
 * Base class of the derived induction variables.
 */
public abstract class DerivedInductionVariable extends InductionVariable {

    protected final InductionVariable base;

    public DerivedInductionVariable(Loop loop, InductionVariable base) {
        super(loop);
        this.base = base;
    }

    @Override
    public StructuredGraph graph() {
        return base.graph();
    }

    public InductionVariable getBase() {
        CompilationAlarm.checkProgress(base.graph());
        return base;
    }

    @Override
    public InductionVariable duplicate() {
        InductionVariable newBase = base.duplicate();
        return copy(newBase, copyValue(newBase, false/* no gvn */));
    }

    @Override
    public InductionVariable duplicateWithNewInit(ValueNode newInit) {
        InductionVariable newBase = base.duplicateWithNewInit(newInit);
        return copy(newBase, copyValue(newBase, false/* no gvn */));
    }

    public abstract ValueNode copyValue(InductionVariable newBase);

    public abstract ValueNode copyValue(InductionVariable newBase, boolean gvn);

    public abstract InductionVariable copy(InductionVariable newBase, ValueNode newValue);
}
