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
package com.oracle.truffle.llvm.parser.listeners;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.oracle.truffle.llvm.parser.metadata.MDLocation;
import com.oracle.truffle.llvm.parser.model.attributes.AttributesCodeEntry;
import com.oracle.truffle.llvm.parser.model.blocks.InstructionBlock;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDefinition;
import com.oracle.truffle.llvm.parser.model.symbols.Symbols;
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
import com.oracle.truffle.llvm.parser.records.FunctionRecord;
import com.oracle.truffle.llvm.parser.records.Records;
import com.oracle.truffle.llvm.parser.scanner.Block;
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

    private final FunctionDefinition function;

    protected final Types types;

    private final int mode;

    private InstructionBlock instructionBlock = null;

    private boolean isLastBlockTerminated = true;

    private MDLocation lastLocation = null;

    private final List<Integer> implicitIndices = new ArrayList<>();

    private final ParameterAttributes paramAttributes;

    Function(Types types, FunctionDefinition function, int mode, ParameterAttributes paramAttributes) {
        this.types = types;
        this.function = function;
        this.mode = mode;
        this.paramAttributes = paramAttributes;
    }

    @Override
    public ParserListener enter(Block block) {
        switch (block) {
            case CONSTANTS:
                return new Constants(types, function);

            case VALUE_SYMTAB:
                return new ValueSymbolTable(function);

            case METADATA:
            case METADATA_ATTACHMENT:
            case METADATA_KIND:
                return new Metadata(types, function);

            default:
                return ParserListener.DEFAULT;
        }
    }

    @Override
    public void exit() {
        function.exitFunction();
    }

    @Override
    public void record(long id, long[] args) {
        FunctionRecord record = FunctionRecord.decode(id);

        // debug locations can occur after terminating instructions, we process them before we
        // replace the old block
        switch (record) {
            case DEBUG_LOC:
                parseDebugLocation(args); // intentional fallthrough

            case DEBUG_LOC_AGAIN:
                applyDebugLocation();
                return;

            case DECLAREBLOCKS:
                function.allocateBlocks((int) args[0]);
                return;

            default:
                break;
        }

        if (isLastBlockTerminated) {
            instructionBlock = function.generateBlock();
            isLastBlockTerminated = false;
        }

        switch (record) {

            case BINOP:
                createBinaryOperation(args);
                break;

            case CAST:
                createCast(args);
                break;

            case GEP_OLD:
                createGetElementPointerOld(args, false);
                break;

            case EXTRACTELT:
                createExtractElement(args);
                break;

            case INSERTELT:
                createInsertElement(args);
                break;

            case SHUFFLEVEC:
                createShuffleVector(args);
                break;

            case RET:
                createReturn(args);
                break;

            case BR:
                createBranch(args);
                break;

            case SWITCH:
                createSwitch(args);
                break;

            case UNREACHABLE:
                createUnreachable(args);
                break;

            case PHI:
                createPhi(args);
                break;

            case ALLOCA:
                createAlloca(args);
                break;

            case LOAD:
                createLoad(args);
                break;

            case STORE_OLD:
                createStoreOld(args);
                break;

            case EXTRACTVAL:
                createExtractValue(args);
                break;

            case INSERTVAL:
                createInsertValue(args);
                break;

            case CMP2:
                createCompare2(args);
                break;

            case VSELECT:
                createSelect(args);
                break;

            case INBOUNDS_GEP_OLD:
                createGetElementPointerOld(args, true);
                break;

            case INDIRECTBR:
                createIndirectBranch(args);
                break;

            case CALL:
                createFunctionCall(args);
                break;

            case INVOKE:
                createInvoke(args);
                break;

            case LANDINGPAD:
                createLandingpad(args);
                break;

            case LANDINGPAD_OLD:
                createLandingpadOld(args);
                break;

            case RESUME:
                createResume(args);
                break;

            case GEP:
                createGetElementPointer(args);
                break;

            case STORE:
                createStore(args);
                break;

            case LOADATOMIC:
                createLoadAtomic(args);
                break;

            case STOREATOMIC:
                createAtomicStore(args);
                break;

            case CMPXCHG_OLD:
            case CMPXCHG:
                createCompareExchange(args, record);
                break;

            case ATOMICRMW:
                createAtomicReadModifyWrite(args);
                break;

            case FENCE:
                createFence(args);
                break;

            default:
                throw new UnsupportedOperationException("Unsupported Record: " + record);
        }
    }

    private void emit(ValueInstruction instruction) {
        instructionBlock.append(instruction);
        function.addSymbol(instruction, instruction.getType());
    }

    private void emit(VoidInstruction instruction) {
        instructionBlock.append(instruction);
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
            functionType = (FunctionType) types.get(args[i++]);
        }

        final int target = getIndex(args[i++]);
        final Type calleeType;
        if (function.isValueForwardRef(target)) {
            calleeType = types.get(args[i++]);
        } else {
            calleeType = function.getValueType(target);
        }

        if (functionType == null) {
            if (calleeType instanceof PointerType) {
                functionType = (FunctionType) ((PointerType) calleeType).getPointeeType();
            } else if (calleeType instanceof FunctionType) {
                functionType = (FunctionType) calleeType;
            } else {
                throw new AssertionError("Cannot find Type of invoked function: " + calleeType.toString());
            }
        }

        final int[] arguments = new int[args.length - i];
        for (int j = 0; i < args.length; j++) {
            int index = getIndex(args[i++]);
            arguments[j] = index;
            if (function.isValueForwardRef(index)) {
                i++;
            }
        }
        final Type returnType = functionType.getReturnType();
        if (returnType == VoidType.INSTANCE) {
            emit(VoidInvokeInstruction.fromSymbols(function.getSymbols(), target, arguments, normalSuccessor, unwindSuccessor, paramAttr));
        } else {
            emit(InvokeInstruction.fromSymbols(function.getSymbols(), returnType, target, arguments, normalSuccessor, unwindSuccessor, paramAttr));
        }
        isLastBlockTerminated = true;
    }

    private void createResume(long[] args) {
        int i = 0;
        final int val = getIndex(args[i]);
        // args[i + 1] -> type
        emit(ResumeInstruction.fromSymbols(function.getSymbols(), val));
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
            if (function.isValueForwardRef(clauseTypes[j])) {
                i++;
            }
        }
        emit(LandingpadInstruction.generate(function.getSymbols(), type, isCleanup, clauseKinds, clauseTypes));
    }

    private void createLandingpadOld(long[] args) {
        int i = 0;
        final Type type = types.get(args[i++]);

        long persFn = getIndex(args[i++]);
        if (function.isValueForwardRef((int) persFn)) {
            i++;
        }

        final boolean isCleanup = args[i++] != 0;
        final int numClauses = (int) args[i++];
        long[] clauseKinds = new long[numClauses]; // catch = 0, filter = 1
        long[] clauseTypes = new long[numClauses];
        for (int j = 0; j < numClauses; j++) {
            clauseKinds[j] = args[i++];
            clauseTypes[j] = getIndex(args[i++]);
            if (function.isValueForwardRef(clauseTypes[j])) {
                i++;
            }
        }
        emit(LandingpadInstruction.generate(function.getSymbols(), type, isCleanup, clauseKinds, clauseTypes));
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
            functionType = (FunctionType) types.get(args[i++]);
        }

        int callee = getIndex(args[i++]);
        Type calleeType;
        if (function.isValueForwardRef(callee)) {
            calleeType = types.get(args[i++]);
        } else {
            calleeType = function.getValueType(callee);
        }

        if (functionType == null) {
            if (calleeType instanceof FunctionType) {
                functionType = (FunctionType) calleeType;
            } else {
                functionType = (FunctionType) ((PointerType) calleeType).getPointeeType();
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
            if (function.isValueForwardRef(index)) {
                i++;
                skipped++;
            }
        }
        if (skipped > 0) {
            arguments = Arrays.copyOf(arguments, arguments.length - skipped);
        }

        final Type returnType = functionType.getReturnType();
        final Symbols symbols = function.getSymbols();
        if (returnType == VoidType.INSTANCE) {
            emit(VoidCallInstruction.fromSymbols(symbols, callee, arguments, paramAttr));
        } else {
            emit(CallInstruction.fromSymbols(function.getSymbols(), returnType, callee, arguments, paramAttr));
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

            emit(SwitchOldInstruction.generate(function, cond, defaultBlock, caseConstants, caseBlocks));

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

            emit(SwitchInstruction.generate(function, cond, defaultBlock, caseValues, caseBlocks));
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
            throw new AssertionError("Alloca must have PointerType!");
        }
        emit(AllocateInstruction.fromSymbols(function.getSymbols(), type, count, align));
    }

    private static final int LOAD_ARGS_EXPECTED_AFTER_TYPE = 3;

    private void createLoad(long[] args) {
        int i = 0;
        final int src = getIndex(args[i++]);

        final Type srcType;
        if (function.isValueForwardRef(src)) {
            srcType = types.get(args[i++]);
        } else {
            srcType = function.getValueType(src);
        }

        final Type opType;
        if (i + LOAD_ARGS_EXPECTED_AFTER_TYPE == args.length) {
            opType = types.get(args[i++]);
        } else {
            opType = ((PointerType) srcType).getPointeeType();
        }

        final int align = getAlign(args[i++]);
        final boolean isVolatile = args[i] != 0;

        emit(LoadInstruction.fromSymbols(function.getSymbols(), opType, src, align, isVolatile));
    }

    private static final int LOADATOMIC_ARGS_EXPECTED_AFTER_TYPE = 5;

    private void createLoadAtomic(long[] args) {
        int i = 0;
        final int src = getIndex(args[i++]);

        final Type srcType;
        if (function.isValueForwardRef(src)) {
            srcType = types.get(args[i++]);
        } else {
            srcType = function.getValueType(src);
        }

        final Type opType;
        if (i + LOADATOMIC_ARGS_EXPECTED_AFTER_TYPE == args.length) {
            opType = types.get(args[i++]);
        } else {
            opType = ((PointerType) srcType).getPointeeType();
        }

        final int align = getAlign(args[i++]);
        final boolean isVolatile = args[i++] != 0;
        final long atomicOrdering = args[i++];
        final long synchronizationScope = args[i];

        emit(LoadInstruction.fromSymbols(function.getSymbols(), opType, src, align, isVolatile, atomicOrdering, synchronizationScope));
    }

    private void createCompareExchange(long[] args, FunctionRecord record) {
        int i = 0;

        final Type ptrType;
        final int ptr = getIndex(args[i]);
        if (function.isValueForwardRef(ptr)) {
            ptrType = types.get(args[++i]);
        } else {
            ptrType = function.getValueType(ptr);
        }
        final int cmp = getIndex(args[++i]);
        if (record == FunctionRecord.CMPXCHG && function.isValueForwardRef(cmp)) {
            ++i; // type of cmp
        }
        final int replace = getIndex(args[++i]);
        final boolean isVolatile = args[++i] != 0;
        final long successOrdering = args[++i];
        final long synchronizationScope = args[++i];
        final long failureOrdering = i < args.length - 1 ? args[++i] : -1L;
        final boolean addExtractValue = i >= args.length - 1;
        final boolean isWeak = addExtractValue || (args[++i] != 0);

        final Type type = findCmpxchgResultType(((PointerType) ptrType).getPointeeType());

        emit(CompareExchangeInstruction.fromSymbols(function.getSymbols(), type, ptr, cmp, replace, isVolatile, successOrdering, synchronizationScope, failureOrdering, isWeak));

        if (addExtractValue) {
            // in older llvm versions cmpxchg just returned the new value at the pointer, to emulate
            // this we have to add an extractelvalue instruction. llvm does the same thing
            createExtractValue(new long[]{1, 0});
            implicitIndices.add(function.getNextValueIndex() - 1); // register the implicit index
        }
    }

    private static final int CMPXCHG_TYPE_LENGTH = 2;
    private static final int CMPXCHG_TYPE_ELEMENTTYPE = 0;
    private static final int CMPXCHG_TYPE_BOOLTYPE = 1;

    private Type findCmpxchgResultType(Type elementType) {
        // cmpxchg is the only instruction that does not directly reference its return type in the
        // type table
        for (Type t : types) {
            if (t != null && t instanceof StructureType) {
                final Type[] elts = ((StructureType) t).getElementTypes();
                if (elts.length == CMPXCHG_TYPE_LENGTH && elementType == elts[CMPXCHG_TYPE_ELEMENTTYPE] && PrimitiveType.I1 == elts[CMPXCHG_TYPE_BOOLTYPE]) {
                    return t;
                }
            }
        }
        // the type may not exist if the value is not being used
        return new StructureType(true, new Type[]{elementType, PrimitiveType.I1});
    }

    private void parseDebugLocation(long[] args) {
        // if e.g. the previous instruction was @llvm.debug.declare this will be the location of the
        // declaration of the variable in the source file
        lastLocation = MDLocation.createFromFunctionArgs(args, function.getMetadata());
    }

    private void applyDebugLocation() {
        final int lastInstructionIndex = instructionBlock.getInstructionCount() - 1;
        instructionBlock.getInstruction(lastInstructionIndex).setDebugLocation(lastLocation);
    }

    private void createAtomicStore(long[] args) {
        int i = 0;

        final int destination = getIndex(args[i++]);
        if (function.isValueForwardRef(destination)) {
            i++;
        }

        final int source = getIndex(args[i++]);
        if (function.isValueForwardRef(source)) {
            i++;
        }

        final int align = getAlign(args[i++]);
        final boolean isVolatile = args[i++] != 0;
        final long atomicOrdering = args[i++];
        final long synchronizationScope = args[i];

        emit(StoreInstruction.fromSymbols(function.getSymbols(), destination, source, align, isVolatile, atomicOrdering, synchronizationScope));
    }

    private void createAtomicReadModifyWrite(long[] args) {
        int i = 0;

        final int ptr = getIndex(args[i++]);
        final Type ptrType;
        if (function.isValueForwardRef(ptr)) {
            ptrType = types.get(args[i++]);
        } else {
            ptrType = function.getValueType(ptr);
        }
        final int value = getIndex(args[i++]);
        final int opcode = (int) args[i++];
        final boolean isVolatile = args[i++] != 0;
        final long atomicOrdering = args[i++];
        final long synchronizationScope = args[i];

        final Type type = ((PointerType) ptrType).getPointeeType();

        emit(ReadModifyWriteInstruction.fromSymbols(function.getSymbols(), type, ptr, value, opcode, isVolatile, atomicOrdering, synchronizationScope));
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
        if (function.isValueForwardRef(lhs)) {
            type = types.get(args[i++]);
        } else {
            type = function.getValueType(lhs);
        }
        int rhs = getIndex(args[i++]);
        int opcode = (int) args[i++];
        int flags = i < args.length ? (int) args[i] : 0;

        emit(BinaryOperationInstruction.fromSymbols(function.getSymbols(), type, opcode, flags, lhs, rhs));
    }

    private void createBranch(long[] args) {
        if (args.length == 1) {
            emit(BranchInstruction.fromTarget(function.getBlock(args[0])));

        } else {
            final int condition = getIndex(args[2]);
            final InstructionBlock trueSuccessor = function.getBlock(args[0]);
            final InstructionBlock falseSuccessor = function.getBlock(args[1]);
            emit(ConditionalBranchInstruction.fromSymbols(function.getSymbols(), condition, trueSuccessor, falseSuccessor));
        }

        isLastBlockTerminated = true;
    }

    private void createCast(long[] args) {
        int i = 0;
        int value = getIndex(args[i++]);
        if (function.isValueForwardRef(value)) {
            i++;
        }
        Type type = types.get(args[i++]);
        int opcode = (int) args[i];

        emit(CastInstruction.fromSymbols(function.getSymbols(), type, opcode, value));
    }

    private void createCompare2(long[] args) {
        int i = 0;
        Type operandType;
        int lhs = getIndex(args[i++]);
        if (function.isValueForwardRef(lhs)) {
            operandType = types.get(args[i++]);
        } else {
            operandType = function.getValueType(lhs);
        }
        int rhs = getIndex(args[i++]);
        int opcode = (int) args[i];

        Type type = operandType instanceof VectorType
                        ? new VectorType(PrimitiveType.I1, ((VectorType) operandType).getNumberOfElements())
                        : PrimitiveType.I1;

        emit(CompareInstruction.fromSymbols(function.getSymbols(), type, opcode, lhs, rhs));
    }

    private void createExtractElement(long[] args) {
        int i = 0;
        int vector = getIndex(args[i++]);

        Type vectorType;
        if (function.isValueForwardRef(vector)) {
            vectorType = types.get(args[i++]);
        } else {
            vectorType = function.getValueType(vector);
        }

        int index = getIndex(args[i]);

        final Type elementType = ((VectorType) vectorType).getElementType();
        emit(ExtractElementInstruction.fromSymbols(function.getSymbols(), elementType, vector, index));
    }

    private void createExtractValue(long[] args) {
        int i = 0;
        int aggregate = getIndex(args[i++]);
        Type aggregateType = null;
        if (function.isValueForwardRef(aggregate)) {
            aggregateType = types.get(args[i++]);
        }
        int index = (int) args[i++];
        if (aggregateType == null) {
            aggregateType = function.getValueType(aggregate);
        }

        if (i != args.length) {
            throw new UnsupportedOperationException("Multiple indices are not yet supported!");
        }

        final Type elementType = ((AggregateType) aggregateType).getElementType(index);
        emit(ExtractValueInstruction.fromSymbols(function.getSymbols(), elementType, aggregate, index));
    }

    private void createGetElementPointer(long[] args) {
        int i = 0;
        boolean isInbounds = args[i++] != 0;
        i++; // we do not use this parameter
        int pointer = getIndex(args[i++]);
        Type base;
        if (function.isValueForwardRef(pointer)) {
            base = types.get(args[i++]);
        } else {
            base = function.getValueType(pointer);
        }
        List<Integer> indices = getIndices(args, i);
        Type type = new PointerType(getElementPointerType(base, indices));

        emit(GetElementPointerInstruction.fromSymbols(function.getSymbols(), type, pointer, indices, isInbounds));
    }

    private void createGetElementPointerOld(long[] args, boolean isInbounds) {
        int i = 0;
        int pointer = getIndex(args[i++]);
        Type base;
        if (function.isValueForwardRef(pointer)) {
            base = types.get(args[i++]);
        } else {
            base = function.getValueType(pointer);
        }
        List<Integer> indices = getIndices(args, i);
        Type type = new PointerType(getElementPointerType(base, indices));

        emit(GetElementPointerInstruction.fromSymbols(function.getSymbols(), type, pointer, indices, isInbounds));
    }

    private void createIndirectBranch(long[] args) {
        int address = getIndex(args[1]);
        int[] successors = new int[args.length - 2];
        for (int i = 0; i < successors.length; i++) {
            successors[i] = (int) args[i + 2];
        }

        emit(IndirectBranchInstruction.generate(function, address, successors));
        isLastBlockTerminated = true;
    }

    private void createInsertElement(long[] args) {
        int i = 0;

        int vector = getIndex(args[i++]);
        Type type;
        if (function.isValueForwardRef(vector)) {
            type = types.get(args[i++]);
        } else {
            type = function.getValueType(vector);
        }

        int value = getIndex(args[i++]);
        int index = getIndex(args[i]);

        emit(InsertElementInstruction.fromSymbols(function.getSymbols(), type, vector, index, value));
    }

    private void createInsertValue(long[] args) {
        int i = 0;

        int aggregate = getIndex(args[i++]);
        Type type;
        if (function.isValueForwardRef(aggregate)) {
            type = types.get(args[i++]);
        } else {
            type = function.getValueType(aggregate);
        }

        int value = getIndex(args[i++]);
        if (function.isValueForwardRef(value)) {
            i++;
        }

        int index = (int) args[i++];

        if (args.length != i) {
            throw new UnsupportedOperationException("Multiple indices are not yet supported!");
        }

        emit(InsertValueInstruction.fromSymbols(function.getSymbols(), type, aggregate, index, value));
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

        emit(PhiInstruction.generate(function, type, values, blocks));
    }

    private void createReturn(long[] args) {
        if (args.length == 0 || args[0] == 0) {
            emit(ReturnInstruction.generate());
        } else {
            final int value = getIndex(args[0]);
            emit(ReturnInstruction.generate(function.getSymbols(), value));
        }

        isLastBlockTerminated = true;
    }

    private void createSelect(long[] args) {
        int i = 0;
        Type type;
        int trueValue = getIndex(args[i++]);
        if (function.isValueForwardRef(trueValue)) {
            type = types.get(args[i++]);
        } else {
            type = function.getValueType(trueValue);
        }
        int falseValue = getIndex(args[i++]);
        int condition = getIndex(args[i]);

        emit(SelectInstruction.fromSymbols(function.getSymbols(), type, condition, trueValue, falseValue));
    }

    private void createShuffleVector(long[] args) {
        int i = 0;

        int vector1 = getIndex(args[i++]);
        Type vectorType;
        if (function.isValueForwardRef(vector1)) {
            vectorType = types.get(args[i++]);
        } else {
            vectorType = function.getValueType(vector1);
        }

        int vector2 = getIndex(args[i++]);
        int mask = getIndex(args[i]);

        PrimitiveType subtype = ((VectorType) vectorType).getElementType();
        int length = ((VectorType) function.getValueType(mask)).getNumberOfElements();
        Type type = new VectorType(subtype, length);

        emit(ShuffleVectorInstruction.fromSymbols(function.getSymbols(), type, vector1, vector2, mask));
    }

    private void createStore(long[] args) {
        int i = 0;

        int destination = getIndex(args[i++]);
        if (function.isValueForwardRef(destination)) {
            i++;
        }

        int source = getIndex(args[i++]);
        if (function.isValueForwardRef(source)) {
            i++;
        }

        int align = getAlign(args[i++]);
        boolean isVolatile = args[i] != 0;

        emit(StoreInstruction.fromSymbols(function.getSymbols(), destination, source, align, isVolatile));
    }

    private void createStoreOld(long[] args) {
        int i = 0;

        int destination = getIndex(args[i++]);
        if (function.isValueForwardRef(destination)) {
            i++;
        }

        int source = getIndex(args[i++]);
        int align = getAlign(args[i++]);
        boolean isVolatile = args[i] != 0;

        emit(StoreInstruction.fromSymbols(function.getSymbols(), destination, source, align, isVolatile));
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
                Type indexType = function.getValueType(indexIndex);
                if (!(indexType instanceof PrimitiveType)) {
                    throw new IllegalStateException("Cannot infer structure element from " + indexType);
                }
                Number indexNumber = (Number) ((PrimitiveType) indexType).getConstant();
                assert ((PrimitiveType) indexType).getPrimitiveKind() == PrimitiveKind.I32;
                elementType = structure.getElementType(indexNumber.intValue());
            } else {
                throw new IllegalStateException("Cannot index type: " + elementType);
            }
        }
        return elementType;
    }

    private int getIndex(long index) {
        if (mode == 0) {
            return getIndexAbsolute(index);
        } else {
            return getIndexRelative(index);
        }
    }

    private List<Integer> getIndices(long[] arguments, int from) {
        List<Integer> indices = new ArrayList<>(arguments.length - from);
        int i = from;
        while (i < arguments.length) {
            int index = getIndex(arguments[i++]);
            if (function.isValueForwardRef(index)) {
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
        long actualIndex = function.getNextValueIndex() - index;
        for (int i = implicitIndices.size() - 1; i >= 0 && implicitIndices.get(i) > actualIndex; i--) {
            actualIndex--;
        }
        return (int) actualIndex;
    }
}
