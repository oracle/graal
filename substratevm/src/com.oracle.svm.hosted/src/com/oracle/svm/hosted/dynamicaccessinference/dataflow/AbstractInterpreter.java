/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.svm.hosted.dynamicaccessinference.dataflow;

import java.util.List;
import java.util.stream.IntStream;

import jdk.graal.compiler.bytecode.Bytecode;
import jdk.graal.compiler.bytecode.BytecodeStream;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaField;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.Signature;

import static jdk.graal.compiler.bytecode.Bytecodes.AALOAD;
import static jdk.graal.compiler.bytecode.Bytecodes.AASTORE;
import static jdk.graal.compiler.bytecode.Bytecodes.ACONST_NULL;
import static jdk.graal.compiler.bytecode.Bytecodes.ALOAD;
import static jdk.graal.compiler.bytecode.Bytecodes.ALOAD_0;
import static jdk.graal.compiler.bytecode.Bytecodes.ALOAD_1;
import static jdk.graal.compiler.bytecode.Bytecodes.ALOAD_2;
import static jdk.graal.compiler.bytecode.Bytecodes.ALOAD_3;
import static jdk.graal.compiler.bytecode.Bytecodes.ANEWARRAY;
import static jdk.graal.compiler.bytecode.Bytecodes.ARETURN;
import static jdk.graal.compiler.bytecode.Bytecodes.ARRAYLENGTH;
import static jdk.graal.compiler.bytecode.Bytecodes.ASTORE;
import static jdk.graal.compiler.bytecode.Bytecodes.ASTORE_0;
import static jdk.graal.compiler.bytecode.Bytecodes.ASTORE_1;
import static jdk.graal.compiler.bytecode.Bytecodes.ASTORE_2;
import static jdk.graal.compiler.bytecode.Bytecodes.ASTORE_3;
import static jdk.graal.compiler.bytecode.Bytecodes.ATHROW;
import static jdk.graal.compiler.bytecode.Bytecodes.BALOAD;
import static jdk.graal.compiler.bytecode.Bytecodes.BASTORE;
import static jdk.graal.compiler.bytecode.Bytecodes.BIPUSH;
import static jdk.graal.compiler.bytecode.Bytecodes.BREAKPOINT;
import static jdk.graal.compiler.bytecode.Bytecodes.CALOAD;
import static jdk.graal.compiler.bytecode.Bytecodes.CASTORE;
import static jdk.graal.compiler.bytecode.Bytecodes.CHECKCAST;
import static jdk.graal.compiler.bytecode.Bytecodes.D2F;
import static jdk.graal.compiler.bytecode.Bytecodes.D2I;
import static jdk.graal.compiler.bytecode.Bytecodes.D2L;
import static jdk.graal.compiler.bytecode.Bytecodes.DADD;
import static jdk.graal.compiler.bytecode.Bytecodes.DALOAD;
import static jdk.graal.compiler.bytecode.Bytecodes.DASTORE;
import static jdk.graal.compiler.bytecode.Bytecodes.DCMPG;
import static jdk.graal.compiler.bytecode.Bytecodes.DCMPL;
import static jdk.graal.compiler.bytecode.Bytecodes.DCONST_0;
import static jdk.graal.compiler.bytecode.Bytecodes.DCONST_1;
import static jdk.graal.compiler.bytecode.Bytecodes.DDIV;
import static jdk.graal.compiler.bytecode.Bytecodes.DLOAD;
import static jdk.graal.compiler.bytecode.Bytecodes.DLOAD_0;
import static jdk.graal.compiler.bytecode.Bytecodes.DLOAD_1;
import static jdk.graal.compiler.bytecode.Bytecodes.DLOAD_2;
import static jdk.graal.compiler.bytecode.Bytecodes.DLOAD_3;
import static jdk.graal.compiler.bytecode.Bytecodes.DMUL;
import static jdk.graal.compiler.bytecode.Bytecodes.DNEG;
import static jdk.graal.compiler.bytecode.Bytecodes.DREM;
import static jdk.graal.compiler.bytecode.Bytecodes.DRETURN;
import static jdk.graal.compiler.bytecode.Bytecodes.DSTORE;
import static jdk.graal.compiler.bytecode.Bytecodes.DSTORE_0;
import static jdk.graal.compiler.bytecode.Bytecodes.DSTORE_1;
import static jdk.graal.compiler.bytecode.Bytecodes.DSTORE_2;
import static jdk.graal.compiler.bytecode.Bytecodes.DSTORE_3;
import static jdk.graal.compiler.bytecode.Bytecodes.DSUB;
import static jdk.graal.compiler.bytecode.Bytecodes.DUP;
import static jdk.graal.compiler.bytecode.Bytecodes.DUP2;
import static jdk.graal.compiler.bytecode.Bytecodes.DUP2_X1;
import static jdk.graal.compiler.bytecode.Bytecodes.DUP2_X2;
import static jdk.graal.compiler.bytecode.Bytecodes.DUP_X1;
import static jdk.graal.compiler.bytecode.Bytecodes.DUP_X2;
import static jdk.graal.compiler.bytecode.Bytecodes.F2D;
import static jdk.graal.compiler.bytecode.Bytecodes.F2I;
import static jdk.graal.compiler.bytecode.Bytecodes.F2L;
import static jdk.graal.compiler.bytecode.Bytecodes.FADD;
import static jdk.graal.compiler.bytecode.Bytecodes.FALOAD;
import static jdk.graal.compiler.bytecode.Bytecodes.FASTORE;
import static jdk.graal.compiler.bytecode.Bytecodes.FCMPG;
import static jdk.graal.compiler.bytecode.Bytecodes.FCMPL;
import static jdk.graal.compiler.bytecode.Bytecodes.FCONST_0;
import static jdk.graal.compiler.bytecode.Bytecodes.FCONST_1;
import static jdk.graal.compiler.bytecode.Bytecodes.FCONST_2;
import static jdk.graal.compiler.bytecode.Bytecodes.FDIV;
import static jdk.graal.compiler.bytecode.Bytecodes.FLOAD;
import static jdk.graal.compiler.bytecode.Bytecodes.FLOAD_0;
import static jdk.graal.compiler.bytecode.Bytecodes.FLOAD_1;
import static jdk.graal.compiler.bytecode.Bytecodes.FLOAD_2;
import static jdk.graal.compiler.bytecode.Bytecodes.FLOAD_3;
import static jdk.graal.compiler.bytecode.Bytecodes.FMUL;
import static jdk.graal.compiler.bytecode.Bytecodes.FNEG;
import static jdk.graal.compiler.bytecode.Bytecodes.FREM;
import static jdk.graal.compiler.bytecode.Bytecodes.FRETURN;
import static jdk.graal.compiler.bytecode.Bytecodes.FSTORE;
import static jdk.graal.compiler.bytecode.Bytecodes.FSTORE_0;
import static jdk.graal.compiler.bytecode.Bytecodes.FSTORE_1;
import static jdk.graal.compiler.bytecode.Bytecodes.FSTORE_2;
import static jdk.graal.compiler.bytecode.Bytecodes.FSTORE_3;
import static jdk.graal.compiler.bytecode.Bytecodes.FSUB;
import static jdk.graal.compiler.bytecode.Bytecodes.GETFIELD;
import static jdk.graal.compiler.bytecode.Bytecodes.GETSTATIC;
import static jdk.graal.compiler.bytecode.Bytecodes.GOTO;
import static jdk.graal.compiler.bytecode.Bytecodes.GOTO_W;
import static jdk.graal.compiler.bytecode.Bytecodes.I2B;
import static jdk.graal.compiler.bytecode.Bytecodes.I2C;
import static jdk.graal.compiler.bytecode.Bytecodes.I2D;
import static jdk.graal.compiler.bytecode.Bytecodes.I2F;
import static jdk.graal.compiler.bytecode.Bytecodes.I2L;
import static jdk.graal.compiler.bytecode.Bytecodes.I2S;
import static jdk.graal.compiler.bytecode.Bytecodes.IADD;
import static jdk.graal.compiler.bytecode.Bytecodes.IALOAD;
import static jdk.graal.compiler.bytecode.Bytecodes.IAND;
import static jdk.graal.compiler.bytecode.Bytecodes.IASTORE;
import static jdk.graal.compiler.bytecode.Bytecodes.ICONST_0;
import static jdk.graal.compiler.bytecode.Bytecodes.ICONST_1;
import static jdk.graal.compiler.bytecode.Bytecodes.ICONST_2;
import static jdk.graal.compiler.bytecode.Bytecodes.ICONST_3;
import static jdk.graal.compiler.bytecode.Bytecodes.ICONST_4;
import static jdk.graal.compiler.bytecode.Bytecodes.ICONST_5;
import static jdk.graal.compiler.bytecode.Bytecodes.ICONST_M1;
import static jdk.graal.compiler.bytecode.Bytecodes.IDIV;
import static jdk.graal.compiler.bytecode.Bytecodes.IFEQ;
import static jdk.graal.compiler.bytecode.Bytecodes.IFGE;
import static jdk.graal.compiler.bytecode.Bytecodes.IFGT;
import static jdk.graal.compiler.bytecode.Bytecodes.IFLE;
import static jdk.graal.compiler.bytecode.Bytecodes.IFLT;
import static jdk.graal.compiler.bytecode.Bytecodes.IFNE;
import static jdk.graal.compiler.bytecode.Bytecodes.IFNONNULL;
import static jdk.graal.compiler.bytecode.Bytecodes.IFNULL;
import static jdk.graal.compiler.bytecode.Bytecodes.IF_ACMPEQ;
import static jdk.graal.compiler.bytecode.Bytecodes.IF_ACMPNE;
import static jdk.graal.compiler.bytecode.Bytecodes.IF_ICMPEQ;
import static jdk.graal.compiler.bytecode.Bytecodes.IF_ICMPGE;
import static jdk.graal.compiler.bytecode.Bytecodes.IF_ICMPGT;
import static jdk.graal.compiler.bytecode.Bytecodes.IF_ICMPLE;
import static jdk.graal.compiler.bytecode.Bytecodes.IF_ICMPLT;
import static jdk.graal.compiler.bytecode.Bytecodes.IF_ICMPNE;
import static jdk.graal.compiler.bytecode.Bytecodes.IINC;
import static jdk.graal.compiler.bytecode.Bytecodes.ILOAD;
import static jdk.graal.compiler.bytecode.Bytecodes.ILOAD_0;
import static jdk.graal.compiler.bytecode.Bytecodes.ILOAD_1;
import static jdk.graal.compiler.bytecode.Bytecodes.ILOAD_2;
import static jdk.graal.compiler.bytecode.Bytecodes.ILOAD_3;
import static jdk.graal.compiler.bytecode.Bytecodes.IMUL;
import static jdk.graal.compiler.bytecode.Bytecodes.INEG;
import static jdk.graal.compiler.bytecode.Bytecodes.INSTANCEOF;
import static jdk.graal.compiler.bytecode.Bytecodes.INVOKEDYNAMIC;
import static jdk.graal.compiler.bytecode.Bytecodes.INVOKEINTERFACE;
import static jdk.graal.compiler.bytecode.Bytecodes.INVOKESPECIAL;
import static jdk.graal.compiler.bytecode.Bytecodes.INVOKESTATIC;
import static jdk.graal.compiler.bytecode.Bytecodes.INVOKEVIRTUAL;
import static jdk.graal.compiler.bytecode.Bytecodes.IOR;
import static jdk.graal.compiler.bytecode.Bytecodes.IREM;
import static jdk.graal.compiler.bytecode.Bytecodes.IRETURN;
import static jdk.graal.compiler.bytecode.Bytecodes.ISHL;
import static jdk.graal.compiler.bytecode.Bytecodes.ISHR;
import static jdk.graal.compiler.bytecode.Bytecodes.ISTORE;
import static jdk.graal.compiler.bytecode.Bytecodes.ISTORE_0;
import static jdk.graal.compiler.bytecode.Bytecodes.ISTORE_1;
import static jdk.graal.compiler.bytecode.Bytecodes.ISTORE_2;
import static jdk.graal.compiler.bytecode.Bytecodes.ISTORE_3;
import static jdk.graal.compiler.bytecode.Bytecodes.ISUB;
import static jdk.graal.compiler.bytecode.Bytecodes.IUSHR;
import static jdk.graal.compiler.bytecode.Bytecodes.IXOR;
import static jdk.graal.compiler.bytecode.Bytecodes.JSR;
import static jdk.graal.compiler.bytecode.Bytecodes.JSR_W;
import static jdk.graal.compiler.bytecode.Bytecodes.L2D;
import static jdk.graal.compiler.bytecode.Bytecodes.L2F;
import static jdk.graal.compiler.bytecode.Bytecodes.L2I;
import static jdk.graal.compiler.bytecode.Bytecodes.LADD;
import static jdk.graal.compiler.bytecode.Bytecodes.LALOAD;
import static jdk.graal.compiler.bytecode.Bytecodes.LAND;
import static jdk.graal.compiler.bytecode.Bytecodes.LASTORE;
import static jdk.graal.compiler.bytecode.Bytecodes.LCMP;
import static jdk.graal.compiler.bytecode.Bytecodes.LCONST_0;
import static jdk.graal.compiler.bytecode.Bytecodes.LCONST_1;
import static jdk.graal.compiler.bytecode.Bytecodes.LDC;
import static jdk.graal.compiler.bytecode.Bytecodes.LDC2_W;
import static jdk.graal.compiler.bytecode.Bytecodes.LDC_W;
import static jdk.graal.compiler.bytecode.Bytecodes.LDIV;
import static jdk.graal.compiler.bytecode.Bytecodes.LLOAD;
import static jdk.graal.compiler.bytecode.Bytecodes.LLOAD_0;
import static jdk.graal.compiler.bytecode.Bytecodes.LLOAD_1;
import static jdk.graal.compiler.bytecode.Bytecodes.LLOAD_2;
import static jdk.graal.compiler.bytecode.Bytecodes.LLOAD_3;
import static jdk.graal.compiler.bytecode.Bytecodes.LMUL;
import static jdk.graal.compiler.bytecode.Bytecodes.LNEG;
import static jdk.graal.compiler.bytecode.Bytecodes.LOOKUPSWITCH;
import static jdk.graal.compiler.bytecode.Bytecodes.LOR;
import static jdk.graal.compiler.bytecode.Bytecodes.LREM;
import static jdk.graal.compiler.bytecode.Bytecodes.LRETURN;
import static jdk.graal.compiler.bytecode.Bytecodes.LSHL;
import static jdk.graal.compiler.bytecode.Bytecodes.LSHR;
import static jdk.graal.compiler.bytecode.Bytecodes.LSTORE;
import static jdk.graal.compiler.bytecode.Bytecodes.LSTORE_0;
import static jdk.graal.compiler.bytecode.Bytecodes.LSTORE_1;
import static jdk.graal.compiler.bytecode.Bytecodes.LSTORE_2;
import static jdk.graal.compiler.bytecode.Bytecodes.LSTORE_3;
import static jdk.graal.compiler.bytecode.Bytecodes.LSUB;
import static jdk.graal.compiler.bytecode.Bytecodes.LUSHR;
import static jdk.graal.compiler.bytecode.Bytecodes.LXOR;
import static jdk.graal.compiler.bytecode.Bytecodes.MONITORENTER;
import static jdk.graal.compiler.bytecode.Bytecodes.MONITOREXIT;
import static jdk.graal.compiler.bytecode.Bytecodes.MULTIANEWARRAY;
import static jdk.graal.compiler.bytecode.Bytecodes.NEW;
import static jdk.graal.compiler.bytecode.Bytecodes.NEWARRAY;
import static jdk.graal.compiler.bytecode.Bytecodes.NOP;
import static jdk.graal.compiler.bytecode.Bytecodes.POP;
import static jdk.graal.compiler.bytecode.Bytecodes.POP2;
import static jdk.graal.compiler.bytecode.Bytecodes.PUTFIELD;
import static jdk.graal.compiler.bytecode.Bytecodes.PUTSTATIC;
import static jdk.graal.compiler.bytecode.Bytecodes.RET;
import static jdk.graal.compiler.bytecode.Bytecodes.RETURN;
import static jdk.graal.compiler.bytecode.Bytecodes.SALOAD;
import static jdk.graal.compiler.bytecode.Bytecodes.SASTORE;
import static jdk.graal.compiler.bytecode.Bytecodes.SIPUSH;
import static jdk.graal.compiler.bytecode.Bytecodes.SWAP;
import static jdk.graal.compiler.bytecode.Bytecodes.TABLESWITCH;

