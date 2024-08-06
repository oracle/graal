/*
 * Copyright (c) 2009, 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_2;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_4;

import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.graal.compiler.nodes.memory.MemoryMapNode;

import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.JavaKind;

@NodeInfo(cycles = CYCLES_2, size = SIZE_4, cyclesRationale = "Restore frame + ret", sizeRationale = "Restore frame + ret")
public final class ReturnNode extends MemoryMapControlSinkNode implements LIRLowerable {

    public static final NodeClass<ReturnNode> TYPE = NodeClass.create(ReturnNode.class);
    @OptionalInput ValueNode result;

    public ValueNode result() {
        return result;
    }

    public ReturnNode(ValueNode result) {
        this(result, null);
    }

    public ReturnNode(ValueNode result, MemoryMapNode memoryMap) {
        super(TYPE, memoryMap);
        this.result = result;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        assert verifyReturn(gen.getLIRGeneratorTool().target());
        if (result == null) {
            gen.getLIRGeneratorTool().emitReturn(JavaKind.Void, null);
        } else {
            gen.getLIRGeneratorTool().emitReturn(result.getStackKind(), gen.operand(result));
        }
    }

    private boolean verifyReturn(TargetDescription target) {
        if (graph().method() != null) {
            JavaKind actual = result == null ? JavaKind.Void : result.getStackKind();
            JavaKind expected = graph().method().getSignature().getReturnKind().getStackKind();
            if (actual == target.wordJavaKind && expected == JavaKind.Object) {
                // OK, we're compiling a snippet that returns a Word
                return true;
            }
            assert actual == expected : "return kind doesn't match: actual " + actual + ", expected: " + expected;
        }
        return true;
    }
}
