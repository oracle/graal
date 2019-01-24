/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.parser.listeners;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.oracle.truffle.llvm.parser.metadata.MDBaseNode;
import com.oracle.truffle.llvm.parser.metadata.MDKind;
import com.oracle.truffle.llvm.parser.metadata.MDLocation;
import com.oracle.truffle.llvm.parser.metadata.MDSubprogram;
import com.oracle.truffle.llvm.parser.metadata.MDValue;
import com.oracle.truffle.llvm.parser.model.IRScope;
import com.oracle.truffle.llvm.parser.model.attributes.AttributesCodeEntry;
import com.oracle.truffle.llvm.parser.model.blocks.InstructionBlock;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDefinition;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.AllocateInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.BinaryOperationInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.BranchInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.CallInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.CastInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.CompareExchangeInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.CompareInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ConditionalBranchInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ExtractElementInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ExtractValueInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.FenceInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.GetElementPointerInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.IndirectBranchInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.InsertElementInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.InsertValueInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.InvokeInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.LandingpadInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.LoadInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.PhiInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ReadModifyWriteInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ResumeInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ReturnInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.SelectInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ShuffleVectorInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.StoreInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.SwitchInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.SwitchOldInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.UnreachableInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ValueInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.VoidCallInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.VoidInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.VoidInvokeInstruction;
import com.oracle.truffle.llvm.parser.records.Records;
import com.oracle.truffle.llvm.parser.scanner.Block;
import com.oracle.truffle.llvm.runtime.except.LLVMParserException;
import com.oracle.truffle.llvm.runtime.types.AggregateType;
import com.oracle.truffle.llvm.runtime.types.ArrayType;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType.PrimitiveKind;
import com.oracle.truffle.llvm.runtime.types.StructureType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.VectorType;
import com.oracle.truffle.llvm.runtime.types.VoidType;

public final class Function implements ParserListener {

    private static final int INSTRUCTION_DECLAREBLOCKS = 1;
    private static final int INSTRUCTION_BINOP = 2;
    private static final int INSTRUCTION_CAST = 3;
    private static final int INSTRUCTION_GEP_OLD = 4;
    // private static final int INSTRUCTION_SELECT = 5;
    private static final int INSTRUCTION_EXTRACTELT = 6;
    private static final int INSTRUCTION_INSERTELT = 7;
    private static final int INSTRUCTION_SHUFFLEVEC = 8;
    // private static final int INSTRUCTION_CMP = 9;
    private static final int INSTRUCTION_RET = 10;
    private static final int INSTRUCTION_BR = 11;
    private static final int INSTRUCTION_SWITCH = 12;
    private static final int INSTRUCTION_INVOKE = 13;
    private static final int INSTRUCTION_UNREACHABLE = 15;
    private static final int INSTRUCTION_PHI = 16;
    private static final int INSTRUCTION_ALLOCA = 19;
    private static final int INSTRUCTION_LOAD = 20;
    // private static final int INSTRUCTION_VAARG = 23;
    private static final int INSTRUCTION_STORE_OLD = 24;
    private static final int INSTRUCTION_EXTRACTVAL = 26;
    private static final int INSTRUCTION_INSERTVAL = 27;
    private static final int INSTRUCTION_CMP2 = 28;
    private static final int INSTRUCTION_VSELECT = 29;
    private static final int INSTRUCTION_INBOUNDS_GEP_OLD = 30;
    private static final int INSTRUCTION_INDIRECTBR = 31;
    private static final int INSTRUCTION_DEBUG_LOC_AGAIN = 33;
    private static final int INSTRUCTION_CALL = 34;
    private static final int INSTRUCTION_DEBUG_LOC = 35;
    private static final int INSTRUCTION_FENCE = 36;
    private static final int INSTRUCTION_CMPXCHG_OLD = 37;
    private static final int INSTRUCTION_ATOMICRMW = 38;
    private static final int INSTRUCTION_RESUME = 39;
    private static final int INSTRUCTION_LANDINGPAD_OLD = 40;
    private static final int INSTRUCTION_LOADATOMIC = 41;
    // private static final int INSTRUCTION_STOREATOMIC_OLD = 42;
    private static final int INSTRUCTION_GEP = 43;
    private static final int INSTRUCTION_STORE = 44;
    private static final int INSTRUCTION_STOREATOMIC = 45;
    private static final int INSTRUCTION_CMPXCHG = 46;
    private static final int INSTRUCTION_LANDINGPAD = 47;
    // private static final int INSTRUCTION_CLEANUPRET = 48;
    // private static final int INSTRUCTION_CATCHRET = 49;
    // private static final int INSTRUCTION_CATCHPAD = 50;
    // private static final int INSTRUCTION_CLEANUPPAD = 51;
    // private static final int INSTRUCTION_CATCHSWITCH = 52;
    // private static final int INSTRUCTION_OPERAND_BUNDLE = 55;

