/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.resource.XtextResourceSet;

import com.google.inject.Injector;
import com.intel.llvm.ireditor.LLVM_IRStandaloneSetup;
import com.intel.llvm.ireditor.lLVM_IR.Model;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.vm.PolyglotEngine;
import com.oracle.truffle.api.vm.PolyglotEngine.Builder;
import com.oracle.truffle.llvm.nodes.base.LLVMNode;
import com.oracle.truffle.llvm.parser.LLVMVisitor;
import com.oracle.truffle.llvm.parser.factories.NodeFactoryFacade;
import com.oracle.truffle.llvm.parser.factories.NodeFactoryFacadeImpl;
import com.oracle.truffle.llvm.runtime.LLVMOptions;
import com.oracle.truffle.llvm.runtime.LLVMPropertyOptimizationConfiguration;
import com.oracle.truffle.llvm.types.LLVMAddress;
import com.oracle.truffle.llvm.types.LLVMFunction;
import com.oracle.truffle.llvm.types.LLVMIVarBit;
import com.oracle.truffle.llvm.types.memory.LLVMHeap;
import com.oracle.truffle.llvm.types.memory.LLVMMemory;

/**
 * This is the main LLVM execution class.
 */
public class LLVM {

    static final LLVMPropertyOptimizationConfiguration OPTIMIZATION_CONFIGURATION = new LLVMPropertyOptimizationConfiguration();

