/*
 * Copyright (c) 2020, Oracle and/or its affiliates.
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

import static com.oracle.truffle.llvm.parser.model.GlobalSymbol.CONSTRUCTORS_VARNAME;
import static com.oracle.truffle.llvm.parser.model.GlobalSymbol.DESTRUCTORS_VARNAME;

import java.util.ArrayList;
import java.util.Comparator;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.parser.LLVMParserResult;
import com.oracle.truffle.llvm.parser.model.SymbolImpl;
import com.oracle.truffle.llvm.parser.model.symbols.constants.aggregate.ArrayConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.aggregate.StructureConstant;
import com.oracle.truffle.llvm.parser.model.symbols.globals.GlobalVariable;
import com.oracle.truffle.llvm.parser.nodes.LLVMSymbolReadResolver;
import com.oracle.truffle.llvm.parser.util.Pair;
import com.oracle.truffle.llvm.runtime.CommonNodeFactory;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.LLVMScope;
import com.oracle.truffle.llvm.runtime.NodeFactory;
import com.oracle.truffle.llvm.runtime.datalayout.DataLayout;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMHasDatalayoutNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStatementNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMVoidStatementNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.others.LLVMStatementRootNode;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.StructureType;
import com.oracle.truffle.llvm.runtime.types.Type;

/**
 * Registers the destructor and executes the constructor of a module. This happens after
 * <emph>all</emph> globals have been initialized by {@link InitializeGlobalNode}.
 *
 * @see InitializeScopeNode
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

    @Child StaticInitsNode constructor;

    public InitializeModuleNode(LLVMLanguage language, LLVMParserResult parserResult, String moduleName) {
        this.destructor = createDestructor(parserResult, moduleName, language);
        this.dataLayout = parserResult.getDataLayout();

        this.constructor = createConstructor(parserResult, moduleName);
    }

    public void execute(VirtualFrame frame, LLVMContext ctx) {
        if (destructor != null) {
            ctx.registerDestructorFunctions(destructor);
        }
        constructor.execute(frame);
    }

    @Override
    public DataLayout getDatalayout() {
        return dataLayout;
    }

    public static RootCallTarget createDestructor(LLVMParserResult parserResult, String moduleName, LLVMLanguage language) {
        LLVMStatementNode[] destructors = createStructor(DESTRUCTORS_VARNAME, parserResult, DESCENDING_PRIORITY);
        if (destructors.length > 0) {
            FrameDescriptor frameDescriptor = new FrameDescriptor();
            LLVMStatementRootNode root = new LLVMStatementRootNode(language, StaticInitsNodeGen.create(destructors, "fini", moduleName), frameDescriptor,
                            parserResult.getRuntime().getNodeFactory().createStackAccess(frameDescriptor));
            return Truffle.getRuntime().createCallTarget(root);
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
                return resolveStructor(parserResult.getRuntime().getFileScope(), globalVariable, priorityComparator, parserResult.getDataLayout(), parserResult.getRuntime().getNodeFactory());
            }
        }
        return LLVMStatementNode.NO_STATEMENTS;
    }

    private static LLVMStatementNode[] resolveStructor(LLVMScope fileScope, GlobalVariable globalSymbol, Comparator<Pair<Integer, ?>> priorityComparator, DataLayout dataLayout,
                    NodeFactory nodeFactory) {
        if (!(globalSymbol.getValue() instanceof ArrayConstant)) {
            // array globals of length 0 may be initialized with scalar null
            return LLVMStatementNode.NO_STATEMENTS;
        }

        final LLVMGlobal global = (LLVMGlobal) fileScope.get(globalSymbol.getName());
        final ArrayConstant arrayConstant = (ArrayConstant) globalSymbol.getValue();
        final int elemCount = arrayConstant.getElementCount();

        final StructureType elementType = (StructureType) arrayConstant.getType().getElementType();
        try {
            final long elementSize = elementType.getSize(dataLayout);

            final FunctionType functionType = (FunctionType) ((PointerType) elementType.getElementType(1)).getPointeeType();
            final int indexedTypeLength = functionType.getAlignment(dataLayout);

            final ArrayList<Pair<Integer, LLVMStatementNode>> structors = new ArrayList<>(elemCount);
            for (int i = 0; i < elemCount; i++) {
                final LLVMExpressionNode globalVarAddress = nodeFactory.createLiteral(global, new PointerType(globalSymbol.getType()));
                final LLVMExpressionNode iNode = nodeFactory.createLiteral(i, PrimitiveType.I32);
                final LLVMExpressionNode structPointer = nodeFactory.createTypedElementPointer(elementSize, elementType, globalVarAddress, iNode);
                final LLVMExpressionNode loadedStruct = CommonNodeFactory.createLoad(elementType, structPointer);

                final LLVMExpressionNode oneLiteralNode = nodeFactory.createLiteral(1, PrimitiveType.I32);
                final LLVMExpressionNode functionLoadTarget = nodeFactory.createTypedElementPointer(indexedTypeLength, functionType, loadedStruct, oneLiteralNode);
                final LLVMExpressionNode loadedFunction = CommonNodeFactory.createLoad(functionType, functionLoadTarget);
                final LLVMExpressionNode[] argNodes = new LLVMExpressionNode[]{nodeFactory.createGetStackFromFrame()};
                final LLVMStatementNode functionCall = LLVMVoidStatementNodeGen.create(CommonNodeFactory.createFunctionCall(loadedFunction, argNodes, functionType));

                final StructureConstant structorDefinition = (StructureConstant) arrayConstant.getElement(i);
                final SymbolImpl prioritySymbol = structorDefinition.getElement(0);
                final Integer priority = LLVMSymbolReadResolver.evaluateIntegerConstant(prioritySymbol);
                structors.add(new Pair<>(priority != null ? priority : LEAST_CONSTRUCTOR_PRIORITY, functionCall));
            }

            return structors.stream().sorted(priorityComparator).map(Pair::getSecond).toArray(LLVMStatementNode[]::new);
        } catch (Type.TypeOverflowException e) {
            return new LLVMStatementNode[]{Type.handleOverflowStatement(e)};
        }
    }
}