    private final FunctionDefinition function;

    protected final Types types;

    private final int mode;

    private InstructionBlock instructionBlock = null;

    private boolean isLastBlockTerminated = true;

    private MDLocation lastLocation = null;

    private final List<Integer> implicitIndices = new ArrayList<>();

    private final ParameterAttributes paramAttributes;

    private final IRScope scope;

    Function(IRScope scope, Types types, FunctionDefinition function, int mode, ParameterAttributes paramAttributes) {
        this.scope = scope;
        this.types = types;
        this.function = function;
        this.mode = mode;
        this.paramAttributes = paramAttributes;
    }

    public void setupScope() {
        scope.startLocalScope(function);
        final FunctionType functionType = function.getType();
        for (Type argType : functionType.getArgumentTypes()) {
            scope.addSymbol(function.createParameter(argType), argType);
        }
    }

    @Override
    public ParserListener enter(Block block) {
        switch (block) {
            case CONSTANTS:
                return new Constants(types, scope);

            case VALUE_SYMTAB:
                return new ValueSymbolTable(scope);

            case METADATA:
            case METADATA_ATTACHMENT:
            case METADATA_KIND:
                return new Metadata(types, scope);

            default:
                return ParserListener.DEFAULT;
        }
    }

    @Override
    public void exit() {
        if (function.hasAttachedMetadata()) {
            MDBaseNode md = function.getMetadataAttachment(MDKind.DBG_NAME);
            if (md instanceof MDSubprogram) {
                ((MDSubprogram) md).setFunction(MDValue.create(function));
            }
        }
        scope.exitLocalScope();
    }

    @Override
    public void record(long id, long[] args) {
        final int opCode = (int) id;

        // debug locations can occur after terminating instructions, we process them before we
        // replace the old block
        switch (opCode) {
            case INSTRUCTION_DEBUG_LOC:
                parseDebugLocation(args); // intentional fallthrough

            case INSTRUCTION_DEBUG_LOC_AGAIN:
                applyDebugLocation();
                return;

            case INSTRUCTION_DECLAREBLOCKS:
                function.allocateBlocks((int) args[0]);
                return;

            default:
                break;
        }

        if (isLastBlockTerminated) {
            instructionBlock = function.generateBlock();
            isLastBlockTerminated = false;
        }

        switch (opCode) {

            case INSTRUCTION_BINOP:
                createBinaryOperation(args);
                break;

            case INSTRUCTION_CAST:
                createCast(args);
                break;

            case INSTRUCTION_GEP_OLD:
                createGetElementPointerOld(args, false);
                break;

            case INSTRUCTION_EXTRACTELT:
                createExtractElement(args);
                break;

            case INSTRUCTION_INSERTELT:
                createInsertElement(args);
                break;

            case INSTRUCTION_SHUFFLEVEC:
                createShuffleVector(args);
                break;

            case INSTRUCTION_RET:
                createReturn(args);
                break;

            case INSTRUCTION_BR:
                createBranch(args);
                break;

            case INSTRUCTION_SWITCH:
                createSwitch(args);
                break;

            case INSTRUCTION_UNREACHABLE:
                createUnreachable(args);
                break;

            case INSTRUCTION_PHI:
                createPhi(args);
                break;

            case INSTRUCTION_ALLOCA:
                createAlloca(args);
                break;

            case INSTRUCTION_LOAD:
                createLoad(args);
                break;

            case INSTRUCTION_STORE_OLD:
                createStoreOld(args);
                break;

            case INSTRUCTION_EXTRACTVAL:
                createExtractValue(args);
                break;

            case INSTRUCTION_INSERTVAL:
                createInsertValue(args);
                break;

            case INSTRUCTION_CMP2:
                createCompare2(args);
                break;

            case INSTRUCTION_VSELECT:
                createSelect(args);
                break;

            case INSTRUCTION_INBOUNDS_GEP_OLD:
                createGetElementPointerOld(args, true);
                break;

            case INSTRUCTION_INDIRECTBR:
                createIndirectBranch(args);
                break;

            case INSTRUCTION_CALL:
                createFunctionCall(args);
                break;

            case INSTRUCTION_INVOKE:
                createInvoke(args);
                break;

            case INSTRUCTION_LANDINGPAD:
                createLandingpad(args);
                break;

            case INSTRUCTION_LANDINGPAD_OLD:
                createLandingpadOld(args);
                break;

            case INSTRUCTION_RESUME:
                createResume(args);
                break;

            case INSTRUCTION_GEP:
                createGetElementPointer(args);
                break;

            case INSTRUCTION_STORE:
                createStore(args);
                break;

            case INSTRUCTION_LOADATOMIC:
                createLoadAtomic(args);
                break;

            case INSTRUCTION_STOREATOMIC:
                createAtomicStore(args);
                break;

            case INSTRUCTION_CMPXCHG_OLD:
            case INSTRUCTION_CMPXCHG:
                createCompareExchange(args, opCode);
                break;

            case INSTRUCTION_ATOMICRMW:
                createAtomicReadModifyWrite(args);
                break;

            case INSTRUCTION_FENCE:
                createFence(args);
                break;

            default:
                throw new LLVMParserException("Unsupported opCode in function block: " + opCode);
        }
    }

