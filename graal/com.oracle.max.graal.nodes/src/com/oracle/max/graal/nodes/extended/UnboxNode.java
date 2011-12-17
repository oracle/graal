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
import com.oracle.max.graal.nodes.spi.*;
import com.oracle.max.graal.nodes.type.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;


public final class UnboxNode extends FixedWithNextNode implements Node.IterableNodeType, Canonicalizable {

    @Input private ValueNode source;

    public UnboxNode(CiKind kind, ValueNode source) {
        super(StampFactory.forKind(kind));
        this.source = source;
        assert kind != CiKind.Object : "can only unbox to primitive";
        assert source.kind() == CiKind.Object : "can only unbox objects";
    }

    public ValueNode source() {
        return source;
    }

    public CiKind destinationKind() {
        return this.kind();
    }

    public void expand(BoxingMethodPool pool) {
        RiResolvedField field = pool.getBoxField(kind());
        LoadFieldNode loadField = graph().add(new LoadFieldNode(source, field));
        loadField.setProbability(probability());
        this.replaceWithFixedWithNext(loadField);
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (source.isConstant()) {
            CiConstant constant = source.asConstant();
            Object o = constant.asObject();
            switch (kind()) {
                case Boolean:
                    return ConstantNode.forBoolean((Boolean) o, graph());
                case Byte:
                    return ConstantNode.forByte((Byte) o, graph());
                case Char:
                    return ConstantNode.forChar((Character) o, graph());
                case Short:
                    return ConstantNode.forShort((Short) o, graph());
                case Int:
                    return ConstantNode.forInt((Integer) o, graph());
                case Long:
                    return ConstantNode.forLong((Long) o, graph());
                case Float:
                    return ConstantNode.forFloat((Long) o, graph());
                case Double:
                    return ConstantNode.forDouble((Long) o, graph());
                default:
                    assert false;
            }
        }
        return this;
    }
}
