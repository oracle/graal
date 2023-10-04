/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.compiler.graal.hotspot.amd64;

import static jdk.vm.ci.amd64.AMD64.rax;
import static jdk.vm.ci.amd64.AMD64.rdx;
import static jdk.vm.ci.meta.Value.ILLEGAL;
import static jdk.compiler.graal.replacements.nodes.BinaryMathIntrinsicNode.BinaryOperation.POW;
import static jdk.compiler.graal.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.COS;
import static jdk.compiler.graal.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.EXP;
import static jdk.compiler.graal.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.LOG;
import static jdk.compiler.graal.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.LOG10;
import static jdk.compiler.graal.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.SIN;
import static jdk.compiler.graal.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.TAN;

import jdk.compiler.graal.core.common.LIRKind;
import jdk.compiler.graal.hotspot.GraalHotSpotVMConfig;
import jdk.compiler.graal.hotspot.HotSpotBackend;
import jdk.compiler.graal.hotspot.HotSpotForeignCallLinkage;
import jdk.compiler.graal.hotspot.HotSpotForeignCallLinkageImpl;
import jdk.compiler.graal.hotspot.HotSpotGraalRuntimeProvider;
import jdk.compiler.graal.hotspot.meta.HotSpotForeignCallDescriptor;
import jdk.compiler.graal.hotspot.meta.HotSpotHostForeignCallsProvider;
import jdk.compiler.graal.hotspot.meta.HotSpotProviders;
import jdk.compiler.graal.hotspot.stubs.IntrinsicStubsGen;
import jdk.compiler.graal.options.OptionValues;
import jdk.compiler.graal.replacements.nodes.ArrayEqualsForeignCalls;
import jdk.compiler.graal.replacements.nodes.VectorizedHashCodeNode;
import jdk.compiler.graal.word.WordTypes;

import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.Value;

public class AMD64HotSpotForeignCallsProvider extends HotSpotHostForeignCallsProvider {

    private final Value[] nativeABICallerSaveRegisters;

    public AMD64HotSpotForeignCallsProvider(HotSpotJVMCIRuntime jvmciRuntime, HotSpotGraalRuntimeProvider runtime, MetaAccessProvider metaAccess, CodeCacheProvider codeCache,
                    WordTypes wordTypes, Value[] nativeABICallerSaveRegisters) {
        super(jvmciRuntime, runtime, metaAccess, codeCache, wordTypes);
        this.nativeABICallerSaveRegisters = nativeABICallerSaveRegisters;
    }

    @Override
    public void initialize(HotSpotProviders providers, OptionValues options) {
        TargetDescription target = providers.getCodeCache().getTarget();
        PlatformKind word = target.arch.getWordKind();

        // The calling convention for the exception handler stub is (only?) defined in
        // TemplateInterpreterGenerator::generate_throw_exception()
        // in templateInterpreter_x86_64.cpp around line 1923
        RegisterValue exception = rax.asValue(LIRKind.reference(word));
        RegisterValue exceptionPc = rdx.asValue(LIRKind.value(word));
        CallingConvention exceptionCc = new CallingConvention(0, ILLEGAL, exception, exceptionPc);
        register(new HotSpotForeignCallLinkageImpl(HotSpotBackend.EXCEPTION_HANDLER, 0L, HotSpotForeignCallLinkage.RegisterEffect.DESTROYS_ALL_CALLER_SAVE_REGISTERS, exceptionCc, null));
        register(new HotSpotForeignCallLinkageImpl(HotSpotBackend.EXCEPTION_HANDLER_IN_CALLER, HotSpotForeignCallLinkage.JUMP_ADDRESS,
                        HotSpotForeignCallLinkage.RegisterEffect.DESTROYS_ALL_CALLER_SAVE_REGISTERS, exceptionCc, null));

        linkSnippetStubs(providers, options, IntrinsicStubsGen::new, ArrayEqualsForeignCalls.STUBS_AMD64);
        linkSnippetStubs(providers, options, IntrinsicStubsGen::new, VectorizedHashCodeNode.STUBS);

        super.initialize(providers, options);
    }

    @Override
    public Value[] getNativeABICallerSaveRegisters() {
        return nativeABICallerSaveRegisters;
    }

    @Override
    protected void registerMathStubs(GraalHotSpotVMConfig hotSpotVMConfig, HotSpotProviders providers, OptionValues options) {
        if (HotSpotBackend.Options.GraalArithmeticStubs.getValue(options)) {
            link(new AMD64MathStub(SIN, options, providers, registerStubCall(SIN.foreignCallSignature, HotSpotForeignCallDescriptor.Transition.LEAF,
                            HotSpotForeignCallDescriptor.Reexecutability.REEXECUTABLE, HotSpotForeignCallLinkage.RegisterEffect.COMPUTES_REGISTERS_KILLED, NO_LOCATIONS)));
            link(new AMD64MathStub(COS, options, providers, registerStubCall(COS.foreignCallSignature, HotSpotForeignCallDescriptor.Transition.LEAF,
                            HotSpotForeignCallDescriptor.Reexecutability.REEXECUTABLE, HotSpotForeignCallLinkage.RegisterEffect.COMPUTES_REGISTERS_KILLED, NO_LOCATIONS)));
            link(new AMD64MathStub(TAN, options, providers, registerStubCall(TAN.foreignCallSignature, HotSpotForeignCallDescriptor.Transition.LEAF,
                            HotSpotForeignCallDescriptor.Reexecutability.REEXECUTABLE, HotSpotForeignCallLinkage.RegisterEffect.COMPUTES_REGISTERS_KILLED, NO_LOCATIONS)));
            link(new AMD64MathStub(EXP, options, providers, registerStubCall(EXP.foreignCallSignature, HotSpotForeignCallDescriptor.Transition.LEAF,
                            HotSpotForeignCallDescriptor.Reexecutability.REEXECUTABLE, HotSpotForeignCallLinkage.RegisterEffect.COMPUTES_REGISTERS_KILLED, NO_LOCATIONS)));
            link(new AMD64MathStub(LOG, options, providers, registerStubCall(LOG.foreignCallSignature, HotSpotForeignCallDescriptor.Transition.LEAF,
                            HotSpotForeignCallDescriptor.Reexecutability.REEXECUTABLE, HotSpotForeignCallLinkage.RegisterEffect.COMPUTES_REGISTERS_KILLED, NO_LOCATIONS)));
            link(new AMD64MathStub(LOG10, options, providers, registerStubCall(LOG10.foreignCallSignature, HotSpotForeignCallDescriptor.Transition.LEAF,
                            HotSpotForeignCallDescriptor.Reexecutability.REEXECUTABLE, HotSpotForeignCallLinkage.RegisterEffect.COMPUTES_REGISTERS_KILLED, NO_LOCATIONS)));
            link(new AMD64MathStub(POW, options, providers, registerStubCall(POW.foreignCallSignature, HotSpotForeignCallDescriptor.Transition.LEAF,
                            HotSpotForeignCallDescriptor.Reexecutability.REEXECUTABLE, HotSpotForeignCallLinkage.RegisterEffect.COMPUTES_REGISTERS_KILLED, NO_LOCATIONS)));
        } else {
            super.registerMathStubs(hotSpotVMConfig, providers, options);
        }
    }
}
