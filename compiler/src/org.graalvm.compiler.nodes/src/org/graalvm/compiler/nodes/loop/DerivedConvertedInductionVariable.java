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
package org.graalvm.compiler.nodes.loop;

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.IntegerConvertNode;
import org.graalvm.compiler.nodes.calc.ZeroExtendNode;

public class DerivedConvertedInductionVariable extends DerivedInductionVariable {

    protected final Stamp stamp;
    protected final ValueNode value;

    public DerivedConvertedInductionVariable(LoopEx loop, InductionVariable base, Stamp stamp, ValueNode value) {
        super(loop, base);
        this.stamp = stamp;
        this.value = value;
    }

    @Override
    public ValueNode valueNode() {
        return value;
    }

    @Override
    public Direction direction() {
        return base.direction();
    }

    @Override
    public ValueNode initNode() {
        return op(base.initNode(), true);
    }

    @Override
    public ValueNode strideNode() {
        return op(base.strideNode(), false);
    }

    @Override
    public boolean isConstantInit() {
        return base.isConstantInit();
    }

    @Override
    public boolean isConstantStride() {
        return base.isConstantStride();
    }

    @Override
    public long constantInit() {
        return base.constantInit();
    }

    @Override
    public long constantStride() {
        return base.constantStride();
    }

    @Override
    public ValueNode extremumNode(boolean assumeLoopEntered, Stamp s) {
        return base.extremumNode(assumeLoopEntered, s);
    }

    @Override
    public ValueNode exitValueNode() {
        return op(base.exitValueNode(), true);
    }

    @Override
    public boolean isConstantExtremum() {
        return base.isConstantExtremum();
    }

    @Override
    public long constantExtremum() {
        return base.constantExtremum();
    }

    @Override
    public void deleteUnusedNodes() {
    }

    public ValueNode op(ValueNode v, boolean allowZeroExtend) {
        return op(v, allowZeroExtend, true);
    }

    private ValueNode op(ValueNode v, boolean allowZeroExtend, boolean gvn) {
        boolean zeroExtend = allowZeroExtend && value instanceof ZeroExtendNode;
        return IntegerConvertNode.convert(v, stamp, zeroExtend, graph(), NodeView.DEFAULT, gvn);
    }

    @Override
    public String toString() {
        return String.format("DerivedConvertedInductionVariable base (%s) %s %s", base, value.getNodeClass().shortName(), stamp);
    }

    @Override
    public InductionVariable copy(InductionVariable newBase, ValueNode newValue) {
        return new DerivedConvertedInductionVariable(loop, newBase, stamp, newValue);
    }

    @Override
    public ValueNode copyValue(InductionVariable newBase) {
        return op(newBase.valueNode(), true);
    }

    @Override
    public ValueNode copyValue(InductionVariable newBase, boolean gvn) {
        return op(newBase.valueNode(), true, gvn);
    }

    @Override
    public ValueNode entryTripValue() {
        return op(getBase().entryTripValue(), true);
    }
}
