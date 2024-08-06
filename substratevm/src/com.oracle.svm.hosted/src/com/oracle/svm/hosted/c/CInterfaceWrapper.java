/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.c;

import com.oracle.svm.core.graal.replacements.SubstrateGraphKit;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.memory.address.OffsetAddressNode;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * This wrapper exposes an interface that can be used to insert additional code around native
 * accesses. For reads and writes, the CInterfaceReadNode and CInterfaceWriteNode can be
 * conditionally replaced by another node. For function calls and entry points, the prologue and
 * epilogue tagging functions can be used to add code before and after the method invocation.
 */
public interface CInterfaceWrapper {
    /**
     * This method is called in
     * {@link com.oracle.svm.hosted.phases.CInterfaceInvocationPlugin#readPrimitive}. To indicate
     * that the CInterfaceReadNode should be replaced, the callee adds the new node to the graph and
     * returns it.
     *
     * @param b the context
     * @param address base and offset for the primitive read
     * @param stamp the type of the read
     * @param method the original read method for the primitive object
     * @return the node added to the graph in place of the CInterfaceReadNode, null otherwise.
     */
    ValueNode replacePrimitiveRead(GraphBuilderContext b, OffsetAddressNode address, Stamp stamp, ResolvedJavaMethod method);

    /**
     * This method is called in
     * {@link com.oracle.svm.hosted.phases.CInterfaceInvocationPlugin#writePrimitive}. To indicate
     * that the CInterfaceWriteNode should be replaced, the callee adds the new node to the graph
     * and returns true.
     *
     * @param b the context
     * @param address base and offset for the primitive write
     * @param value the value to be written
     * @param method the original write method for the primitive object
     * @return true if the node should be replaced, false otherwise.
     */
    boolean replacePrimitiveWrite(GraphBuilderContext b, OffsetAddressNode address, ValueNode value, ResolvedJavaMethod method);

    void tagCEntryPointPrologue(SubstrateGraphKit kit, ResolvedJavaMethod method);

    void tagCEntryPointEpilogue(SubstrateGraphKit kit, ResolvedJavaMethod method);

    void tagCFunctionCallPrologue(SubstrateGraphKit kit, ResolvedJavaMethod method);

    void tagCFunctionCallEpilogue(SubstrateGraphKit kit, ResolvedJavaMethod method);
}