    private void emit(ValueInstruction instruction) {
        instructionBlock.append(instruction);
        scope.addSymbol(instruction, instruction.getType());
        scope.addInstruction(instruction);
    }

    private void emit(VoidInstruction instruction) {
        instructionBlock.append(instruction);
        scope.addInstruction(instruction);
    }

    private static final int INVOKE_HASEXPLICITFUNCTIONTYPE_SHIFT = 13;

    private void createInvoke(long[] args) {
        int i = 0;
        final AttributesCodeEntry paramAttr = paramAttributes.getCodeEntry(args[i++]);
        final long ccInfo = args[i++];

        final InstructionBlock normalSuccessor = function.getBlock(args[i++]);
        final InstructionBlock unwindSuccessor = function.getBlock(args[i++]);

        FunctionType functionType = null;
        if (((ccInfo >> INVOKE_HASEXPLICITFUNCTIONTYPE_SHIFT) & 1) != 0) {
            functionType = Types.castToFunction(types.get(args[i++]));
        }

        final int target = getIndex(args[i++]);
        final Type calleeType;
        if (scope.isValueForwardRef(target)) {
            calleeType = types.get(args[i++]);
        } else {
            calleeType = scope.getValueType(target);
        }

        if (functionType == null) {
            if (calleeType instanceof PointerType) {
                functionType = Types.castToFunction(((PointerType) calleeType).getPointeeType());
            } else if (calleeType instanceof FunctionType) {
                functionType = (FunctionType) calleeType;
            } else {
                throw new LLVMParserException("Cannot find Type of invoked function: " + calleeType);
            }
        }

        int[] arguments = new int[args.length - i];
        int skipped = 0;
        int j = 0;
        while (j < functionType.getArgumentTypes().length && i < args.length) {
            arguments[j++] = getIndex(args[i++]);
        }
        while (i < args.length) {
            int index = getIndex(args[i++]);
            arguments[j++] = index;
            if (scope.isValueForwardRef(index)) {
                i++;
                skipped++;
            }
        }
        if (skipped > 0) {
            arguments = Arrays.copyOf(arguments, arguments.length - skipped);
        }

        final Type returnType = functionType.getReturnType();
        if (returnType == VoidType.INSTANCE) {
            emit(VoidInvokeInstruction.fromSymbols(scope, target, arguments, normalSuccessor, unwindSuccessor, paramAttr));
        } else {
            emit(InvokeInstruction.fromSymbols(scope, returnType, target, arguments, normalSuccessor, unwindSuccessor, paramAttr));
        }
        isLastBlockTerminated = true;
    }

    private void createResume(long[] args) {
        int i = 0;
        final int val = getIndex(args[i]);
        // args[i + 1] -> type
        emit(ResumeInstruction.fromSymbols(scope.getSymbols(), val));
        isLastBlockTerminated = true;
    }

    private void createLandingpad(long[] args) {
        int i = 0;
        final Type type = types.get(args[i++]);
        final boolean isCleanup = args[i++] != 0;
        final int numClauses = (int) args[i++];
        long[] clauseKinds = new long[numClauses]; // catch = 0, filter = 1
        long[] clauseTypes = new long[numClauses];
        for (int j = 0; j < numClauses; j++) {
            clauseKinds[j] = args[i++];
            clauseTypes[j] = getIndex(args[i++]);
            if (scope.isValueForwardRef(clauseTypes[j])) {
                i++;
            }
        }
        emit(LandingpadInstruction.generate(scope.getSymbols(), type, isCleanup, clauseKinds, clauseTypes));
    }