    private static final int THREE_ARGS = 3;

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            throw new IllegalArgumentException("please provide a file to execute!");
        }
        File file = new File(args[0]);
        Object[] otherArgs = new Object[args.length - 1];
        for (int i = 1; i < args.length; i++) {
            otherArgs[i - 1] = args[i];
        }
        int status = executeMain(file, otherArgs);
        System.exit(status);
    }

    public interface NodeFactoryFacadeProvider {

        NodeFactoryFacade getFacade(LLVMVisitor visitor);

    }

    public static NodeFactoryFacadeProvider provider = new NodeFactoryFacadeProvider() {

        public NodeFactoryFacade getFacade(LLVMVisitor visitor) {
            return new NodeFactoryFacadeImpl(visitor);
        }

    };

    /**
     * Parses the given file and registers all the functions.
     *
     * @param filePath the file path of the bitcode file
     * @param context the context in which the functions should be registered
     * @return a list of contained functions.
     */
    public static List<LLVMFunction> parseFile(String filePath, LLVMContext context) {
        LLVM_IRStandaloneSetup setup = new LLVM_IRStandaloneSetup();
        Injector injector = setup.createInjectorAndDoEMFRegistration();
        XtextResourceSet resourceSet = injector.getInstance(XtextResourceSet.class);
        resourceSet.addLoadOption(XtextResource.OPTION_RESOLVE_ALL, Boolean.TRUE);
        Resource resource = resourceSet.getResource(URI.createURI(filePath), true);
        EList<EObject> contents = resource.getContents();
        if (contents.size() == 0) {
            throw new IllegalStateException("empty file?");
        }
        Model model = (Model) contents.get(0);
        LLVMVisitor llvmVisitor = new LLVMVisitor(context, OPTIMIZATION_CONFIGURATION);
        NodeFactoryFacade facade = provider.getFacade(llvmVisitor);
        List<LLVMFunction> llvmFunctions = llvmVisitor.visit(model, facade);
        return llvmFunctions;
    }

    public static int executeMain(File file, Object... args) {
        if (LLVMOptions.isDebug()) {
            System.out.println("current file: " + file.getAbsolutePath());
        }
        Source fileSource;
        try {
            fileSource = Source.fromFileName(file.getAbsolutePath());
            return evaluateFromSource(fileSource, args);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    public static int executeMain(String codeString, Object... args) {
        Source fromText = Source.fromText(codeString, "code string").withMimeType(LLVMLanguage.LLVM_MIME_TYPE);
        if (LLVMOptions.isDebug()) {
            System.out.println("current code string: " + codeString);
        }
        return evaluateFromSource(fromText, args);
    }

    private static int evaluateFromSource(Source fileSource, Object... args) {
        Builder engineBuilder = PolyglotEngine.newBuilder();
        PolyglotEngine vm = engineBuilder.build();
        try {
            LLVMModule module = vm.eval(fileSource).as(LLVMModule.class);
            if (module.getMainFunction() == null) {
                throw new RuntimeException("no @main found!");
            }
            RootCallTarget originalCallTarget = module.getMainFunction();
            LLVMFunction mainSignature = module.getMainSignature();
            int argParamCount = mainSignature.getLlvmParamTypes().length;
            LLVMGlobalRootNode globalFunction;
            if (argParamCount == 0) {
                globalFunction = LLVMGlobalRootNode.createNoArgumentsMain(module.getStaticInits(), originalCallTarget, module.getAllocatedAddresses());
            } else if (argParamCount == 1) {
                globalFunction = LLVMGlobalRootNode.createArgsCountMain(module.getStaticInits(), originalCallTarget, module.getAllocatedAddresses(), args.length + 1);
            } else {
                LLVMAddress allocatedArgsStartAddress = getArgsAsStringArray(fileSource, args);
                if (argParamCount == 2) {
                    globalFunction = LLVMGlobalRootNode.createArgsMain(module.getStaticInits(), originalCallTarget, module.getAllocatedAddresses(), args.length + 1, allocatedArgsStartAddress);
                } else if (argParamCount == THREE_ARGS) {
                    LLVMAddress posixEnvPointer = LLVMAddress.NULL_POINTER;
                    globalFunction = LLVMGlobalRootNode.createArgsEnvMain(module.getStaticInits(), originalCallTarget, module.getAllocatedAddresses(), args.length + 1, allocatedArgsStartAddress,
                                    posixEnvPointer);
                } else {
                    throw new AssertionError(argParamCount);
                }
            }
            RootCallTarget callTarget = Truffle.getRuntime().createCallTarget(globalFunction);
            Object result = callTarget.call(args);
            if (LLVMOptions.isNativeCallStats()) {
                printNativeCallStats(module);
            }
            // TODO: specialize instead
            if (result instanceof Number) {
                return ((Number) result).intValue();
            } else if (result instanceof LLVMIVarBit) {
                return ((LLVMIVarBit) result).getIntValue();
            } else if (result instanceof Boolean) {
                return ((boolean) result) ? 1 : 0;
            } else {
                throw new AssertionError(result);
            }

        } catch (IOException e) {
            throw new AssertionError(e);
        } finally {
            // FIXME w
            LLVMFunction.reset();
        }
    }

    private static void printNativeCallStats(LLVMModule module) {
        Map<LLVMFunction, Integer> nativeFunctionCallSites = module.getContext().getNativeFunctionLookupStats();
        if (!nativeFunctionCallSites.isEmpty()) {
            System.out.println("==========================");
            System.out.println("native function sites:");
            System.out.println("==========================");
            for (LLVMFunction function : nativeFunctionCallSites.keySet()) {
                String output = String.format("%15s: %3d", function.getName(), nativeFunctionCallSites.get(function));
                System.out.println(output);
            }
            System.out.println("==========================");
        }
    }

    private static LLVMAddress getArgsAsStringArray(Source fileSource, Object... args) {
        String[] stringArgs = getStringArgs(fileSource, args);
        int argsMemory = stringArgs.length * LLVMAddress.WORD_LENGTH_BIT / Byte.SIZE;
        LLVMAddress allocatedArgsStartAddress = LLVMHeap.allocateMemory(argsMemory);
        LLVMAddress allocatedArgs = allocatedArgsStartAddress;
        for (int i = 0; i < stringArgs.length; i++) {
            LLVMAddress allocatedCString = LLVMHeap.allocateCString(stringArgs[i]);
            LLVMMemory.putAddress(allocatedArgs, allocatedCString);
            allocatedArgs = allocatedArgs.increment(LLVMAddress.WORD_LENGTH_BIT / Byte.SIZE);
        }
        return allocatedArgsStartAddress;
    }

    private static String[] getStringArgs(Source fileSource, Object... args) {
        String[] stringArgs = new String[args.length + 1];
        stringArgs[0] = fileSource.getName();
        for (int i = 0; i < args.length; i++) {
            stringArgs[i + 1] = args[i].toString();
        }
        return stringArgs;
    }

    static class LLVMModule implements TruffleObject {
        private final RootCallTarget mainFunction;
        private final LLVMNode[] staticInits;
        private final LLVMAddress[] allocatedAddresses;
        private final LLVMFunction mainSignature;
        private final LLVMContext context;

        LLVMModule(RootCallTarget mainFunction, LLVMFunction mainSignature, LLVMNode[] staticInits, LLVMAddress[] allocatedAddresses, LLVMContext context) {
            this.mainFunction = mainFunction;
            this.mainSignature = mainSignature;
            this.staticInits = staticInits;
            this.allocatedAddresses = allocatedAddresses;
            this.context = context;
        }

        public RootCallTarget getMainFunction() {
            return mainFunction;
        }

        public ForeignAccess getForeignAccess() {
            throw new AssertionError();
        }

        public LLVMNode[] getStaticInits() {
            return staticInits;
        }

        public LLVMFunction getMainSignature() {
            return mainSignature;
        }

        public LLVMAddress[] getAllocatedAddresses() {
            return allocatedAddresses;
        }

        public LLVMContext getContext() {
            return context;
        }
    }

    public static CallTarget getLLVMModuleCall(File file, LLVMContext context) {
        parseFile(file.toString(), context);
        RootCallTarget mainFunction = context.getFunction(LLVMFunction.createFromName("@main"));
        LLVMFunction mainSignature = LLVMFunction.createFromName("@main");
        LLVMNode[] staticInits = context.getStaticInits();
        LLVMAddress[] allocatedAddresses = context.getAllocatedGlobalAddresses();
        return new CallTarget() {

            public Object call(Object... arguments) {
                return new LLVMModule(mainFunction, mainSignature, staticInits, allocatedAddresses, context);
            }
        };
    }

}
