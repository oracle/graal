/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.nodes;

import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;

import com.oracle.svm.common.meta.MultiMethod;
import com.oracle.svm.core.ReservedRegisters;

import jdk.vm.ci.code.Register;

public class ReadReservedRegister {

    public static ValueNode createReadStackPointerNode(StructuredGraph graph) {
        return createReadNode(graph, ReservedRegisters.singleton().getFrameRegister());
    }

    public static ValueNode createReadIsolateThreadNode(StructuredGraph graph) {
        return createReadNode(graph, ReservedRegisters.singleton().getThreadRegister());
    }

    public static ValueNode createReadHeapBaseNode(StructuredGraph graph) {
        return createReadNode(graph, ReservedRegisters.singleton().getHeapBaseRegister());
    }

    private static ValueNode createReadNode(StructuredGraph graph, Register register) {
        /*
         * A floating node to access the register is more efficient: it allows value numbering of
         * multiple accesses, including floating nodes that use it as an input. But for
         * deoptimization target methods, we must not do value numbering because there is no
         * proxying at deoptimization entry points for this node, so the value is not restored
         * during deoptimization.
         */
        if (MultiMethod.isDeoptTarget(graph.method())) {
            return new ReadReservedRegisterFixedNode(register);
        } else {
            return new ReadReservedRegisterFloatingNode(register);
        }
    }
}