    private void createLandingpadOld(long[] args) {
        int i = 0;
        final Type type = types.get(args[i++]);

        long persFn = getIndex(args[i++]);
        if (scope.isValueForwardRef((int) persFn)) {
            i++;
        }

        final boolean isCleanup = args[i++] != 0;
        final int numClauses = (int) args[i++];
        long[] clauseKinds = new long[numClauses]; // catch = 0, filter = 1
        long[] clauseTypes = new long[numClauses];
        for (int j = 0; j < numClauses; j++) {
            clauseKinds[j] = args[i++];
            clauseTypes[j] = getIndex(args[i++]);
            if (scope.isValueForwardRef(clauseTypes[j])) {
                i++;
            }
        }
        emit(LandingpadInstruction.generate(scope.getSymbols(), type, isCleanup, clauseKinds, clauseTypes));
    }

    private static final int CALL_HAS_FMF_SHIFT = 17;
    private static final int CALL_HAS_EXPLICITTYPE_SHIFT = 15;

    private void createFunctionCall(long[] args) {
        int i = 0;
        final AttributesCodeEntry paramAttr = paramAttributes.getCodeEntry(args[i++]);
        final long ccinfo = args[i++];

        if (((ccinfo >> CALL_HAS_FMF_SHIFT) & 1) != 0) {
            i++; // fast math flags
        }

        FunctionType functionType = null;
        if (((ccinfo >> CALL_HAS_EXPLICITTYPE_SHIFT) & 1) != 0) {
            functionType = Types.castToFunction(types.get(args[i++]));
        }

        int callee = getIndex(args[i++]);
        Type calleeType;
        if (scope.isValueForwardRef(callee)) {
            calleeType = types.get(args[i++]);
        } else {
            calleeType = scope.getValueType(callee);
        }

        if (functionType == null) {
            if (calleeType instanceof FunctionType) {
                functionType = (FunctionType) calleeType;
            } else {
                functionType = Types.castToFunction(Types.castToPointer(calleeType).getPointeeType());
            }
        }

        int[] arguments = new int[args.length - i];
        int skipped = 0;
        int j = 0;
        while (j < functionType.getArgumentTypes().length && i < args.length) {
            arguments[j++] = getIndex(args[i++]);
        }
        while (i < args.length) {
            int index = getIndex(args[i++]);
            arguments[j++] = index;
            if (scope.isValueForwardRef(index)) {
                i++;
                skipped++;
            }
        }
        if (skipped > 0) {
            arguments = Arrays.copyOf(arguments, arguments.length - skipped);
        }

        final Type returnType = functionType.getReturnType();

        if (returnType == VoidType.INSTANCE) {
            emit(VoidCallInstruction.fromSymbols(scope, callee, arguments, paramAttr));
        } else {
            emit(CallInstruction.fromSymbols(scope, returnType, callee, arguments, paramAttr));
        }
    }

    private static final long SWITCH_CASERANGE_SHIFT = 16;
    private static final long SWITCH_CASERANGE_FLAG = 0x4B5;

    private void createSwitch(long[] args) {
        int i = 0;

        if ((args[0] >> SWITCH_CASERANGE_SHIFT) == SWITCH_CASERANGE_FLAG) {
            i++; // indicator
            i++; // type
            final int cond = getIndex(args[i++]);
            final int defaultBlock = (int) args[i++];

            final int count = (int) args[i++];
            final long[] caseConstants = new long[count];
            final int[] caseBlocks = new int[count];
            for (int j = 0; j < count; j++) {
                i += 2;
                caseConstants[j] = Records.toSignedValue(args[i++]);
                caseBlocks[j] = (int) args[i++];
            }

            emit(SwitchOldInstruction.generate(function, scope.getSymbols(), cond, defaultBlock, caseConstants, caseBlocks));

        } else {
            i++; // type

            final int cond = getIndex(args[i++]);
            final int defaultBlock = (int) args[i++];
            final int count = (args.length - i) >> 1;
            final int[] caseValues = new int[count];
            final int[] caseBlocks = new int[count];
            for (int j = 0; j < count; j++) {
                caseValues[j] = getIndexAbsolute(args[i++]);
                caseBlocks[j] = (int) args[i++];
            }

            emit(SwitchInstruction.generate(function, scope.getSymbols(), cond, defaultBlock, caseValues, caseBlocks));
        }

        isLastBlockTerminated = true;
    }

    private static final long ALLOCA_INMASK = 1L << 5;
    private static final long ALLOCA_EXPLICITTYPEMASK = 1L << 6;
    private static final long ALLOCA_SWIFTERRORMASK = 1L << 7;
    private static final long ALLOCA_FLAGSMASK = ALLOCA_INMASK | ALLOCA_EXPLICITTYPEMASK | ALLOCA_SWIFTERRORMASK;

