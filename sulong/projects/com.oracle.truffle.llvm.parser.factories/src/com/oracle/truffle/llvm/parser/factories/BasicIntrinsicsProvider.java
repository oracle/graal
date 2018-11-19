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

import static com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM.ForeignToLLVMType.DOUBLE;
import static com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM.ForeignToLLVMType.FLOAT;
import static com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM.ForeignToLLVMType.I1;
import static com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM.ForeignToLLVMType.I16;
import static com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM.ForeignToLLVMType.I32;
import static com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM.ForeignToLLVMType.I64;
import static com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM.ForeignToLLVMType.I8;
import static com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM.ForeignToLLVMType.POINTER;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
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
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMExpm1NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMFAbsNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMFloorNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMFmodNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMLdexpNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMLog10NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMLog1pNodeGen;
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
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMPolyglotHasMemberNodeGen;
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
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleCannotBeHandleNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleDecorateFunctionNodeGen;
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
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.arith.LLVMComplex80BitFloatDiv;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.arith.LLVMComplex80BitFloatMul;
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
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMContext.ExternalLibrary;
import com.oracle.truffle.llvm.runtime.LLVMIntrinsicProvider;
import com.oracle.truffle.llvm.runtime.NodeFactory;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

/**
 * If an intrinsic is defined for a function, then the intrinsic is used instead of doing a call to
 * native code. The intrinsic is also preferred over LLVM bitcode that is part of a Sulong-internal
 * library.
 */
