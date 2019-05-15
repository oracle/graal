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
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMContext.ExternalLibrary;
import com.oracle.truffle.llvm.runtime.LLVMIntrinsicProvider;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
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
        }, context));
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
        }, context);
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
        LLVMExpressionNode generate(List<LLVMExpressionNode> args, LLVMContext context);
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
        add("__sulong_destructor_functions", (args, context) -> LLVMRunDestructorFunctionsNodeGen.create());
        add("__sulong_print_stacktrace", (args, context) -> LLVMPrintStackTraceNodeGen.create());
        add("__sulong_should_print_stacktrace_on_abort", (args, context) -> LLVMShouldPrintStackTraceOnAbortNodeGen.create());
    }

    private static void registerTruffleIntrinsics() {
        add("polyglot_import", (args, context) -> LLVMPolyglotImportNodeGen.create(args.get(1)));
        add("polyglot_export", (args, context) -> LLVMPolyglotExportNodeGen.create(args.get(1), args.get(2)));
        add("polyglot_eval", (args, context) -> LLVMPolyglotEval.create(args.get(1), args.get(2)));
        add("polyglot_eval_file", (args, context) -> LLVMPolyglotEval.createFile(args.get(1), args.get(2)));
        add("polyglot_java_type", (args, context) -> LLVMPolyglotJavaTypeNodeGen.create(args.get(1)));

        add("polyglot_is_value", (args, context) -> LLVMPolyglotIsValueNodeGen.create(args.get(1)));
        add("polyglot_is_number", (args, context) -> LLVMPolyglotBoxedPredicateNodeGen.create(InteropLibrary::isNumber, args.get(1)));
        add("polyglot_is_boolean", (args, context) -> LLVMPolyglotBoxedPredicateNodeGen.create(InteropLibrary::isBoolean, args.get(1)));
        add("polyglot_is_string", (args, context) -> LLVMPolyglotBoxedPredicateNodeGen.create(InteropLibrary::isString, args.get(1)));
        add("polyglot_fits_in_i8", (args, context) -> LLVMPolyglotBoxedPredicateNodeGen.create(InteropLibrary::fitsInByte, args.get(1)));
        add("polyglot_fits_in_i16", (args, context) -> LLVMPolyglotBoxedPredicateNodeGen.create(InteropLibrary::fitsInShort, args.get(1)));
        add("polyglot_fits_in_i32", (args, context) -> LLVMPolyglotBoxedPredicateNodeGen.create(InteropLibrary::fitsInInt, args.get(1)));
        add("polyglot_fits_in_i64", (args, context) -> LLVMPolyglotBoxedPredicateNodeGen.create(InteropLibrary::fitsInLong, args.get(1)));
        add("polyglot_fits_in_float", (args, context) -> LLVMPolyglotBoxedPredicateNodeGen.create(InteropLibrary::fitsInFloat, args.get(1)));
        add("polyglot_fits_in_double", (args, context) -> LLVMPolyglotBoxedPredicateNodeGen.create(InteropLibrary::fitsInDouble, args.get(1)));

        add("polyglot_put_member", (args, context) -> LLVMPolyglotPutMemberNodeGen.create(args.size() - 1, args.get(1), args.get(2), args.get(3)));

        add("polyglot_set_array_element", (args, context) -> LLVMPolyglotSetArrayElementNodeGen.create(args.size() - 1, args.get(1), args.get(2), args.get(3)));

        add("polyglot_get_member", (args, context) -> LLVMPolyglotGetMemberNodeGen.create(LLVMLanguage.getLanguage().getNodeFactory().createForeignToLLVM(POINTER), args.get(1), args.get(2)));

        add("polyglot_get_array_element", (args, context) -> LLVMPolyglotGetArrayElementNodeGen.create(LLVMLanguage.getLanguage().getNodeFactory().createForeignToLLVM(POINTER), args.get(1), args.get(2)));

        add("polyglot_remove_member", (args, context) -> LLVMPolyglotRemoveMemberNodeGen.create(args.get(1), args.get(2)));

        add("polyglot_remove_array_element", (args, context) -> LLVMPolyglotRemoveArrayElementNodeGen.create(args.get(1), args.get(2)));

        add("polyglot_as_i8", (args, context) -> LLVMPolyglotAsPrimitive.AsI8.create(args.get(1)));
        add("polyglot_as_i16", (args, context) -> LLVMPolyglotAsPrimitive.AsI16.create(args.get(1)));
        add("polyglot_as_i32", (args, context) -> LLVMPolyglotAsPrimitive.AsI32.create(args.get(1)));
        add("polyglot_as_i64", (args, context) -> LLVMPolyglotAsPrimitive.AsI64.create(args.get(1)));
        add("polyglot_as_float", (args, context) -> LLVMPolyglotAsPrimitive.AsFloat.create(args.get(1)));
        add("polyglot_as_double", (args, context) -> LLVMPolyglotAsPrimitive.AsDouble.create(args.get(1)));
        add("polyglot_as_boolean", (args, context) -> LLVMPolyglotAsPrimitive.AsBoolean.create(args.get(1)));

        add("polyglot_new_instance", (args, context) -> LLVMPolyglotNewInstanceNodeGen.create(argumentsArray(args, 2, args.size() - 2), args.get(1)));

        add("polyglot_invoke",
                        (args, context) -> LLVMPolyglotInvokeNodeGen.create(LLVMLanguage.getLanguage().getNodeFactory().createForeignToLLVM(POINTER), argumentsArray(args, 3, args.size() - 3), args.get(1), args.get(2)));

        add("truffle_decorate_function", (args, context) -> LLVMTruffleDecorateFunctionNodeGen.create(args.get(1), args.get(2)));
        add("polyglot_can_execute", (args, context) -> LLVMPolyglotBoxedPredicateNodeGen.create(InteropLibrary::isExecutable, args.get(1)));
        add("polyglot_can_instantiate", (args, context) -> LLVMPolyglotBoxedPredicateNodeGen.create(InteropLibrary::isInstantiable, args.get(1)));
        add("polyglot_is_null", (args, context) -> LLVMPolyglotBoxedPredicateNodeGen.create(InteropLibrary::isNull, args.get(1)));
        add("polyglot_has_array_elements", (args, context) -> LLVMPolyglotBoxedPredicateNodeGen.create(InteropLibrary::hasArrayElements, args.get(1)));
        add("polyglot_has_members", (args, context) -> LLVMPolyglotBoxedPredicateNodeGen.create(InteropLibrary::hasMembers, args.get(1)));
        add("polyglot_has_member", (args, context) -> LLVMPolyglotHasMemberNodeGen.create(args.get(1), args.get(2)));
        add("polyglot_get_array_size", (args, context) -> LLVMPolyglotGetArraySizeNodeGen.create(args.get(1)));

        add("polyglot_get_string_size", (args, context) -> LLVMPolyglotGetStringSizeNodeGen.create(args.get(1)));
        add("polyglot_as_string", (args, context) -> LLVMPolyglotAsString.create(args.get(1), args.get(2), args.get(3), args.get(4)));
        add("polyglot_from_string", (args, context) -> LLVMPolyglotFromString.create(args.get(1), args.get(2)));
        add("polyglot_from_string_n", (args, context) -> LLVMPolyglotFromString.createN(args.get(1), args.get(2), args.get(3)));

        add("truffle_load_library", (args, context) -> LLVMLoadLibraryNodeGen.create(args.get(1)));

        add("__polyglot_as_typeid", (args, context) -> LLVMTypeIDNode.create(args.get(1)));
        add("polyglot_as_typed", (args, context) -> LLVMPolyglotAsTyped.create(args.get(1), args.get(2)));
        add("polyglot_from_typed", (args, context) -> LLVMPolyglotFromTyped.create(args.get(1), args.get(2)));
        add("polyglot_array_typeid", (args, context) -> LLVMArrayTypeIDNode.create(args.get(1), args.get(2)));
    }

    private static void registerManagedAllocationIntrinsics() {
        add("truffle_managed_malloc", (args, context) -> LLVMTruffleManagedMallocNodeGen.create(args.get(1)));
        add("truffle_handle_for_managed", (args, context) -> LLVMTruffleManagedToHandleNodeGen.create(args.get(1)));
        add("truffle_deref_handle_for_managed", (args, context) -> LLVMTruffleDerefHandleToManagedNodeGen.create(args.get(1)));
        add("truffle_release_handle", (args, context) -> LLVMTruffleReleaseHandleNodeGen.create(args.get(1)));
        add("truffle_managed_from_handle", (args, context) -> LLVMTruffleHandleToManagedNodeGen.create(args.get(1)));
        add("truffle_is_handle_to_managed", (args, context) -> LLVMTruffleIsHandleToManagedNodeGen.create(args.get(1)));
        add("truffle_cannot_be_handle", (args, context) -> LLVMTruffleCannotBeHandleNodeGen.create(args.get(1)));
        add("truffle_assign_managed", (args, context) -> LLVMTruffleWriteManagedToGlobalNodeGen.create(args.get(1), args.get(2)));
        add("truffle_virtual_malloc", (args, context) -> LLVMVirtualMallocNodeGen.create(args.get(1)));
    }

    private static void registerAbortIntrinsics() {
        add("_gfortran_abort", (args, context) -> LLVMAbortNodeGen.create());
        add("signal", (args, context) -> LLVMSignalNodeGen.create(args.get(1), args.get(2)));
        add("syscall", "__syscall", (args, context) -> LLVMSyscall.create(argumentsArray(args, 1, args.size() - 1)));
    }

    private static void registerRustIntrinsics() {
        add("std::rt::lang_start", (args, context) -> LLVMLangStartNodeGen.create(args.get(0), args.get(1), args.get(2), args.get(3)));
        add("std::rt::lang_start_internal", (args, context) -> LLVMLangStartInternalNodeGen.create(args.get(0), args.get(1), args.get(2), args.get(3), args.get(4)));
        add("std::process::exit", (args, context) -> LLVMExitNodeGen.create(args.get(1)));
        add("core::panicking::panic", (args, context) -> LLVMPanicNodeGen.create(args.get(1)));
    }

    private static void registerMathFunctionIntrinsics() {
        // TODO (chaeubl): There is no doubt that not all of these intrinsics are valid as they use
        // double arithmetics to simulate floating arithmetics, which can change the precision.
        // Furthermore, it is possible that there are mismatches between Java and C semantics.
        addFloatingPointMathFunction("sqrt", (args, context) -> LLVMSqrtNodeGen.create(args.get(1), null));
        addFloatingPointMathFunction("log", (args, context) -> LLVMLogNodeGen.create(args.get(1), null));
        addFloatingPointMathFunction("log2", (args, context) -> LLVMLog2NodeGen.create(args.get(1), null));
        addFloatingPointMathFunction("log10", (args, context) -> LLVMLog10NodeGen.create(args.get(1), null));
        addFloatingPointMathFunction("log1p", (args, context) -> LLVMLog1pNodeGen.create(args.get(1), null));
        addFloatingPointMathFunction("rint", (args, context) -> LLVMRintNodeGen.create(args.get(1), null));
        addFloatingPointMathFunction("ceil", (args, context) -> LLVMCeilNodeGen.create(args.get(1), null));
        addFloatingPointMathFunction("floor", (args, context) -> LLVMFloorNodeGen.create(args.get(1), null));
        addFloatingPointMathFunction("fabs", (args, context) -> LLVMFAbsNodeGen.create(args.get(1), null));
        addFloatingPointMathFunction("minnum", (args, context) -> LLVMMinnumNodeGen.create(args.get(1), args.get(2), null));
        addFloatingPointMathFunction("maxnum", (args, context) -> LLVMMaxnumNodeGen.create(args.get(1), args.get(2), null));
        addFloatingPointMathFunction("pow", (args, context) -> LLVMPowNodeGen.create(args.get(1), args.get(2), null));
        addFloatingPointMathFunction("exp", (args, context) -> LLVMExpNodeGen.create(args.get(1), null));
        addFloatingPointMathFunction("exp2", (args, context) -> LLVMExp2NodeGen.create(args.get(1), null));
        addFloatingPointMathFunction("expm1", (args, context) -> LLVMExpm1NodeGen.create(args.get(1), null));
        addFloatingPointMathFunction("sin", (args, context) -> LLVMSinNodeGen.create(args.get(1), null));
        addFloatingPointMathFunction("cos", (args, context) -> LLVMCosNodeGen.create(args.get(1), null));
        addFloatingPointMathFunction("tan", (args, context) -> LLVMTanNodeGen.create(args.get(1)));
        addFloatingPointMathFunction("atan2", (args, context) -> LLVMATan2NodeGen.create(args.get(1), args.get(2)));
        addFloatingPointMathFunction("asin", (args, context) -> LLVMASinNodeGen.create(args.get(1)));
        addFloatingPointMathFunction("acos", (args, context) -> LLVMACosNodeGen.create(args.get(1)));
        addFloatingPointMathFunction("atan", (args, context) -> LLVMATanNodeGen.create(args.get(1)));
        addFloatingPointMathFunction("sinh", (args, context) -> LLVMSinhNodeGen.create(args.get(1)));
        addFloatingPointMathFunction("cosh", (args, context) -> LLVMCoshNodeGen.create(args.get(1)));
        addFloatingPointMathFunction("tanh", (args, context) -> LLVMTanhNodeGen.create(args.get(1)));
        addFloatingPointMathFunction("ldexp", (args, context) -> LLVMLdexpNodeGen.create(args.get(1), args.get(2)));
        addFloatingPointMathFunction("modf", (args, context) -> LLVMModfNodeGen.create(args.get(1), args.get(2)));
        addFloatingPointMathFunction("fmod", (args, context) -> LLVMFmodNodeGen.create(args.get(1), args.get(2)));
        addFloatingPointMathFunction("copysign", (args, context) -> LLVMCMathsIntrinsicsFactory.LLVMCopySignNodeGen.create(args.get(1), args.get(2), null));

        addIntegerMathFunction("abs", (args, context) -> LLVMAbsNodeGen.create(args.get(1)));
    }

    private static void registerCTypeIntrinsics() {
        add("isalpha", (args, context) -> LLVMIsalphaNodeGen.create(args.get(1)));
        add("tolower", (args, context) -> LLVMTolowerNodeGen.create(args.get(1)));
        add("toupper", (args, context) -> LLVMToUpperNodeGen.create(args.get(1)));
        add("isspace", (args, context) -> LLVMIsspaceNodeGen.create(args.get(1)));
        add("isupper", (args, context) -> LLVMIsupperNodeGen.create(args.get(1)));
    }

    private static void registerMemoryFunctionIntrinsics() {
        add("malloc", (args, context) -> LLVMMallocNodeGen.create(args.get(1)));
        add("calloc", (args, context) -> LLVMCallocNodeGen.create(LLVMLanguage.getLanguage().getNodeFactory().createMemSet(), args.get(1), args.get(2)));
        add("realloc", (args, context) -> LLVMReallocNodeGen.create(args.get(1), args.get(2)));
        add("free", (args, context) -> LLVMFreeNodeGen.create(args.get(1)));
        add("memset", "__memset_chk", (args, context) -> LLVMLibcMemsetNodeGen.create(LLVMLanguage.getLanguage().getNodeFactory().createMemSet(), args.get(1), args.get(2), args.get(3)));
        add("memcpy", "__memcpy_chk", (args, context) -> LLVMLibcMemcpyNodeGen.create(LLVMLanguage.getLanguage().getNodeFactory().createMemMove(), args.get(1), args.get(2), args.get(3)));
    }

    private static void registerExceptionIntrinsics() {
        add("_Unwind_RaiseException", (args, context) -> new LLVMRaiseExceptionNode(args.get(1)));
        add("__cxa_call_unexpected", (args, context) -> LLVMAbortNodeGen.create());
    }

    private static void registerComplexNumberIntrinsics() {
        // float functions return a vector of <2x float>
        add("__divsc3", (args, context) -> new LLVMComplexFloatDiv(args.get(1), args.get(2), args.get(3), args.get(4)));
        add("__mulsc3", (args, context) -> new LLVMComplexFloatMul(args.get(1), args.get(2), args.get(3), args.get(4)));

        // double functions store their double results in the structure that is passed as arg1
        add("__divdc3", (args, context) -> new LLVMComplexDoubleDiv(args.get(1), args.get(2), args.get(3), args.get(4), args.get(5)));
        add("__muldc3", (args, context) -> new LLVMComplexDoubleMul(args.get(1), args.get(2), args.get(3), args.get(4), args.get(5)));

        // 80-bit FP functions store their results in the structure that is passed as arg1
        add("__divxc3", (args, context) -> LLVMComplex80BitFloatDivNodeGen.create(args.get(1), args.get(2), args.get(3), args.get(4), args.get(5)));
        add("__mulxc3", (args, context) -> LLVMComplex80BitFloatMulNodeGen.create(args.get(1), args.get(2), args.get(3), args.get(4), args.get(5)));
    }

    private static void add(String name, LLVMIntrinsicFactory factory) {
        LLVMIntrinsicFactory existing = FACTORIES.put(name, factory);
        assert existing == null : "same intrinsic was added more than once";
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