    private void createAlloca(long[] args) {
        int i = 0;
        final long typeRecord = args[i++];
        i++; // type of count
        final int count = getIndexAbsolute(args[i++]);
        final long alignRecord = args[i];

        final int align = getAlign(alignRecord & ~ALLOCA_FLAGSMASK);

        Type type = types.get(typeRecord);
        if ((alignRecord & ALLOCA_EXPLICITTYPEMASK) != 0L) {
            type = new PointerType(type);
        } else if (!(type instanceof PointerType)) {
            throw new LLVMParserException("Alloca with unexpected type: " + type);
        }
        emit(AllocateInstruction.fromSymbols(scope.getSymbols(), type, count, align));
    }

    private static final int LOAD_ARGS_EXPECTED_AFTER_TYPE = 3;

    private void createLoad(long[] args) {
        int i = 0;
        final int src = getIndex(args[i++]);

        final Type srcType;
        if (scope.isValueForwardRef(src)) {
            srcType = types.get(args[i++]);
        } else {
            srcType = scope.getValueType(src);
        }

        final Type opType;
        if (i + LOAD_ARGS_EXPECTED_AFTER_TYPE == args.length) {
            opType = types.get(args[i++]);
        } else {
            opType = Types.castToPointer(srcType).getPointeeType();
        }

        final int align = getAlign(args[i++]);
        final boolean isVolatile = args[i] != 0;

        emit(LoadInstruction.fromSymbols(scope.getSymbols(), opType, src, align, isVolatile));
    }

    private static final int LOADATOMIC_ARGS_EXPECTED_AFTER_TYPE = 5;

    private void createLoadAtomic(long[] args) {
        int i = 0;
        final int src = getIndex(args[i++]);

        final Type srcType;
        if (scope.isValueForwardRef(src)) {
            srcType = types.get(args[i++]);
        } else {
            srcType = scope.getValueType(src);
        }

        final Type opType;
        if (i + LOADATOMIC_ARGS_EXPECTED_AFTER_TYPE == args.length) {
            opType = types.get(args[i++]);
        } else {
            opType = Types.castToPointer(srcType).getPointeeType();
        }

        final int align = getAlign(args[i++]);
        final boolean isVolatile = args[i++] != 0;
        final long atomicOrdering = args[i++];
        final long synchronizationScope = args[i];

        emit(LoadInstruction.fromSymbols(scope.getSymbols(), opType, src, align, isVolatile, atomicOrdering, synchronizationScope));
    }

    private void createCompareExchange(long[] args, int record) {
        int i = 0;

        final Type ptrType;
        final int ptr = getIndex(args[i]);
        if (scope.isValueForwardRef(ptr)) {
            ptrType = types.get(args[++i]);
        } else {
            ptrType = scope.getValueType(ptr);
        }
        final int cmp = getIndex(args[++i]);
        if (record == INSTRUCTION_CMPXCHG && scope.isValueForwardRef(cmp)) {
            ++i; // type of cmp
        }
        final int replace = getIndex(args[++i]);
        final boolean isVolatile = args[++i] != 0;
        final long successOrdering = args[++i];
        final long synchronizationScope = args[++i];
        final long failureOrdering = i < args.length - 1 ? args[++i] : -1L;
        final boolean addExtractValue = i >= args.length - 1;
        final boolean isWeak = addExtractValue || (args[++i] != 0);

        final AggregateType type = findCmpxchgResultType(Types.castToPointer(ptrType).getPointeeType());

        emit(CompareExchangeInstruction.fromSymbols(scope.getSymbols(), type, ptr, cmp, replace, isVolatile, successOrdering, synchronizationScope, failureOrdering, isWeak));

        if (addExtractValue) {
            // in older llvm versions cmpxchg just returned the new value at the pointer, to emulate
            // this we have to add an extractelvalue instruction. llvm does the same thing
            createExtractValue(new long[]{1, 0});
            implicitIndices.add(scope.getNextValueIndex() - 1); // register the implicit index
        }
    }

    private static final int CMPXCHG_TYPE_LENGTH = 2;
    private static final int CMPXCHG_TYPE_ELEMENTTYPE = 0;
    private static final int CMPXCHG_TYPE_BOOLTYPE = 1;

    private AggregateType findCmpxchgResultType(Type elementType) {
        // cmpxchg is the only instruction that does not directly reference its return type in the
        // type table
        for (Type t : types) {
            if (t instanceof StructureType) {
                final Type[] elts = ((StructureType) t).getElementTypes();
                if (elts.length == CMPXCHG_TYPE_LENGTH && elementType == elts[CMPXCHG_TYPE_ELEMENTTYPE] && PrimitiveType.I1 == elts[CMPXCHG_TYPE_BOOLTYPE]) {
                    return (AggregateType) t;
                }
            }
        }
        // the type may not exist if the value is not being used
        return new StructureType(true, new Type[]{elementType, PrimitiveType.I1});
    }

