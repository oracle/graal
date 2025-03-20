/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.parser.metadata.debuginfo;

import static com.oracle.truffle.llvm.parser.metadata.debuginfo.DebugInfoCache.getDebugInfo;

import java.util.List;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.llvm.parser.metadata.MDBaseNode;
import com.oracle.truffle.llvm.parser.metadata.MDExpression;
import com.oracle.truffle.llvm.parser.metadata.MDLocalVariable;
import com.oracle.truffle.llvm.parser.metadata.MDLocation;
import com.oracle.truffle.llvm.parser.metadata.MDNode;
import com.oracle.truffle.llvm.parser.metadata.MDValue;
import com.oracle.truffle.llvm.parser.metadata.MetadataSymbol;
import com.oracle.truffle.llvm.parser.metadata.MetadataVisitor;
import com.oracle.truffle.llvm.parser.model.IRScope;
import com.oracle.truffle.llvm.parser.model.SymbolImpl;
import com.oracle.truffle.llvm.parser.model.blocks.InstructionBlock;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDeclaration;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDefinition;
import com.oracle.truffle.llvm.parser.model.functions.FunctionParameter;
import com.oracle.truffle.llvm.parser.model.symbols.constants.NullConstant;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.DbgDeclareInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.DbgNoaliasScopeDeclInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.DbgValueInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.DebugInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.DebugTrapInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.Instruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.VoidCallInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.DebugInstruction.DebugInstructionKind;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceSymbol;
import com.oracle.truffle.llvm.runtime.debug.type.LLVMSourceFunctionType;
import com.oracle.truffle.llvm.runtime.debug.type.LLVMSourceType;
import com.oracle.truffle.llvm.runtime.types.MetaType;

public final class DebugInfoFunctionProcessor {

    private static final int LLVM_DBG_INTRINSICS_VALUE_ARGINDEX = 0;

    private static final String LLVM_DBG_DECLARE_NAME = "llvm.dbg.declare";
    private static final int LLVM_DBG_DECLARE_LOCALREF_ARGINDEX = 1;
    private static final int LLVM_DBG_DECLARE_EXPR_ARGINDEX = 2;

    private static final String LLVM_DBG_ADDR_NAME = "llvm.dbg.addr";

    private static final String LLVM_DBG_VALUE_NAME = "llvm.dbg.value";

    private static final int LLVM_DBG_VALUE_LOCALREF_ARGINDEX_OLD = 2;
    private static final int LLVM_DBG_VALUE_EXPR_ARGINDEX_OLD = 3;
    private static final int LLVM_DBG_VALUE_LOCALREF_ARGSIZE_OLD = 4;

    private static final int LLVM_DBG_VALUE_LOCALREF_ARGINDEX_NEW = 1;
    private static final int LLVM_DBG_VALUE_EXPR_ARGINDEX_NEW = 2;
    private static final int LLVM_DBG_VALUE_LOCALREF_ARGSIZE_NEW = 3;

    private static final String LLVM_DEBUGTRAP_NAME = "llvm.debugtrap";

    private final DebugInfoCache cache;

    DebugInfoFunctionProcessor(DebugInfoCache cache) {
        this.cache = cache;
    }

    public void process(FunctionDefinition function, IRScope scope, Source bitcodeSource) {
        ImportsProcessor.process(scope.getMetadata(), cache);
        initSourceFunction(function, bitcodeSource);

        for (InstructionBlock block : function.getBlocks()) {
            List<Instruction> instructions = block.getInstructions();
            for (int i = 0; i < instructions.size(); i++) {
                Instruction instruction = instructions.get(i);
                if (instruction instanceof DebugInstruction) {
                    Instruction replacement = visit(function, (DebugInstruction) instruction);
                    if (replacement != instruction) {
                        instructions.set(i, replacement);
                    }
                } else if (instruction instanceof VoidCallInstruction) {
                    Instruction replacement = visit(function, (VoidCallInstruction) instruction);
                    if (replacement != instruction) {
                        instructions.set(i, replacement);
                    }
                } else {
                    visitInstruction(instruction);
                }
            }
        }
        scope.getMetadata().consumeLocals(new MetadataProcessor());
        for (SourceVariable local : function.getSourceFunction().getVariables()) {
            local.processFragments();
        }
        cache.endLocalScope();
    }

