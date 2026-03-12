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
package com.oracle.svm.hosted.nodes;

import com.oracle.svm.core.ReservedRegisters;
import com.oracle.svm.core.graal.nodes.ReadReservedRegisterFixedNode;
import com.oracle.svm.core.graal.nodes.ReadReservedRegisterFloatingNode;

import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.vm.ci.code.Register;

public class ReadReservedRegister {

    public static ValueNode createReadStackPointerNode(StructuredGraph graph) {
        var rr = ReservedRegisters.singleton();
        return createReadNode(rr.mustUseFixedRead(graph), rr.getFrameRegister());
    }

    public static ValueNode createReadIsolateThreadNode(StructuredGraph graph) {
        var rr = ReservedRegisters.singleton();
        return createReadNode(rr.mustUseFixedRead(graph), rr.getThreadRegister());
    }

    public static ValueNode createReadHeapBaseNode(StructuredGraph graph) {
        var rr = ReservedRegisters.singleton();
        return createReadNode(rr.mustUseFixedRead(graph), rr.getHeapBaseRegister());
    }

    public static ValueNode createReadCodeBaseNode(StructuredGraph graph) {
        var rr = ReservedRegisters.singleton();
        return createReadNode(rr.mustUseFixedRead(graph), rr.getCodeBaseRegister());
    }

    private static ValueNode createReadNode(boolean useFixedRead, Register register) {
        if (useFixedRead) {
            return new ReadReservedRegisterFixedNode(register);
        } else {
            return new ReadReservedRegisterFloatingNode(register);
        }
    }
}
