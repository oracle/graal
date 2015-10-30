/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.replacements.nodes;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.LIRKind;
import jdk.vm.ci.meta.Value;

import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.lir.gen.LIRGeneratorTool;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.FixedWithNextNode;
import com.oracle.graal.nodes.StateSplit;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.extended.UnsafeStoreNode;
import com.oracle.graal.nodes.spi.LIRLowerable;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;

/**
 * A special purpose store node that differs from {@link UnsafeStoreNode} in that it is not a
 * {@link StateSplit} and takes a computed address instead of an object.
 */
@NodeInfo
public final class DirectReadNode extends FixedWithNextNode implements LIRLowerable {

    public static final NodeClass<DirectReadNode> TYPE = NodeClass.create(DirectReadNode.class);
    @Input protected ValueNode address;
    protected final JavaKind readKind;

    public DirectReadNode(ValueNode address, JavaKind readKind) {
        super(TYPE, StampFactory.forKind(readKind.getStackKind()));
        this.address = address;
        this.readKind = readKind;
    }

    protected ValueNode getAddress() {
        return address;
    }

    /**
     * If we are sub int sizes, we try to sign/zero extend the value to at least int as it is done
     * in the {@link com.oracle.graal.replacements.DefaultJavaLoweringProvider#implicitLoadConvert}
     * and {@link com.oracle.graal.replacements.DefaultJavaLoweringProvider#createUnsafeRead}.
     *
     * @see com.oracle.graal.replacements.DefaultJavaLoweringProvider#implicitLoadConvert
     * @see com.oracle.graal.replacements.DefaultJavaLoweringProvider#createUnsafeRead
     */
    @Override
    public void generate(NodeLIRBuilderTool builder) {
        LIRGeneratorTool gen = builder.getLIRGeneratorTool();
        LIRKind kind = gen.target().getLIRKind(readKind);
        Value loaded = gen.emitLoad(kind, builder.operand(address), null);
        switch (readKind) {
            case Byte:
                loaded = gen.getArithmetic().emitSignExtend(loaded, 8, 32);
                break;
            case Short:
                loaded = gen.getArithmetic().emitSignExtend(loaded, 16, 32);
                break;
            case Boolean:
                loaded = gen.getArithmetic().emitZeroExtend(loaded, 8, 32);
                break;
            case Char:
                loaded = gen.getArithmetic().emitZeroExtend(loaded, 16, 32);
                break;
        }
        builder.setResult(this, loaded);
    }
}