    private void initSourceFunction(FunctionDefinition function, Source bitcodeSource) {
        final MDBaseNode debugInfo = getDebugInfo(function);
        LLVMSourceLocation scope = null;
        LLVMSourceFunctionType type = null;
        if (debugInfo != null) {
            scope = cache.buildLocation(debugInfo);
            LLVMSourceType actualType = cache.parseType(debugInfo);
            if (actualType instanceof LLVMSourceFunctionType) {
                type = (LLVMSourceFunctionType) actualType;
            }
        }

        if (scope == null) {
            final String sourceText = String.format("%s:%s", bitcodeSource.getName(), function.getName());
            final Source irSource = Source.newBuilder("llvm", sourceText, sourceText).mimeType(DIScopeBuilder.getMimeType(null)).build();
            final SourceSection simpleSection = irSource.createSection(1);
            scope = LLVMSourceLocation.createBitcodeFunction(function.getName(), simpleSection);
        }

        final SourceFunction sourceFunction = new SourceFunction(scope, type);
        function.setSourceFunction(sourceFunction);
    }

    private static SymbolImpl getArg(VoidCallInstruction call, int index) {
        return index < call.getArgumentCount() ? call.getArgument(index) : null;
    }

    private static MDExpression getExpression(VoidCallInstruction call, int index) {
        final SymbolImpl argSymbol = getArg(call, index);
        if (argSymbol instanceof MetadataSymbol) {
            final MDBaseNode mdNode = ((MetadataSymbol) argSymbol).getNode();
            if (mdNode instanceof MDExpression) {
                return (MDExpression) mdNode;
            }
        }
        return MDExpression.EMPTY;
    }

    private static SymbolImpl getValue(DebugInstruction debug) {
        MDBaseNode valueNode = debug.getValue();
        SymbolImpl value = null;
        if (valueNode instanceof MDValue) {
            value = ((MDValue) valueNode).getValue();
        } else {
            value = null;
        }

        if (value instanceof MetadataSymbol) {
            value = MDSymbolExtractor.getSymbol(((MetadataSymbol) value).getNode());
        }

        if (value == null) {
            // this may happen if llvm optimizations removed a variable
            value = new NullConstant(MetaType.DEBUG);
        }

        return value;
    }

    private void visitInstruction(Instruction instruction) {
        final MDLocation loc = instruction.getDebugLocation();
        if (loc != null) {
            final LLVMSourceLocation scope = cache.buildLocation(loc);
            if (scope != null) {
                instruction.setSourceLocation(scope);
            }
        }
    }

    private Instruction visit(FunctionDefinition function, DebugInstruction debug) {
        SymbolImpl value = getValue(debug);

        final SourceVariable variable = getVariable(function, debug);
        if (variable == null) {
            // invalid or unsupported debug information
            // remove upper indices so we do not need to update the later ones
            return null;
        }

        final MDExpression expression = debug.getExpression();
        if (ValueFragment.describesFragment(expression)) {
            variable.addFragment(ValueFragment.parse(expression));
        } else {
            variable.addFullDefinition();
        }

        Instruction ret;
        switch (debug.getKind()) {
            case DECLARE:
                ret = new DbgDeclareInstruction(value, variable, expression);
                break;
            case VALUE:
                ret = new DbgValueInstruction(value, variable, expression);
                break;
            default:
                return debug;
        }

        ret.setDebugLocation(debug.getDebugLocation());
        visitInstruction(ret);
        return ret;
    }

    private Instruction visit(FunctionDefinition function, VoidCallInstruction call) {
        final SymbolImpl callTarget = call.getCallTarget();
        if (callTarget instanceof FunctionDeclaration) {
            DebugInstruction debug = null;
            switch (((FunctionDeclaration) callTarget).getName()) {
                case LLVM_DBG_DECLARE_NAME:
                    debug = handleDebugIntrinsic(call, DebugInstructionKind.DECLARE);
                    break;

                case LLVM_DBG_ADDR_NAME:
                    // dbg.declare and dbg.addr have the same interface and, for our purposes,
                    // the same semantics
                    debug = handleDebugIntrinsic(call, DebugInstructionKind.DECLARE);
                    break;

                case LLVM_DBG_VALUE_NAME:
                    debug = handleDebugIntrinsic(call, DebugInstructionKind.VALUE);
                    break;

                case LLVM_DEBUGTRAP_NAME:
                    return visitDebugTrap(call);

                case "llvm.experimental.noalias.scope.decl":
                    return handleNoaliasScopeDecl(call);
            }
            if (debug != null) {
                return visit(function, debug);
            }
        }

        visitInstruction(call);
        return call;
    }

    private Instruction visitDebugTrap(VoidCallInstruction call) {
        DebugTrapInstruction trap = DebugTrapInstruction.create(call);
        visitInstruction(trap);
        return trap;
    }