/**
 * A {@link ForwardDataFlowAnalyzer} where the data-flow state is represented by an abstract
 * bytecode execution frame. This analyzer assumes that the provided bytecode is valid and verified
 * by a bytecode verifier.
 * <p>
 * The interpreter records {@link AbstractFrame abstract frames} for each instruction in the
 * bytecode sequence of a method. Each abstract frame represents the abstract state before the
 * would-be execution of the corresponding bytecode instruction.
 * <p>
 * JSR and RET opcodes are currently unsupported, and a {@link DataFlowAnalysisException} will be
 * thrown in case the analyzed method contains them.
 *
 * @param <T> The abstract representation of values pushed and popped from the operand stack and
 *            stored in the local variable table.
 */
public abstract class AbstractInterpreter<T> extends ForwardDataFlowAnalyzer<AbstractFrame<T>> {

    @Override
    protected AbstractFrame<T> createInitialState(ResolvedJavaMethod method) {
        /*
         * The initial state has an empty operand stack and local variable table slots containing
         * values corresponding to the method arguments and receiver (if non-static).
         */
        AbstractFrame<T> state = new AbstractFrame<>(method);

        int variableIndex = 0;
        if (method.hasReceiver()) {
            state.localVariableTable.put(defaultValue(), variableIndex, false);
            variableIndex++;
        }

        Signature signature = method.getSignature();
        int numOfParameters = signature.getParameterCount(false);
        for (int i = 0; i < numOfParameters; i++) {
            boolean parameterNeedsTwoSlots = signature.getParameterKind(i).needsTwoSlots();
            state.localVariableTable.put(defaultValue(), variableIndex, parameterNeedsTwoSlots);
            variableIndex += parameterNeedsTwoSlots ? 2 : 1;
        }

        return state;
    }

