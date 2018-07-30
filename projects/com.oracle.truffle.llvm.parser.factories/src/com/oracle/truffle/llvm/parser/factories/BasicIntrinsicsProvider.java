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

    protected final Map<String, LLVMIntrinsicFactory> factories = new HashMap<>();
    protected final Demangler demangler = new Demangler();
    protected final TruffleLanguage<?> language;

    public BasicIntrinsicsProvider(TruffleLanguage<?> language) {
        this.language = language;
    }

    public abstract static class LLVMIntrinsicFactory {
        private final boolean forceInline;
        private final boolean forceSplit;

        public LLVMIntrinsicFactory(boolean forceInline, boolean forceSplit) {
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
        add(name, factories.get(demangledName));
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
        add("@__sulong_destructor_functions", new LLVMIntrinsicFactory(true, true) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMRunDestructorFunctionsNodeGen.create();
            }
        });

        add("@__sulong_print_stacktrace", new LLVMIntrinsicFactory(true, true) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMPrintStackTraceNodeGen.create();
            }
        });

        add("@__sulong_should_print_stacktrace_on_abort", new LLVMIntrinsicFactory(true, true) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMShouldPrintStackTraceOnAbortNodeGen.create();
            }
        });
    }

    protected void registerTruffleIntrinsics(NodeFactory nodeFactory) {
        LLVMIntrinsicFactory polyglotImport = new LLVMIntrinsicFactory(true, true) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMPolyglotImportNodeGen.create(LLVMArgNodeGen.create(1));
            }
        };
        add("@polyglot_import", polyglotImport);
        add("@truffle_import", polyglotImport);
        add("@truffle_import_cached", polyglotImport);

        add("@polyglot_export", new LLVMIntrinsicFactory(true, true) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMPolyglotExportNodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });

        add("@polyglot_eval", new LLVMIntrinsicFactory(true, true) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMPolyglotEval.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });

        add("@polyglot_eval_file", new LLVMIntrinsicFactory(true, true) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMPolyglotEval.createFile(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });

        add("@polyglot_java_type", new LLVMIntrinsicFactory(true, true) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMPolyglotJavaTypeNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });

        //

        add("@polyglot_is_value", new LLVMIntrinsicFactory(true, true) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMPolyglotIsValueNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });

        add("@polyglot_is_number", new LLVMIntrinsicFactory(true, true) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMPolyglotBoxedPredicateNodeGen.create(new IsNumber(), LLVMArgNodeGen.create(1));
            }
        });

        add("@polyglot_is_boolean", new LLVMIntrinsicFactory(true, true) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMPolyglotBoxedPredicateNodeGen.create(new IsBoolean(), LLVMArgNodeGen.create(1));
            }
        });

        add("@polyglot_is_string", new LLVMIntrinsicFactory(true, true) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMPolyglotBoxedPredicateNodeGen.create(new IsString(), LLVMArgNodeGen.create(1));
            }
        });

        add("@polyglot_fits_in_i8", new LLVMIntrinsicFactory(true, true) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMPolyglotBoxedPredicateNodeGen.create(FitsInI8NodeGen.create(), LLVMArgNodeGen.create(1));
            }
        });

        add("@polyglot_fits_in_i16", new LLVMIntrinsicFactory(true, true) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMPolyglotBoxedPredicateNodeGen.create(FitsInI16NodeGen.create(), LLVMArgNodeGen.create(1));
            }
        });

        add("@polyglot_fits_in_i32", new LLVMIntrinsicFactory(true, true) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMPolyglotBoxedPredicateNodeGen.create(FitsInI32NodeGen.create(), LLVMArgNodeGen.create(1));
            }
        });

        add("@polyglot_fits_in_i64", new LLVMIntrinsicFactory(true, true) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMPolyglotBoxedPredicateNodeGen.create(FitsInI64NodeGen.create(), LLVMArgNodeGen.create(1));
            }
        });

        add("@polyglot_fits_in_float", new LLVMIntrinsicFactory(true, true) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMPolyglotBoxedPredicateNodeGen.create(FitsInFloatNodeGen.create(), LLVMArgNodeGen.create(1));
            }
        });

        add("@polyglot_fits_in_double", new LLVMIntrinsicFactory(true, true) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMPolyglotBoxedPredicateNodeGen.create(FitsInDoubleNodeGen.create(), LLVMArgNodeGen.create(1));
            }
        });

        LLVMIntrinsicFactory polyglotPutMember = new LLVMIntrinsicFactory(true, true) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleWriteToNameNodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3));
            }
        };
        add("@polyglot_put_member", polyglotPutMember);
        add("@truffle_write", polyglotPutMember);

        add("@truffle_write_i", new LLVMIntrinsicFactory(true, true) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleWriteToNameNodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3));
            }
        });
        add("@truffle_write_l", new LLVMIntrinsicFactory(true, true) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleWriteToNameNodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3));
            }
        });
        add("@truffle_write_c", new LLVMIntrinsicFactory(true, true) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleWriteToNameNodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3));
            }
        });
        add("@truffle_write_f", new LLVMIntrinsicFactory(true, true) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleWriteToNameNodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3));
            }
        });
        add("@truffle_write_d", new LLVMIntrinsicFactory(true, true) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleWriteToNameNodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3));
            }
        });
        add("@truffle_write_b", new LLVMIntrinsicFactory(true, true) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleWriteToNameNodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3));
            }
        });

        LLVMIntrinsicFactory polyglotSetArrayElement = new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleWriteToIndexNodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3));
            }
        };
        add("@polyglot_set_array_element", polyglotSetArrayElement);
        add("@truffle_write_idx", polyglotSetArrayElement);

        add("@truffle_write_idx_i", new LLVMIntrinsicFactory(true, true) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleWriteToIndexNodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3));
            }
        });
        add("@truffle_write_idx_l", new LLVMIntrinsicFactory(true, true) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleWriteToIndexNodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3));
            }
        });
        add("@truffle_write_idx_c", new LLVMIntrinsicFactory(true, true) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleWriteToIndexNodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3));
            }
        });
        add("@truffle_write_idx_f", new LLVMIntrinsicFactory(true, true) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleWriteToIndexNodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3));
            }
        });
        add("@truffle_write_idx_d", new LLVMIntrinsicFactory(true, true) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleWriteToIndexNodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3));
            }
        });
        add("@truffle_write_idx_b", new LLVMIntrinsicFactory(true, true) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleWriteToIndexNodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3));
            }
        });

        LLVMIntrinsicFactory polyglotGetMember = new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleReadFromNameNodeGen.create(ForeignToLLVM.create(ForeignToLLVMType.POINTER), LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        };
        add("@polyglot_get_member", polyglotGetMember);
        add("@truffle_read", polyglotGetMember);

        add("@truffle_read_i", new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleReadFromNameNodeGen.create(ForeignToLLVM.create(ForeignToLLVMType.I32), LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });
        add("@truffle_read_l", new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleReadFromNameNodeGen.create(ForeignToLLVM.create(ForeignToLLVMType.I64), LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });
        add("@truffle_read_c", new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleReadFromNameNodeGen.create(ForeignToLLVM.create(ForeignToLLVMType.I8), LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });
        add("@truffle_read_f", new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleReadFromNameNodeGen.create(ForeignToLLVM.create(ForeignToLLVMType.FLOAT), LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });
        add("@truffle_read_d", new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleReadFromNameNodeGen.create(ForeignToLLVM.create(ForeignToLLVMType.DOUBLE), LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });
        add("@truffle_read_b", new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleReadFromNameNodeGen.create(ForeignToLLVM.create(ForeignToLLVMType.I1), LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });

        LLVMIntrinsicFactory polyglotGetArrayElement = new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleReadFromIndexNodeGen.create(ForeignToLLVM.create(ForeignToLLVMType.POINTER), LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        };
        add("@polyglot_get_array_element", polyglotGetArrayElement);
        add("@truffle_read_idx", polyglotGetArrayElement);

        add("@truffle_read_idx_i", new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleReadFromIndexNodeGen.create(ForeignToLLVM.create(ForeignToLLVMType.I32), LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });
        add("@truffle_read_idx_l", new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleReadFromIndexNodeGen.create(ForeignToLLVM.create(ForeignToLLVMType.I64), LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });
        add("@truffle_read_idx_c", new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleReadFromIndexNodeGen.create(ForeignToLLVM.create(ForeignToLLVMType.I8), LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });
        add("@truffle_read_idx_f", new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleReadFromIndexNodeGen.create(ForeignToLLVM.create(ForeignToLLVMType.FLOAT), LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });
        add("@truffle_read_idx_d", new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleReadFromIndexNodeGen.create(ForeignToLLVM.create(ForeignToLLVMType.DOUBLE), LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });
        add("@truffle_read_idx_b", new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleReadFromIndexNodeGen.create(ForeignToLLVM.create(ForeignToLLVMType.I1), LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });

        add("@polyglot_remove_member", new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMPolyglotRemoveMemberNodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });

        add("@polyglot_remove_array_element", new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMPolyglotRemoveArrayElementNodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });

        LLVMIntrinsicFactory polyglotAsI8 = new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleUnboxNodeGen.create(ForeignToLLVM.create(ForeignToLLVMType.I8), LLVMArgNodeGen.create(1));
            }
        };
        add("@polyglot_as_i8", polyglotAsI8);
        add("@truffle_unbox_c", polyglotAsI8);

        LLVMIntrinsicFactory polyglotAsI16 = new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleUnboxNodeGen.create(ForeignToLLVM.create(ForeignToLLVMType.I16), LLVMArgNodeGen.create(1));
            }
        };
        add("@polyglot_as_i16", polyglotAsI16);

        LLVMIntrinsicFactory polyglotAsI32 = new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleUnboxNodeGen.create(ForeignToLLVM.create(ForeignToLLVMType.I32), LLVMArgNodeGen.create(1));
            }
        };
        add("@polyglot_as_i32", polyglotAsI32);
        add("@truffle_unbox_i", polyglotAsI32);

        LLVMIntrinsicFactory polyglotAsI64 = new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleUnboxNodeGen.create(ForeignToLLVM.create(ForeignToLLVMType.I64), LLVMArgNodeGen.create(1));
            }
        };
        add("@polyglot_as_i64", polyglotAsI64);
        add("@truffle_unbox_l", polyglotAsI64);

        LLVMIntrinsicFactory polyglotAsFloat = new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleUnboxNodeGen.create(ForeignToLLVM.create(ForeignToLLVMType.FLOAT), LLVMArgNodeGen.create(1));
            }
        };
        add("@polyglot_as_float", polyglotAsFloat);
        add("@truffle_unbox_f", polyglotAsFloat);

        LLVMIntrinsicFactory polyglotAsDouble = new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleUnboxNodeGen.create(ForeignToLLVM.create(ForeignToLLVMType.DOUBLE), LLVMArgNodeGen.create(1));
            }
        };
        add("@polyglot_as_double", polyglotAsDouble);
        add("@truffle_unbox_d", polyglotAsDouble);

        LLVMIntrinsicFactory polyglotAsBoolean = new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleUnboxNodeGen.create(ForeignToLLVM.create(ForeignToLLVMType.I1), LLVMArgNodeGen.create(1));
            }
        };
        add("@polyglot_as_boolean", polyglotAsBoolean);
        add("@truffle_unbox_b", polyglotAsBoolean);

        //

        add("@polyglot_new_instance", new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMPolyglotNewInstanceNodeGen.create(argumentsArray(2, type.getArgumentTypes().length - 2), LLVMArgNodeGen.create(1));
            }
        });

        LLVMIntrinsicFactory polyglotInvoke = new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleInvokeNodeGen.create(ForeignToLLVM.create(ForeignToLLVMType.POINTER), argumentsArray(3, type.getArgumentTypes().length - 3), LLVMArgNodeGen.create(1),
                                LLVMArgNodeGen.create(2));
            }
        };
        add("@polyglot_invoke", polyglotInvoke);
        add("@truffle_invoke", polyglotInvoke);

        add("@truffle_invoke_i", new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleInvokeNodeGen.create(ForeignToLLVM.create(ForeignToLLVMType.I32), argumentsArray(3, type.getArgumentTypes().length - 3), LLVMArgNodeGen.create(1),
                                LLVMArgNodeGen.create(2));
            }
        });

        add("@truffle_invoke_l", new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleInvokeNodeGen.create(ForeignToLLVM.create(ForeignToLLVMType.I64), argumentsArray(3, type.getArgumentTypes().length - 3), LLVMArgNodeGen.create(1),
                                LLVMArgNodeGen.create(2));
            }
        });

        add("@truffle_invoke_c", new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleInvokeNodeGen.create(ForeignToLLVM.create(ForeignToLLVMType.I8), argumentsArray(3, type.getArgumentTypes().length - 3), LLVMArgNodeGen.create(1),
                                LLVMArgNodeGen.create(2));
            }
        });

        add("@truffle_invoke_f", new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleInvokeNodeGen.create(ForeignToLLVM.create(ForeignToLLVMType.FLOAT), argumentsArray(3, type.getArgumentTypes().length - 3), LLVMArgNodeGen.create(1),
                                LLVMArgNodeGen.create(2));
            }
        });

        add("@truffle_invoke_d", new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleInvokeNodeGen.create(ForeignToLLVM.create(ForeignToLLVMType.DOUBLE), argumentsArray(3, type.getArgumentTypes().length - 3), LLVMArgNodeGen.create(1),
                                LLVMArgNodeGen.create(2));
            }
        });

        add("@truffle_invoke_b", new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleInvokeNodeGen.create(ForeignToLLVM.create(ForeignToLLVMType.I1), argumentsArray(3, type.getArgumentTypes().length - 3), LLVMArgNodeGen.create(1),
                                LLVMArgNodeGen.create(2));
            }
        });

        //

        add("@truffle_execute", new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleExecuteNodeGen.create(ForeignToLLVM.create(ForeignToLLVMType.POINTER), argumentsArray(2, type.getArgumentTypes().length - 2), LLVMArgNodeGen.create(1));
            }
        });

        add("@truffle_execute_i", new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleExecuteNodeGen.create(ForeignToLLVM.create(ForeignToLLVMType.I32), argumentsArray(2, type.getArgumentTypes().length - 2), LLVMArgNodeGen.create(1));
            }
        });

        add("@truffle_execute_l", new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleExecuteNodeGen.create(ForeignToLLVM.create(ForeignToLLVMType.I64), argumentsArray(2, type.getArgumentTypes().length - 2), LLVMArgNodeGen.create(1));
            }
        });

        add("@truffle_execute_c", new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleExecuteNodeGen.create(ForeignToLLVM.create(ForeignToLLVMType.I8), argumentsArray(2, type.getArgumentTypes().length - 2), LLVMArgNodeGen.create(1));
            }
        });

        add("@truffle_execute_f", new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleExecuteNodeGen.create(ForeignToLLVM.create(ForeignToLLVMType.FLOAT), argumentsArray(2, type.getArgumentTypes().length - 2), LLVMArgNodeGen.create(1));
            }
        });

        add("@truffle_execute_d", new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleExecuteNodeGen.create(ForeignToLLVM.create(ForeignToLLVMType.DOUBLE), argumentsArray(2, type.getArgumentTypes().length - 2), LLVMArgNodeGen.create(1));
            }
        });

        add("@truffle_execute_b", new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleExecuteNodeGen.create(ForeignToLLVM.create(ForeignToLLVMType.I1), argumentsArray(2, type.getArgumentTypes().length - 2), LLVMArgNodeGen.create(1));
            }
        });

        //

        add("@truffle_address_to_function", new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleAddressToFunctionNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });

        LLVMIntrinsicFactory polyglotCanExecute = new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleIsExecutableNodeGen.create(LLVMArgNodeGen.create(1));
            }
        };
        add("@polyglot_can_execute", polyglotCanExecute);
        add("@truffle_is_executable", polyglotCanExecute);

        add("@polyglot_can_instantiate", new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMPolyglotCanInstantiateNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });

        LLVMIntrinsicFactory polyglotIsNull = new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleIsNullNodeGen.create(LLVMArgNodeGen.create(1));
            }
        };
        add("@polyglot_is_null", polyglotIsNull);
        add("@truffle_is_null", polyglotIsNull);

        LLVMIntrinsicFactory polyglotHasArrayElements = new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleHasSizeNodeGen.create(LLVMArgNodeGen.create(1));
            }
        };
        add("@polyglot_has_array_elements", polyglotHasArrayElements);
        add("@truffle_has_size", polyglotHasArrayElements);

        add("@polyglot_has_members", new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleHasKeysNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });

        add("@truffle_is_boxed", new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleIsBoxedNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });

        add("@polyglot_get_array_size", new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleGetSizeNodeGen.create(ForeignToLLVMType.I64, LLVMArgNodeGen.create(1));
            }
        });

        add("@truffle_get_size", new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleGetSizeNodeGen.create(ForeignToLLVMType.I32, LLVMArgNodeGen.create(1));
            }
        });

        //

        add("@polyglot_get_string_size", new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMPolyglotGetStringSizeNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });

        add("@polyglot_as_string", new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMPolyglotAsString.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3), LLVMArgNodeGen.create(4));
            }
        });

        add("@polyglot_from_string", new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMPolyglotFromString.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });

        add("@polyglot_from_string_n", new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMPolyglotFromString.createN(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3));
            }
        });

        //

        add("@truffle_read_string", new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleReadStringNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });

        add("@truffle_read_n_string", new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleReadNStringNodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });

        add("@truffle_read_bytes", new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleReadBytesNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });

        add("@truffle_read_n_bytes", new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleReadNBytesNodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });

        add("@truffle_string_to_cstr", new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleStringAsCStringNodeGen.create(nodeFactory.createAllocateString(), LLVMArgNodeGen.create(1));
            }
        });

        add("@truffle_free_cstr", new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleFreeCStringNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });

        add("@truffle_is_truffle_object", new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleIsTruffleObjectNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });

        add("@truffle_sulong_function_to_native_pointer", new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMSulongFunctionToNativePointerNodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });

        add("@truffle_load_library", new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMLoadLibraryNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });

        add("@truffle_polyglot_eval", new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMPolyglotEval.createLegacy(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });

        //

        add("@__polyglot_as_typeid", new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTypeIDNode.create(LLVMArgNodeGen.create(1));
            }
        });

        add("@polyglot_as_typed", new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMPolyglotAsTyped.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });

        add("@polyglot_from_typed", new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMPolyglotFromTyped.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });

        add("@polyglot_array_typeid", new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMArrayTypeIDNode.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });

        /*
         * For binary compatibility with bitcode files compiled with polyglot.h from 1.0-RC2 or
         * earlier.
         */

        add("@__polyglot_as_typed", new LLVMIntrinsicFactory(true, true) {

            @Override
            @SuppressWarnings("deprecation")
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMPolyglotAsTyped.createStruct(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });

        add("@__polyglot_as_typed_array", new LLVMIntrinsicFactory(true, true) {

            @Override
            @SuppressWarnings("deprecation")
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMPolyglotAsTyped.createArray(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });

        add("@__polyglot_from_typed", new LLVMIntrinsicFactory(true, true) {

            @Override
            @SuppressWarnings("deprecation")
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMPolyglotFromTyped.createStruct(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });

        add("@__polyglot_from_typed_array", new LLVMIntrinsicFactory(true, true) {

            @Override
            @SuppressWarnings("deprecation")
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMPolyglotFromTyped.createArray(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3));
            }
        });
    }

    protected void registerManagedAllocationIntrinsics() {
        add("@truffle_managed_malloc", new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleManagedMallocNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });

        add("@truffle_handle_for_managed", new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleManagedToHandleNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });

        add("@truffle_deref_handle_for_managed", new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleDerefHandleToManagedNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });

        add("@truffle_release_handle", new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleReleaseHandleNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });

        add("@truffle_managed_from_handle", new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleHandleToManagedNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });

        add("@truffle_is_handle_to_managed", new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleIsHandleToManagedNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });

        add("@truffle_assign_managed", new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTruffleWriteManagedToGlobalNodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });

        add("@truffle_virtual_malloc", new LLVMIntrinsicFactory(true, true) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMVirtualMallocNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });
    }

    protected void registerAbortIntrinsics() {
        add("@_gfortran_abort", new LLVMIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMAbortNodeGen.create();
            }
        });
        add("@signal", new LLVMIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMSignalNodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });

        LLVMIntrinsicFactory syscall = new LLVMIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return new LLVMSyscall();
            }
        };

        add("@syscall", syscall);
        add("@__syscall", syscall);
    }

    protected void registerRustIntrinsics() {
        add("@std::rt::lang_start", new LLVMIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMLangStartNodeGen.create(LLVMArgNodeGen.create(0), LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3));
            }
        });
        add("@std::rt::lang_start_internal", new LLVMIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMLangStartInternalNodeGen.create(LLVMArgNodeGen.create(0), LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3), LLVMArgNodeGen.create(4));
            }
        });
        add("@std::process::exit", new LLVMIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMExitNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });
        add("@core::panicking::panic", new LLVMIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMPanicNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });
    }

    protected void registerMathFunctionIntrinsics() {
        add("@log2", new LLVMIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMLog2NodeGen.create(LLVMArgNodeGen.create(1), null);
            }
        });
        add("@sqrt", new LLVMIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMSqrtNodeGen.create(LLVMArgNodeGen.create(1), null);
            }
        });
        add("@sqrtf", new LLVMIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMSqrtNodeGen.create(LLVMArgNodeGen.create(1), null);
            }
        });
        add("@log", new LLVMIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMLogNodeGen.create(LLVMArgNodeGen.create(1), null);
            }
        });
        add("@log10", new LLVMIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMLog10NodeGen.create(LLVMArgNodeGen.create(1), null);
            }
        });
        add("@rint", new LLVMIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMRintNodeGen.create(LLVMArgNodeGen.create(1), null);
            }
        });
        add("@ceil", new LLVMIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMCeilNodeGen.create(LLVMArgNodeGen.create(1), null);
            }
        });
        add("@floor", new LLVMIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMFloorNodeGen.create(LLVMArgNodeGen.create(1), null);
            }
        });
        add("@abs", new LLVMIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMAbsNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });
        add("@labs", new LLVMIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMLAbsNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });
        add("@fabs", new LLVMIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMFAbsNodeGen.create(LLVMArgNodeGen.create(1), null);
            }
        });
        add("@fabsf", new LLVMIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMFAbsNodeGen.create(LLVMArgNodeGen.create(1), null);
            }
        });
        add("@pow", new LLVMIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMPowNodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), null);
            }
        });
        add("@exp", new LLVMIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMExpNodeGen.create(LLVMArgNodeGen.create(1), null);
            }
        });
        add("@exp2", new LLVMIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMExp2NodeGen.create(LLVMArgNodeGen.create(1), null);
            }
        });

        add("@sin", new LLVMIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMSinNodeGen.create(LLVMArgNodeGen.create(1), null);
            }
        });

        add("@sinf", new LLVMIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMSinNodeGen.create(LLVMArgNodeGen.create(1), null);
            }
        });

        add("@cos", new LLVMIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMCosNodeGen.create(LLVMArgNodeGen.create(1), null);
            }
        });

        add("@cosf", new LLVMIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMCosNodeGen.create(LLVMArgNodeGen.create(1), null);
            }
        });

        add("@tan", new LLVMIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTanNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });

        add("@tanf", new LLVMIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTanNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });

        add("@atan2", new LLVMIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMATan2NodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });

        add("@atan2f", new LLVMIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMATan2NodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });

        add("@asin", new LLVMIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMASinNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });

        add("@asinf", new LLVMIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMASinNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });

        add("@acos", new LLVMIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMACosNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });

        add("@acosf", new LLVMIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMACosNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });

        add("@atan", new LLVMIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMATanNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });

        add("@atanf", new LLVMIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMATanNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });

        add("@sinh", new LLVMIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMSinhNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });

        add("@sinhf", new LLVMIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMSinhNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });

        add("@cosh", new LLVMIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMCoshNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });

        add("@coshf", new LLVMIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMCoshNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });

        add("@tanh", new LLVMIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTanhNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });

        add("@tanhf", new LLVMIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTanhNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });

        add("@ldexp", new LLVMIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMLdexpNodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });

        add("@modf", new LLVMIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMModfNodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });

        add("@fmod", new LLVMIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMFmodNodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });

        add("@fmodl", new LLVMIntrinsicFactory(true, false) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMFmodlNodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });

        add("@copysign", new LLVMIntrinsicFactory(true, false) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMCMathsIntrinsicsFactory.LLVMCopySignNodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), null);
            }
        });
    }

    protected void registerCTypeIntrinsics() {
        add("@isalpha", new LLVMIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMIsalphaNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });
        add("@tolower", new LLVMIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMTolowerNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });
        add("@toupper", new LLVMIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMToUpperNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });
        add("@isspace", new LLVMIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMIsspaceNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });
        add("@isupper", new LLVMIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMIsupperNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });
    }

    protected void registerMemoryFunctionIntrinsics(NodeFactory factory) {
        add("@malloc", new LLVMIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMMallocNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });
        add("@calloc", new LLVMIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMCallocNodeGen.create(factory.createMemSet(), LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });
        add("@realloc", new LLVMIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMReallocNodeGen.create(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2));
            }
        });
        add("@free", new LLVMIntrinsicFactory(true, false) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMFreeNodeGen.create(LLVMArgNodeGen.create(1));
            }
        });
        LLVMIntrinsicFactory memset = new LLVMIntrinsicFactory(true, false) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMLibcMemsetNodeGen.create(factory.createMemSet(), LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3));
            }
        };
        add("@memset", memset);
        add("@__memset_chk", memset);
        LLVMIntrinsicFactory memcpy = new LLVMIntrinsicFactory(true, false) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMLibcMemcpyNodeGen.create(factory.createMemMove(), LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3));
            }
        };
        add("@memcpy", memcpy);
        add("@__memcpy_chk", memcpy);
    }

    protected void registerExceptionIntrinsics() {
        add("@_Unwind_RaiseException", new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return new LLVMRaiseExceptionNode(LLVMArgNodeGen.create(1));
            }
        });
        add("@__cxa_call_unexpected", new LLVMIntrinsicFactory(true, true) {

            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return LLVMAbortNodeGen.create();
            }
        });
    }

    public void registerComplexNumberIntrinsics() {
        // float functions return a vector of <2x float>
        add("@__divsc3", new LLVMIntrinsicFactory(true, false) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return new LLVMComplexFloatDiv(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3), LLVMArgNodeGen.create(4));
            }
        });
        add("@__mulsc3", new LLVMIntrinsicFactory(true, false) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return new LLVMComplexFloatMul(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3), LLVMArgNodeGen.create(4));
            }
        });

        // double functions store their double results in the structure that is passed as arg1
        add("@__divdc3", new LLVMIntrinsicFactory(true, false) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return new LLVMComplexDoubleDiv(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3), LLVMArgNodeGen.create(4), LLVMArgNodeGen.create(5));
            }
        });
        add("@__muldc3", new LLVMIntrinsicFactory(true, false) {
            @Override
            protected LLVMExpressionNode generate(FunctionType type) {
                return new LLVMComplexDoubleMul(LLVMArgNodeGen.create(1), LLVMArgNodeGen.create(2), LLVMArgNodeGen.create(3), LLVMArgNodeGen.create(4), LLVMArgNodeGen.create(5));
            }
        });
    }

    protected void add(String name, LLVMIntrinsicFactory factory) {
        LLVMIntrinsicFactory existing = factories.put(name, factory);
        assert existing == null : "same intrinsic was added more than once";
    }
}