    /**
     * Attaches debug information about a particular function argument to the corresponding
     * function's type. As an example: which struct member this argument actually is in the source
     * code.
     *
     * @param function The corresponding function.
     * @param debug The LLVM debug instruction.
     */
    private static void attachSourceArgumentInformation(FunctionDefinition function, DebugInstruction debug) {
        if (debug.getKind() == DebugInstructionKind.VALUE) {
            SymbolImpl intrinsicValue = getValue(debug);
            MDLocalVariable local = debug.getVariable();
            MDExpression expr = debug.getExpression();
            if (!(intrinsicValue instanceof FunctionParameter)) {
                return;
            }
            FunctionParameter parameter = (FunctionParameter) intrinsicValue;

            ValueFragment fragment = ValueFragment.parse(expr);
            if (!fragment.isComplete()) {
                long sourceArgIndex = local.getArg();

                if (Long.compareUnsigned(sourceArgIndex, Integer.MAX_VALUE) > 0) {
                    throw new IndexOutOfBoundsException(String.format("Source argument index (%s) is out of integer range", Long.toUnsignedString(sourceArgIndex)));
                }

                /*
                 * Attach the argument info to the source function type: sourceArgIndex needs to be
                 * decremented by 1 because the 0th index belongs to the return type.
                 */
                function.getSourceFunction().getSourceType().attachSourceArgumentInformation(parameter.getArgIndex(), (int) sourceArgIndex - 1, fragment.getOffset(), fragment.getLength());
            }
        }
    }

    private SourceVariable getVariable(FunctionDefinition function, DebugInstruction debug) {
        MDLocalVariable mdLocal = debug.getVariable();
        LLVMSourceSymbol symbol = cache.getSourceSymbol(mdLocal, false);

        attachSourceArgumentInformation(function, debug);

        return function.getSourceFunction().getLocal(symbol);
    }

    private static Instruction handleNoaliasScopeDecl(VoidCallInstruction call) {
        SymbolImpl value = getArg(call, 0);
        MDNode node = (MDNode) ((MetadataSymbol) value).getNode();
        return new DbgNoaliasScopeDeclInstruction(node);
    }

    /**
     * Backwards compat to LLVM < 20: Convert a call instruction calling a debug intrinsic to a
     * debug instruction.
     */
    private static DebugInstruction handleDebugIntrinsic(VoidCallInstruction call, DebugInstructionKind kind) {
        int mdLocalArgIndex;
        int mdExprArgIndex;
        if (kind == DebugInstructionKind.DECLARE) {
            mdLocalArgIndex = LLVM_DBG_DECLARE_LOCALREF_ARGINDEX;
            mdExprArgIndex = LLVM_DBG_DECLARE_EXPR_ARGINDEX;

        } else if (call.getArgumentCount() == LLVM_DBG_VALUE_LOCALREF_ARGSIZE_NEW) {
            mdLocalArgIndex = LLVM_DBG_VALUE_LOCALREF_ARGINDEX_NEW;
            mdExprArgIndex = LLVM_DBG_VALUE_EXPR_ARGINDEX_NEW;

        } else if (call.getArgumentCount() == LLVM_DBG_VALUE_LOCALREF_ARGSIZE_OLD) {
            mdLocalArgIndex = LLVM_DBG_VALUE_LOCALREF_ARGINDEX_OLD;
            mdExprArgIndex = LLVM_DBG_VALUE_EXPR_ARGINDEX_OLD;

        } else {
            return null;
        }

        SymbolImpl value = getArg(call, LLVM_DBG_INTRINSICS_VALUE_ARGINDEX);
        SymbolImpl localSymbol = getArg(call, mdLocalArgIndex);
        if (!(value instanceof MetadataSymbol && localSymbol instanceof MetadataSymbol)) {
            return null;
        }
        MDBaseNode valueNode = ((MetadataSymbol) value).getNode();
        MDBaseNode localNode = ((MetadataSymbol) localSymbol).getNode();
        if (!(localNode instanceof MDLocalVariable)) {
            return null;
        }

        MDExpression expr = getExpression(call, mdExprArgIndex);
        return new DebugInstruction(kind, call.getDebugLocation(), (MDLocalVariable) localNode, expr, valueNode);
    }

    private final class MetadataProcessor implements MetadataVisitor {

        @Override
        public void visit(MDLocalVariable md) {
            // make sure the symbol is registered even if no value is available
            cache.getSourceSymbol(md, false);
        }

    }
}