    private void parseDebugLocation(long[] args) {
        // if e.g. the previous instruction was @llvm.debug.declare this will be the location of the
        // declaration of the variable in the source file
        lastLocation = MDLocation.createFromFunctionArgs(args, scope.getMetadata());
    }

    private void applyDebugLocation() {
        final int lastInstructionIndex = instructionBlock.getInstructionCount() - 1;
        instructionBlock.getInstruction(lastInstructionIndex).setDebugLocation(lastLocation);
    }

    private void createAtomicStore(long[] args) {
        int i = 0;

        final int destination = getIndex(args[i++]);
        if (scope.isValueForwardRef(destination)) {
            i++;
        }

        final int source = getIndex(args[i++]);
        if (scope.isValueForwardRef(source)) {
            i++;
        }

        final int align = getAlign(args[i++]);
        final boolean isVolatile = args[i++] != 0;
        final long atomicOrdering = args[i++];
        final long synchronizationScope = args[i];

        emit(StoreInstruction.fromSymbols(scope.getSymbols(), destination, source, align, isVolatile, atomicOrdering, synchronizationScope));
    }

    private void createAtomicReadModifyWrite(long[] args) {
        int i = 0;

        final int ptr = getIndex(args[i++]);
        final Type ptrType;
        if (scope.isValueForwardRef(ptr)) {
            ptrType = types.get(args[i++]);
        } else {
            ptrType = scope.getValueType(ptr);
        }
        final int value = getIndex(args[i++]);
        final int opcode = (int) args[i++];
        final boolean isVolatile = args[i++] != 0;
        final long atomicOrdering = args[i++];
        final long synchronizationScope = args[i];

        final Type type = Types.castToPointer(ptrType).getPointeeType();

        emit(ReadModifyWriteInstruction.fromSymbols(scope.getSymbols(), type, ptr, value, opcode, isVolatile, atomicOrdering, synchronizationScope));
    }

    private void createFence(long[] args) {
        int i = 0;

        final long atomicOrdering = args[i++];
        final long synchronizationScope = args[i];

        emit(FenceInstruction.generate(atomicOrdering, synchronizationScope));
    }

    private void createBinaryOperation(long[] args) {
        int i = 0;
        Type type;
        int lhs = getIndex(args[i++]);
        if (scope.isValueForwardRef(lhs)) {
            type = types.get(args[i++]);
        } else {
            type = scope.getValueType(lhs);
        }
        int rhs = getIndex(args[i++]);
        int opcode = (int) args[i++];
        int flags = i < args.length ? (int) args[i] : 0;

        emit(BinaryOperationInstruction.fromSymbols(scope.getSymbols(), type, opcode, flags, lhs, rhs));
    }

    private void createBranch(long[] args) {
        if (args.length == 1) {
            emit(BranchInstruction.fromTarget(function.getBlock(args[0])));

        } else {
            final int condition = getIndex(args[2]);
            final InstructionBlock trueSuccessor = function.getBlock(args[0]);
            final InstructionBlock falseSuccessor = function.getBlock(args[1]);
            emit(ConditionalBranchInstruction.fromSymbols(scope.getSymbols(), condition, trueSuccessor, falseSuccessor));
        }

        isLastBlockTerminated = true;
    }

    private void createCast(long[] args) {
        int i = 0;
        int value = getIndex(args[i++]);
        if (scope.isValueForwardRef(value)) {
            i++;
        }
        Type type = types.get(args[i++]);
        int opcode = (int) args[i];

        emit(CastInstruction.fromSymbols(scope.getSymbols(), type, opcode, value));
    }

    private void createCompare2(long[] args) {
        int i = 0;
        Type operandType;
        int lhs = getIndex(args[i++]);
        if (scope.isValueForwardRef(lhs)) {
            operandType = types.get(args[i++]);
        } else {
            operandType = scope.getValueType(lhs);
        }
        int rhs = getIndex(args[i++]);
        int opcode = (int) args[i];

        Type type = operandType instanceof VectorType
                        ? new VectorType(PrimitiveType.I1, Types.castToVector(operandType).getNumberOfElements())
                        : PrimitiveType.I1;

        emit(CompareInstruction.fromSymbols(scope.getSymbols(), type, opcode, lhs, rhs));
    }

