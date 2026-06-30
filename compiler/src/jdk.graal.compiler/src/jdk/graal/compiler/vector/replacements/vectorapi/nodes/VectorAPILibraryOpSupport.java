/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vector.replacements.vectorapi.nodes;

import org.graalvm.collections.EconomicMap;

import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.core.common.type.FloatStamp;
import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.vector.architecture.VectorArchitecture;
import jdk.graal.compiler.vector.nodes.simd.SimdStamp;
import jdk.vm.ci.meta.JavaKind;

/**
 * Shared support for VectorSupport library operation macros. The first macro argument is a native
 * entry address. Macros expand to HotSpot foreign calls with a known target address when the
 * address is a known constant.
 */
final class VectorAPILibraryOpSupport {

    private VectorAPILibraryOpSupport() {
    }

    /**
     * Checks that the macro has an exact species, a floating point vector stamp, matching vector
     * inputs, and a vector size supported for moves on the target.
     */
    static boolean canExpand(ObjectStamp speciesStamp, SimdStamp vectorStamp, VectorArchitecture vectorArch, EconomicMap<ValueNode, Stamp> simdStamps, ValueNode... vectors) {
        if (!speciesStamp.isExactType() || vectorStamp == null || !(vectorStamp.getComponent(0) instanceof FloatStamp)) {
            return false;
        }
        for (ValueNode vector : vectors) {
            Stamp inputStamp = simdStamps.get(vector);
            if (inputStamp == null || !inputStamp.isCompatible(vectorStamp)) {
                return false;
            }
        }
        Stamp elementStamp = vectorStamp.getComponent(0);
        int vectorLength = vectorStamp.getVectorLength();
        return vectorArch.getSupportedVectorMoveLength(elementStamp, vectorLength) == vectorLength;
    }

    /**
     * Try to compute an improved descriptor for a Vector API library call. If
     * {@code oldCallDescriptor} is not {@code null}, it is returned unchanged.
     */
    static ForeignCallDescriptor improveCallDescriptor(ForeignCallDescriptor oldCallDescriptor, ValueNode[] arguments, int addressIndex, int nameIndex, SimdStamp vectorStamp, int argumentCount,
                    CoreProviders providers) {
        if (oldCallDescriptor != null) {
            return oldCallDescriptor;
        }
        ValueNode address = arguments[addressIndex];
        ValueNode name = arguments[nameIndex];
        if (address.isJavaConstant() && address.asJavaConstant().getJavaKind() == JavaKind.Long && name.isJavaConstant() && vectorStamp != null &&
                        vectorStamp.getComponent(0) instanceof FloatStamp elementStamp) {
            long targetAddress = address.asJavaConstant().asLong();
            String targetName = providers.getSnippetReflection().asObject(String.class, name.asJavaConstant());
            if (targetName == null) {
                return null;
            }
            return providers.getForeignCalls().lookupVectorAPILibraryCall(targetName, targetAddress, vectorStamp.getVectorLength(), elementStamp.getStackKind(), argumentCount);
        }
        return null;
    }
}
