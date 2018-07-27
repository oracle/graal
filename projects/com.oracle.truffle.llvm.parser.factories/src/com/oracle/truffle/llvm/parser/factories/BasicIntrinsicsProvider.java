/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.parser.factories;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.llvm.nodes.func.LLVMArgNodeGen;
import com.oracle.truffle.llvm.nodes.func.LLVMRaiseExceptionNode;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMAbortNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMACosNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMASinNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMATan2NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMATanNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMAbsNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMCeilNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMCosNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMCoshNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMExp2NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMExpNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMFAbsNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMFloorNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMFmodNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMFmodlNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMLAbsNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMLdexpNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMLog10NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMLog2NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMLogNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMModfNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMPowNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMRintNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMSinNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMSinhNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMSqrtNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMTanNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMTanhNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCTypeIntrinsicsFactory.LLVMIsalphaNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCTypeIntrinsicsFactory.LLVMIsspaceNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCTypeIntrinsicsFactory.LLVMIsupperNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCTypeIntrinsicsFactory.LLVMToUpperNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCTypeIntrinsicsFactory.LLVMTolowerNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMExitNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMMemIntrinsicFactory.LLVMLibcMemcpyNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMMemIntrinsicFactory.LLVMLibcMemsetNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMSignalNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMSyscall;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMTruffleReadBytesNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMLoadLibraryNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMPolyglotAsString;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMPolyglotBoxedPredicate.IsBoolean;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMPolyglotBoxedPredicate.IsNumber;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMPolyglotBoxedPredicate.IsString;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMPolyglotBoxedPredicateNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMPolyglotBoxedPredicateNodeGen.FitsInDoubleNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMPolyglotBoxedPredicateNodeGen.FitsInFloatNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMPolyglotBoxedPredicateNodeGen.FitsInI16NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMPolyglotBoxedPredicateNodeGen.FitsInI32NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMPolyglotBoxedPredicateNodeGen.FitsInI64NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMPolyglotBoxedPredicateNodeGen.FitsInI8NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMPolyglotEval;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMPolyglotExportNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMPolyglotFromString;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMPolyglotGetStringSizeNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMPolyglotImportNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMPolyglotIsValueNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMPolyglotJavaTypeNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMPolyglotNewInstanceNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMPolyglotPredicateFactory.LLVMPolyglotCanInstantiateNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMPolyglotRemoveFactory.LLVMPolyglotRemoveArrayElementNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMPolyglotRemoveFactory.LLVMPolyglotRemoveMemberNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMSulongFunctionToNativePointerNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleAddressToFunctionNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleBinaryFactory.LLVMTruffleHasKeysNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleBinaryFactory.LLVMTruffleHasSizeNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleBinaryFactory.LLVMTruffleIsBoxedNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleBinaryFactory.LLVMTruffleIsExecutableNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleBinaryFactory.LLVMTruffleIsNullNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleDerefHandleToManagedNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleExecuteNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleFreeCStringNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleGetSizeNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleHandleToManagedNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleInvokeNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleIsHandleToManagedNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleIsTruffleObjectNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleManagedMallocNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleManagedToHandleNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleReadFactory.LLVMTruffleReadFromIndexNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleReadFactory.LLVMTruffleReadFromNameNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleReadNBytesNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleReadNStringNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleReadStringNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleReleaseHandleNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleStringAsCStringNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleUnboxNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleWriteFactory.LLVMTruffleWriteToIndexNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleWriteFactory.LLVMTruffleWriteToNameNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleWriteManagedToGlobalNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMVirtualMallocNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.typed.LLVMArrayTypeIDNode;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.typed.LLVMPolyglotAsTyped;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.typed.LLVMPolyglotFromTyped;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.typed.LLVMTypeIDNode;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMIntrinsicRootNodeFactory.LLVMIntrinsicExpressionNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMMemoryIntrinsicFactory.LLVMCallocNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMMemoryIntrinsicFactory.LLVMFreeNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMMemoryIntrinsicFactory.LLVMMallocNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMMemoryIntrinsicFactory.LLVMReallocNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.arith.LLVMComplexDoubleDiv;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.arith.LLVMComplexDoubleMul;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.arith.LLVMComplexFloatDiv;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.arith.LLVMComplexFloatMul;
import com.oracle.truffle.llvm.nodes.intrinsics.rust.LLVMPanicNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.rust.LLVMStartFactory.LLVMLangStartInternalNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.rust.LLVMStartFactory.LLVMLangStartNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.sulong.LLVMPrintStackTraceNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.sulong.LLVMRunDestructorFunctionsNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.sulong.LLVMShouldPrintStackTraceOnAbortNodeGen;
import com.oracle.truffle.llvm.runtime.ContextExtension;
import com.oracle.truffle.llvm.runtime.LLVMContext.ExternalLibrary;
import com.oracle.truffle.llvm.runtime.LLVMIntrinsicProvider;
import com.oracle.truffle.llvm.runtime.NodeFactory;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM.ForeignToLLVMType;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.types.FunctionType;