    @Override
    protected AbstractFrame<T> createExceptionState(AbstractFrame<T> inState, List<JavaType> exceptionTypes) {
        /*
         * The initial frame state in exception handlers is created by clearing the operand stack
         * and placing the caught exception object on it.
         */
        AbstractFrame<T> exceptionState = new AbstractFrame<>(inState);
        exceptionState.operandStack.clear();
        exceptionState.operandStack.push(defaultValue(), false);
        return exceptionState;
    }

    @Override
    protected AbstractFrame<T> copyState(AbstractFrame<T> state) {
        return new AbstractFrame<>(state);
    }

    @Override
    protected AbstractFrame<T> mergeStates(AbstractFrame<T> left, AbstractFrame<T> right) {
        return left.merge(right, this::merge);
    }

    @Override
    @SuppressWarnings("DuplicateBranchesInSwitch")
    protected AbstractFrame<T> processInstruction(AbstractFrame<T> inState, BytecodeStream stream, Bytecode code) {
        AbstractFrame<T> outState = copyState(inState);

        var stack = outState.operandStack;
        var variables = outState.localVariableTable;

        int bci = stream.currentBCI();
        int opcode = stream.currentBC();

        InstructionContext<T> context = new InstructionContext<>(code.getMethod(), bci, opcode, outState);
        ConstantPool cp = code.getConstantPool();

        // @formatter:off
        // Checkstyle: stop
        switch (opcode) {
            case NOP            : break;
            case ACONST_NULL    : handleConstant(context, JavaConstant.NULL_POINTER, false); break;
            case ICONST_M1      : handleConstant(context, JavaConstant.forInt(-1), false); break;
            case ICONST_0       : // fall through
            case ICONST_1       : // fall through
            case ICONST_2       : // fall through
            case ICONST_3       : // fall through
            case ICONST_4       : // fall through
            case ICONST_5       : handleConstant(context, JavaConstant.forInt(opcode - ICONST_0), false); break;
            case LCONST_0       : // fall through
            case LCONST_1       : handleConstant(context, JavaConstant.forLong(opcode - LCONST_0), true); break;
            case FCONST_0       : // fall through
            case FCONST_1       : // fall through
            case FCONST_2       : handleConstant(context, JavaConstant.forFloat(opcode - FCONST_0), false); break;
            case DCONST_0       : // fall through
            case DCONST_1       : handleConstant(context, JavaConstant.forDouble(opcode - DCONST_0), true); break;
            case BIPUSH         : handleConstant(context, JavaConstant.forByte(stream.readByte()), false); break;
            case SIPUSH         : handleConstant(context, JavaConstant.forShort(stream.readShort()), false); break;
            case LDC            : // fall through
            case LDC_W          : handleConstant(context, lookupConstant(cp, stream.readCPI(), opcode), false); break;
            case LDC2_W         : handleConstant(context, lookupConstant(cp, stream.readCPI(), opcode), true); break;
            case ILOAD          : handleVariableLoad(context, stream.readLocalIndex(), false); break;
            case LLOAD          : handleVariableLoad(context, stream.readLocalIndex(), true); break;
            case FLOAD          : handleVariableLoad(context, stream.readLocalIndex(), false); break;
            case DLOAD          : handleVariableLoad(context, stream.readLocalIndex(), true); break;
            case ALOAD          : handleVariableLoad(context, stream.readLocalIndex(), false); break;
            case ILOAD_0        : // fall through
            case ILOAD_1        : // fall through
            case ILOAD_2        : // fall through
            case ILOAD_3        : handleVariableLoad(context, opcode - ILOAD_0, false); break;
            case LLOAD_0        : // fall through
            case LLOAD_1        : // fall through
            case LLOAD_2        : // fall through
            case LLOAD_3        : handleVariableLoad(context, opcode - LLOAD_0, true); break;
            case FLOAD_0        : // fall through
            case FLOAD_1        : // fall through
            case FLOAD_2        : // fall through
            case FLOAD_3        : handleVariableLoad(context, opcode - FLOAD_0, false); break;
            case DLOAD_0        : // fall through
            case DLOAD_1        : // fall through
            case DLOAD_2        : // fall through
            case DLOAD_3        : handleVariableLoad(context, opcode - DLOAD_0, true); break;
            case ALOAD_0        : // fall through
            case ALOAD_1        : // fall through
            case ALOAD_2        : // fall through
            case ALOAD_3        : handleVariableLoad(context, opcode - ALOAD_0, false); break;
            case IALOAD         : stack.pop(); stack.pop(); stack.push(defaultValue(), false); break;
            case LALOAD         : stack.pop(); stack.pop(); stack.push(defaultValue(), true); break;
            case FALOAD         : stack.pop(); stack.pop(); stack.push(defaultValue(), false); break;
            case DALOAD         : stack.pop(); stack.pop(); stack.push(defaultValue(), true); break;
            case AALOAD         : // fall through
            case BALOAD         : // fall through
            case CALOAD         : // fall through
            case SALOAD         : stack.pop(); stack.pop(); stack.push(defaultValue(), false); break;
            case ISTORE         : handleVariableStore(context, stream.readLocalIndex(), false); break;
            case LSTORE         : handleVariableStore(context, stream.readLocalIndex(), true); break;
            case FSTORE         : handleVariableStore(context, stream.readLocalIndex(), false); break;
            case DSTORE         : handleVariableStore(context, stream.readLocalIndex(), true); break;
            case ASTORE         : handleVariableStore(context, stream.readLocalIndex(), false); break;
            case ISTORE_0       : // fall through
            case ISTORE_1       : // fall through
            case ISTORE_2       : // fall through
            case ISTORE_3       : handleVariableStore(context, opcode - ISTORE_0, false); break;
            case LSTORE_0       : // fall through
            case LSTORE_1       : // fall through
            case LSTORE_2       : // fall through
            case LSTORE_3       : handleVariableStore(context, opcode - LSTORE_0, true); break;
            case FSTORE_0       : // fall through
            case FSTORE_1       : // fall through
            case FSTORE_2       : // fall through
            case FSTORE_3       : handleVariableStore(context, opcode - FSTORE_0, false); break;
            case DSTORE_0       : // fall through
            case DSTORE_1       : // fall through
            case DSTORE_2       : // fall through
            case DSTORE_3       : handleVariableStore(context, opcode - DSTORE_0, true); break;
            case ASTORE_0       : // fall through
            case ASTORE_1       : // fall through
            case ASTORE_2       : // fall through
            case ASTORE_3       : handleVariableStore(context, opcode - ASTORE_0, false); break;
            case IASTORE        : // fall through
            case LASTORE        : // fall through
            case FASTORE        : // fall through
            case DASTORE        : // fall through
            case AASTORE        : // fall through
            case BASTORE        : // fall through
            case CASTORE        : // fall through
            case SASTORE        : handleArrayElementStore(context); break;
            case POP            : stack.applyPop(); break;
            case POP2           : stack.applyPop2(); break;
            case DUP            : stack.applyDup(); break;
            case DUP_X1         : stack.applyDupX1(); break;
            case DUP_X2         : stack.applyDupX2(); break;
            case DUP2           : stack.applyDup2(); break;
            case DUP2_X1        : stack.applyDup2X1(); break;
            case DUP2_X2        : stack.applyDup2X2(); break;
            case SWAP           : stack.applySwap(); break;
            case IADD           : // fall through
            case ISUB           : // fall through
            case IMUL           : // fall through
            case IDIV           : // fall through
            case IREM           : stack.pop(); stack.pop(); stack.push(defaultValue(), false); break;
            case LADD           : // fall through
            case LSUB           : // fall through
            case LMUL           : // fall through
            case LDIV           : // fall through
            case LREM           : stack.pop(); stack.pop(); stack.push(defaultValue(), true); break;
            case FADD           : // fall through
            case FSUB           : // fall through
            case FMUL           : // fall through
            case FDIV           : // fall through
            case FREM           : stack.pop(); stack.pop(); stack.push(defaultValue(), false); break;
            case DADD           : // fall through
            case DSUB           : // fall through
            case DMUL           : // fall through
            case DDIV           : // fall through
            case DREM           : stack.pop(); stack.pop(); stack.push(defaultValue(), true); break;
            case INEG           : stack.pop(); stack.push(defaultValue(), false); break;
            case LNEG           : stack.pop(); stack.push(defaultValue(), true); break;
            case FNEG           : stack.pop(); stack.push(defaultValue(), false); break;
            case DNEG           : stack.pop(); stack.push(defaultValue(), true); break;
            case ISHL           : // fall through
            case ISHR           : // fall through
            case IUSHR          : // fall through
            case IAND           : // fall through
            case IOR            : // fall through
            case IXOR           : stack.pop(); stack.pop(); stack.push(defaultValue(), false); break;
            case LSHL           : // fall through
            case LSHR           : // fall through
            case LUSHR          : // fall through
            case LAND           : // fall through
            case LOR            : // fall through
            case LXOR           : stack.pop(); stack.pop(); stack.push(defaultValue(), true); break;
            case IINC           : variables.put(defaultValue(), stream.readLocalIndex(), false); break;
            case I2F            : stack.pop(); stack.push(defaultValue(), false); break;
            case I2D            : stack.pop(); stack.push(defaultValue(), true); break;
            case L2F            : stack.pop(); stack.push(defaultValue(), false); break;
            case L2D            : stack.pop(); stack.push(defaultValue(), true); break;
            case F2I            : stack.pop(); stack.push(defaultValue(), false); break;
            case F2L            : // fall through
            case F2D            : stack.pop(); stack.push(defaultValue(), true); break;
            case D2I            : stack.pop(); stack.push(defaultValue(), false); break;
            case D2L            : stack.pop(); stack.push(defaultValue(), true); break;
            case D2F            : // fall through
            case L2I            : stack.pop(); stack.push(defaultValue(), false); break;
            case I2L            : stack.pop(); stack.push(defaultValue(), true); break;
            case I2B            : // fall through
            case I2S            : // fall through
            case I2C            : stack.pop(); stack.push(defaultValue(), false); break;
            case LCMP           : // fall through
            case FCMPL          : // fall through
            case FCMPG          : // fall through
            case DCMPL          : // fall through
            case DCMPG          : stack.pop(); stack.pop(); stack.push(defaultValue(), false); break;
            case IFEQ           : // fall through
            case IFNE           : // fall through
            case IFLT           : // fall through
            case IFGE           : // fall through
            case IFGT           : // fall through
            case IFLE           : stack.pop(); break;
            case IF_ICMPEQ      : // fall through
            case IF_ICMPNE      : // fall through
            case IF_ICMPLT      : // fall through
            case IF_ICMPGE      : // fall through
            case IF_ICMPGT      : // fall through
            case IF_ICMPLE      : // fall through
            case IF_ACMPEQ      : // fall through
            case IF_ACMPNE      : stack.pop(); stack.pop(); break;
            case GOTO           : break;
            case JSR            : // fall through
            case RET            : throw new DataFlowAnalysisException("Unsupported opcode " + opcode);
            case TABLESWITCH    : // fall through
            case LOOKUPSWITCH   : stack.pop(); break;
            case IRETURN        : // fall through
            case LRETURN        : // fall through
            case FRETURN        : // fall through
            case DRETURN        : // fall through
            case ARETURN        : stack.pop(); break;
            case RETURN         : break;
            case GETSTATIC      : handleStaticFieldLoad(context, lookupField(cp, stream.readCPI(), opcode, code.getMethod())); break;
            case PUTSTATIC      : onValueEscape(context, stack.pop()); break;
            case GETFIELD       : handleFieldLoad(context, lookupField(cp, stream.readCPI(), opcode, code.getMethod())); break;
            case PUTFIELD       : onValueEscape(context, stack.pop()); stack.pop(); break;
            case INVOKEVIRTUAL  : handleInvoke(context, lookupMethod(cp, stream.readCPI(), opcode, code.getMethod()), lookupAppendix(cp, stream.readCPI(), opcode)); break;
            case INVOKESPECIAL  : // fall through
            case INVOKESTATIC   : // fall through
            case INVOKEINTERFACE: handleInvoke(context, lookupMethod(cp, stream.readCPI(), opcode, code.getMethod()), null); break;
            case INVOKEDYNAMIC  : handleInvoke(context, lookupMethod(cp, stream.readCPI4(), opcode, code.getMethod()), lookupAppendix(cp, stream.readCPI4(), opcode)); break;
            case NEW            : stack.push(defaultValue(), false); break;
            case NEWARRAY       : stack.pop(); stack.push(defaultValue(), false); break;
            case ANEWARRAY      : handleNewObjectArray(context, lookupType(cp, stream.readCPI(), opcode)); break;
            case ARRAYLENGTH    : stack.pop(); stack.push(defaultValue(), false); break;
            case ATHROW         : stack.pop(); break;
            case CHECKCAST      : handleCastCheck(context, lookupType(cp, stream.readCPI(), opcode)); break;
            case INSTANCEOF     : stack.pop(); stack.push(defaultValue(), false); break;
            case MONITORENTER   : // fall through
            case MONITOREXIT    : stack.pop(); break;
            case MULTIANEWARRAY : popOperands(stack, stream.readUByte(bci + 3)); stack.push(defaultValue(), false); break;
            case IFNULL         : // fall through
            case IFNONNULL      : stack.pop(); break;
            case GOTO_W         : break;
            case JSR_W          : // fall through
            case BREAKPOINT     : // fall through
            default             : throw new DataFlowAnalysisException("Unsupported opcode " + opcode);
        }
        // @formatter:on
        // Checkstyle: resume

        return outState;
    }

