/*
 * Copyright (c) 2012, 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.compiler.graal.hotspot.stubs;

import jdk.compiler.graal.core.common.LIRKind;
import jdk.compiler.graal.core.common.spi.ForeignCallDescriptor;
import jdk.compiler.graal.core.common.type.Stamp;
import jdk.compiler.graal.core.common.type.StampFactory;
import jdk.compiler.graal.hotspot.HotSpotForeignCallLinkage;
import jdk.compiler.graal.hotspot.meta.HotSpotForeignCallDescriptor;
import jdk.compiler.graal.hotspot.meta.HotSpotProviders;
import jdk.compiler.graal.hotspot.nodes.StubForeignCallNode;
import jdk.compiler.graal.nodes.ParameterNode;
import jdk.compiler.graal.nodes.ValueNode;
import jdk.compiler.graal.options.OptionValues;
import jdk.compiler.graal.replacements.GraphKit;
import jdk.compiler.graal.replacements.nodes.ReadRegisterNode;
import jdk.compiler.graal.word.Word;

import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.meta.JavaKind;

/**
 * A {@linkplain #getGraph generated} stub for a {@link HotSpotForeignCallDescriptor.Transition
 * non-leaf} foreign call from compiled code. A stub is required for such calls as the caller may be
 * scheduled for deoptimization while the call is in progress. And since these are foreign/runtime
 * calls on slow paths, we don't want to force the register allocator to spill around the call. As
 * such, this stub saves and restores all allocatable registers. It also
 * {@linkplain ForeignCallSnippets#handlePendingException handles} any exceptions raised during the
 * foreign call.
 */
public class ForeignCallStub extends AbstractForeignCallStub {

    /**
     * Creates a stub for a call to code at a given address.
     *
     * @param address the address of the code to call
     * @param descriptor the signature of the call to this stub
     * @param prependThread true if the JavaThread value for the current thread is to be prepended
     *            to the arguments for the call to {@code address}
     */
    public ForeignCallStub(OptionValues options, HotSpotJVMCIRuntime runtime, HotSpotProviders providers, long address, HotSpotForeignCallDescriptor descriptor, boolean prependThread) {
        super(options, runtime, providers, address, descriptor, HotSpotForeignCallLinkage.RegisterEffect.COMPUTES_REGISTERS_KILLED, prependThread);
    }

    public ForeignCallStub(OptionValues options, HotSpotJVMCIRuntime runtime, HotSpotProviders providers, long address, HotSpotForeignCallDescriptor descriptor, boolean prependThread,
                    HotSpotForeignCallLinkage.RegisterEffect effect) {
        super(options, runtime, providers, address, descriptor, effect, prependThread);
    }

    @Override
    protected HotSpotForeignCallDescriptor getTargetSignature(HotSpotForeignCallDescriptor descriptor) {
        Class<?>[] targetParameterTypes = createTargetParameters(descriptor);
        HotSpotForeignCallDescriptor targetSig = new HotSpotForeignCallDescriptor(descriptor.getTransition(), descriptor.getReexecutability(), descriptor.getKilledLocations(),
                        descriptor.getName() + ":C", descriptor.getResultType(), targetParameterTypes);
        return targetSig;
    }

    private Class<?>[] createTargetParameters(ForeignCallDescriptor descriptor) {
        Class<?>[] parameters = descriptor.getArgumentTypes();
        if (prependThread) {
            Class<?>[] newParameters = new Class<?>[parameters.length + 1];
            System.arraycopy(parameters, 0, newParameters, 1, parameters.length);
            newParameters[0] = Word.class;
            return newParameters;
        }
        return parameters;
    }

    @Override
    protected boolean returnsObject() {
        return !LIRKind.isValue(linkage.getOutgoingCallingConvention().getReturn());
    }

    @Override
    protected boolean shouldClearException() {
        return linkage.getDescriptor().isReexecutable();
    }

    @Override
    protected ValueNode createTargetCall(GraphKit kit, ReadRegisterNode thread) {
        ParameterNode[] params = createParameters(kit);
        Stamp stamp = StampFactory.forKind(JavaKind.fromJavaClass(target.getDescriptor().getResultType()));
        if (prependThread) {
            ValueNode[] targetArguments = new ValueNode[1 + params.length];
            targetArguments[0] = thread;
            System.arraycopy(params, 0, targetArguments, 1, params.length);
            return kit.append(new StubForeignCallNode(providers.getForeignCalls(), stamp, target.getDescriptor(), targetArguments));
        } else {
            return kit.append(new StubForeignCallNode(providers.getForeignCalls(), stamp, target.getDescriptor(), params));
        }
    }
}
