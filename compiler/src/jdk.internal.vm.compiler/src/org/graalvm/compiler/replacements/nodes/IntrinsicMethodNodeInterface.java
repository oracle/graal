/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements.nodes;

import static org.graalvm.compiler.core.common.GraalOptions.InlineGraalStubs;

import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.core.common.spi.ForeignCallLinkage;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValueNodeInterface;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.Value;

/**
 * Mixin for nodes that represent an entire custom assembly method. These nodes can either emit the
 * method body or a foreign call to a stub containing the method body.
 */
public interface IntrinsicMethodNodeInterface extends ValueNodeInterface, LIRLowerable {

    StructuredGraph graph();

    ForeignCallDescriptor getForeignCallDescriptor();

    ValueNode[] getForeignCallArguments();

    @Override
    default void generate(NodeLIRBuilderTool gen) {
        if (!InlineGraalStubs.getValue(graph().getOptions())) {
            ForeignCallDescriptor foreignCallDescriptor = getForeignCallDescriptor();
            ForeignCallLinkage linkage = gen.lookupGraalStub(asNode(), foreignCallDescriptor);
            if (linkage != null) {
                ValueNode[] args = getForeignCallArguments();
                Value[] operands = new Value[args.length];
                for (int i = 0; i < args.length; i++) {
                    operands[i] = gen.operand(args[i]);
                }
                Value result = gen.getLIRGeneratorTool().emitForeignCall(linkage, null, operands);
                if (foreignCallDescriptor.getResultType() != void.class) {
                    gen.setResult(asNode(), result);
                }
                return;
            }
        }
        emitIntrinsic(gen);
    }

    /**
     * Emit the method body.
     */
    void emitIntrinsic(NodeLIRBuilderTool gen);
}