    /**
     * Execution context of a bytecode instruction.
     *
     * @param method The method to which this instruction belongs.
     * @param bci The bytecode index of this instruction.
     * @param opcode The opcode of this instruction.
     * @param state The abstract state of the bytecode frame right before the execution of this
     *            instruction (its input state). Any modifications of the {@code state} will be
     *            reflected on the input state of successor instructions.
     */
    protected record InstructionContext<T>(ResolvedJavaMethod method, int bci, int opcode, AbstractFrame<T> state) {

    }

    /**
     * @return The default abstract value. This value usually represents an over-saturated value
     *         from which no useful information can be inferred.
     */
    protected abstract T defaultValue();

    /**
     * Merge two matching operand stack or local variable table values from divergent control-flow
     * paths.
     *
     * @return The merged value.
     */
    protected abstract T merge(T left, T right);

    protected abstract T loadConstant(InstructionContext<T> context, Constant constant);

    protected abstract T loadType(InstructionContext<T> context, JavaType type);

    protected abstract T loadVariable(InstructionContext<T> context, T value);

    protected abstract T loadStaticField(InstructionContext<T> context, JavaField field);

    protected abstract T storeVariable(InstructionContext<T> context, T value);

    protected abstract void storeArrayElement(InstructionContext<T> context, T array, T index, T value);

