/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.stubs;

import static org.graalvm.compiler.core.common.type.PrimitiveStamp.getBits;
import static org.graalvm.compiler.hotspot.meta.HotSpotHostForeignCallsProvider.INVOKE_STATIC_METHOD_ONE_ARG;

import org.graalvm.compiler.core.common.type.FloatStamp;
import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.PrimitiveStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.hotspot.meta.HotSpotForeignCallDescriptor;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.hotspot.nodes.StubForeignCallNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.ReinterpretNode;
import org.graalvm.compiler.nodes.calc.ZeroExtendNode;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.replacements.GraphKit;
import org.graalvm.compiler.replacements.nodes.ReadRegisterNode;
import org.graalvm.compiler.word.WordCastNode;

import jdk.vm.ci.code.site.ConstantReference;
import jdk.vm.ci.code.site.DataPatch;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotMetaspaceConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * A stub for invoking a Java method out of line from the surrounding Java bytecode.
 */
public class InvokeJavaMethodStub extends AbstractForeignCallStub {

    private final ResolvedJavaMethod javaMethod;

    /**
     * Creates a stub for a call to code at a given address.
     *
     * @param address the address of the code to call
     * @param descriptor the signature of the call to this stub
     * @param staticMethod the Java method to be invoked by HotSpot
     */
    public InvokeJavaMethodStub(OptionValues options, HotSpotJVMCIRuntime runtime, HotSpotProviders providers, long address, HotSpotForeignCallDescriptor descriptor,
                    ResolvedJavaMethod staticMethod) {
        super(options, runtime, providers, address, descriptor, true);
        this.javaMethod = staticMethod;
        // This stub is compiled to kill no registers which interferes with returning float and
        // double through a long. For simplicity disallow that case until it's required.
        GraalError.guarantee(!javaMethod.getSignature().getReturnKind().isNumericFloat(), "float/double returns don't work with register save/restore logic: %s", javaMethod);
    }

    @Override
    protected HotSpotForeignCallDescriptor getTargetSignature(HotSpotForeignCallDescriptor descriptor) {
        return INVOKE_STATIC_METHOD_ONE_ARG;
    }

    @Override
    protected boolean returnsObject() {
        return javaMethod.getSignature().getReturnKind().isObject();
    }

    @Override
    protected boolean shouldClearException() {
        return false;
    }

    @Override
    protected ValueNode createTargetCall(GraphKit kit, ReadRegisterNode thread) {
        ParameterNode[] params = createParameters(kit);
        ValueNode[] targetArguments = new ValueNode[2 + params.length];
        targetArguments[0] = thread;
        targetArguments[1] = ConstantNode.forConstant(providers.getStampProvider().createMethodStamp(), javaMethod.getEncoding(), providers.getMetaAccess(), kit.getGraph());
        if (params.length == 0) {
            targetArguments[2] = ConstantNode.defaultForKind(JavaKind.Long, kit.getGraph());
        } else {
            // Repack the value into a Java long
            ValueNode value = params[0];
            Stamp valueStamp = value.stamp(NodeView.DEFAULT);
            if (valueStamp instanceof ObjectStamp) {
                value = WordCastNode.objectToUntrackedPointer(value, JavaKind.Long);
                kit.append(value);
            } else if (valueStamp instanceof PrimitiveStamp) {
                if (valueStamp instanceof FloatStamp) {
                    // Convert float to integer
                    value = ReinterpretNode.create(valueStamp.getStackKind() == JavaKind.Float ? JavaKind.Int : JavaKind.Long, value, NodeView.DEFAULT);
                }
                int bits = getBits(valueStamp);
                if (bits != 0 && bits < JavaKind.Long.getBitCount()) {
                    // The VM will narrow these values to their expected size so simply zero extend
                    // to the required bits.
                    value = new ZeroExtendNode(value, JavaKind.Long.getBitCount());
                }
            }
            targetArguments[2] = value;
        }
        assert INVOKE_STATIC_METHOD_ONE_ARG.getResultType() == long.class;
        Stamp returnStamp = StampFactory.forKind(JavaKind.Long);
        ValueNode result = kit.append(new StubForeignCallNode(providers.getForeignCalls(), returnStamp, INVOKE_STATIC_METHOD_ONE_ARG, targetArguments));
        return result;
    }

    @Override
    protected void checkSafeDataReference(DataPatch data) {
        if (data.reference instanceof ConstantReference) {
            ConstantReference reference = (ConstantReference) data.reference;
            if (reference.getConstant() instanceof HotSpotMetaspaceConstant) {
                HotSpotMetaspaceConstant meta = (HotSpotMetaspaceConstant) reference.getConstant();
                if (javaMethod.equals(meta.asResolvedJavaMethod())) {
                    // Permit direct metadata reference to the target method since metadata doesn't
                    // move and the ResolvedJavaMethod instance will keep it alive.
                    return;
                }
            }
        }
        super.checkSafeDataReference(data);
    }
}
