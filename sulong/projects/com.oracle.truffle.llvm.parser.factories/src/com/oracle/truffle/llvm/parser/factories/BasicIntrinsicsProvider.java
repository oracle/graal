/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
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
import com.oracle.truffle.api.interop.InteropLibrary;
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
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMMaxnumNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMMinnumNodeGen;
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
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMLoadLibraryNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMPolyglotAsPrimitive;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMPolyglotAsString;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMPolyglotBoxedPredicateNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMPolyglotEval;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMPolyglotExportNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMPolyglotFromString;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMPolyglotGetArraySizeNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMPolyglotGetStringSizeNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMPolyglotHasMemberNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMPolyglotImportNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMPolyglotInvokeNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMPolyglotIsValueNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMPolyglotJavaTypeNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMPolyglotNewInstanceNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMPolyglotReadFactory.LLVMPolyglotGetArrayElementNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMPolyglotReadFactory.LLVMPolyglotGetMemberNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMPolyglotRemoveFactory.LLVMPolyglotRemoveArrayElementNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMPolyglotRemoveFactory.LLVMPolyglotRemoveMemberNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMPolyglotWriteFactory.LLVMPolyglotPutMemberNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMPolyglotWriteFactory.LLVMPolyglotSetArrayElementNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleCannotBeHandleNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleDecorateFunctionNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleDerefHandleToManagedNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleHandleToManagedNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleIsHandleToManagedNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleManagedMallocNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleManagedToHandleNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleReleaseHandleNodeGen;
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
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.arith.LLVMComplex80BitFloatDivNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.arith.LLVMComplex80BitFloatMulNodeGen;
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
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.types.Type;

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
    public final RootCallTarget generateIntrinsicTarget(String name, Type[] argTypes) {
        CompilerAsserts.neverPartOfCompilation();
        LLVMTypedIntrinsicFactory factory = getFactory(name);
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
                return argTypes.length;
            }
        }, language, argTypes));
    }

    @Override
    public final LLVMExpressionNode generateIntrinsicNode(String name, LLVMExpressionNode[] arguments, Type[] argTypes) {
        CompilerAsserts.neverPartOfCompilation();
        LLVMTypedIntrinsicFactory factory = getFactory(name);
        if (factory == null) {
            return null;
        }
        return factory.generate(Arrays.asList(arguments), language, argTypes);
    }

    private LLVMTypedIntrinsicFactory getFactory(String name) {
        LLVMTypedIntrinsicFactory factory = getFactories().get(name);
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
        return Truffle.getRuntime().createCallTarget(LLVMIntrinsicExpressionNodeGen.create(language, functionName, node));
    }

    protected final LLVMLanguage language;

    public BasicIntrinsicsProvider(LLVMLanguage language) {
        this.language = language;
    }

    protected Map<String, LLVMTypedIntrinsicFactory> getFactories() {
        return FACTORIES;
    }

    public abstract static class LLVMIntrinsicArgFactory {
        public abstract int size();

        public abstract LLVMExpressionNode get(int index);
    }

    public interface LLVMTypedIntrinsicFactory {
        LLVMExpressionNode generate(List<LLVMExpressionNode> args, LLVMLanguage language, Type[] argTypes);
    }

    public interface LLVMIntrinsicFactory extends LLVMTypedIntrinsicFactory {
        LLVMExpressionNode generate(List<LLVMExpressionNode> args, LLVMLanguage language);

        @Override
        default LLVMExpressionNode generate(List<LLVMExpressionNode> args, LLVMLanguage language, Type[] argTypes) {
            return generate(args, language);
        }
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
                if (!(scanner.skip("_ZN") || scanner.skip("ZN"))) {
                    return null;
                }

                StringBuilder builder = new StringBuilder();
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

    protected static final ConcurrentHashMap<String, LLVMTypedIntrinsicFactory> FACTORIES = new ConcurrentHashMap<>();

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
        add("__sulong_destructor_functions", (args, language) -> LLVMRunDestructorFunctionsNodeGen.create());
        add("__sulong_print_stacktrace", (args, language) -> LLVMPrintStackTraceNodeGen.create());
        add("__sulong_should_print_stacktrace_on_abort", (args, language) -> LLVMShouldPrintStackTraceOnAbortNodeGen.create());
    }

    private static void registerTruffleIntrinsics() {
        add("polyglot_import", (args, language) -> LLVMPolyglotImportNodeGen.create(args.get(1)));
        add("polyglot_export", (args, language) -> LLVMPolyglotExportNodeGen.create(args.get(1), args.get(2)));
        add("polyglot_eval", (args, language) -> LLVMPolyglotEval.create(args.get(1), args.get(2)));
        add("polyglot_eval_file", (args, language) -> LLVMPolyglotEval.createFile(args.get(1), args.get(2)));
        add("polyglot_java_type", (args, language) -> LLVMPolyglotJavaTypeNodeGen.create(args.get(1)));

        add("polyglot_is_value", (args, language) -> LLVMPolyglotIsValueNodeGen.create(args.get(1)));
        add("polyglot_is_number", (args, language) -> LLVMPolyglotBoxedPredicateNodeGen.create(InteropLibrary::isNumber, args.get(1)));
        add("polyglot_is_boolean", (args, language) -> LLVMPolyglotBoxedPredicateNodeGen.create(InteropLibrary::isBoolean, args.get(1)));
        add("polyglot_is_string", (args, language) -> LLVMPolyglotBoxedPredicateNodeGen.create(InteropLibrary::isString, args.get(1)));
        add("polyglot_fits_in_i8", (args, language) -> LLVMPolyglotBoxedPredicateNodeGen.create(InteropLibrary::fitsInByte, args.get(1)));
        add("polyglot_fits_in_i16", (args, language) -> LLVMPolyglotBoxedPredicateNodeGen.create(InteropLibrary::fitsInShort, args.get(1)));
        add("polyglot_fits_in_i32", (args, language) -> LLVMPolyglotBoxedPredicateNodeGen.create(InteropLibrary::fitsInInt, args.get(1)));
        add("polyglot_fits_in_i64", (args, language) -> LLVMPolyglotBoxedPredicateNodeGen.create(InteropLibrary::fitsInLong, args.get(1)));
        add("polyglot_fits_in_float", (args, language) -> LLVMPolyglotBoxedPredicateNodeGen.create(InteropLibrary::fitsInFloat, args.get(1)));
        add("polyglot_fits_in_double", (args, language) -> LLVMPolyglotBoxedPredicateNodeGen.create(InteropLibrary::fitsInDouble, args.get(1)));

        add("polyglot_put_member", (args, language, types) -> LLVMPolyglotPutMemberNodeGen.create(types, args.get(1), args.get(2), args.get(3)));

        add("polyglot_set_array_element", (args, language, types) -> LLVMPolyglotSetArrayElementNodeGen.create(types, args.get(1), args.get(2), args.get(3)));

        add("polyglot_get_member", (args, language) -> LLVMPolyglotGetMemberNodeGen.create(LLVMLanguage.getLanguage().getNodeFactory().createForeignToLLVM(POINTER), args.get(1), args.get(2)));

        add("polyglot_get_array_element",
                        (args, language) -> LLVMPolyglotGetArrayElementNodeGen.create(LLVMLanguage.getLanguage().getNodeFactory().createForeignToLLVM(POINTER), args.get(1), args.get(2)));

        add("polyglot_remove_member", (args, language) -> LLVMPolyglotRemoveMemberNodeGen.create(args.get(1), args.get(2)));

        add("polyglot_remove_array_element", (args, language) -> LLVMPolyglotRemoveArrayElementNodeGen.create(args.get(1), args.get(2)));

        add("polyglot_as_i8", (args, language) -> LLVMPolyglotAsPrimitive.AsI8.create(args.get(1)));
        add("polyglot_as_i16", (args, language) -> LLVMPolyglotAsPrimitive.AsI16.create(args.get(1)));
        add("polyglot_as_i32", (args, language) -> LLVMPolyglotAsPrimitive.AsI32.create(args.get(1)));
        add("polyglot_as_i64", (args, language) -> LLVMPolyglotAsPrimitive.AsI64.create(args.get(1)));
        add("polyglot_as_float", (args, language) -> LLVMPolyglotAsPrimitive.AsFloat.create(args.get(1)));
        add("polyglot_as_double", (args, language) -> LLVMPolyglotAsPrimitive.AsDouble.create(args.get(1)));
        add("polyglot_as_boolean", (args, language) -> LLVMPolyglotAsPrimitive.AsBoolean.create(args.get(1)));

        add("polyglot_new_instance",
                        (args, language, types) -> LLVMPolyglotNewInstanceNodeGen.create(argumentsArray(args, 2, args.size() - 2),
                                        Arrays.copyOfRange(types, 2, types.length),
                                        args.get(1)));

        add("polyglot_invoke",
                        (args, language, types) -> LLVMPolyglotInvokeNodeGen.create(LLVMLanguage.getLanguage().getNodeFactory().createForeignToLLVM(POINTER), argumentsArray(args, 3, args.size() - 3),
                                        Arrays.copyOfRange(types, 3, types.length),
                                        args.get(1), args.get(2)));

        add("truffle_decorate_function", (args, language) -> LLVMTruffleDecorateFunctionNodeGen.create(args.get(1), args.get(2)));
        add("polyglot_can_execute", (args, language) -> LLVMPolyglotBoxedPredicateNodeGen.create(InteropLibrary::isExecutable, args.get(1)));
        add("polyglot_can_instantiate", (args, language) -> LLVMPolyglotBoxedPredicateNodeGen.create(InteropLibrary::isInstantiable, args.get(1)));
        add("polyglot_is_null", (args, language) -> LLVMPolyglotBoxedPredicateNodeGen.create(InteropLibrary::isNull, args.get(1)));
        add("polyglot_has_array_elements", (args, language) -> LLVMPolyglotBoxedPredicateNodeGen.create(InteropLibrary::hasArrayElements, args.get(1)));
        add("polyglot_has_members", (args, language) -> LLVMPolyglotBoxedPredicateNodeGen.create(InteropLibrary::hasMembers, args.get(1)));
        add("polyglot_has_member", (args, language) -> LLVMPolyglotHasMemberNodeGen.create(args.get(1), args.get(2)));
        add("polyglot_get_array_size", (args, language) -> LLVMPolyglotGetArraySizeNodeGen.create(args.get(1)));

        add("polyglot_get_string_size", (args, language) -> LLVMPolyglotGetStringSizeNodeGen.create(args.get(1)));
        add("polyglot_as_string", (args, language) -> LLVMPolyglotAsString.create(args.get(1), args.get(2), args.get(3), args.get(4)));
        add("polyglot_from_string", (args, language) -> LLVMPolyglotFromString.create(args.get(1), args.get(2)));
        add("polyglot_from_string_n", (args, language) -> LLVMPolyglotFromString.createN(args.get(1), args.get(2), args.get(3)));

        add("truffle_load_library", (args, language) -> LLVMLoadLibraryNodeGen.create(args.get(1)));

        add("__polyglot_as_typeid", (args, language) -> LLVMTypeIDNode.create(args.get(1)));
        add("polyglot_as_typed", (args, language) -> LLVMPolyglotAsTyped.create(args.get(1), args.get(2)));
        add("polyglot_from_typed", (args, language) -> LLVMPolyglotFromTyped.create(args.get(1), args.get(2)));
        add("polyglot_array_typeid", (args, language) -> LLVMArrayTypeIDNode.create(args.get(1), args.get(2)));
    }

    private static void registerManagedAllocationIntrinsics() {
        add("truffle_managed_malloc", (args, language) -> LLVMTruffleManagedMallocNodeGen.create(args.get(1)));
        add("truffle_handle_for_managed", (args, language) -> LLVMTruffleManagedToHandleNodeGen.create(args.get(1)));
        add("truffle_deref_handle_for_managed", (args, language) -> LLVMTruffleDerefHandleToManagedNodeGen.create(args.get(1)));
        add("truffle_release_handle", (args, language) -> LLVMTruffleReleaseHandleNodeGen.create(args.get(1)));
        add("truffle_managed_from_handle", (args, language) -> LLVMTruffleHandleToManagedNodeGen.create(args.get(1)));
        add("truffle_is_handle_to_managed", (args, language) -> LLVMTruffleIsHandleToManagedNodeGen.create(args.get(1)));
        add("truffle_cannot_be_handle", (args, language) -> LLVMTruffleCannotBeHandleNodeGen.create(args.get(1)));
        add("truffle_assign_managed", (args, language) -> LLVMTruffleWriteManagedToGlobalNodeGen.create(args.get(1), args.get(2)));
        add("truffle_virtual_malloc", (args, language) -> LLVMVirtualMallocNodeGen.create(args.get(1)));
    }

    private static void registerAbortIntrinsics() {
        add("_gfortran_abort", (args, language) -> LLVMAbortNodeGen.create());
        add("signal", (args, language) -> LLVMSignalNodeGen.create(args.get(1), args.get(2)));
        add("syscall", "__syscall", (args, language) -> LLVMSyscall.create(argumentsArray(args, 1, args.size() - 1)));
    }

    private static void registerRustIntrinsics() {
        add("std::rt::lang_start", (args, language) -> LLVMLangStartNodeGen.create(args.get(0), args.get(1), args.get(2), args.get(3)));
        add("std::rt::lang_start_internal", (args, language) -> LLVMLangStartInternalNodeGen.create(args.get(0), args.get(1), args.get(2), args.get(3), args.get(4)));
        add("std::process::exit", (args, language) -> LLVMExitNodeGen.create(args.get(1)));
        add("core::panicking::panic", (args, language) -> LLVMPanicNodeGen.create(args.get(1)));
    }

    private static void registerMathFunctionIntrinsics() {
        // TODO (chaeubl): There is no doubt that not all of these intrinsics are valid as they use
        // double arithmetics to simulate floating arithmetics, which can change the precision.
        // Furthermore, it is possible that there are mismatches between Java and C semantics.
        addFloatingPointMathFunction("sqrt", (args, language) -> LLVMSqrtNodeGen.create(args.get(1), null));
        addFloatingPointMathFunction("log", (args, language) -> LLVMLogNodeGen.create(args.get(1), null));
        addFloatingPointMathFunction("log2", (args, language) -> LLVMLog2NodeGen.create(args.get(1), null));
        addFloatingPointMathFunction("log10", (args, language) -> LLVMLog10NodeGen.create(args.get(1), null));
        addFloatingPointMathFunction("log1p", (args, language) -> LLVMLog1pNodeGen.create(args.get(1), null));
        addFloatingPointMathFunction("rint", (args, language) -> LLVMRintNodeGen.create(args.get(1), null));
        addFloatingPointMathFunction("ceil", (args, language) -> LLVMCeilNodeGen.create(args.get(1), null));
        addFloatingPointMathFunction("floor", (args, language) -> LLVMFloorNodeGen.create(args.get(1), null));
        addFloatingPointMathFunction("fabs", (args, language) -> LLVMFAbsNodeGen.create(args.get(1), null));
        addFloatingPointMathFunction("minnum", (args, language) -> LLVMMinnumNodeGen.create(args.get(1), args.get(2), null));
        addFloatingPointMathFunction("maxnum", (args, language) -> LLVMMaxnumNodeGen.create(args.get(1), args.get(2), null));
        addFloatingPointMathFunction("pow", (args, language) -> LLVMPowNodeGen.create(args.get(1), args.get(2), null));
        addFloatingPointMathFunction("exp", (args, language) -> LLVMExpNodeGen.create(args.get(1), null));
        addFloatingPointMathFunction("exp2", (args, language) -> LLVMExp2NodeGen.create(args.get(1), null));
        addFloatingPointMathFunction("expm1", (args, language) -> LLVMExpm1NodeGen.create(args.get(1), null));
        addFloatingPointMathFunction("sin", (args, language) -> LLVMSinNodeGen.create(args.get(1), null));
        addFloatingPointMathFunction("cos", (args, language) -> LLVMCosNodeGen.create(args.get(1), null));
        addFloatingPointMathFunction("tan", (args, language) -> LLVMTanNodeGen.create(args.get(1)));
        addFloatingPointMathFunction("atan2", (args, language) -> LLVMATan2NodeGen.create(args.get(1), args.get(2)));
        addFloatingPointMathFunction("asin", (args, language) -> LLVMASinNodeGen.create(args.get(1)));
        addFloatingPointMathFunction("acos", (args, language) -> LLVMACosNodeGen.create(args.get(1)));
        addFloatingPointMathFunction("atan", (args, language) -> LLVMATanNodeGen.create(args.get(1)));
        addFloatingPointMathFunction("sinh", (args, language) -> LLVMSinhNodeGen.create(args.get(1)));
        addFloatingPointMathFunction("cosh", (args, language) -> LLVMCoshNodeGen.create(args.get(1)));
        addFloatingPointMathFunction("tanh", (args, language) -> LLVMTanhNodeGen.create(args.get(1)));
        addFloatingPointMathFunction("ldexp", (args, language) -> LLVMLdexpNodeGen.create(args.get(1), args.get(2)));
        addFloatingPointMathFunction("modf", (args, language) -> LLVMModfNodeGen.create(args.get(1), args.get(2)));
        addFloatingPointMathFunction("fmod", (args, language) -> LLVMFmodNodeGen.create(args.get(1), args.get(2)));
        addFloatingPointMathFunction("copysign", (args, language) -> LLVMCMathsIntrinsicsFactory.LLVMCopySignNodeGen.create(args.get(1), args.get(2), null));

        addIntegerMathFunction("abs", (args, language) -> LLVMAbsNodeGen.create(args.get(1)));
    }

    private static void registerCTypeIntrinsics() {
        add("isalpha", (args, language) -> LLVMIsalphaNodeGen.create(args.get(1)));
        add("tolower", (args, language) -> LLVMTolowerNodeGen.create(args.get(1)));
        add("toupper", (args, language) -> LLVMToUpperNodeGen.create(args.get(1)));
        add("isspace", (args, language) -> LLVMIsspaceNodeGen.create(args.get(1)));
        add("isupper", (args, language) -> LLVMIsupperNodeGen.create(args.get(1)));
    }

    private static void registerMemoryFunctionIntrinsics() {
        add("malloc", (args, language) -> LLVMMallocNodeGen.create(args.get(1)));
        add("calloc", (args, language) -> LLVMCallocNodeGen.create(LLVMLanguage.getLanguage().getNodeFactory().createMemSet(), args.get(1), args.get(2)));
        add("realloc", (args, language) -> LLVMReallocNodeGen.create(args.get(1), args.get(2)));
        add("free", (args, language) -> LLVMFreeNodeGen.create(args.get(1)));
        add("memset", "__memset_chk", (args, language) -> LLVMLibcMemsetNodeGen.create(LLVMLanguage.getLanguage().getNodeFactory().createMemSet(), args.get(1), args.get(2), args.get(3)));
        add("memcpy", "__memcpy_chk", (args, language) -> LLVMLibcMemcpyNodeGen.create(LLVMLanguage.getLanguage().getNodeFactory().createMemMove(), args.get(1), args.get(2), args.get(3)));
    }

    private static void registerExceptionIntrinsics() {
        add("_Unwind_RaiseException", (args, language) -> new LLVMRaiseExceptionNode(args.get(1)));
        add("__cxa_call_unexpected", (args, language) -> LLVMAbortNodeGen.create());
    }

    private static void registerComplexNumberIntrinsics() {
        // float functions return a vector of <2x float>
        add("__divsc3", (args, language) -> new LLVMComplexFloatDiv(args.get(1), args.get(2), args.get(3), args.get(4)));
        add("__mulsc3", (args, language) -> new LLVMComplexFloatMul(args.get(1), args.get(2), args.get(3), args.get(4)));

        // double functions store their double results in the structure that is passed as arg1
        add("__divdc3", (args, language) -> new LLVMComplexDoubleDiv(args.get(1), args.get(2), args.get(3), args.get(4), args.get(5)));
        add("__muldc3", (args, language) -> new LLVMComplexDoubleMul(args.get(1), args.get(2), args.get(3), args.get(4), args.get(5)));

        // 80-bit FP functions store their results in the structure that is passed as arg1
        add("__divxc3", (args, language) -> LLVMComplex80BitFloatDivNodeGen.create(args.get(1), args.get(2), args.get(3), args.get(4), args.get(5)));
        add("__mulxc3", (args, language) -> LLVMComplex80BitFloatMulNodeGen.create(args.get(1), args.get(2), args.get(3), args.get(4), args.get(5)));
    }

    private static void add(String name, LLVMTypedIntrinsicFactory factory) {
        LLVMTypedIntrinsicFactory existing = FACTORIES.put(name, factory);
        assert existing == null : "same intrinsic was added more than once";
    }

    private static void add(String name, LLVMIntrinsicFactory factory) {
        add(name, (LLVMTypedIntrinsicFactory) factory);
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
        add("l" + functionName, factory);
    }
}