public class BasicIntrinsicsProvider implements LLVMIntrinsicProvider, ContextExtension {
    private final ExternalLibrary library = ExternalLibrary.internal("SulongIntrinsics", false);

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
        return getFactory(name) != null;
    }

    @Override
    public final RootCallTarget generateIntrinsicTarget(String name, int argCount) {
        CompilerAsserts.neverPartOfCompilation();
        LLVMIntrinsicFactory factory = getFactory(name);
        if (factory == null) {
            return null;
        }
        return wrap(name, factory.generate(new AbstractList<LLVMExpressionNode>() {
            @Override
            public LLVMExpressionNode get(int index) {
                return LLVMArgNodeGen.create(index);
            }

            @Override
            public int size() {
                return argCount;
            }
        }, context.getNodeFactory()));
    }

    @Override
    public final LLVMExpressionNode generateIntrinsicNode(String name, LLVMExpressionNode[] arguments) {
        CompilerAsserts.neverPartOfCompilation();
        LLVMIntrinsicFactory factory = getFactory(name);
        if (factory == null) {
            return null;
        }
        return factory.generate(new AbstractList<LLVMExpressionNode>() {
            @Override
            public LLVMExpressionNode get(int index) {
                return arguments[index];
            }

            @Override
            public int size() {
                return arguments.length;
            }
        }, context.getNodeFactory());
    }

    private LLVMIntrinsicFactory getFactory(String name) {
        LLVMIntrinsicFactory factory = getFactories().get(name);
        if (factory != null) {
            return factory;
        }
        String demangledName = DEMANGLER.demangle(name);
        if (demangledName == null || (factory = getFactories().get(demangledName)) == null) {
            return null;
        }
        // add the demangled name to make subsequent lookups faster
        add(name, factory);
        return factory;
    }

    private RootCallTarget wrap(String functionName, LLVMExpressionNode node) {
        return Truffle.getRuntime().createCallTarget(LLVMIntrinsicExpressionNodeGen.create(context.getLanguage(), functionName, node));
    }

    protected final LLVMContext context;

    public BasicIntrinsicsProvider(LLVMContext context) {
        this.context = context;
    }

    protected Map<String, LLVMIntrinsicFactory> getFactories() {
        return FACTORIES;
    }

    public abstract static class LLVMIntrinsicArgFactory {
        public abstract int size();

        public abstract LLVMExpressionNode get(int index);
    }

    public interface LLVMIntrinsicFactory {
        LLVMExpressionNode generate(List<LLVMExpressionNode> args, NodeFactory nodeFactory);
    }

    protected static final Demangler DEMANGLER = new Demangler();

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

    protected static final ConcurrentHashMap<String, LLVMIntrinsicFactory> FACTORIES = new ConcurrentHashMap<>();

    static {
        // Initialize the list of intrinsics:
        registerTruffleIntrinsics();
        registerSulongIntrinsics();
        registerAbortIntrinsics();
        registerRustIntrinsics();
        registerMathFunctionIntrinsics();
        registerMemoryFunctionIntrinsics();
        registerExceptionIntrinsics();
        registerComplexNumberIntrinsics();
        registerCTypeIntrinsics();
        registerManagedAllocationIntrinsics();
    }

    protected static LLVMExpressionNode[] argumentsArray(List<LLVMExpressionNode> arguments, int startIndex, int arity) {
        LLVMExpressionNode[] args = new LLVMExpressionNode[arity];
        for (int i = 0; i < arity; i++) {
            args[i] = arguments.get(i + startIndex);
        }
        return args;
    }

    private static void registerSulongIntrinsics() {
        add("@__sulong_destructor_functions", (args, factory) -> LLVMRunDestructorFunctionsNodeGen.create());
        add("@__sulong_print_stacktrace", (args, factory) -> LLVMPrintStackTraceNodeGen.create());
        add("@__sulong_should_print_stacktrace_on_abort", (args, factory) -> LLVMShouldPrintStackTraceOnAbortNodeGen.create());
    }

    private static void registerTruffleIntrinsics() {
        LLVMIntrinsicFactory polyglotImport = (args, factory) -> LLVMPolyglotImportNodeGen.create(args.get(1));
        add("@polyglot_import", polyglotImport);
        add("@truffle_import", polyglotImport);
        add("@truffle_import_cached", polyglotImport);
        add("@polyglot_export", (args, factory) -> LLVMPolyglotExportNodeGen.create(args.get(1), args.get(2)));
        add("@polyglot_eval", (args, factory) -> LLVMPolyglotEval.create(args.get(1), args.get(2)));
        add("@polyglot_eval_file", (args, factory) -> LLVMPolyglotEval.createFile(args.get(1), args.get(2)));
        add("@polyglot_java_type", (args, factory) -> LLVMPolyglotJavaTypeNodeGen.create(args.get(1)));

        add("@polyglot_is_value", (args, factory) -> LLVMPolyglotIsValueNodeGen.create(args.get(1)));
        add("@polyglot_is_number", (args, factory) -> LLVMPolyglotBoxedPredicateNodeGen.create(new IsNumber(), args.get(1)));
        add("@polyglot_is_boolean", (args, factory) -> LLVMPolyglotBoxedPredicateNodeGen.create(new IsBoolean(), args.get(1)));
        add("@polyglot_is_string", (args, factory) -> LLVMPolyglotBoxedPredicateNodeGen.create(new IsString(), args.get(1)));
        add("@polyglot_fits_in_i8", (args, factory) -> LLVMPolyglotBoxedPredicateNodeGen.create(FitsInI8NodeGen.create(), args.get(1)));
        add("@polyglot_fits_in_i16", (args, factory) -> LLVMPolyglotBoxedPredicateNodeGen.create(FitsInI16NodeGen.create(), args.get(1)));
        add("@polyglot_fits_in_i32", (args, factory) -> LLVMPolyglotBoxedPredicateNodeGen.create(FitsInI32NodeGen.create(), args.get(1)));
        add("@polyglot_fits_in_i64", (args, factory) -> LLVMPolyglotBoxedPredicateNodeGen.create(FitsInI64NodeGen.create(), args.get(1)));
        add("@polyglot_fits_in_float", (args, factory) -> LLVMPolyglotBoxedPredicateNodeGen.create(FitsInFloatNodeGen.create(), args.get(1)));
        add("@polyglot_fits_in_double", (args, factory) -> LLVMPolyglotBoxedPredicateNodeGen.create(FitsInDoubleNodeGen.create(), args.get(1)));

        add("@polyglot_put_member", "@truffle_write", (args, factory) -> LLVMTruffleWriteToNameNodeGen.create(args.get(1), args.get(2), args.get(3)));

        add("@truffle_write_i", (args, factory) -> LLVMTruffleWriteToNameNodeGen.create(args.get(1), args.get(2), args.get(3)));
        add("@truffle_write_l", (args, factory) -> LLVMTruffleWriteToNameNodeGen.create(args.get(1), args.get(2), args.get(3)));
        add("@truffle_write_c", (args, factory) -> LLVMTruffleWriteToNameNodeGen.create(args.get(1), args.get(2), args.get(3)));
        add("@truffle_write_f", (args, factory) -> LLVMTruffleWriteToNameNodeGen.create(args.get(1), args.get(2), args.get(3)));
        add("@truffle_write_d", (args, factory) -> LLVMTruffleWriteToNameNodeGen.create(args.get(1), args.get(2), args.get(3)));
        add("@truffle_write_b", (args, factory) -> LLVMTruffleWriteToNameNodeGen.create(args.get(1), args.get(2), args.get(3)));

        add("@polyglot_set_array_element", "@truffle_write_idx", (args, factory) -> LLVMTruffleWriteToIndexNodeGen.create(args.get(1), args.get(2), args.get(3)));

        add("@truffle_write_idx_i", (args, factory) -> LLVMTruffleWriteToIndexNodeGen.create(args.get(1), args.get(2), args.get(3)));
        add("@truffle_write_idx_l", (args, factory) -> LLVMTruffleWriteToIndexNodeGen.create(args.get(1), args.get(2), args.get(3)));
        add("@truffle_write_idx_c", (args, factory) -> LLVMTruffleWriteToIndexNodeGen.create(args.get(1), args.get(2), args.get(3)));
        add("@truffle_write_idx_f", (args, factory) -> LLVMTruffleWriteToIndexNodeGen.create(args.get(1), args.get(2), args.get(3)));
        add("@truffle_write_idx_d", (args, factory) -> LLVMTruffleWriteToIndexNodeGen.create(args.get(1), args.get(2), args.get(3)));
        add("@truffle_write_idx_b", (args, factory) -> LLVMTruffleWriteToIndexNodeGen.create(args.get(1), args.get(2), args.get(3)));

        add("@polyglot_get_member", "@truffle_read", (args, factory) -> LLVMTruffleReadFromNameNodeGen.create(factory.createForeignToLLVM(POINTER), args.get(1), args.get(2)));

        add("@truffle_read_i", (args, factory) -> LLVMTruffleReadFromNameNodeGen.create(factory.createForeignToLLVM(I32), args.get(1), args.get(2)));
        add("@truffle_read_l", (args, factory) -> LLVMTruffleReadFromNameNodeGen.create(factory.createForeignToLLVM(I64), args.get(1), args.get(2)));
        add("@truffle_read_c", (args, factory) -> LLVMTruffleReadFromNameNodeGen.create(factory.createForeignToLLVM(I8), args.get(1), args.get(2)));
        add("@truffle_read_f", (args, factory) -> LLVMTruffleReadFromNameNodeGen.create(factory.createForeignToLLVM(FLOAT), args.get(1), args.get(2)));
        add("@truffle_read_d", (args, factory) -> LLVMTruffleReadFromNameNodeGen.create(factory.createForeignToLLVM(DOUBLE), args.get(1), args.get(2)));
        add("@truffle_read_b", (args, factory) -> LLVMTruffleReadFromNameNodeGen.create(factory.createForeignToLLVM(I1), args.get(1), args.get(2)));

        add("@polyglot_get_array_element", "@truffle_read_idx",
                        (args, factory) -> LLVMTruffleReadFromIndexNodeGen.create(factory.createForeignToLLVM(POINTER), args.get(1), args.get(2)));

        add("@truffle_read_idx_i", (args, factory) -> LLVMTruffleReadFromIndexNodeGen.create(factory.createForeignToLLVM(I32), args.get(1), args.get(2)));
        add("@truffle_read_idx_l", (args, factory) -> LLVMTruffleReadFromIndexNodeGen.create(factory.createForeignToLLVM(I64), args.get(1), args.get(2)));
        add("@truffle_read_idx_c", (args, factory) -> LLVMTruffleReadFromIndexNodeGen.create(factory.createForeignToLLVM(I8), args.get(1), args.get(2)));
        add("@truffle_read_idx_f", (args, factory) -> LLVMTruffleReadFromIndexNodeGen.create(factory.createForeignToLLVM(FLOAT), args.get(1), args.get(2)));
        add("@truffle_read_idx_d", (args, factory) -> LLVMTruffleReadFromIndexNodeGen.create(factory.createForeignToLLVM(DOUBLE), args.get(1), args.get(2)));
        add("@truffle_read_idx_b", (args, factory) -> LLVMTruffleReadFromIndexNodeGen.create(factory.createForeignToLLVM(I1), args.get(1), args.get(2)));

        add("@polyglot_remove_member", (args, factory) -> LLVMPolyglotRemoveMemberNodeGen.create(args.get(1), args.get(2)));

        add("@polyglot_remove_array_element", (args, factory) -> LLVMPolyglotRemoveArrayElementNodeGen.create(args.get(1), args.get(2)));

        add("@polyglot_as_i8", "@truffle_unbox_c", (args, factory) -> LLVMTruffleUnboxNodeGen.create(factory.createForeignToLLVM(I8), args.get(1)));
        add("@polyglot_as_i16", (args, factory) -> LLVMTruffleUnboxNodeGen.create(factory.createForeignToLLVM(I16), args.get(1)));
        add("@polyglot_as_i32", "@truffle_unbox_i", (args, factory) -> LLVMTruffleUnboxNodeGen.create(factory.createForeignToLLVM(I32), args.get(1)));
        add("@polyglot_as_i64", "@truffle_unbox_l", (args, factory) -> LLVMTruffleUnboxNodeGen.create(factory.createForeignToLLVM(I64), args.get(1)));
        add("@polyglot_as_float", "@truffle_unbox_f", (args, factory) -> LLVMTruffleUnboxNodeGen.create(factory.createForeignToLLVM(FLOAT), args.get(1)));
        add("@polyglot_as_double", "@truffle_unbox_d", (args, factory) -> LLVMTruffleUnboxNodeGen.create(factory.createForeignToLLVM(DOUBLE), args.get(1)));
        add("@polyglot_as_boolean", "@truffle_unbox_b", (args, factory) -> LLVMTruffleUnboxNodeGen.create(factory.createForeignToLLVM(I1), args.get(1)));

        add("@polyglot_new_instance", (args, factory) -> LLVMPolyglotNewInstanceNodeGen.create(argumentsArray(args, 2, args.size() - 2), args.get(1)));

        add("@polyglot_invoke", "@truffle_invoke",
                        (args, factory) -> LLVMTruffleInvokeNodeGen.create(factory.createForeignToLLVM(POINTER), argumentsArray(args, 3, args.size() - 3), args.get(1), args.get(2)));
        add("@truffle_invoke_i",
                        (args, factory) -> LLVMTruffleInvokeNodeGen.create(factory.createForeignToLLVM(I32), argumentsArray(args, 3, args.size() - 3), args.get(1), args.get(2)));
        add("@truffle_invoke_l",
                        (args, factory) -> LLVMTruffleInvokeNodeGen.create(factory.createForeignToLLVM(I64), argumentsArray(args, 3, args.size() - 3), args.get(1), args.get(2)));
        add("@truffle_invoke_c",
                        (args, factory) -> LLVMTruffleInvokeNodeGen.create(factory.createForeignToLLVM(I8), argumentsArray(args, 3, args.size() - 3), args.get(1), args.get(2)));
        add("@truffle_invoke_f",
                        (args, factory) -> LLVMTruffleInvokeNodeGen.create(factory.createForeignToLLVM(FLOAT), argumentsArray(args, 3, args.size() - 3), args.get(1), args.get(2)));
        add("@truffle_invoke_d",
                        (args, factory) -> LLVMTruffleInvokeNodeGen.create(factory.createForeignToLLVM(DOUBLE), argumentsArray(args, 3, args.size() - 3), args.get(1), args.get(2)));
        add("@truffle_invoke_b",
                        (args, factory) -> LLVMTruffleInvokeNodeGen.create(factory.createForeignToLLVM(I1), argumentsArray(args, 3, args.size() - 3), args.get(1), args.get(2)));

        add("@truffle_execute", (args, factory) -> LLVMTruffleExecuteNodeGen.create(factory.createForeignToLLVM(POINTER), argumentsArray(args, 2, args.size() - 2), args.get(1)));
        add("@truffle_execute_i", (args, factory) -> LLVMTruffleExecuteNodeGen.create(factory.createForeignToLLVM(I32), argumentsArray(args, 2, args.size() - 2), args.get(1)));
        add("@truffle_execute_l", (args, factory) -> LLVMTruffleExecuteNodeGen.create(factory.createForeignToLLVM(I64), argumentsArray(args, 2, args.size() - 2), args.get(1)));
        add("@truffle_execute_c", (args, factory) -> LLVMTruffleExecuteNodeGen.create(factory.createForeignToLLVM(I8), argumentsArray(args, 2, args.size() - 2), args.get(1)));
        add("@truffle_execute_f", (args, factory) -> LLVMTruffleExecuteNodeGen.create(factory.createForeignToLLVM(FLOAT), argumentsArray(args, 2, args.size() - 2), args.get(1)));
        add("@truffle_execute_d",
                        (args, factory) -> LLVMTruffleExecuteNodeGen.create(factory.createForeignToLLVM(DOUBLE), argumentsArray(args, 2, args.size() - 2), args.get(1)));
        add("@truffle_execute_b", (args, factory) -> LLVMTruffleExecuteNodeGen.create(factory.createForeignToLLVM(I1), argumentsArray(args, 2, args.size() - 2), args.get(1)));

        add("@truffle_address_to_function", (args, factory) -> LLVMTruffleAddressToFunctionNodeGen.create(args.get(1)));
        add("@truffle_decorate_function", (args, factory) -> LLVMTruffleDecorateFunctionNodeGen.create(args.get(1), args.get(2)));
        add("@polyglot_can_execute", "@truffle_is_executable", (args, factory) -> LLVMTruffleIsExecutableNodeGen.create(args.get(1)));
        add("@polyglot_can_instantiate", (args, factory) -> LLVMPolyglotCanInstantiateNodeGen.create(args.get(1)));
        add("@polyglot_is_null", "@truffle_is_null", (args, factory) -> LLVMTruffleIsNullNodeGen.create(args.get(1)));
        add("@polyglot_has_array_elements", "@truffle_has_size", (args, factory) -> LLVMTruffleHasSizeNodeGen.create(args.get(1)));
        add("@polyglot_has_members", (args, factory) -> LLVMTruffleHasKeysNodeGen.create(args.get(1)));
        add("@polyglot_has_member", (args, factory) -> LLVMPolyglotHasMemberNodeGen.create(args.get(1), args.get(2)));
        add("@truffle_is_boxed", (args, factory) -> LLVMTruffleIsBoxedNodeGen.create(args.get(1)));
        add("@polyglot_get_array_size", (args, factory) -> LLVMTruffleGetSizeNodeGen.create(I64, args.get(1)));
        add("@truffle_get_size", (args, factory) -> LLVMTruffleGetSizeNodeGen.create(I32, args.get(1)));

        add("@polyglot_get_string_size", (args, factory) -> LLVMPolyglotGetStringSizeNodeGen.create(args.get(1)));
        add("@polyglot_as_string", (args, factory) -> LLVMPolyglotAsString.create(args.get(1), args.get(2), args.get(3), args.get(4)));
        add("@polyglot_from_string", (args, factory) -> LLVMPolyglotFromString.create(args.get(1), args.get(2)));
        add("@polyglot_from_string_n", (args, factory) -> LLVMPolyglotFromString.createN(args.get(1), args.get(2), args.get(3)));

        add("@truffle_read_string", (args, factory) -> LLVMTruffleReadStringNodeGen.create(args.get(1)));
        add("@truffle_read_n_string", (args, factory) -> LLVMTruffleReadNStringNodeGen.create(args.get(1), args.get(2)));
        add("@truffle_read_bytes", (args, factory) -> LLVMTruffleReadBytesNodeGen.create(args.get(1)));
        add("@truffle_read_n_bytes", (args, factory) -> LLVMTruffleReadNBytesNodeGen.create(args.get(1), args.get(2)));
        add("@truffle_string_to_cstr", (args, factory) -> LLVMTruffleStringAsCStringNodeGen.create(factory.createAllocateString(), args.get(1)));
        add("@truffle_free_cstr", (args, factory) -> LLVMTruffleFreeCStringNodeGen.create(args.get(1)));
        add("@truffle_is_truffle_object", (args, factory) -> LLVMTruffleIsTruffleObjectNodeGen.create(args.get(1)));
        add("@truffle_sulong_function_to_native_pointer", (args, factory) -> LLVMSulongFunctionToNativePointerNodeGen.create(args.get(1), args.get(2)));
        add("@truffle_load_library", (args, factory) -> LLVMLoadLibraryNodeGen.create(args.get(1)));
        add("@truffle_polyglot_eval", (args, factory) -> LLVMPolyglotEval.createLegacy(args.get(1), args.get(2)));

        add("@__polyglot_as_typeid", (args, factory) -> LLVMTypeIDNode.create(args.get(1)));
        add("@polyglot_as_typed", (args, factory) -> LLVMPolyglotAsTyped.create(args.get(1), args.get(2)));
        add("@polyglot_from_typed", (args, factory) -> LLVMPolyglotFromTyped.create(args.get(1), args.get(2)));
        add("@polyglot_array_typeid", (args, factory) -> LLVMArrayTypeIDNode.create(args.get(1), args.get(2)));

        registerObsoleteTruffleIntrinsics();
    }

    @SuppressWarnings("deprecation")
    private static void registerObsoleteTruffleIntrinsics() {
        /*
         * For binary compatibility with bitcode files compiled with polyglot.h from 1.0-RC2 or
         * earlier.
         */
        add("@__polyglot_as_typed", (args, factory) -> LLVMPolyglotAsTyped.createStruct(args.get(1), args.get(2)));
        add("@__polyglot_as_typed_array", (args, factory) -> LLVMPolyglotAsTyped.createArray(args.get(1), args.get(2)));
        add("@__polyglot_from_typed", (args, factory) -> LLVMPolyglotFromTyped.createStruct(args.get(1), args.get(2)));
        add("@__polyglot_from_typed_array", (args, factory) -> LLVMPolyglotFromTyped.createArray(args.get(1), args.get(2), args.get(3)));
    }

    private static void registerManagedAllocationIntrinsics() {
        add("@truffle_managed_malloc", (args, factory) -> LLVMTruffleManagedMallocNodeGen.create(args.get(1)));
        add("@truffle_handle_for_managed", (args, factory) -> LLVMTruffleManagedToHandleNodeGen.create(args.get(1)));
        add("@truffle_deref_handle_for_managed", (args, factory) -> LLVMTruffleDerefHandleToManagedNodeGen.create(args.get(1)));
        add("@truffle_release_handle", (args, factory) -> LLVMTruffleReleaseHandleNodeGen.create(args.get(1)));
        add("@truffle_managed_from_handle", (args, factory) -> LLVMTruffleHandleToManagedNodeGen.create(args.get(1)));
        add("@truffle_is_handle_to_managed", (args, factory) -> LLVMTruffleIsHandleToManagedNodeGen.create(args.get(1)));
        add("@truffle_cannot_be_handle", (args, factory) -> LLVMTruffleCannotBeHandleNodeGen.create(args.get(1)));
        add("@truffle_assign_managed", (args, factory) -> LLVMTruffleWriteManagedToGlobalNodeGen.create(args.get(1), args.get(2)));
        add("@truffle_virtual_malloc", (args, factory) -> LLVMVirtualMallocNodeGen.create(args.get(1)));
    }

    private static void registerAbortIntrinsics() {
        add("@_gfortran_abort", (args, factory) -> LLVMAbortNodeGen.create());
        add("@signal", (args, factory) -> LLVMSignalNodeGen.create(args.get(1), args.get(2)));
        add("@syscall", "@__syscall", (args, factory) -> LLVMSyscall.create(argumentsArray(args, 1, args.size() - 1)));
    }

    private static void registerRustIntrinsics() {
        add("@std::rt::lang_start", (args, factory) -> LLVMLangStartNodeGen.create(args.get(0), args.get(1), args.get(2), args.get(3)));
        add("@std::rt::lang_start_internal", (args, factory) -> LLVMLangStartInternalNodeGen.create(args.get(0), args.get(1), args.get(2), args.get(3), args.get(4)));
        add("@std::process::exit", (args, factory) -> LLVMExitNodeGen.create(args.get(1)));
        add("@core::panicking::panic", (args, factory) -> LLVMPanicNodeGen.create(args.get(1)));
    }

    private static void registerMathFunctionIntrinsics() {
        // TODO (chaeubl): There is no doubt that not all of these intrinsics are valid as they use
        // double arithmetics to simulate floating arithmetics, which can change the precision.
        // Furthermore, it is possible that there are mismatches between Java and C semantics.
        addFloatingPointMathFunction("@sqrt", (args, factory) -> LLVMSqrtNodeGen.create(args.get(1), null));
        addFloatingPointMathFunction("@log", (args, factory) -> LLVMLogNodeGen.create(args.get(1), null));
        addFloatingPointMathFunction("@log2", (args, factory) -> LLVMLog2NodeGen.create(args.get(1), null));
        addFloatingPointMathFunction("@log10", (args, factory) -> LLVMLog10NodeGen.create(args.get(1), null));
        addFloatingPointMathFunction("@log1p", (args, factory) -> LLVMLog1pNodeGen.create(args.get(1), null));
        addFloatingPointMathFunction("@rint", (args, factory) -> LLVMRintNodeGen.create(args.get(1), null));
        addFloatingPointMathFunction("@ceil", (args, factory) -> LLVMCeilNodeGen.create(args.get(1), null));
        addFloatingPointMathFunction("@floor", (args, factory) -> LLVMFloorNodeGen.create(args.get(1), null));
        addFloatingPointMathFunction("@fabs", (args, factory) -> LLVMFAbsNodeGen.create(args.get(1), null));
        addFloatingPointMathFunction("@pow", (args, factory) -> LLVMPowNodeGen.create(args.get(1), args.get(2), null));
        addFloatingPointMathFunction("@exp", (args, factory) -> LLVMExpNodeGen.create(args.get(1), null));
        addFloatingPointMathFunction("@exp2", (args, factory) -> LLVMExp2NodeGen.create(args.get(1), null));
        addFloatingPointMathFunction("@expm1", (args, factory) -> LLVMExpm1NodeGen.create(args.get(1), null));
        addFloatingPointMathFunction("@sin", (args, factory) -> LLVMSinNodeGen.create(args.get(1), null));
        addFloatingPointMathFunction("@cos", (args, factory) -> LLVMCosNodeGen.create(args.get(1), null));
        addFloatingPointMathFunction("@tan", (args, factory) -> LLVMTanNodeGen.create(args.get(1)));
        addFloatingPointMathFunction("@atan2", (args, factory) -> LLVMATan2NodeGen.create(args.get(1), args.get(2)));
        addFloatingPointMathFunction("@asin", (args, factory) -> LLVMASinNodeGen.create(args.get(1)));
        addFloatingPointMathFunction("@acos", (args, factory) -> LLVMACosNodeGen.create(args.get(1)));
        addFloatingPointMathFunction("@atan", (args, factory) -> LLVMATanNodeGen.create(args.get(1)));
        addFloatingPointMathFunction("@sinh", (args, factory) -> LLVMSinhNodeGen.create(args.get(1)));
        addFloatingPointMathFunction("@cosh", (args, factory) -> LLVMCoshNodeGen.create(args.get(1)));
        addFloatingPointMathFunction("@tanh", (args, factory) -> LLVMTanhNodeGen.create(args.get(1)));
        addFloatingPointMathFunction("@ldexp", (args, factory) -> LLVMLdexpNodeGen.create(args.get(1), args.get(2)));
        addFloatingPointMathFunction("@modf", (args, factory) -> LLVMModfNodeGen.create(args.get(1), args.get(2)));
        addFloatingPointMathFunction("@fmod", (args, factory) -> LLVMFmodNodeGen.create(args.get(1), args.get(2)));
        addFloatingPointMathFunction("@copysign", (args, factory) -> LLVMCMathsIntrinsicsFactory.LLVMCopySignNodeGen.create(args.get(1), args.get(2), null));

        addIntegerMathFunction("@abs", (args, factory) -> LLVMAbsNodeGen.create(args.get(1)));
    }

    private static void registerCTypeIntrinsics() {
        add("@isalpha", (args, factory) -> LLVMIsalphaNodeGen.create(args.get(1)));
        add("@tolower", (args, factory) -> LLVMTolowerNodeGen.create(args.get(1)));
        add("@toupper", (args, factory) -> LLVMToUpperNodeGen.create(args.get(1)));
        add("@isspace", (args, factory) -> LLVMIsspaceNodeGen.create(args.get(1)));
        add("@isupper", (args, factory) -> LLVMIsupperNodeGen.create(args.get(1)));
    }

    private static void registerMemoryFunctionIntrinsics() {
        add("@malloc", (args, factory) -> LLVMMallocNodeGen.create(args.get(1)));
        add("@calloc", (args, factory) -> LLVMCallocNodeGen.create(factory.createMemSet(), args.get(1), args.get(2)));
        add("@realloc", (args, factory) -> LLVMReallocNodeGen.create(args.get(1), args.get(2)));
        add("@free", (args, factory) -> LLVMFreeNodeGen.create(args.get(1)));
        add("@memset", "@__memset_chk", (args, factory) -> LLVMLibcMemsetNodeGen.create(factory.createMemSet(), args.get(1), args.get(2), args.get(3)));
        add("@memcpy", "@__memcpy_chk", (args, factory) -> LLVMLibcMemcpyNodeGen.create(factory.createMemMove(), args.get(1), args.get(2), args.get(3)));
    }

    private static void registerExceptionIntrinsics() {
        add("@_Unwind_RaiseException", (args, factory) -> new LLVMRaiseExceptionNode(args.get(1)));
        add("@__cxa_call_unexpected", (args, factory) -> LLVMAbortNodeGen.create());
    }

    private static void registerComplexNumberIntrinsics() {
        // float functions return a vector of <2x float>
        add("@__divsc3", (args, factory) -> new LLVMComplexFloatDiv(args.get(1), args.get(2), args.get(3), args.get(4)));
        add("@__mulsc3", (args, factory) -> new LLVMComplexFloatMul(args.get(1), args.get(2), args.get(3), args.get(4)));

        // double functions store their double results in the structure that is passed as arg1
        add("@__divdc3", (args, factory) -> new LLVMComplexDoubleDiv(args.get(1), args.get(2), args.get(3), args.get(4), args.get(5)));
        add("@__muldc3", (args, factory) -> new LLVMComplexDoubleMul(args.get(1), args.get(2), args.get(3), args.get(4), args.get(5)));

        // 80-bit FP functions store their results in the structure that is passed as arg1
        add("@__divxc3", (args, factory) -> new LLVMComplex80BitFloatDiv(args.get(1), args.get(2), args.get(3), args.get(4), args.get(5)));
        add("@__mulxc3", (args, factory) -> new LLVMComplex80BitFloatMul(args.get(1), args.get(2), args.get(3), args.get(4), args.get(5)));
    }

    private static void add(String name, LLVMIntrinsicFactory factory) {
        LLVMIntrinsicFactory existing = FACTORIES.put(name, factory);
        assert existing == null : "same intrinsic was added more than once";
        assert name.length() > 0 && name.charAt(0) == '@';
    }

    private static void add(String name1, String name2, LLVMIntrinsicFactory factory) {
        add(name1, factory);
        add(name2, factory);
    }

    private static void addFloatingPointMathFunction(String functionName, LLVMIntrinsicFactory factory) {
        add(functionName, factory);
        add(functionName + "f", factory);
        add(functionName + "l", factory);
    }

    private static void addIntegerMathFunction(String functionName, LLVMIntrinsicFactory factory) {
        add(functionName, factory);
        add(functionName.replaceFirst("@", "@l") + functionName, factory);
    }
}
