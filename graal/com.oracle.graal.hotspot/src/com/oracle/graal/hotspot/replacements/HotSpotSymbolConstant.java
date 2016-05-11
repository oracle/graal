/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.replacements;

import com.oracle.graal.debug.GraalError;
import com.oracle.graal.graph.Node.ConstantNodeParameter;
import com.oracle.graal.graph.Node.NodeIntrinsic;
import com.oracle.graal.hotspot.nodes.type.SymbolPointerStamp;
import com.oracle.graal.hotspot.word.SymbolPointer;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.graphbuilderconf.GraphBuilderContext;

import jdk.vm.ci.hotspot.HotSpotMemoryAccessProvider;
import jdk.vm.ci.hotspot.HotSpotMetaAccessProvider;
import jdk.vm.ci.hotspot.HotSpotResolvedObjectType;
import jdk.vm.ci.hotspot.HotSpotSymbol;
import jdk.vm.ci.hotspot.HotSpotVMConfig;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public final class HotSpotSymbolConstant {

    public static boolean intrinsify(GraphBuilderContext b, @SuppressWarnings("unused") ResolvedJavaMethod targetMethod, ResolvedJavaType symbol) {
        HotSpotMemoryAccessProvider memoryAccess = (HotSpotMemoryAccessProvider) b.getConstantReflection().getMemoryAccessProvider();
        HotSpotResolvedObjectType type = (HotSpotResolvedObjectType) symbol;
        Constant vmSymbol = memoryAccess.readSymbolConstant(type.klass(), HotSpotVMConfig.config().klassNameOffset);
        b.addPush(JavaKind.Object, new ConstantNode(vmSymbol, SymbolPointerStamp.symbol()));
        return true;
    }

    public static boolean intrinsify(GraphBuilderContext b, @SuppressWarnings("unused") ResolvedJavaMethod targetMethod, String symbol) {
        HotSpotMetaAccessProvider hsMeta = (HotSpotMetaAccessProvider) b.getMetaAccess();
        HotSpotSymbol vmSymbol = hsMeta.lookupSymbol(symbol);
        if (vmSymbol == null) {
            throw GraalError.shouldNotReachHere(String.format("VM symbol '%s' not found", symbol));
        }

        b.addPush(JavaKind.Object, new ConstantNode(vmSymbol.asConstant(), SymbolPointerStamp.symbolNonNull()));
        return true;
    }

    @NodeIntrinsic
    public static native SymbolPointer vmSymbol(@ConstantNodeParameter Class<?> symbol);

    @NodeIntrinsic
    public static native SymbolPointer vmSymbol(@ConstantNodeParameter String symbol);
}