/**
 * If an intrinsic is defined for a function, then the intrinsic is used instead of doing a call to
 * native code. The intrinsic is also preferred over LLVM bitcode that is part of a Sulong-internal
 * library.
 */
public class BasicIntrinsicsProvider implements LLVMIntrinsicProvider, ContextExtension {
    private final ExternalLibrary library = new ExternalLibrary("SulongIntrinsics", false);

    @Override
    public ExternalLibrary getLibrary() {
        return library;
    }

    @Override
    public Class<?> extensionClass() {
        return LLVMIntrinsicProvider.class;
    }

    @Override
    @TruffleBoundary
    public final boolean isIntrinsified(String name) {
        return factoriesContainKey(name);
    }

    @Override
    public final RootCallTarget generateIntrinsic(String name, FunctionType type) {
        CompilerAsserts.neverPartOfCompilation();
        if (factoriesContainKey(name)) {
            return wrap(name, factories.get(name).generate(type));
        }
        return null;
    }

    @Override
    public final boolean forceInline(String name) {
        CompilerAsserts.neverPartOfCompilation();
        if (factoriesContainKey(name)) {
            return factories.get(name).forceInline;
        }
        return false;
    }

    @Override
    public final boolean forceSplit(String name) {
        CompilerAsserts.neverPartOfCompilation();
        if (factoriesContainKey(name)) {
            return factories.get(name).forceSplit;
        }
        return false;
    }

    protected final Map<String, LLVMNativeIntrinsicFactory> factories = new HashMap<>();
    protected final Demangler demangler = new Demangler();
    protected final TruffleLanguage<?> language;

    public BasicIntrinsicsProvider(TruffleLanguage<?> language) {
        this.language = language;
    }

    public abstract static class LLVMNativeIntrinsicFactory {
        private final boolean forceInline;
        private final boolean forceSplit;

        public LLVMNativeIntrinsicFactory(boolean forceInline, boolean forceSplit) {
            this.forceInline = forceInline;
            this.forceSplit = forceSplit;
        }

        protected abstract LLVMExpressionNode generate(FunctionType type);
    }

    protected static class Demangler {
        protected final List<UnaryOperator<String>> demanglerFunctions = Arrays.asList(new RustDemangleFunction());

        protected String demangle(String name) {
            CompilerAsserts.neverPartOfCompilation();
            for (UnaryOperator<String> func : demanglerFunctions) {
                String demangledName = func.apply(name);
                if (demangledName != null) {
                    return demangledName;
                }
            }
            return null;
        }

        protected static class RustDemangleFunction implements UnaryOperator<String> {

            @Override
            public String apply(String name) {
                if (!name.endsWith("E")) {
                    return null;
                }
                NameScanner scanner = new NameScanner(name);
                if (!(scanner.skip("@_ZN") || scanner.skip("@ZN"))) {
                    return null;
                }

                StringBuilder builder = new StringBuilder("@");
                int elemLen;
                while ((elemLen = scanner.scanUnsignedInt()) != -1) {
                    String elem = scanner.scan(elemLen);
                    if (elem == null) {
                        return null;
                    }
                    if (elem.matches("h[0-9a-fA-F]+")) {
                        break;
                    }
                    builder.append(elem);
                    builder.append("::");
                }
                if (builder.length() < 2 || !scanner.skip("E")) {
                    return null;
                }
                builder.delete(builder.length() - 2, builder.length());
                return builder.toString();
            }
        }

        protected static class NameScanner {
            protected final String name;
            protected int index;

            protected NameScanner(String name) {
                this.name = name;
                index = 0;
            }

            protected boolean skip(String str) {
                int endi = index + str.length();
                if (endi <= name.length() && str.equals(name.substring(index, endi))) {
                    index = endi;
                    return true;
                }
                return false;
            }

            protected String scan(int nchars) {
                if (index + nchars > name.length()) {
                    return null;
                }
                String result = name.substring(index, index + nchars);
                index += nchars;
                return result;
            }

            protected int scanUnsignedInt() {
                int endi = index;
                while (endi < name.length() && Character.isDigit(name.charAt(endi))) {
                    endi++;
                }
                try {
                    int result = Integer.parseInt(name.substring(index, endi));
                    index = endi;
                    return result;
                } catch (NumberFormatException e) {
                    return -1;
                }
            }
        }
    }

    public BasicIntrinsicsProvider collectIntrinsics(NodeFactory nodeFactory) {
        registerTruffleIntrinsics(nodeFactory);
        registerSulongIntrinsics();
        registerAbortIntrinsics();
        registerRustIntrinsics();
        registerMathFunctionIntrinsics();
        registerMemoryFunctionIntrinsics(nodeFactory);
        registerExceptionIntrinsics();
        registerComplexNumberIntrinsics();
        registerCTypeIntrinsics();
        registerManagedAllocationIntrinsics();
        return this;
    }

