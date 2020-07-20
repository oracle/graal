/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates.
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
import com.oracle.truffle.llvm.parser.model.symbols.instructions.UnaryOperationInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.UnreachableInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ValueInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.VoidCallInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.VoidInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.VoidInvokeInstruction;
import com.oracle.truffle.llvm.parser.scanner.Block;
import com.oracle.truffle.llvm.parser.scanner.RecordBuffer;
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
    private static final int INSTRUCTION_SELECT = 5;
    private static final int INSTRUCTION_EXTRACTELT = 6;
    private static final int INSTRUCTION_INSERTELT = 7;
    private static final int INSTRUCTION_SHUFFLEVEC = 8;
    private static final int INSTRUCTION_CMP = 9;
    private static final int INSTRUCTION_RET = 10;
    private static final int INSTRUCTION_BR = 11;
    private static final int INSTRUCTION_SWITCH = 12;
    private static final int INSTRUCTION_INVOKE = 13;
    private static final int INSTRUCTION_UNREACHABLE = 15;
    private static final int INSTRUCTION_PHI = 16;
    private static final int INSTRUCTION_ALLOCA = 19;
    private static final int INSTRUCTION_LOAD = 20;
    private static final int INSTRUCTION_VAARG = 23;
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
    private static final int INSTRUCTION_STOREATOMIC_OLD = 42;
    private static final int INSTRUCTION_GEP = 43;
    private static final int INSTRUCTION_STORE = 44;
    private static final int INSTRUCTION_STOREATOMIC = 45;
    private static final int INSTRUCTION_CMPXCHG = 46;
    private static final int INSTRUCTION_LANDINGPAD = 47;
    private static final int INSTRUCTION_CLEANUPRET = 48;
    private static final int INSTRUCTION_CATCHRET = 49;
    private static final int INSTRUCTION_CATCHPAD = 50;
    private static final int INSTRUCTION_CLEANUPPAD = 51;
    private static final int INSTRUCTION_CATCHSWITCH = 52;
    private static final int INSTRUCTION_OPERAND_BUNDLE = 55;
    private static final int INSTRUCTION_UNOP = 56;

    private final FunctionDefinition function;

    private final Types types;

    private final int mode;

    private InstructionBlock instructionBlock = null;

    private boolean isLastBlockTerminated = true;

    private MDLocation lastLocation = null;

    private final List<Integer> implicitIndices = new ArrayList<>();

    private final ParameterAttributes paramAttributes;

    private final IRScope scope;

    public Function(IRScope scope, Types types, FunctionDefinition function, int mode, ParameterAttributes paramAttributes) {
        this.scope = scope;
        this.types = types;
        this.function = function;
        this.mode = mode;
        this.paramAttributes = paramAttributes;
    }

    public void setupScope() {
        scope.startLocalScope(function);
        final FunctionType functionType = function.getType();
        for (int i = 0; i < functionType.getNumberOfArguments(); i++) {
            Type argType = functionType.getArgumentType(i);
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
    public void record(RecordBuffer buffer) {
        int opCode = buffer.getId();

        // debug locations can occur after terminating instructions, we process them before we
        // replace the old block
        switch (opCode) {
            case INSTRUCTION_DEBUG_LOC:
                parseDebugLocation(buffer); // intentional fallthrough

            case INSTRUCTION_DEBUG_LOC_AGAIN:
                applyDebugLocation();
                return;

            case INSTRUCTION_DECLAREBLOCKS:
                function.allocateBlocks(buffer.readInt());
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
                createBinaryOperation(buffer);
                break;

            case INSTRUCTION_UNOP:
                createUnaryOperation(buffer);
                break;

            case INSTRUCTION_CAST:
                createCast(buffer);
                break;

            case INSTRUCTION_GEP_OLD:
                createGetElementPointerOld(buffer, false);
                break;

            case INSTRUCTION_EXTRACTELT:
                createExtractElement(buffer);
                break;

            case INSTRUCTION_INSERTELT:
                createInsertElement(buffer);
                break;

            case INSTRUCTION_SHUFFLEVEC:
                createShuffleVector(buffer);
                break;

            case INSTRUCTION_RET:
                createReturn(buffer);
                break;

            case INSTRUCTION_BR:
                createBranch(buffer);
                break;

            case INSTRUCTION_SWITCH:
                createSwitch(buffer);
                break;

            case INSTRUCTION_UNREACHABLE:
                createUnreachable(buffer);
                break;

            case INSTRUCTION_PHI:
                createPhi(buffer);
                break;

            case INSTRUCTION_ALLOCA:
                createAlloca(buffer);
                break;

            case INSTRUCTION_LOAD:
                createLoad(buffer);
                break;

            case INSTRUCTION_STORE_OLD:
                createStoreOld(buffer);
                break;

            case INSTRUCTION_EXTRACTVAL:
                createExtractValue(buffer);
                break;

            case INSTRUCTION_INSERTVAL:
                createInsertValue(buffer);
                break;

            case INSTRUCTION_CMP2:
                createCompare2(buffer);
                break;

            case INSTRUCTION_VSELECT:
                createSelect(buffer);
                break;

            case INSTRUCTION_INBOUNDS_GEP_OLD:
                createGetElementPointerOld(buffer, true);
                break;

            case INSTRUCTION_INDIRECTBR:
                createIndirectBranch(buffer);
                break;

            case INSTRUCTION_CALL:
                createFunctionCall(buffer);
                break;

            case INSTRUCTION_INVOKE:
                createInvoke(buffer);
                break;

            case INSTRUCTION_LANDINGPAD:
                createLandingpad(buffer);
                break;

            case INSTRUCTION_LANDINGPAD_OLD:
                createLandingpadOld(buffer);
                break;

            case INSTRUCTION_RESUME:
                createResume(buffer);
                break;

            case INSTRUCTION_GEP:
                createGetElementPointer(buffer);
                break;

            case INSTRUCTION_STORE:
                createStore(buffer);
                break;

            case INSTRUCTION_LOADATOMIC:
                createLoadAtomic(buffer);
                break;

            case INSTRUCTION_STOREATOMIC:
                createAtomicStore(buffer);
                break;

            case INSTRUCTION_CMPXCHG_OLD:
            case INSTRUCTION_CMPXCHG:
                createCompareExchange(buffer, opCode);
                break;

            case INSTRUCTION_ATOMICRMW:
                createAtomicReadModifyWrite(buffer);
                break;

            case INSTRUCTION_FENCE:
                createFence(buffer);
                break;

            default:
                // differentiate between unknown and unsupported instructions
                switch (opCode) {
                    case INSTRUCTION_SELECT:
                    case INSTRUCTION_CMP:
                    case INSTRUCTION_VAARG:
                    case INSTRUCTION_STOREATOMIC_OLD:
                    case INSTRUCTION_CLEANUPRET:
                    case INSTRUCTION_CATCHRET:
                    case INSTRUCTION_CATCHPAD:
                    case INSTRUCTION_CLEANUPPAD:
                    case INSTRUCTION_CATCHSWITCH:
                    case INSTRUCTION_OPERAND_BUNDLE:
                        throw new LLVMParserException("Unsupported opCode in function block: " + opCode);
                    default:
                        throw new LLVMParserException("Unknown opCode in function block: " + opCode);
                }
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

    private void createInvoke(RecordBuffer buffer) {
        AttributesCodeEntry paramAttr = paramAttributes.getCodeEntry(buffer.read());
        long ccInfo = buffer.read();

        InstructionBlock normalSuccessor = function.getBlock(buffer.read());
        InstructionBlock unwindSuccessor = function.getBlock(buffer.read());

        FunctionType functionType = null;
        if (((ccInfo >> INVOKE_HASEXPLICITFUNCTIONTYPE_SHIFT) & 1) != 0) {
            functionType = Types.castToFunction(readType(buffer));
        }

        int target = readIndex(buffer);
        Type calleeType = readValueType(buffer, target);

        if (functionType == null) {
            if (calleeType instanceof PointerType) {
                functionType = Types.castToFunction(((PointerType) calleeType).getPointeeType());
            } else if (calleeType instanceof FunctionType) {
                functionType = (FunctionType) calleeType;
            } else {
                throw new LLVMParserException("Cannot find Type of invoked function: " + calleeType);
            }
        }

        int[] args = new int[buffer.remaining()];
        int j = 0;
        // the formal parameters are read without forward types
        while (j < functionType.getNumberOfArguments() && buffer.remaining() > 0) {
            args[j++] = readIndex(buffer);
        }
        // now varargs are read with forward types
        while (buffer.remaining() > 0) {
            args[j++] = readIndexSkipType(buffer);
        }
        if (args.length != j) {
            args = Arrays.copyOf(args, j);
        }

        final Type returnType = functionType.getReturnType();
        if (returnType == VoidType.INSTANCE) {
            emit(VoidInvokeInstruction.fromSymbols(scope, target, args, normalSuccessor, unwindSuccessor, paramAttr));
        } else {
            emit(InvokeInstruction.fromSymbols(scope, returnType, target, args, normalSuccessor, unwindSuccessor, paramAttr));
        }
        isLastBlockTerminated = true;
    }

    private void createResume(RecordBuffer buffer) {
        int val = readIndexSkipType(buffer);
        emit(ResumeInstruction.fromSymbols(scope.getSymbols(), val));
        isLastBlockTerminated = true;
    }

    private void createLandingpad(RecordBuffer buffer) {
        Type type = readType(buffer);
        boolean isCleanup = buffer.readBoolean();
        int numClauses = buffer.readInt();
        long[] clauseKinds = new long[numClauses]; // catch = 0, filter = 1
        long[] clauseTypes = new long[numClauses];
        for (int j = 0; j < numClauses; j++) {
            clauseKinds[j] = buffer.read();
            clauseTypes[j] = readIndexSkipType(buffer);
        }
        emit(LandingpadInstruction.generate(scope.getSymbols(), type, isCleanup, clauseKinds, clauseTypes));
    }

    private void createLandingpadOld(RecordBuffer buffer) {
        final Type type = readType(buffer);
        readIndexSkipType(buffer);  // personality function

        final boolean isCleanup = buffer.readBoolean();
        final int numClauses = buffer.readInt();
        long[] clauseKinds = new long[numClauses]; // catch = 0, filter = 1
        long[] clauseTypes = new long[numClauses];
        for (int j = 0; j < numClauses; j++) {
            clauseKinds[j] = buffer.read();
            clauseTypes[j] = readIndexSkipType(buffer);
        }
        emit(LandingpadInstruction.generate(scope.getSymbols(), type, isCleanup, clauseKinds, clauseTypes));
    }

    private static final int CALL_HAS_FMF_SHIFT = 17;
    private static final int CALL_HAS_EXPLICITTYPE_SHIFT = 15;

    private void createFunctionCall(RecordBuffer buffer) {
        final AttributesCodeEntry paramAttr = paramAttributes.getCodeEntry(buffer.read());
        final long ccinfo = buffer.read();

        if (((ccinfo >> CALL_HAS_FMF_SHIFT) & 1) != 0) {
            buffer.read(); // fast math flags
        }

        FunctionType functionType = null;
        if (((ccinfo >> CALL_HAS_EXPLICITTYPE_SHIFT) & 1) != 0) {
            functionType = Types.castToFunction(readType(buffer));
        }

        int callee = readIndex(buffer);
        Type calleeType = readValueType(buffer, callee);

        if (functionType == null) {
            if (calleeType instanceof FunctionType) {
                functionType = (FunctionType) calleeType;
            } else {
                functionType = Types.castToFunction(Types.castToPointer(calleeType).getPointeeType());
            }
        }

        int[] args = new int[buffer.remaining()];
        int j = 0;
        while (j < functionType.getNumberOfArguments() && buffer.remaining() > 0) {
            args[j++] = readIndex(buffer);
        }
        while (buffer.remaining() > 0) {
            args[j++] = readIndexSkipType(buffer);
        }
        if (j != args.length) {
            args = Arrays.copyOf(args, j);
        }

        final Type returnType = functionType.getReturnType();

        if (returnType == VoidType.INSTANCE) {
            emit(VoidCallInstruction.fromSymbols(scope, callee, args, paramAttr));
        } else {
            emit(CallInstruction.fromSymbols(scope, returnType, callee, args, paramAttr));
        }
    }

    private static final long SWITCH_CASERANGE_SHIFT = 16;
    private static final long SWITCH_CASERANGE_FLAG = 0x4B5;

    private void createSwitch(RecordBuffer buffer) {
        long first = buffer.read();
        if ((first >> SWITCH_CASERANGE_SHIFT) == SWITCH_CASERANGE_FLAG) {
            // "special" format that likely isn't produced by LLVM at all

            // first value is indicator
            buffer.read(); // type
            final int cond = readIndex(buffer);
            final int defaultBlock = buffer.readInt();

            final int count = buffer.readInt();
            final long[] caseConstants = new long[count];
            final int[] caseBlocks = new int[count];
            for (int j = 0; j < count; j++) {
                buffer.read();
                buffer.read();
                caseConstants[j] = buffer.readSignedValue();
                caseBlocks[j] = buffer.readInt();
            }

            emit(SwitchOldInstruction.generate(function, scope.getSymbols(), cond, defaultBlock, caseConstants, caseBlocks));

        } else {
            // first value is type

            final int cond = readIndex(buffer);
            final int defaultBlock = buffer.readInt();
            final int count = buffer.remaining() >> 1;
            final int[] caseValues = new int[count];
            final int[] caseBlocks = new int[count];
            for (int j = 0; j < count; j++) {
                caseValues[j] = getIndexAbsolute(buffer.read());
                caseBlocks[j] = buffer.readInt();
            }

            emit(SwitchInstruction.generate(function, scope.getSymbols(), cond, defaultBlock, caseValues, caseBlocks));
        }

        isLastBlockTerminated = true;
    }

    private static final long ALLOCA_INMASK = 1L << 5;
    private static final long ALLOCA_EXPLICITTYPEMASK = 1L << 6;
    private static final long ALLOCA_SWIFTERRORMASK = 1L << 7;
    private static final long ALLOCA_FLAGSMASK = ALLOCA_INMASK | ALLOCA_EXPLICITTYPEMASK | ALLOCA_SWIFTERRORMASK;

    private void createAlloca(RecordBuffer buffer) {
        long typeRecord = buffer.read();
        buffer.read(); // type of count
        int count = getIndexAbsolute(buffer.read());
        long alignRecord = buffer.read();

        int align = getAlign(alignRecord & ~ALLOCA_FLAGSMASK);

        Type type = types.get(typeRecord);
        if ((alignRecord & ALLOCA_EXPLICITTYPEMASK) != 0L) {
            type = new PointerType(type);
        } else if (!(type instanceof PointerType)) {
            throw new LLVMParserException("Alloca with unexpected type: " + type);
        }
        emit(AllocateInstruction.fromSymbols(scope.getSymbols(), type, count, align));
    }

    private static final int LOAD_ARGS_EXPECTED_AFTER_TYPE = 3;

    private void createLoad(RecordBuffer buffer) {
        int src = readIndex(buffer);
        Type srcType = readValueType(buffer, src);
        Type opType;
        if (buffer.remaining() == LOAD_ARGS_EXPECTED_AFTER_TYPE) {
            opType = readType(buffer);
        } else {
            opType = Types.castToPointer(srcType).getPointeeType();
        }
        buffer.read(); // skip alignment
        boolean isVolatile = buffer.readBoolean();

        emit(LoadInstruction.fromSymbols(scope.getSymbols(), opType, src, isVolatile));
    }

    private static final int LOADATOMIC_ARGS_EXPECTED_AFTER_TYPE = 5;

    private void createLoadAtomic(RecordBuffer buffer) {
        int src = readIndex(buffer);
        Type srcType = readValueType(buffer, src);
        Type opType;
        if (buffer.remaining() == LOADATOMIC_ARGS_EXPECTED_AFTER_TYPE) {
            opType = readType(buffer);
        } else {
            opType = Types.castToPointer(srcType).getPointeeType();
        }
        buffer.read(); // skip alignment
        boolean isVolatile = buffer.readBoolean();
        long atomicOrdering = buffer.read();
        long synchronizationScope = buffer.read();

        emit(LoadInstruction.fromSymbols(scope.getSymbols(), opType, src, isVolatile, atomicOrdering, synchronizationScope));
    }

    private void createCompareExchange(RecordBuffer buffer, int record) {
        int ptr = readIndex(buffer);
        Type ptrType = readValueType(buffer, ptr);
        int cmp = record == INSTRUCTION_CMPXCHG ? readIndexSkipType(buffer) : readIndex(buffer);
        final int replace = readIndex(buffer);
        final boolean isVolatile = buffer.readBoolean();
        final long successOrdering = buffer.read();
        final long synchronizationScope = buffer.read();
        final long failureOrdering = buffer.remaining() > 0 ? buffer.read() : -1L;
        final boolean addExtractValue = buffer.remaining() == 0;
        final boolean isWeak = addExtractValue || buffer.readBoolean();

        final AggregateType type = findCmpxchgResultType(Types.castToPointer(ptrType).getPointeeType());
        CompareExchangeInstruction inst;
        emit(inst = CompareExchangeInstruction.fromSymbols(scope.getSymbols(), type, ptr, cmp, replace, isVolatile, successOrdering, synchronizationScope, failureOrdering, isWeak));

        if (addExtractValue) {
            // in older llvm versions cmpxchg just returned the new value at the pointer, to emulate
            // this we have to add an extractelvalue instruction. llvm does the same thing

            Type elementType = inst.getAggregateType().getElementType(0);
            emit(ExtractValueInstruction.create(inst, elementType, 0));

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
                StructureType st = (StructureType) t;
                if (st.getNumberOfElementsInt() == CMPXCHG_TYPE_LENGTH && elementType == st.getElementType(CMPXCHG_TYPE_ELEMENTTYPE) && PrimitiveType.I1 == st.getElementType(CMPXCHG_TYPE_BOOLTYPE)) {
                    return st;
                }
            }
        }
        // the type may not exist if the value is not being used
        return StructureType.createUnnamed(true, elementType, PrimitiveType.I1);
    }

    private void parseDebugLocation(RecordBuffer buffer) {
        // if e.g. the previous instruction was @llvm.debug.declare this will be the location of the
        // declaration of the variable in the source file
        lastLocation = MDLocation.createFromFunctionArgs(buffer, scope.getMetadata());
    }

    private void applyDebugLocation() {
        final int lastInstructionIndex = instructionBlock.getInstructionCount() - 1;
        instructionBlock.getInstruction(lastInstructionIndex).setDebugLocation(lastLocation);
    }

    private void createAtomicStore(RecordBuffer buffer) {
        int destination = readIndexSkipType(buffer);
        int source = readIndexSkipType(buffer);
        int align = getAlign(buffer.read());
        boolean isVolatile = buffer.readBoolean();
        long atomicOrdering = buffer.read();
        long synchronizationScope = buffer.read();

        emit(StoreInstruction.fromSymbols(scope.getSymbols(), destination, source, align, isVolatile, atomicOrdering, synchronizationScope));
    }

    private void createAtomicReadModifyWrite(RecordBuffer buffer) {
        int ptr = readIndex(buffer);
        Type ptrType = readValueType(buffer, ptr);
        int value = readIndex(buffer);
        int opcode = buffer.readInt();
        boolean isVolatile = buffer.readBoolean();
        long atomicOrdering = buffer.read();
        long synchronizationScope = buffer.read();

        final Type type = Types.castToPointer(ptrType).getPointeeType();

        emit(ReadModifyWriteInstruction.fromSymbols(scope.getSymbols(), type, ptr, value, opcode, isVolatile, atomicOrdering, synchronizationScope));
    }

    private void createFence(RecordBuffer buffer) {
        long atomicOrdering = buffer.read();
        long synchronizationScope = buffer.read();

        emit(FenceInstruction.generate(atomicOrdering, synchronizationScope));
    }

    private void createBinaryOperation(RecordBuffer buffer) {
        int lhs = readIndex(buffer);
        Type type = readValueType(buffer, lhs);
        int rhs = readIndex(buffer);
        int opcode = buffer.readInt();
        int flags = buffer.remaining() > 0 ? buffer.readInt() : 0;

        emit(BinaryOperationInstruction.fromSymbols(scope.getSymbols(), type, opcode, flags, lhs, rhs));
    }

    private void createUnaryOperation(RecordBuffer buffer) {
        int operand = readIndex(buffer);
        Type type = readValueType(buffer, operand);
        int opcode = buffer.readInt();
        int flags = buffer.remaining() > 0 ? buffer.readInt() : 0;

        emit(UnaryOperationInstruction.fromSymbols(scope.getSymbols(), type, opcode, flags, operand));
    }

    private void createBranch(RecordBuffer buffer) {
        if (buffer.size() == 1) {
            emit(BranchInstruction.fromTarget(function.getBlock(buffer.read())));

        } else {
            InstructionBlock trueSuccessor = function.getBlock(buffer.read());
            InstructionBlock falseSuccessor = function.getBlock(buffer.read());
            int condition = readIndex(buffer);
            emit(ConditionalBranchInstruction.fromSymbols(scope.getSymbols(), condition, trueSuccessor, falseSuccessor));
        }

        isLastBlockTerminated = true;
    }

    private void createCast(RecordBuffer buffer) {
        int value = readIndexSkipType(buffer);
        Type type = readType(buffer);
        int opcode = buffer.readInt();
        emit(CastInstruction.fromSymbols(scope.getSymbols(), type, opcode, value));
    }

    private void createCompare2(RecordBuffer buffer) {
        int lhs = readIndex(buffer);
        Type operandType = readValueType(buffer, lhs);
        int rhs = readIndex(buffer);
        int opcode = buffer.readInt();

        Type type = operandType instanceof VectorType ? new VectorType(PrimitiveType.I1, Types.castToVector(operandType).getNumberOfElementsInt()) : PrimitiveType.I1;
        emit(CompareInstruction.fromSymbols(scope.getSymbols(), type, opcode, lhs, rhs));
    }

    private void createExtractElement(RecordBuffer buffer) {
        int vector = readIndex(buffer);
        Type vectorType = readValueType(buffer, vector);
        int index = readIndex(buffer);

        Type elementType = Types.castToVector(vectorType).getElementType();
        emit(ExtractElementInstruction.fromSymbols(scope.getSymbols(), elementType, vector, index));
    }

    private void createExtractValue(RecordBuffer buffer) {
        int aggregate = readIndex(buffer);
        Type aggregateType = readValueType(buffer, aggregate);
        int index = buffer.readInt();
        buffer.checkEnd("Multiple indices for extractvalue are not yet supported!");

        Type elementType = Types.castToAggregate(aggregateType).getElementType(index);
        emit(ExtractValueInstruction.fromSymbols(scope.getSymbols(), elementType, aggregate, index));
    }

    private void createGetElementPointer(RecordBuffer buffer) {
        boolean isInbounds = buffer.readBoolean();
        buffer.read(); // we do not use this parameter
        int pointer = readIndex(buffer);
        Type base = readValueType(buffer, pointer);
        int[] indices = readIndices(buffer);
        Type type = getElementPointerType(base, indices);
        emit(GetElementPointerInstruction.fromSymbols(scope.getSymbols(), type, pointer, indices, isInbounds));
    }

    private void createGetElementPointerOld(RecordBuffer buffer, boolean isInbounds) {
        int pointer = readIndex(buffer);
        Type base = readValueType(buffer, pointer);
        int[] indices = readIndices(buffer);
        Type type = getElementPointerType(base, indices);
        emit(GetElementPointerInstruction.fromSymbols(scope.getSymbols(), type, pointer, indices, isInbounds));
    }

    private void createIndirectBranch(RecordBuffer buffer) {
        buffer.read(); // skipped
        int address = readIndex(buffer);
        int[] successors = new int[buffer.size() - 2];
        for (int i = 0; i < successors.length; i++) {
            successors[i] = buffer.readInt();
        }

        emit(IndirectBranchInstruction.generate(function, scope.getSymbols(), address, successors));
        isLastBlockTerminated = true;
    }

    private void createInsertElement(RecordBuffer buffer) {
        int vector = readIndex(buffer);
        Type type = readValueType(buffer, vector);
        int value = readIndex(buffer);
        int index = readIndex(buffer);

        emit(InsertElementInstruction.fromSymbols(scope.getSymbols(), type, vector, index, value));
    }

    private void createInsertValue(RecordBuffer buffer) {
        int aggregate = readIndex(buffer);
        Type type = readValueType(buffer, aggregate);
        int value = readIndexSkipType(buffer);
        int index = buffer.readInt();
        buffer.checkEnd("Multiple indices for insertvalue are not yet supported!");

        emit(InsertValueInstruction.fromSymbols(scope.getSymbols(), type, aggregate, index, value));
    }

    private void createPhi(RecordBuffer buffer) {
        Type type = readType(buffer);
        int count = (buffer.size() - 1) >> 1;
        int[] values = new int[count];
        InstructionBlock[] blocks = new InstructionBlock[count];
        for (int i = 0; i < count; i++) {
            values[i] = getIndex(buffer.readSignedValue());
            blocks[i] = function.getBlock(buffer.read());
        }

        emit(PhiInstruction.generate(scope.getSymbols(), type, values, blocks));
    }

    private void createReturn(RecordBuffer buffer) {
        if (buffer.size() == 0 || buffer.getAt(0) == 0) {
            emit(ReturnInstruction.generate());
        } else {
            int value = readIndex(buffer);
            emit(ReturnInstruction.generate(scope.getSymbols(), value));
        }
        isLastBlockTerminated = true;
    }

    private void createSelect(RecordBuffer buffer) {
        int trueValue = readIndex(buffer);
        Type type = readValueType(buffer, trueValue);
        int falseValue = readIndex(buffer);
        int condition = readIndex(buffer);

        emit(SelectInstruction.fromSymbols(scope.getSymbols(), type, condition, trueValue, falseValue));
    }

    private void createShuffleVector(RecordBuffer buffer) {
        int vector1 = readIndex(buffer);
        Type vectorType = readValueType(buffer, vector1);
        int vector2 = readIndex(buffer);
        int mask = readIndex(buffer);

        Type subtype = Types.castToVector(vectorType).getElementType();
        int length = Types.castToVector(scope.getValueType(mask)).getNumberOfElementsInt();
        Type type = new VectorType(subtype, length);

        emit(ShuffleVectorInstruction.fromSymbols(scope.getSymbols(), type, vector1, vector2, mask));
    }

    private void createStore(RecordBuffer buffer) {
        int destination = readIndexSkipType(buffer);
        int source = readIndexSkipType(buffer);
        int align = getAlign(buffer.read());
        boolean isVolatile = buffer.readBoolean();

        emit(StoreInstruction.fromSymbols(scope.getSymbols(), destination, source, align, isVolatile));
    }

    private void createStoreOld(RecordBuffer buffer) {
        int destination = readIndexSkipType(buffer);
        int source = readIndex(buffer);
        int align = getAlign(buffer.read());
        boolean isVolatile = buffer.readBoolean();

        emit(StoreInstruction.fromSymbols(scope.getSymbols(), destination, source, align, isVolatile));
    }

    private void createUnreachable(@SuppressWarnings("unused") RecordBuffer buffer) {
        emit(UnreachableInstruction.generate());
        isLastBlockTerminated = true;
    }

    private static int getAlign(long argument) {
        return (int) argument & (Long.SIZE - 1);
    }

    private Type getElementPointerType(Type type, int[] indices) {
        boolean vectorized = type instanceof VectorType;
        int length = vectorized ? ((VectorType) type).getNumberOfElementsInt() : 0;
        Type elementType = vectorized ? ((VectorType) type).getElementType() : type;
        for (int indexIndex : indices) {
            Type indexType = scope.getValueType(indexIndex);

            if (elementType instanceof PointerType) {
                elementType = ((PointerType) elementType).getPointeeType();
            } else if (elementType instanceof ArrayType) {
                elementType = ((ArrayType) elementType).getElementType();
            } else if (elementType instanceof VectorType) {
                elementType = ((VectorType) elementType).getElementType();
            } else if (elementType instanceof StructureType) {
                StructureType structure = (StructureType) elementType;
                if (!(indexType instanceof PrimitiveType)) {
                    throw new LLVMParserException("Cannot infer structure element from " + indexType);
                }
                Number indexNumber = (Number) ((PrimitiveType) indexType).getConstant();
                assert ((PrimitiveType) indexType).getPrimitiveKind() == PrimitiveKind.I32;
                elementType = structure.getElementType(indexNumber.intValue());
            } else {
                throw new LLVMParserException("Cannot index type: " + elementType);
            }

            if (indexType instanceof VectorType) {
                int indexVectorLength = ((VectorType) indexType).getNumberOfElementsInt();
                if (vectorized) {
                    if (indexVectorLength != length) {
                        throw new LLVMParserException(String.format("Vectors of different lengths are not supported: %d != %d", indexVectorLength, length));
                    }
                } else {
                    vectorized = true;
                    length = indexVectorLength;
                }
            }
        }

        Type pointer = new PointerType(elementType);
        if (vectorized) {
            return new VectorType(pointer, length);
        } else {
            return pointer;
        }
    }

    private int readIndex(RecordBuffer buffer) {
        return getIndex(buffer.read());
    }

    private int readIndexSkipType(RecordBuffer buffer) {
        int value = Function.this.getIndex(buffer.read());
        if (scope.isValueForwardRef(value)) {
            buffer.read();
        }
        return value;
    }

    private Type readValueType(RecordBuffer buffer, int valueIndex) {
        if (scope.isValueForwardRef(valueIndex)) {
            return types.get(buffer.read());
        } else {
            return scope.getValueType(valueIndex);
        }
    }

    private int getIndex(long index) {
        if (mode >= 1) {
            return getIndexRelative(index);
        } else {
            return getIndexAbsolute(index);
        }
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

    private int[] readIndices(RecordBuffer buffer) {
        int[] indices = new int[buffer.remaining()];
        int pos = 0;
        while (buffer.remaining() > 0) {
            indices[pos++] = readIndexSkipType(buffer);
        }
        return pos == indices.length ? indices : Arrays.copyOf(indices, pos);
    }

    private Type readType(RecordBuffer buffer) {
        return types.get(buffer.read());
    }

    public FunctionDefinition getFunction() {
        return function;
    }

    public IRScope getScope() {
        return scope;
    }
}
