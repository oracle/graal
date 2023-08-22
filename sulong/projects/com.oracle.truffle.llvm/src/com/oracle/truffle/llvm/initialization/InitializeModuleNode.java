/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.initialization;

import com.oracle.truffle.api.InternalResource.OS;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.parser.LLVMParserResult;
import com.oracle.truffle.llvm.parser.LLVMParserRuntime;
import com.oracle.truffle.llvm.parser.model.SymbolImpl;
import com.oracle.truffle.llvm.parser.model.symbols.constants.aggregate.ArrayConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.aggregate.StructureConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.Constant;
import com.oracle.truffle.llvm.parser.model.symbols.globals.GlobalVariable;
import com.oracle.truffle.llvm.parser.nodes.LLVMSymbolReadResolver;
import com.oracle.truffle.llvm.parser.util.Pair;
import com.oracle.truffle.llvm.runtime.CommonNodeFactory;
import com.oracle.truffle.llvm.runtime.IDGenerater.BitcodeID;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.NodeFactory;
import com.oracle.truffle.llvm.runtime.PlatformCapability;
import com.oracle.truffle.llvm.runtime.datalayout.DataLayout;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMHasDatalayoutNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStatementNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMVoidStatementNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.others.LLVMStatementRootNode;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.PointerType;

import java.util.ArrayList;
import java.util.Comparator;

import static com.oracle.truffle.llvm.parser.model.GlobalSymbol.CONSTRUCTORS_VARNAME;
import static com.oracle.truffle.llvm.parser.model.GlobalSymbol.DESTRUCTORS_VARNAME;

/**
 * Registers the destructor and executes the constructor of a module. This happens after
 * <emph>all</emph> globals have been initialized by {@link InitializeGlobalNode}.
 *
 * @see InitializeSymbolsNode
 * @see InitializeGlobalNode
 * @see InitializeExternalNode
 * @see InitializeOverwriteNode
 */
public final class InitializeModuleNode extends LLVMNode implements LLVMHasDatalayoutNode {

    private static final int LEAST_CONSTRUCTOR_PRIORITY = 65535;

    private static final Comparator<Pair<Integer, ?>> ASCENDING_PRIORITY = (p1, p2) -> p1.getFirst() - p2.getFirst();
    private static final Comparator<Pair<Integer, ?>> DESCENDING_PRIORITY = (p1, p2) -> p2.getFirst() - p1.getFirst();

    private final RootCallTarget destructor;
    private final DataLayout dataLayout;
    private final BitcodeID bitcodeID;

    @Child private StaticInitsNode constructor;
    @Child private InitObjcSelectorsNode selectors;

    public InitializeModuleNode(LLVMLanguage language, LLVMParserResult parserResult, String moduleName) {
        this.destructor = createDestructor(parserResult, moduleName, language);
        this.dataLayout = parserResult.getDataLayout();
        this.bitcodeID = parserResult.getRuntime().getBitcodeID();
        this.constructor = createConstructor(parserResult, moduleName);
        if (language.getCapability(PlatformCapability.class).getOS() == OS.DARWIN) {
            this.selectors = InitObjcSelectorsNodeGen.create(parserResult);
        }
    }

    public void execute(VirtualFrame frame, LLVMContext ctx) {
        if (destructor != null) {
            ctx.registerDestructorFunctions(bitcodeID, destructor);
        }
        constructor.execute(frame);

        if (selectors != null) {
            selectors.execute(frame);
        }
    }

    @Override
    public DataLayout getDatalayout() {
        return dataLayout;
    }

    public static RootCallTarget createDestructor(LLVMParserResult parserResult, String moduleName, LLVMLanguage language) {
        LLVMStatementNode[] destructors = createStructor(DESTRUCTORS_VARNAME, parserResult, DESCENDING_PRIORITY);
        if (destructors.length > 0) {
            NodeFactory nodeFactory = parserResult.getRuntime().getNodeFactory();
            FrameDescriptor.Builder builder = FrameDescriptor.newBuilder();
            nodeFactory.addStackSlots(builder);
            FrameDescriptor frameDescriptor = builder.build();
            LLVMStatementRootNode root = new LLVMStatementRootNode(language, StaticInitsNodeGen.create(destructors, "fini", moduleName), frameDescriptor, nodeFactory.createStackAccess());
            return root.getCallTarget();
        } else {
            return null;
        }
    }

    private static StaticInitsNode createConstructor(LLVMParserResult parserResult, String moduleName) {
        return StaticInitsNodeGen.create(createStructor(CONSTRUCTORS_VARNAME, parserResult, ASCENDING_PRIORITY), "init", moduleName);
    }

    private static LLVMStatementNode[] createStructor(String name, LLVMParserResult parserResult, Comparator<Pair<Integer, ?>> priorityComparator) {
        for (GlobalVariable globalVariable : parserResult.getDefinedGlobals()) {
            if (globalVariable.getName().equals(name)) {
                return resolveStructor(parserResult, globalVariable, priorityComparator);
            }
        }
        return LLVMStatementNode.NO_STATEMENTS;
    }

    private static LLVMStatementNode[] resolveStructor(LLVMParserResult parserResult, GlobalVariable globalSymbol, Comparator<Pair<Integer, ?>> priorityComparator) {
        if (!(globalSymbol.getValue() instanceof ArrayConstant)) {
            // array globals of length 0 may be initialized with scalar null
            return LLVMStatementNode.NO_STATEMENTS;
        }

        LLVMParserRuntime runtime = parserResult.getRuntime();
        NodeFactory nodeFactory = runtime.getNodeFactory();
        final ArrayConstant arrayConstant = (ArrayConstant) globalSymbol.getValue();
        final int elemCount = arrayConstant.getElementCount();

        final ArrayList<Pair<Integer, LLVMStatementNode>> structors = new ArrayList<>(elemCount);
        for (int i = 0; i < elemCount; i++) {
            final StructureConstant structorDefinition = (StructureConstant) arrayConstant.getElement(i);

            Constant function = structorDefinition.getElement(1);
            FunctionType functionType;
            if (function.getType() instanceof FunctionType) {
                functionType = (FunctionType) function.getType();
            } else {
                /*
                 * For compatibility with dragonegg. GCC inserts a bitcast here, which results in a
                 * pointer-to-function type, instead of the function type directly.
                 */
                PointerType ptrType = (PointerType) function.getType();
                functionType = (FunctionType) ptrType.getPointeeType();
            }

            LLVMExpressionNode functionPtr = function.createNode(runtime, parserResult.getDataLayout(), null);
            final LLVMExpressionNode[] argNodes = new LLVMExpressionNode[]{nodeFactory.createGetStackFromFrame()};
            final LLVMStatementNode functionCall = LLVMVoidStatementNodeGen.create(CommonNodeFactory.createFunctionCall(functionPtr, argNodes, functionType));

            final SymbolImpl prioritySymbol = structorDefinition.getElement(0);
            final Integer priority = LLVMSymbolReadResolver.evaluateIntegerConstant(prioritySymbol);
            structors.add(new Pair<>(priority != null ? priority : LEAST_CONSTRUCTOR_PRIORITY, functionCall));
        }

        return structors.stream().sorted(priorityComparator).map(Pair::getSecond).toArray(LLVMStatementNode[]::new);
    }
}
