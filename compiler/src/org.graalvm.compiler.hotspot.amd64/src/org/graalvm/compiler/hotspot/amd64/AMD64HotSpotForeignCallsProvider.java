/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.amd64;

import static jdk.vm.ci.amd64.AMD64.rax;
import static jdk.vm.ci.amd64.AMD64.rdx;
import static jdk.vm.ci.hotspot.HotSpotCallingConventionType.NativeCall;
import static jdk.vm.ci.meta.Value.ILLEGAL;
import static org.graalvm.compiler.hotspot.HotSpotBackend.EXCEPTION_HANDLER;
import static org.graalvm.compiler.hotspot.HotSpotBackend.EXCEPTION_HANDLER_IN_CALLER;
import static org.graalvm.compiler.hotspot.HotSpotBackend.UPDATE_BYTES_CRC32;
import static org.graalvm.compiler.hotspot.HotSpotBackend.UPDATE_BYTES_CRC32C;
import static org.graalvm.compiler.hotspot.HotSpotBackend.Options.GraalArithmeticStubs;
import static org.graalvm.compiler.hotspot.HotSpotForeignCallLinkage.JUMP_ADDRESS;
import static org.graalvm.compiler.hotspot.HotSpotForeignCallLinkage.RegisterEffect.COMPUTES_REGISTERS_KILLED;
import static org.graalvm.compiler.hotspot.HotSpotForeignCallLinkage.RegisterEffect.DESTROYS_ALL_CALLER_SAVE_REGISTERS;
import static org.graalvm.compiler.hotspot.meta.HotSpotForeignCallDescriptor.Reexecutability.REEXECUTABLE;
import static org.graalvm.compiler.hotspot.meta.HotSpotForeignCallDescriptor.Transition.LEAF;
import static org.graalvm.compiler.replacements.nodes.BinaryMathIntrinsicNode.BinaryOperation.POW;
import static org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.COS;
import static org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.EXP;
import static org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.LOG;
import static org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.LOG10;
import static org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.SIN;
import static org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.TAN;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.HotSpotForeignCallLinkageImpl;
import org.graalvm.compiler.hotspot.HotSpotGraalRuntimeProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotHostForeignCallsProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.word.WordTypes;

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
        GraalHotSpotVMConfig config = runtime.getVMConfig();
        TargetDescription target = providers.getCodeCache().getTarget();
        PlatformKind word = target.arch.getWordKind();

        // The calling convention for the exception handler stub is (only?) defined in
        // TemplateInterpreterGenerator::generate_throw_exception()
        // in templateInterpreter_x86_64.cpp around line 1923
        RegisterValue exception = rax.asValue(LIRKind.reference(word));
        RegisterValue exceptionPc = rdx.asValue(LIRKind.value(word));
        CallingConvention exceptionCc = new CallingConvention(0, ILLEGAL, exception, exceptionPc);
        register(new HotSpotForeignCallLinkageImpl(EXCEPTION_HANDLER, 0L, DESTROYS_ALL_CALLER_SAVE_REGISTERS, exceptionCc, null));
        register(new HotSpotForeignCallLinkageImpl(EXCEPTION_HANDLER_IN_CALLER, JUMP_ADDRESS, DESTROYS_ALL_CALLER_SAVE_REGISTERS, exceptionCc, null));

        if (config.useCRC32Intrinsics) {
            // This stub does callee saving
            registerForeignCall(UPDATE_BYTES_CRC32, config.updateBytesCRC32Stub, NativeCall);
        }
        if (config.useCRC32CIntrinsics) {
            registerForeignCall(UPDATE_BYTES_CRC32C, config.updateBytesCRC32C, NativeCall);
        }

        link(new AMD64ArrayEqualsStub(AMD64ArrayEqualsStub.STUB_BOOLEAN_ARRAY_EQUALS, options, providers,
                        registerStubCall(AMD64ArrayEqualsStub.STUB_BOOLEAN_ARRAY_EQUALS, COMPUTES_REGISTERS_KILLED)));
        link(new AMD64ArrayEqualsStub(AMD64ArrayEqualsStub.STUB_BYTE_ARRAY_EQUALS, options, providers,
                        registerStubCall(AMD64ArrayEqualsStub.STUB_BYTE_ARRAY_EQUALS, COMPUTES_REGISTERS_KILLED)));
        link(new AMD64ArrayEqualsStub(AMD64ArrayEqualsStub.STUB_CHAR_ARRAY_EQUALS, options, providers,
                        registerStubCall(AMD64ArrayEqualsStub.STUB_CHAR_ARRAY_EQUALS, COMPUTES_REGISTERS_KILLED)));
        link(new AMD64ArrayEqualsStub(AMD64ArrayEqualsStub.STUB_SHORT_ARRAY_EQUALS, options, providers,
                        registerStubCall(AMD64ArrayEqualsStub.STUB_SHORT_ARRAY_EQUALS, COMPUTES_REGISTERS_KILLED)));
        link(new AMD64ArrayEqualsStub(AMD64ArrayEqualsStub.STUB_INT_ARRAY_EQUALS, options, providers,
                        registerStubCall(AMD64ArrayEqualsStub.STUB_INT_ARRAY_EQUALS, COMPUTES_REGISTERS_KILLED)));
        link(new AMD64ArrayEqualsStub(AMD64ArrayEqualsStub.STUB_LONG_ARRAY_EQUALS, options, providers,
                        registerStubCall(AMD64ArrayEqualsStub.STUB_LONG_ARRAY_EQUALS, COMPUTES_REGISTERS_KILLED)));
        link(new AMD64ArrayEqualsStub(AMD64ArrayEqualsStub.STUB_FLOAT_ARRAY_EQUALS, options, providers,
                        registerStubCall(AMD64ArrayEqualsStub.STUB_FLOAT_ARRAY_EQUALS, COMPUTES_REGISTERS_KILLED)));
        link(new AMD64ArrayEqualsStub(AMD64ArrayEqualsStub.STUB_DOUBLE_ARRAY_EQUALS, options, providers,
                        registerStubCall(AMD64ArrayEqualsStub.STUB_DOUBLE_ARRAY_EQUALS, COMPUTES_REGISTERS_KILLED)));
        link(new AMD64ArrayEqualsStub(AMD64ArrayEqualsStub.STUB_BYTE_ARRAY_EQUALS_DIRECT, options, providers,
                        registerStubCall(AMD64ArrayEqualsStub.STUB_BYTE_ARRAY_EQUALS_DIRECT, COMPUTES_REGISTERS_KILLED)));
        link(new AMD64ArrayEqualsStub(AMD64ArrayEqualsStub.STUB_CHAR_ARRAY_EQUALS_DIRECT, options, providers,
                        registerStubCall(AMD64ArrayEqualsStub.STUB_CHAR_ARRAY_EQUALS_DIRECT, COMPUTES_REGISTERS_KILLED)));
        link(new AMD64ArrayEqualsStub(AMD64ArrayEqualsStub.STUB_CHAR_ARRAY_EQUALS_BYTE_ARRAY, options, providers,
                        registerStubCall(AMD64ArrayEqualsStub.STUB_CHAR_ARRAY_EQUALS_BYTE_ARRAY, COMPUTES_REGISTERS_KILLED)));

        link(new AMD64ArrayCompareToStub(AMD64ArrayCompareToStub.STUB_BYTE_ARRAY_COMPARE_TO_BYTE_ARRAY, options, providers,
                        registerStubCall(AMD64ArrayCompareToStub.STUB_BYTE_ARRAY_COMPARE_TO_BYTE_ARRAY, COMPUTES_REGISTERS_KILLED)));
        link(new AMD64ArrayCompareToStub(AMD64ArrayCompareToStub.STUB_BYTE_ARRAY_COMPARE_TO_CHAR_ARRAY, options, providers,
                        registerStubCall(AMD64ArrayCompareToStub.STUB_BYTE_ARRAY_COMPARE_TO_CHAR_ARRAY, COMPUTES_REGISTERS_KILLED)));
        link(new AMD64ArrayCompareToStub(AMD64ArrayCompareToStub.STUB_CHAR_ARRAY_COMPARE_TO_BYTE_ARRAY, options, providers,
                        registerStubCall(AMD64ArrayCompareToStub.STUB_CHAR_ARRAY_COMPARE_TO_BYTE_ARRAY, COMPUTES_REGISTERS_KILLED)));
        link(new AMD64ArrayCompareToStub(AMD64ArrayCompareToStub.STUB_CHAR_ARRAY_COMPARE_TO_CHAR_ARRAY, options, providers,
                        registerStubCall(AMD64ArrayCompareToStub.STUB_CHAR_ARRAY_COMPARE_TO_CHAR_ARRAY, COMPUTES_REGISTERS_KILLED)));

        super.initialize(providers, options);
    }

    @Override
    public Value[] getNativeABICallerSaveRegisters() {
        return nativeABICallerSaveRegisters;
    }

    @Override
    protected void registerMathStubs(GraalHotSpotVMConfig hotSpotVMConfig, HotSpotProviders providers, OptionValues options) {
        if (GraalArithmeticStubs.getValue(options)) {
            link(new AMD64MathStub(SIN, options, providers, registerStubCall(SIN.foreignCallSignature, LEAF, REEXECUTABLE, COMPUTES_REGISTERS_KILLED, NO_LOCATIONS)));
            link(new AMD64MathStub(COS, options, providers, registerStubCall(COS.foreignCallSignature, LEAF, REEXECUTABLE, COMPUTES_REGISTERS_KILLED, NO_LOCATIONS)));
            link(new AMD64MathStub(TAN, options, providers, registerStubCall(TAN.foreignCallSignature, LEAF, REEXECUTABLE, COMPUTES_REGISTERS_KILLED, NO_LOCATIONS)));
            link(new AMD64MathStub(EXP, options, providers, registerStubCall(EXP.foreignCallSignature, LEAF, REEXECUTABLE, COMPUTES_REGISTERS_KILLED, NO_LOCATIONS)));
            link(new AMD64MathStub(LOG, options, providers, registerStubCall(LOG.foreignCallSignature, LEAF, REEXECUTABLE, COMPUTES_REGISTERS_KILLED, NO_LOCATIONS)));
            link(new AMD64MathStub(LOG10, options, providers, registerStubCall(LOG10.foreignCallSignature, LEAF, REEXECUTABLE, COMPUTES_REGISTERS_KILLED, NO_LOCATIONS)));
            link(new AMD64MathStub(POW, options, providers, registerStubCall(POW.foreignCallSignature, LEAF, REEXECUTABLE, COMPUTES_REGISTERS_KILLED, NO_LOCATIONS)));
        } else {
            super.registerMathStubs(hotSpotVMConfig, providers, options);
        }
    }

}