    protected abstract T invokeNonVoidMethod(InstructionContext<T> context, JavaMethod method, T receiver, List<T> operands);

    protected abstract T newObjectArray(InstructionContext<T> context, JavaType type, T size);

    protected abstract T checkCast(InstructionContext<T> context, JavaType type, T object);

    /**
     * This method is invoked whenever a {@code value} escapes {@link AbstractFrame}, be it by
     * storing it in an array, a field, or using it as a method argument.
     */
    protected abstract void onValueEscape(InstructionContext<T> context, T value);

    protected abstract Object lookupConstant(ConstantPool constantPool, int cpi, int opcode);

    protected abstract JavaType lookupType(ConstantPool constantPool, int cpi, int opcode);

    protected abstract JavaMethod lookupMethod(ConstantPool constantPool, int cpi, int opcode, ResolvedJavaMethod caller);

    protected abstract JavaConstant lookupAppendix(ConstantPool constantPool, int cpi, int opcode);

    protected abstract JavaField lookupField(ConstantPool constantPool, int cpi, int opcode, ResolvedJavaMethod caller);

    private List<T> popOperands(AbstractFrame.OperandStack<T> stack, int n) {
        return IntStream.range(0, n).mapToObj(i -> stack.pop()).toList().reversed();
    }

    private void handleConstant(InstructionContext<T> context, Object value, boolean needsTwoSlots) {
        var stack = context.state.operandStack;
        if (value == null) {
            /*
             * The constant is an unresolved JVM_CONSTANT_Dynamic, JVM_CONSTANT_MethodHandle or
             * JVM_CONSTANT_MethodType.
             */
            stack.push(loadConstant(context, null), needsTwoSlots);
        } else {
            if (value instanceof Constant constant) {
                stack.push(loadConstant(context, constant), needsTwoSlots);
            } else if (value instanceof JavaType type) {
                assert !needsTwoSlots : "Type references occupy a single stack slot";
                stack.push(loadType(context, type), false);
            }
        }
    }