    private void createExtractElement(long[] args) {
        int i = 0;
        int vector = getIndex(args[i++]);

        Type vectorType;
        if (scope.isValueForwardRef(vector)) {
            vectorType = types.get(args[i++]);
        } else {
            vectorType = scope.getValueType(vector);
        }
        int index = getIndex(args[i]);

        final Type elementType = Types.castToVector(vectorType).getElementType();
        emit(ExtractElementInstruction.fromSymbols(scope.getSymbols(), elementType, vector, index));
    }

    private void createExtractValue(long[] args) {
        int i = 0;
        int aggregate = getIndex(args[i++]);
        Type aggregateType = null;
        if (scope.isValueForwardRef(aggregate)) {
            aggregateType = types.get(args[i++]);
        }
        int index = (int) args[i++];
        if (aggregateType == null) {
            aggregateType = scope.getValueType(aggregate);
        }

        if (i != args.length) {
            throw new LLVMParserException("Multiple indices for extractvalue are not yet supported!");
        }

        final Type elementType = Types.castToAggregate(aggregateType).getElementType(index);
        emit(ExtractValueInstruction.fromSymbols(scope.getSymbols(), elementType, aggregate, index));
    }

    private void createGetElementPointer(long[] args) {
        int i = 0;
        boolean isInbounds = args[i++] != 0;
        i++; // we do not use this parameter
        int pointer = getIndex(args[i++]);
        Type base;
        if (scope.isValueForwardRef(pointer)) {
            base = types.get(args[i++]);
        } else {
            base = scope.getValueType(pointer);
        }
        List<Integer> indices = getIndices(args, i);
        Type type = new PointerType(getElementPointerType(base, indices));

        emit(GetElementPointerInstruction.fromSymbols(scope.getSymbols(), type, pointer, indices, isInbounds));
    }

    private void createGetElementPointerOld(long[] args, boolean isInbounds) {
        int i = 0;
        int pointer = getIndex(args[i++]);
        Type base;
        if (scope.isValueForwardRef(pointer)) {
            base = types.get(args[i++]);
        } else {
            base = scope.getValueType(pointer);
        }
        List<Integer> indices = getIndices(args, i);
        Type type = new PointerType(getElementPointerType(base, indices));

        emit(GetElementPointerInstruction.fromSymbols(scope.getSymbols(), type, pointer, indices, isInbounds));
    }

    private void createIndirectBranch(long[] args) {
        int address = getIndex(args[1]);
        int[] successors = new int[args.length - 2];
        for (int i = 0; i < successors.length; i++) {
            successors[i] = (int) args[i + 2];
        }

        emit(IndirectBranchInstruction.generate(function, scope.getSymbols(), address, successors));
        isLastBlockTerminated = true;
    }

    private void createInsertElement(long[] args) {
        int i = 0;

        int vector = getIndex(args[i++]);
        Type type;
        if (scope.isValueForwardRef(vector)) {
            type = types.get(args[i++]);
        } else {
            type = scope.getValueType(vector);
        }

        int value = getIndex(args[i++]);
        int index = getIndex(args[i]);

        emit(InsertElementInstruction.fromSymbols(scope.getSymbols(), type, vector, index, value));
    }

    private void createInsertValue(long[] args) {
        int i = 0;

        int aggregate = getIndex(args[i++]);
        Type type;
        if (scope.isValueForwardRef(aggregate)) {
            type = types.get(args[i++]);
        } else {
            type = scope.getValueType(aggregate);
        }

        int value = getIndex(args[i++]);
        if (scope.isValueForwardRef(value)) {
            i++;
        }

        int index = (int) args[i++];

        if (args.length != i) {
            throw new LLVMParserException("Multiple indices for insertvalue are not yet supported!");
        }

        emit(InsertValueInstruction.fromSymbols(scope.getSymbols(), type, aggregate, index, value));
    }

    private void createPhi(long[] args) {
        Type type = types.get(args[0]);
        int count = (args.length) - 1 >> 1;
        int[] values = new int[count];
        InstructionBlock[] blocks = new InstructionBlock[count];
        for (int i = 0, j = 1; i < count; i++) {
            values[i] = getIndex(Records.toSignedValue(args[j++]));
            blocks[i] = function.getBlock(args[j++]);
        }

        emit(PhiInstruction.generate(scope.getSymbols(), type, values, blocks));
    }

    private void createReturn(long[] args) {
        if (args.length == 0 || args[0] == 0) {
            emit(ReturnInstruction.generate());
        } else {
            final int value = getIndex(args[0]);
            emit(ReturnInstruction.generate(scope.getSymbols(), value));
        }

        isLastBlockTerminated = true;
    }