    protected boolean factoriesContainKey(String name) {
        if (factories.containsKey(name)) {
            return true;
        }
        String demangledName = demangler.demangle(name);
        if (demangledName == null || !factories.containsKey(demangledName)) {
            return false;
        }
        factories.put(name, factories.get(demangledName));
        return true;
    }

    protected RootCallTarget wrap(String functionName, LLVMExpressionNode node) {
        return Truffle.getRuntime().createCallTarget(LLVMIntrinsicExpressionNodeGen.create(language, functionName, node));
    }

    protected LLVMExpressionNode[] argumentsArray(int startIndex, int arity) {
        LLVMExpressionNode[] args = new LLVMExpressionNode[arity];
        for (int i = 0; i < arity; i++) {
            args[i] = LLVMArgNodeGen.create(i + startIndex);
        }
        return args;
    }

    protected void registerSulongIntrinsics() {
        factories.put("@__sulong_destructor_functions", new LLVMNativeIntrinsicFactory(true, true) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMRunDestructorFunctionsNodeGen.create();
            }
        });

        factories.put("@__sulong_print_stacktrace", new LLVMNativeIntrinsicFactory(true, true) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMPrintStackTraceNodeGen.create();
            }
        });

        factories.put("@__sulong_should_print_stacktrace_on_abort", new LLVMNativeIntrinsicFactory(true, true) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMShouldPrintStackTraceOnAbortNodeGen.create();
            }
        });
    }

    protected void registerTruffleIntrinsics(NodeFactory nodeFactory) {
        LLVMNativeIntrinsicFactory polyglotImport = new LLVMNativeIntrinsicFactory(true, true) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMPolyglotImportNodeGen.create(LLVMArgNodeGen.create(1));
            }
        };
        factories.put("@polyglot_import", polyglotImport);
        factories.put("@truffle_import", polyglotImport);
        factories.put("@truffle_import_cached", polyglotImport);

        factories.put("@polyglot_export", new LLVMNativeIntrinsicFactory(true, true) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMPolyglotExportNodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });

        factories.put("@polyglot_eval", new LLVMNativeIntrinsicFactory(true, true) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMPolyglotEval.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });

        factories.put("@polyglot_eval_file", new LLVMNativeIntrinsicFactory(true, true) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMPolyglotEval.createFile(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });

        factories.put("@polyglot_java_type", new LLVMNativeIntrinsicFactory(true, true) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMPolyglotJavaTypeNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });

        //

        factories.put("@polyglot_is_value", new LLVMNativeIntrinsicFactory(true, true) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMPolyglotIsValueNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });

        factories.put("@polyglot_is_number", new LLVMNativeIntrinsicFactory(true, true) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMPolyglotBoxedPredicateNodeGen.create(new IsNumber(), LLVMArgNodeGen.create(1));
            }
        });

        factories.put("@polyglot_is_boolean", new LLVMNativeIntrinsicFactory(true, true) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMPolyglotBoxedPredicateNodeGen.create(new IsBoolean(), LLVMArgNodeGen.create(1));
            }
        });

        factories.put("@polyglot_is_string", new LLVMNativeIntrinsicFactory(true, true) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMPolyglotBoxedPredicateNodeGen.create(new IsString(), LLVMArgNodeGen.create(1));
            }
        });

        factories.put("@polyglot_fits_in_i8", new LLVMNativeIntrinsicFactory(true, true) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMPolyglotBoxedPredicateNodeGen.create(FitsInI8NodeGen.create(), LLVMArgNodeGen.create(1));
            }
        });

        factories.put("@polyglot_fits_in_i16", new LLVMNativeIntrinsicFactory(true, true) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMPolyglotBoxedPredicateNodeGen.create(FitsInI16NodeGen.create(), LLVMArgNodeGen.create(1));
            }
        });

        factories.put("@polyglot_fits_in_i32", new LLVMNativeIntrinsicFactory(true, true) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMPolyglotBoxedPredicateNodeGen.create(FitsInI32NodeGen.create(), LLVMArgNodeGen.create(1));
            }
        });

        factories.put("@polyglot_fits_in_i64", new LLVMNativeIntrinsicFactory(true, true) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMPolyglotBoxedPredicateNodeGen.create(FitsInI64NodeGen.create(), LLVMArgNodeGen.create(1));
            }
        });

        factories.put("@polyglot_fits_in_float", new LLVMNativeIntrinsicFactory(true, true) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMPolyglotBoxedPredicateNodeGen.create(FitsInFloatNodeGen.create(), LLVMArgNodeGen.create(1));
            }
        });

        factories.put("@polyglot_fits_in_double", new LLVMNativeIntrinsicFactory(true, true) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMPolyglotBoxedPredicateNodeGen.create(FitsInDoubleNodeGen.create(), LLVMArgNodeGen.create(1));
            }
        });

        LLVMNativeIntrinsicFactory polyglotPutMember = new LLVMNativeIntrinsicFactory(true, true) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleWriteToNameNodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3));
            }
        };
        factories.put("@polyglot_put_member", polyglotPutMember);
        factories.put("@truffle_write", polyglotPutMember);

        factories.put("@truffle_write_i", new LLVMNativeIntrinsicFactory(true, true) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleWriteToNameNodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3));
            }
        });
        factories.put("@truffle_write_l", new LLVMNativeIntrinsicFactory(true, true) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleWriteToNameNodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3));
            }
        });
        factories.put("@truffle_write_c", new LLVMNativeIntrinsicFactory(true, true) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleWriteToNameNodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3));
            }
        });
        factories.put("@truffle_write_f", new LLVMNativeIntrinsicFactory(true, true) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleWriteToNameNodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3));
            }
        });
        factories.put("@truffle_write_d", new LLVMNativeIntrinsicFactory(true, true) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleWriteToNameNodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3));
            }
        });
        factories.put("@truffle_write_b", new LLVMNativeIntrinsicFactory(true, true) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleWriteToNameNodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3));
            }
        });

        LLVMNativeIntrinsicFactory polyglotSetArrayElement = new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleWriteToIndexNodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3));
            }
        };
        factories.put("@polyglot_set_array_element", polyglotSetArrayElement);
        factories.put("@truffle_write_idx", polyglotSetArrayElement);

        factories.put("@truffle_write_idx_i", new LLVMNativeIntrinsicFactory(true, true) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleWriteToIndexNodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3));
            }
        });
        factories.put("@truffle_write_idx_l", new LLVMNativeIntrinsicFactory(true, true) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleWriteToIndexNodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3));
            }
        });
        factories.put("@truffle_write_idx_c", new LLVMNativeIntrinsicFactory(true, true) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleWriteToIndexNodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3));
            }
        });
        factories.put("@truffle_write_idx_f", new LLVMNativeIntrinsicFactory(true, true) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleWriteToIndexNodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3));
            }
        });
        factories.put("@truffle_write_idx_d", new LLVMNativeIntrinsicFactory(true, true) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleWriteToIndexNodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3));
            }
        });
        factories.put("@truffle_write_idx_b", new LLVMNativeIntrinsicFactory(true, true) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleWriteToIndexNodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3));
            }
        });

        LLVMNativeIntrinsicFactory polyglotGetMember = new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleReadFromNameNodeGen.create(ForeignToLLVM.create(ForeignToLLVMType.POINTER), LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        };
        factories.put("@polyglot_get_member", polyglotGetMember);
        factories.put("@truffle_read", polyglotGetMember);

        factories.put("@truffle_read_i", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleReadFromNameNodeGen.create(ForeignToLLVM.create(ForeignToLLVMType.I32), LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });
        factories.put("@truffle_read_l", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleReadFromNameNodeGen.create(ForeignToLLVM.create(ForeignToLLVMType.I64), LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });
        factories.put("@truffle_read_c", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleReadFromNameNodeGen.create(ForeignToLLVM.create(ForeignToLLVMType.I8), LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });
        factories.put("@truffle_read_f", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleReadFromNameNodeGen.create(ForeignToLLVM.create(ForeignToLLVMType.FLOAT), LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });
        factories.put("@truffle_read_d", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleReadFromNameNodeGen.create(ForeignToLLVM.create(ForeignToLLVMType.DOUBLE), LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });
        factories.put("@truffle_read_b", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleReadFromNameNodeGen.create(ForeignToLLVM.create(ForeignToLLVMType.I1), LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });

        LLVMNativeIntrinsicFactory polyglotGetArrayElement = new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleReadFromIndexNodeGen.create(ForeignToLLVM.create(ForeignToLLVMType.POINTER), LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        };
        factories.put("@polyglot_get_array_element", polyglotGetArrayElement);
        factories.put("@truffle_read_idx", polyglotGetArrayElement);

        factories.put("@truffle_read_idx_i", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleReadFromIndexNodeGen.create(ForeignToLLVM.create(ForeignToLLVMType.I32), LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });
        factories.put("@truffle_read_idx_l", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleReadFromIndexNodeGen.create(ForeignToLLVM.create(ForeignToLLVMType.I64), LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });
        factories.put("@truffle_read_idx_c", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleReadFromIndexNodeGen.create(ForeignToLLVM.create(ForeignToLLVMType.I8), LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });
        factories.put("@truffle_read_idx_f", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleReadFromIndexNodeGen.create(ForeignToLLVM.create(ForeignToLLVMType.FLOAT), LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });
        factories.put("@truffle_read_idx_d", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleReadFromIndexNodeGen.create(ForeignToLLVM.create(ForeignToLLVMType.DOUBLE), LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });
        factories.put("@truffle_read_idx_b", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleReadFromIndexNodeGen.create(ForeignToLLVM.create(ForeignToLLVMType.I1), LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });

        factories.put("@polyglot_remove_member", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMPolyglotRemoveMemberNodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });

        factories.put("@polyglot_remove_array_element", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMPolyglotRemoveArrayElementNodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });

        LLVMNativeIntrinsicFactory polyglotAsI8 = new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleUnboxNodeGen.create(ForeignToLLVM.create(ForeignToLLVMType.I8), LLVMArgNodeGen.create(1));
            }
        };
        factories.put("@polyglot_as_i8", polyglotAsI8);
        factories.put("@truffle_unbox_c", polyglotAsI8);

        LLVMNativeIntrinsicFactory polyglotAsI16 = new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleUnboxNodeGen.create(ForeignToLLVM.create(ForeignToLLVMType.I16), LLVMArgNodeGen.create(1));
            }
        };
        factories.put("@polyglot_as_i16", polyglotAsI16);

        LLVMNativeIntrinsicFactory polyglotAsI32 = new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleUnboxNodeGen.create(ForeignToLLVM.create(ForeignToLLVMType.I32), LLVMArgNodeGen.create(1));
            }
        };
        factories.put("@polyglot_as_i32", polyglotAsI32);
        factories.put("@truffle_unbox_i", polyglotAsI32);

        LLVMNativeIntrinsicFactory polyglotAsI64 = new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleUnboxNodeGen.create(ForeignToLLVM.create(ForeignToLLVMType.I64), LLVMArgNodeGen.create(1));
            }
        };
        factories.put("@polyglot_as_i64", polyglotAsI64);
        factories.put("@truffle_unbox_l", polyglotAsI64);

        LLVMNativeIntrinsicFactory polyglotAsFloat = new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleUnboxNodeGen.create(ForeignToLLVM.create(ForeignToLLVMType.FLOAT), LLVMArgNodeGen.create(1));
            }
        };
        factories.put("@polyglot_as_float", polyglotAsFloat);
        factories.put("@truffle_unbox_f", polyglotAsFloat);

        LLVMNativeIntrinsicFactory polyglotAsDouble = new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleUnboxNodeGen.create(ForeignToLLVM.create(ForeignToLLVMType.DOUBLE), LLVMArgNodeGen.create(1));
            }
        };
        factories.put("@polyglot_as_double", polyglotAsDouble);
        factories.put("@truffle_unbox_d", polyglotAsDouble);

        LLVMNativeIntrinsicFactory polyglotAsBoolean = new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleUnboxNodeGen.create(ForeignToLLVM.create(ForeignToLLVMType.I1), LLVMArgNodeGen.create(1));
            }
        };
        factories.put("@polyglot_as_boolean", polyglotAsBoolean);
        factories.put("@truffle_unbox_b", polyglotAsBoolean);

        //

        factories.put("@polyglot_new_instance", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMPolyglotNewInstanceNodeGen.create(argumentsArray(2, type.getArgumentTypes().length - 2), LLVMArgNodeGen.create(1));
            }
        });

        LLVMNativeIntrinsicFactory polyglotInvoke = new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleInvokeNodeGen.create(ForeignToLLVM.create(ForeignToLLVMType.POINTER), argumentsArray(3, type.getArgumentTypes().length - 3), LLVMArgNodeGen.create(1),
                                LLVMArgNodeGen.create(2));
            }
        };
        factories.put("@polyglot_invoke", polyglotInvoke);
        factories.put("@truffle_invoke", polyglotInvoke);

        factories.put("@truffle_invoke_i", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleInvokeNodeGen.create(ForeignToLLVM.create(ForeignToLLVMType.I32), argumentsArray(3, type.getArgumentTypes().length - 3), LLVMArgNodeGen.create(1),
                                LLVMArgNodeGen.create(2));
            }
        });

        factories.put("@truffle_invoke_l", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleInvokeNodeGen.create(ForeignToLLVM.create(ForeignToLLVMType.I64), argumentsArray(3, type.getArgumentTypes().length - 3), LLVMArgNodeGen.create(1),
                                LLVMArgNodeGen.create(2));
            }
        });

        factories.put("@truffle_invoke_c", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleInvokeNodeGen.create(ForeignToLLVM.create(ForeignToLLVMType.I8), argumentsArray(3, type.getArgumentTypes().length - 3), LLVMArgNodeGen.create(1),
                                LLVMArgNodeGen.create(2));
            }
        });

        factories.put("@truffle_invoke_f", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleInvokeNodeGen.create(ForeignToLLVM.create(ForeignToLLVMType.FLOAT), argumentsArray(3, type.getArgumentTypes().length - 3), LLVMArgNodeGen.create(1),
                                LLVMArgNodeGen.create(2));
            }
        });

        factories.put("@truffle_invoke_d", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleInvokeNodeGen.create(ForeignToLLVM.create(ForeignToLLVMType.DOUBLE), argumentsArray(3, type.getArgumentTypes().length - 3), LLVMArgNodeGen.create(1),
                                LLVMArgNodeGen.create(2));
            }
        });

        factories.put("@truffle_invoke_b", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleInvokeNodeGen.create(ForeignToLLVM.create(ForeignToLLVMType.I1), argumentsArray(3, type.getArgumentTypes().length - 3), LLVMArgNodeGen.create(1),
                                LLVMArgNodeGen.create(2));
            }
        });

        //

        factories.put("@truffle_execute", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleExecuteNodeGen.create(ForeignToLLVM.create(ForeignToLLVMType.POINTER), argumentsArray(2, type.getArgumentTypes().length - 2), LLVMArgNodeGen.create(1));
            }
        });

        factories.put("@truffle_execute_i", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleExecuteNodeGen.create(ForeignToLLVM.create(ForeignToLLVMType.I32), argumentsArray(2, type.getArgumentTypes().length - 2), LLVMArgNodeGen.create(1));
            }
        });

        factories.put("@truffle_execute_l", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleExecuteNodeGen.create(ForeignToLLVM.create(ForeignToLLVMType.I64), argumentsArray(2, type.getArgumentTypes().length - 2), LLVMArgNodeGen.create(1));
            }
        });

        factories.put("@truffle_execute_c", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleExecuteNodeGen.create(ForeignToLLVM.create(ForeignToLLVMType.I8), argumentsArray(2, type.getArgumentTypes().length - 2), LLVMArgNodeGen.create(1));
            }
        });

        factories.put("@truffle_execute_f", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleExecuteNodeGen.create(ForeignToLLVM.create(ForeignToLLVMType.FLOAT), argumentsArray(2, type.getArgumentTypes().length - 2), LLVMArgNodeGen.create(1));
            }
        });

        factories.put("@truffle_execute_d", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleExecuteNodeGen.create(ForeignToLLVM.create(ForeignToLLVMType.DOUBLE), argumentsArray(2, type.getArgumentTypes().length - 2), LLVMArgNodeGen.create(1));
            }
        });

        factories.put("@truffle_execute_b", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleExecuteNodeGen.create(ForeignToLLVM.create(ForeignToLLVMType.I1), argumentsArray(2, type.getArgumentTypes().length - 2), LLVMArgNodeGen.create(1));
            }
        });

        //

        factories.put("@truffle_address_to_function", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleAddressToFunctionNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });

        LLVMNativeIntrinsicFactory polyglotCanExecute = new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleIsExecutableNodeGen.create(LLVMArgNodeGen.create(1));
            }
        };
        factories.put("@polyglot_can_execute", polyglotCanExecute);
        factories.put("@truffle_is_executable", polyglotCanExecute);

        factories.put("@polyglot_can_instantiate", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMPolyglotCanInstantiateNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });

        LLVMNativeIntrinsicFactory polyglotIsNull = new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleIsNullNodeGen.create(LLVMArgNodeGen.create(1));
            }
        };
        factories.put("@polyglot_is_null", polyglotIsNull);
        factories.put("@truffle_is_null", polyglotIsNull);

        LLVMNativeIntrinsicFactory polyglotHasArrayElements = new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleHasSizeNodeGen.create(LLVMArgNodeGen.create(1));
            }
        };
        factories.put("@polyglot_has_array_elements", polyglotHasArrayElements);
        factories.put("@truffle_has_size", polyglotHasArrayElements);

        factories.put("@polyglot_has_members", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleHasKeysNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });

        factories.put("@truffle_is_boxed", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleIsBoxedNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });

        factories.put("@polyglot_get_array_size", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleGetSizeNodeGen.create(ForeignToLLVMType.I64, LLVMArgNodeGen.create(1));
            }
        });

        factories.put("@truffle_get_size", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleGetSizeNodeGen.create(ForeignToLLVMType.I32, LLVMArgNodeGen.create(1));
            }
        });

        //

        factories.put("@polyglot_get_string_size", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMPolyglotGetStringSizeNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });

        factories.put("@polyglot_as_string", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMPolyglotAsString.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3), LLVMArgNodeGen.create(4));
            }
        });

        factories.put("@polyglot_from_string", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMPolyglotFromString.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });

        factories.put("@polyglot_from_string_n", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMPolyglotFromString.createN(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3));
            }
        });

        //

        factories.put("@truffle_read_string", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleReadStringNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });

        factories.put("@truffle_read_n_string", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleReadNStringNodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });

        factories.put("@truffle_read_bytes", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleReadBytesNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });

        factories.put("@truffle_read_n_bytes", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleReadNBytesNodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });

        factories.put("@truffle_string_to_cstr", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleStringAsCStringNodeGen.create(nodeFactory.createAllocateString(), LLVMArgNodeGen.create(1));
            }
        });

        factories.put("@truffle_free_cstr", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleFreeCStringNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });

        factories.put("@truffle_is_truffle_object", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleIsTruffleObjectNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });

        factories.put("@truffle_sulong_function_to_native_pointer", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMSulongFunctionToNativePointerNodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });

        factories.put("@truffle_load_library", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMLoadLibraryNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });

        factories.put("@truffle_polyglot_eval", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMPolyglotEval.createLegacy(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });

        //

        factories.put("@__polyglot_as_typeid", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTypeIDNode.create(LLVMArgNodeGen.create(1));
            }
        });

        factories.put("@polyglot_as_typed", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMPolyglotAsTyped.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });

        factories.put("@polyglot_from_typed", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMPolyglotFromTyped.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });

        factories.put("@polyglot_array_typeid", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMArrayTypeIDNode.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });

        /*
         * For binary compatibility with bitcode files compiled with polyglot.h from 1.0-RC2 or
         * earlier.
         */

        factories.put("@__polyglot_as_typed", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            @SuppressWarnings("deprecation")
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMPolyglotAsTyped.createStruct(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });

        factories.put("@__polyglot_as_typed_array", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            @SuppressWarnings("deprecation")
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMPolyglotAsTyped.createArray(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });

        factories.put("@__polyglot_from_typed", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            @SuppressWarnings("deprecation")
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMPolyglotFromTyped.createStruct(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });

        factories.put("@__polyglot_from_typed_array", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            @SuppressWarnings("deprecation")
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMPolyglotFromTyped.createArray(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3));
            }
        });
    }

    protected void registerManagedAllocationIntrinsics() {
        factories.put("@truffle_managed_malloc", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleManagedMallocNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });

        factories.put("@truffle_handle_for_managed", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleManagedToHandleNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });

        factories.put("@truffle_deref_handle_for_managed", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleDerefHandleToManagedNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });

        factories.put("@truffle_release_handle", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleReleaseHandleNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });

        factories.put("@truffle_managed_from_handle", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleHandleToManagedNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });

        factories.put("@truffle_is_handle_to_managed", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleIsHandleToManagedNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });

        factories.put("@truffle_assign_managed", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleWriteManagedToGlobalNodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });

        factories.put("@truffle_virtual_malloc", new LLVMNativeIntrinsicFactory(true, true) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMVirtualMallocNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });
    }

    protected void registerAbortIntrinsics() {
        factories.put("@_gfortran_abort", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMAbortNodeGen.create();
            }
        });
        factories.put("@signal", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMSignalNodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });
        factories.put("@syscall", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return new LLVMSyscall();
            }
        });
    }

    protected void registerRustIntrinsics() {
        factories.put("@std::rt::lang_start", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMLangStartNodeGen.create(LLVMArgNodeGen.create(0), LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3));
            }
        });
        factories.put("@std::rt::lang_start_internal", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMLangStartInternalNodeGen.create(LLVMArgNodeGen.create(0), LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3), LLVMArgNodeGen.create(4));
            }
        });
        factories.put("@std::process::exit", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMExitNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });
        factories.put("@core::panicking::panic", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMPanicNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });
    }

    protected void registerMathFunctionIntrinsics() {
        // TEMP (chaeubl): add a function that adds the intrinsics and checks if they are not
        // present yet
        factories.put("@log2", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMLog2NodeGen.create(LLVMArgNodeGen.create(1), null);
            }
        });
        factories.put("@sqrt", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMSqrtNodeGen.create(LLVMArgNodeGen.create(1), null);
            }
        });
        factories.put("@sqrtf", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMSqrtNodeGen.create(LLVMArgNodeGen.create(1), null);
            }
        });
        factories.put("@log", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMLogNodeGen.create(LLVMArgNodeGen.create(1), null);
            }
        });
        factories.put("@log10", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMLog10NodeGen.create(LLVMArgNodeGen.create(1), null);
            }
        });
        factories.put("@rint", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMRintNodeGen.create(LLVMArgNodeGen.create(1), null);
            }
        });
        factories.put("@ceil", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMCeilNodeGen.create(LLVMArgNodeGen.create(1), null);
            }
        });
        factories.put("@floor", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMFloorNodeGen.create(LLVMArgNodeGen.create(1), null);
            }
        });
        factories.put("@abs", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMAbsNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });
        factories.put("@labs", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMLAbsNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });
        factories.put("@fabs", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMFAbsNodeGen.create(LLVMArgNodeGen.create(1), null);
            }
        });
        factories.put("@fabsf", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMFAbsNodeGen.create(LLVMArgNodeGen.create(1), null);
            }
        });
        factories.put("@pow", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMPowNodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), null);
            }
        });
        factories.put("@exp", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMExpNodeGen.create(LLVMArgNodeGen.create(1), null);
            }
        });
        factories.put("@exp2", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMExp2NodeGen.create(LLVMArgNodeGen.create(1), null);
            }
        });

        factories.put("@sin", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMSinNodeGen.create(LLVMArgNodeGen.create(1), null);
            }
        });

        factories.put("@sinf", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMSinNodeGen.create(LLVMArgNodeGen.create(1), null);
            }
        });

        factories.put("@cos", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMCosNodeGen.create(LLVMArgNodeGen.create(1), null);
            }
        });

        factories.put("@cosf", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMCosNodeGen.create(LLVMArgNodeGen.create(1), null);
            }
        });

        factories.put("@tan", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTanNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });

        factories.put("@tanf", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTanNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });

        factories.put("@atan2", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMATan2NodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });

        factories.put("@atan2f", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMATan2NodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });

        factories.put("@asin", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMASinNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });

        factories.put("@asinf", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMASinNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });

        factories.put("@acos", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMACosNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });

        factories.put("@acosf", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMACosNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });

        factories.put("@atan", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMATanNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });

        factories.put("@atanf", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMATanNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });

        factories.put("@sinh", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMSinhNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });

        factories.put("@sinhf", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMSinhNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });

        factories.put("@cosh", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMCoshNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });

        factories.put("@coshf", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMCoshNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });

        factories.put("@tanh", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTanhNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });

        factories.put("@tanhf", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTanhNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });

        factories.put("@ldexp", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMLdexpNodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });

        factories.put("@modf", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMModfNodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });

        factories.put("@fmod", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMFmodNodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });

        factories.put("@fmodl", new LLVMNativeIntrinsicFactory(true, false) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMFmodlNodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });

        factories.put("@copysign", new LLVMNativeIntrinsicFactory(true, false) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMCMathsIntrinsicsFactory.LLVMCopySignNodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), null);
            }
        });
    }

    protected void registerCTypeIntrinsics() {
        factories.put("@isalpha", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMIsalphaNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });
        factories.put("@tolower", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTolowerNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });
        factories.put("@toupper", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMToUpperNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });
        factories.put("@isspace", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMIsspaceNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });
        factories.put("@isupper", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMIsupperNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });
    }

    protected void registerMemoryFunctionIntrinsics(NodeFactory factory) {
        factories.put("@malloc", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMMallocNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });
        factories.put("@calloc", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMCallocNodeGen.create(factory.createMemSet(), LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });
        factories.put("@realloc", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMReallocNodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });
        factories.put("@free", new LLVMNativeIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMFreeNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });
        LLVMNativeIntrinsicFactory memset = new LLVMNativeIntrinsicFactory(true, false) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMLibcMemsetNodeGen.create(factory.createMemSet(), LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3));
            }
        };
        factories.put("@memset", memset);
        factories.put("@__memset_chk", memset);
        LLVMNativeIntrinsicFactory memcpy = new LLVMNativeIntrinsicFactory(true, false) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMLibcMemcpyNodeGen.create(factory.createMemMove(), LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3));
            }
        };
        factories.put("@memcpy", memcpy);
        factories.put("@__memcpy_chk", memcpy);
    }

    protected void registerExceptionIntrinsics() {
        factories.put("@_Unwind_RaiseException", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return new LLVMRaiseExceptionNode(LLVMArgNodeGen.create(1));
            }
        });
        factories.put("@__cxa_call_unexpected", new LLVMNativeIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMAbortNodeGen.create();
            }
        });
    }

    public void registerComplexNumberIntrinsics() {
        // float functions return a vector of <2x float>
        factories.put("@__divsc3", new LLVMNativeIntrinsicFactory(true, false) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return new LLVMComplexFloatDiv(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3), LLVMArgNodeGen.create(4));
            }
        });
        factories.put("@__mulsc3", new LLVMNativeIntrinsicFactory(true, false) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return new LLVMComplexFloatMul(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3), LLVMArgNodeGen.create(4));
            }
        });

        // double functions store their double results in the structure that is passed as arg1
        factories.put("@__divdc3", new LLVMNativeIntrinsicFactory(true, false) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return new LLVMComplexDoubleDiv(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3), LLVMArgNodeGen.create(4), LLVMArgNodeGen.create(5));
            }
        });
        factories.put("@__muldc3", new LLVMNativeIntrinsicFactory(true, false) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return new LLVMComplexDoubleMul(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3), LLVMArgNodeGen.create(4), LLVMArgNodeGen.create(5));
            }
        });
    }
}
