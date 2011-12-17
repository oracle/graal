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
package com.oracle.max.graal.nodes.extended;

import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.*;
import com.oracle.max.graal.nodes.java.*;
import com.oracle.max.graal.nodes.java.MethodCallTargetNode.*;
import com.oracle.max.graal.nodes.type.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;


public final class BoxNode extends AbstractStateSplit implements Node.IterableNodeType {

    @Input private ValueNode source;
    @Data private int bci;
    @Data private CiKind sourceKind;

    public BoxNode(ValueNode value, RiResolvedType type, CiKind sourceKind, int bci) {
        super(StampFactory.exactNonNull(type));
        this.source = value;
        this.bci = bci;
        this.sourceKind = sourceKind;
        assert value.kind() != CiKind.Object : "can only box from primitive type";
    }

    public ValueNode source() {
        return source;
    }


    public CiKind getSourceKind() {
        return sourceKind;
    }

    public void expand(BoxingMethodPool pool) {
        RiResolvedMethod boxingMethod = pool.getBoxingMethod(sourceKind);
        MethodCallTargetNode callTarget = graph().add(new MethodCallTargetNode(InvokeKind.Static, boxingMethod, new ValueNode[]{source}, boxingMethod.signature().returnType(boxingMethod.holder())));
        InvokeNode invokeNode = graph().add(new InvokeNode(callTarget, bci));
        invokeNode.setProbability(this.probability());
        invokeNode.setStateAfter(stateAfter());
        this.replaceWithFixedWithNext(invokeNode);
    }
}