    private void createSelect(long[] args) {
        int i = 0;
        Type type;
        int trueValue = getIndex(args[i++]);
        if (scope.isValueForwardRef(trueValue)) {
            type = types.get(args[i++]);
        } else {
            type = scope.getValueType(trueValue);
        }
        int falseValue = getIndex(args[i++]);
        int condition = getIndex(args[i]);

        emit(SelectInstruction.fromSymbols(scope.getSymbols(), type, condition, trueValue, falseValue));
    }

    private void createShuffleVector(long[] args) {
        int i = 0;

        int vector1 = getIndex(args[i++]);
        Type vectorType;
        if (scope.isValueForwardRef(vector1)) {
            vectorType = types.get(args[i++]);
        } else {
            vectorType = scope.getValueType(vector1);
        }

        int vector2 = getIndex(args[i++]);
        int mask = getIndex(args[i]);

        Type subtype = Types.castToVector(vectorType).getElementType();
        int length = Types.castToVector(scope.getValueType(mask)).getNumberOfElements();
        Type type = new VectorType(subtype, length);

        emit(ShuffleVectorInstruction.fromSymbols(scope.getSymbols(), type, vector1, vector2, mask));
    }

    private void createStore(long[] args) {
        int i = 0;

        int destination = getIndex(args[i++]);
        if (scope.isValueForwardRef(destination)) {
            i++;
        }

        int source = getIndex(args[i++]);
        if (scope.isValueForwardRef(source)) {
            i++;
        }

        int align = getAlign(args[i++]);
        boolean isVolatile = args[i] != 0;

        emit(StoreInstruction.fromSymbols(scope.getSymbols(), destination, source, align, isVolatile));
    }

    private void createStoreOld(long[] args) {
        int i = 0;

        int destination = getIndex(args[i++]);
        if (scope.isValueForwardRef(destination)) {
            i++;
        }

        int source = getIndex(args[i++]);
        int align = getAlign(args[i++]);
        boolean isVolatile = args[i] != 0;

        emit(StoreInstruction.fromSymbols(scope.getSymbols(), destination, source, align, isVolatile));
    }

    private void createUnreachable(@SuppressWarnings("unused") long[] args) {
        emit(UnreachableInstruction.generate());
        isLastBlockTerminated = true;
    }

    private static int getAlign(long argument) {
        return (int) argument & (Long.SIZE - 1);
    }

    private Type getElementPointerType(Type type, List<Integer> indices) {
        Type elementType = type;
        for (int indexIndex : indices) {
            if (elementType instanceof PointerType) {
                elementType = ((PointerType) elementType).getPointeeType();
            } else if (elementType instanceof ArrayType) {
                elementType = ((ArrayType) elementType).getElementType();
            } else if (elementType instanceof VectorType) {
                elementType = ((VectorType) elementType).getElementType();
            } else if (elementType instanceof StructureType) {
                StructureType structure = (StructureType) elementType;
                Type indexType = scope.getValueType(indexIndex);
                if (!(indexType instanceof PrimitiveType)) {
                    throw new LLVMParserException("Cannot infer structure element from " + indexType);
                }
                Number indexNumber = (Number) ((PrimitiveType) indexType).getConstant();
                assert ((PrimitiveType) indexType).getPrimitiveKind() == PrimitiveKind.I32;
                elementType = structure.getElementType(indexNumber.intValue());
            } else {
                throw new LLVMParserException("Cannot index type: " + elementType);
            }
        }
        return elementType;
    }

    private int getIndex(long index) {
        if (mode >= 1) {
            return getIndexRelative(index);
        } else {
            return getIndexAbsolute(index);
        }
    }

    private List<Integer> getIndices(long[] arguments, int from) {
        List<Integer> indices = new ArrayList<>(arguments.length - from);
        int i = from;
        while (i < arguments.length) {
            int index = getIndex(arguments[i++]);
            if (scope.isValueForwardRef(index)) {
                // type of forward referenced index
                i++;
            }
            indices.add(index);
        }
        return Collections.unmodifiableList(indices);
    }

    private int getIndexAbsolute(long index) {
        long actualIndex = index;
        for (int i = 0; i < implicitIndices.size() && implicitIndices.get(i) <= actualIndex; i++) {
            actualIndex++;
        }
        return (int) actualIndex;
    }

    private int getIndexRelative(long index) {
        long actualIndex = scope.getNextValueIndex() - index;
        for (int i = implicitIndices.size() - 1; i >= 0 && implicitIndices.get(i) > actualIndex; i--) {
            actualIndex--;
        }
        return (int) actualIndex;
    }

    public FunctionDefinition getFunction() {
        return function;
    }

    public IRScope getScope() {
        return scope;
    }
}