    private void handleVariableLoad(InstructionContext<T> context, int index, boolean needsTwoSlots) {
        T value = context.state.localVariableTable.get(index);
        context.state.operandStack.push(loadVariable(context, value), needsTwoSlots);
    }

    private void handleVariableStore(InstructionContext<T> context, int index, boolean needsTwoSlots) {
        T value = context.state.operandStack.pop();
        context.state.localVariableTable.put(storeVariable(context, value), index, needsTwoSlots);
    }

    private void handleInvoke(InstructionContext<T> context, JavaMethod method, JavaConstant appendix) {
        var stack = context.state.operandStack;
        if (appendix != null) {
            stack.push(defaultValue(), false);
        }
        /*
         * HotSpot can rewrite some (method handle related) invocations, which can potentially lead
         * to an INVOKEVIRTUAL instruction actually invoking a static method. This means that we
         * cannot rely on the opcode to determine if the call has a receiver.
         *
         * https://wiki.openjdk.org/display/HotSpot/Method+handles+and+invokedynamic
         */
        boolean hasReceiver;
        if (method instanceof ResolvedJavaMethod resolved) {
            hasReceiver = resolved.hasReceiver();
        } else {
            hasReceiver = context.opcode != INVOKESTATIC && context.opcode != INVOKEDYNAMIC;
        }

        Signature signature = method.getSignature();

        T receiver = null;
        if (hasReceiver) {
            receiver = stack.pop();
            onValueEscape(context, receiver);
        }
        List<T> operands = popOperands(stack, signature.getParameterCount(false));
        operands.forEach(op -> onValueEscape(context, op));

        JavaKind returnKind = signature.getReturnKind();
        if (!returnKind.equals(JavaKind.Void)) {
            T returnValue = invokeNonVoidMethod(context, method, receiver, operands);
            stack.push(returnValue, returnKind.needsTwoSlots());
        }
    }

    private void handleStaticFieldLoad(InstructionContext<T> context, JavaField field) {
        T value = loadStaticField(context, field);
        context.state.operandStack.push(value, field.getJavaKind().needsTwoSlots());
    }

    private void handleFieldLoad(InstructionContext<T> context, JavaField field) {
        context.state.operandStack.pop();
        context.state.operandStack.push(defaultValue(), field.getJavaKind().needsTwoSlots());
    }

    private void handleNewObjectArray(InstructionContext<T> context, JavaType type) {
        T size = context.state.operandStack.pop();
        context.state.operandStack.push(newObjectArray(context, type, size), false);
    }

    private void handleArrayElementStore(InstructionContext<T> context) {
        var stack = context.state.operandStack;
        T value = stack.pop();
        T index = stack.pop();
        T array = stack.pop();
        onValueEscape(context, value);
        storeArrayElement(context, array, index, value);
    }

    private void handleCastCheck(InstructionContext<T> context, JavaType type) {
        T object = context.state.operandStack.pop();
        context.state.operandStack.push(checkCast(context, type, object), false);
    }
}
