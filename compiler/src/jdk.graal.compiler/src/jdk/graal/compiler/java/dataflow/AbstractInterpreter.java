/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.java.dataflow;

import jdk.graal.compiler.bytecode.Bytecode;
import jdk.graal.compiler.bytecode.BytecodeLookupSwitch;
import jdk.graal.compiler.bytecode.BytecodeStream;
import jdk.graal.compiler.bytecode.BytecodeSwitch;
import jdk.graal.compiler.bytecode.BytecodeTableSwitch;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaField;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.Signature;

import java.util.ArrayList;
import java.util.List;

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
import static jdk.graal.compiler.java.dataflow.AbstractFrame.ValueWithSlots.Slots.ONE_SLOT;
import static jdk.graal.compiler.java.dataflow.AbstractFrame.ValueWithSlots.Slots.TWO_SLOTS;
import static jdk.graal.compiler.java.dataflow.AbstractFrame.ValueWithSlots.wrap;

/**
 * A {@link ForwardDataFlowAnalyzer} where the data flow state is represented by an abstract
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
@SuppressWarnings("unused")
public abstract class AbstractInterpreter<T> extends ForwardDataFlowAnalyzer<AbstractFrame<T>> {

    private final CoreProviders providers;

    public AbstractInterpreter(CoreProviders providers) {
        this.providers = providers;
    }

    public CoreProviders getProviders() {
        return providers;
    }

    @Override
    protected AbstractFrame<T> createInitialState(ResolvedJavaMethod method) {
        /*
         * The initial state has an empty operand stack and local variable table slots containing
         * values corresponding to the method arguments and receiver (if non-static).
         */
        AbstractFrame<T> state = new AbstractFrame<>();

        int variableIndex = 0;
        if (!method.isStatic()) {
            /* The argument position of the receiver is set to -1. */
            state.localVariableTable().put(variableIndex, wrap(storeMethodArgument(method, -1, variableIndex), ONE_SLOT));
            variableIndex++;
        }

        Signature signature = method.getSignature();
        int numParameters = signature.getParameterCount(false);
        for (int parameterIndex = 0; parameterIndex < numParameters; parameterIndex++) {
            JavaKind kind = signature.getParameterKind(parameterIndex);
            AbstractFrame.ValueWithSlots<T> value = wrap(storeMethodArgument(method, parameterIndex, variableIndex), getSizeForKind(kind));
            state.localVariableTable().put(variableIndex, value);
            variableIndex += kind.needsTwoSlots() ? 2 : 1;
        }

        return state;
    }

    @Override
    protected AbstractFrame<T> createExceptionState(AbstractFrame<T> inState, List<JavaType> exceptionTypes) {
        /*
         * The initial frame state in exception handlers is created by clearing the operand stack
         * and placing the caught exception object on it.
         */
        AbstractFrame<T> exceptionPathState = new AbstractFrame<>(inState);
        exceptionPathState.operandStack().clear();

        AbstractFrame.ValueWithSlots<T> exceptionObject = wrap(pushExceptionObject(exceptionTypes), ONE_SLOT);
        exceptionPathState.operandStack().push(exceptionObject);
        return exceptionPathState;
    }

    @Override
    protected AbstractFrame<T> copyState(AbstractFrame<T> state) {
        return new AbstractFrame<>(state);
    }

    @Override
    protected AbstractFrame<T> mergeStates(AbstractFrame<T> left, AbstractFrame<T> right) {
        AbstractFrame<T> merged = copyState(left);
        merged.mergeWith(right, this::merge);
        return merged;
    }

    @Override
    @SuppressWarnings("DuplicateBranchesInSwitch")
    protected AbstractFrame<T> processInstruction(AbstractFrame<T> inState, BytecodeStream stream, Bytecode code) {
        AbstractFrame<T> outState = copyState(inState);

        int bci = stream.currentBCI();
        int opcode = stream.currentBC();
        Context context = Context.create(code.getMethod(), bci, opcode);

        // @formatter:off
        // Checkstyle: stop
        switch (opcode) {
            case NOP            : break;
            case ACONST_NULL    : handleConstant(context, outState, JavaConstant.NULL_POINTER, ONE_SLOT); break;
            case ICONST_M1      : handleConstant(context, outState, JavaConstant.forInt(-1), ONE_SLOT); break;
            case ICONST_0       : // fall through
            case ICONST_1       : // fall through
            case ICONST_2       : // fall through
            case ICONST_3       : // fall through
            case ICONST_4       : // fall through
            case ICONST_5       : handleConstant(context, outState, JavaConstant.forInt(opcode - ICONST_0), ONE_SLOT); break;
            case LCONST_0       : // fall through
            case LCONST_1       : handleConstant(context, outState, JavaConstant.forLong(opcode - LCONST_0), TWO_SLOTS); break;
            case FCONST_0       : // fall through
            case FCONST_1       : // fall through
            case FCONST_2       : handleConstant(context, outState, JavaConstant.forFloat(opcode - FCONST_0), ONE_SLOT); break;
            case DCONST_0       : // fall through
            case DCONST_1       : handleConstant(context, outState, JavaConstant.forDouble(opcode - DCONST_0), TWO_SLOTS); break;
            case BIPUSH         : handleConstant(context, outState, JavaConstant.forByte(stream.readByte()), ONE_SLOT); break;
            case SIPUSH         : handleConstant(context, outState, JavaConstant.forShort(stream.readShort()), ONE_SLOT); break;
            case LDC            : // fall through
            case LDC_W          : handleConstant(context, outState, getConstant(code, stream), ONE_SLOT); break;
            case LDC2_W         : handleConstant(context, outState, getConstant(code, stream), TWO_SLOTS); break;
            case ILOAD          : // fall through
            case LLOAD          : // fall through
            case FLOAD          : // fall through
            case DLOAD          : // fall through
            case ALOAD          : handleVariableLoad(context, outState, stream.readLocalIndex()); break;
            case ILOAD_0        : // fall through
            case ILOAD_1        : // fall through
            case ILOAD_2        : // fall through
            case ILOAD_3        : handleVariableLoad(context, outState, opcode - ILOAD_0); break;
            case LLOAD_0        : // fall through
            case LLOAD_1        : // fall through
            case LLOAD_2        : // fall through
            case LLOAD_3        : handleVariableLoad(context, outState, opcode - LLOAD_0); break;
            case FLOAD_0        : // fall through
            case FLOAD_1        : // fall through
            case FLOAD_2        : // fall through
            case FLOAD_3        : handleVariableLoad(context, outState, opcode - FLOAD_0); break;
            case DLOAD_0        : // fall through
            case DLOAD_1        : // fall through
            case DLOAD_2        : // fall through
            case DLOAD_3        : handleVariableLoad(context, outState, opcode - DLOAD_0); break;
            case ALOAD_0        : // fall through
            case ALOAD_1        : // fall through
            case ALOAD_2        : // fall through
            case ALOAD_3        : handleVariableLoad(context, outState, opcode - ALOAD_0); break;
            case IALOAD         : handleArrayElementLoad(context, outState, ONE_SLOT); break;
            case LALOAD         : handleArrayElementLoad(context, outState, TWO_SLOTS); break;
            case FALOAD         : handleArrayElementLoad(context, outState, ONE_SLOT); break;
            case DALOAD         : handleArrayElementLoad(context, outState, TWO_SLOTS); break;
            case AALOAD         : // fall through
            case BALOAD         : // fall through
            case CALOAD         : // fall through
            case SALOAD         : handleArrayElementLoad(context, outState, ONE_SLOT); break;
            case ISTORE         : // fall through
            case LSTORE         : // fall through
            case FSTORE         : // fall through
            case DSTORE         : // fall through
            case ASTORE         : handleVariableStore(context, outState, stream.readLocalIndex()); break;
            case ISTORE_0       : // fall through
            case ISTORE_1       : // fall through
            case ISTORE_2       : // fall through
            case ISTORE_3       : handleVariableStore(context, outState, opcode - ISTORE_0); break;
            case LSTORE_0       : // fall through
            case LSTORE_1       : // fall through
            case LSTORE_2       : // fall through
            case LSTORE_3       : handleVariableStore(context, outState, opcode - LSTORE_0); break;
            case FSTORE_0       : // fall through
            case FSTORE_1       : // fall through
            case FSTORE_2       : // fall through
            case FSTORE_3       : handleVariableStore(context, outState, opcode - FSTORE_0); break;
            case DSTORE_0       : // fall through
            case DSTORE_1       : // fall through
            case DSTORE_2       : // fall through
            case DSTORE_3       : handleVariableStore(context, outState, opcode - DSTORE_0); break;
            case ASTORE_0       : // fall through
            case ASTORE_1       : // fall through
            case ASTORE_2       : // fall through
            case ASTORE_3       : handleVariableStore(context, outState, opcode - ASTORE_0); break;
            case IASTORE        : // fall through
            case LASTORE        : // fall through
            case FASTORE        : // fall through
            case DASTORE        : // fall through
            case AASTORE        : // fall through
            case BASTORE        : // fall through
            case CASTORE        : // fall through
            case SASTORE        : handleArrayElementStore(context, outState); break;
            case POP            : handlePop(outState); break;
            case POP2           : handlePop2(outState); break;
            case DUP            : handleDup(outState); break;
            case DUP_X1         : handleDupX1(outState); break;
            case DUP_X2         : handleDupX2(outState); break;
            case DUP2           : handleDup2(outState); break;
            case DUP2_X1        : handleDup2X1(outState); break;
            case DUP2_X2        : handleDup2X2(outState); break;
            case SWAP           : handleSwap(outState); break;
            case IADD           : // fall through
            case ISUB           : // fall through
            case IMUL           : // fall through
            case IDIV           : // fall through
            case IREM           : // fall through
            case LADD           : // fall through
            case LSUB           : // fall through
            case LMUL           : // fall through
            case LDIV           : // fall through
            case LREM           : // fall through
            case FADD           : // fall through
            case FSUB           : // fall through
            case FMUL           : // fall through
            case FDIV           : // fall through
            case FREM           : // fall through
            case DADD           : // fall through
            case DSUB           : // fall through
            case DMUL           : // fall through
            case DDIV           : // fall through
            case DREM           : handleBinaryOperation(context, outState); break;
            case INEG           : // fall through
            case LNEG           : // fall through
            case FNEG           : // fall through
            case DNEG           : handleUnaryOperation(context, outState); break;
            case ISHL           : // fall through
            case ISHR           : // fall through
            case IUSHR          : // fall through
            case IAND           : // fall through
            case IOR            : // fall through
            case IXOR           : // fall through
            case LSHL           : // fall through
            case LSHR           : // fall through
            case LUSHR          : // fall through
            case LAND           : // fall through
            case LOR            : // fall through
            case LXOR           : handleBinaryOperation(context, outState); break;
            case IINC           : handleIncrement(context, outState, stream.readLocalIndex(), stream.readIncrement()); break;
            case I2F            : handleCast(context, outState, ONE_SLOT); break;
            case I2D            : handleCast(context, outState, TWO_SLOTS); break;
            case L2F            : handleCast(context, outState, ONE_SLOT); break;
            case L2D            : handleCast(context, outState, TWO_SLOTS); break;
            case F2I            : handleCast(context, outState, ONE_SLOT); break;
            case F2L            : // fall through
            case F2D            : handleCast(context, outState, TWO_SLOTS); break;
            case D2I            : handleCast(context, outState, ONE_SLOT); break;
            case D2L            : handleCast(context, outState, TWO_SLOTS); break;
            case D2F            : handleCast(context, outState, ONE_SLOT); break;
            case L2I            : handleCast(context, outState, ONE_SLOT); break;
            case I2L            : handleCast(context, outState, TWO_SLOTS); break;
            case I2B            : // fall through
            case I2S            : // fall through
            case I2C            : handleCast(context, outState, ONE_SLOT); break;
            case LCMP           : // fall through
            case FCMPL          : // fall through
            case FCMPG          : // fall through
            case DCMPL          : // fall through
            case DCMPG          : handleCompare(context, outState); break;
            case IFEQ           : // fall through
            case IFNE           : // fall through
            case IFLT           : // fall through
            case IFGE           : // fall through
            case IFGT           : // fall through
            case IFLE           : handleUnaryConditionalJump(context, outState, stream.readBranchDest(), stream.nextBCI()); break;
            case IF_ICMPEQ      : // fall through
            case IF_ICMPNE      : // fall through
            case IF_ICMPLT      : // fall through
            case IF_ICMPGE      : // fall through
            case IF_ICMPGT      : // fall through
            case IF_ICMPLE      : // fall through
            case IF_ACMPEQ      : // fall through
            case IF_ACMPNE      : handleBinaryConditionalJump(context, outState, stream.readBranchDest(), stream.nextBCI()); break;
            case GOTO           : unconditionalJump(context, outState, stream.readBranchDest()); break;
            case JSR            : // fall through
            case RET            : throw new DataFlowAnalysisException("Unsupported opcode " + opcode);
            case TABLESWITCH    : handleSwitch(context, outState, new BytecodeTableSwitch(stream, bci)); break;
            case LOOKUPSWITCH   : handleSwitch(context, outState, new BytecodeLookupSwitch(stream, bci)); break;
            case IRETURN        : // fall through
            case LRETURN        : // fall through
            case FRETURN        : // fall through
            case DRETURN        : // fall through
            case ARETURN        : returnValue(context, outState, outState.operandStack().pop().value()); break;
            case RETURN         : returnVoid(context, outState); break;
            case GETSTATIC      : handleStaticFieldLoad(context, outState, getJavaField(code, stream)); break;
            case PUTSTATIC      : handleStaticFieldStore(context, outState, getJavaField(code, stream)); break;
            case GETFIELD       : handleFieldLoad(context, outState, getJavaField(code, stream)); break;
            case PUTFIELD       : handleFieldStore(context, outState, getJavaField(code, stream)); break;
            case INVOKEVIRTUAL  : handleInvoke(context, outState, getJavaMethod(code, stream), getAppendix(code, stream)); break;
            case INVOKESPECIAL  : // fall through
            case INVOKESTATIC   : // fall through
            case INVOKEINTERFACE: handleInvoke(context, outState, getJavaMethod(code, stream), null); break;
            case INVOKEDYNAMIC  : handleInvoke(context, outState, getJavaMethod(code, stream), getAppendix(code, stream)); break;
            case NEW            : handleNew(context, outState, getJavaType(code, stream)); break;
            case NEWARRAY       : handleNewArray(context, outState, getJavaTypeFromPrimitiveArrayCode(stream.readLocalIndex()), 1); break;
            case ANEWARRAY      : handleNewArray(context, outState, getJavaType(code, stream), 1); break;
            case ARRAYLENGTH    : handleArrayLength(context, outState); break;
            case ATHROW         : doThrow(context, outState, outState.operandStack().pop().value()); break;
            case CHECKCAST      : // fall through
            case INSTANCEOF     : handleCastCheck(context, outState, getJavaType(code, stream)); break;
            case MONITORENTER   : // fall through
            case MONITOREXIT    : monitorOperation(context, outState, outState.operandStack().pop().value()); break;
            case MULTIANEWARRAY : handleNewArray(context, outState, getJavaType(code, stream), stream.readUByte(bci + 3)); break;
            case IFNULL         : // fall through
            case IFNONNULL      : handleUnaryConditionalJump(context, outState, stream.readBranchDest(), stream.nextBCI()); break;
            case GOTO_W         : unconditionalJump(context, outState, stream.readBranchDest()); break;
            case JSR_W          : // fall through
            case BREAKPOINT     : // fall through
            default             : throw new DataFlowAnalysisException("Unsupported opcode " + opcode);
        }
        // @formatter:on
        // Checkstyle: resume

        return outState;
    }

    protected Object lookupConstant(ConstantPool constantPool, int cpi, int opcode) {
        tryToResolve(constantPool, cpi, opcode);
        return constantPool.lookupConstant(cpi, false);
    }

    protected JavaType lookupType(ConstantPool constantPool, int cpi, int opcode) {
        tryToResolve(constantPool, cpi, opcode);
        return constantPool.lookupType(cpi, opcode);
    }

    protected JavaField lookupField(ConstantPool constantPool, ResolvedJavaMethod method, int cpi, int opcode) {
        tryToResolve(constantPool, cpi, opcode);
        return constantPool.lookupField(cpi, method, opcode);
    }

    protected JavaMethod lookupMethod(ConstantPool constantPool, ResolvedJavaMethod method, int cpi, int opcode) {
        tryToResolve(constantPool, cpi, opcode);
        return constantPool.lookupMethod(cpi, opcode, method);
    }

    protected JavaConstant lookupAppendix(ConstantPool constantPool, int cpi, int opcode) {
        tryToResolve(constantPool, cpi, opcode);
        return constantPool.lookupAppendix(cpi, opcode);
    }

    protected static void tryToResolve(ConstantPool constantPool, int cpi, int opcode) {
        try {
            constantPool.loadReferencedType(cpi, opcode, false);
        } catch (Throwable t) {
            // Ignore and leave the type unresolved.
        }
    }

    private Object getConstant(Bytecode code, BytecodeStream stream) {
        return lookupConstant(code.getConstantPool(), stream.readCPI(), stream.currentBC());
    }

    private JavaType getJavaType(Bytecode code, BytecodeStream stream) {
        return lookupType(code.getConstantPool(), stream.readCPI(), stream.currentBC());
    }

    private JavaField getJavaField(Bytecode code, BytecodeStream stream) {
        return lookupField(code.getConstantPool(), code.getMethod(), stream.readCPI(), stream.currentBC());
    }

    private JavaMethod getJavaMethod(Bytecode code, BytecodeStream stream) {
        int opcode = stream.currentBC();
        int cpi = opcode == INVOKEDYNAMIC ? stream.readCPI4() : stream.readCPI();
        return lookupMethod(code.getConstantPool(), code.getMethod(), cpi, opcode);
    }

    private JavaConstant getAppendix(Bytecode code, BytecodeStream stream) {
        int opcode = stream.currentBC();
        int cpi = opcode == INVOKEDYNAMIC ? stream.readCPI4() : stream.readCPI();
        return lookupAppendix(code.getConstantPool(), cpi, opcode);
    }

    private void handleConstant(Context context, AbstractFrame<T> state, Object value, AbstractFrame.ValueWithSlots.Slots size) {
        if (value == null) {
            /*
             * The constant is an unresolved JVM_CONSTANT_Dynamic, JVM_CONSTANT_MethodHandle or
             * JVM_CONSTANT_MethodType.
             */
            state.operandStack().push(wrap(pushConstant(context, state, null), size));
        } else {
            if (value instanceof Constant constant) {
                state.operandStack().push(wrap(pushConstant(context, state, constant), size));
            } else if (value instanceof JavaType type) {
                state.operandStack().push(wrap(pushType(context, state, type), size));
            }
        }
    }

    private void handleVariableLoad(Context context, AbstractFrame<T> state, int variableIndex) {
        AbstractFrame.ValueWithSlots<T> value = state.localVariableTable().get(variableIndex);
        state.operandStack().push(wrap(loadVariable(context, state, variableIndex, value.value()), value.size()));
    }

    private void handleArrayElementLoad(Context context, AbstractFrame<T> state, AbstractFrame.ValueWithSlots.Slots size) {
        T index = state.operandStack().pop().value();
        T array = state.operandStack().pop().value();
        state.operandStack().push(wrap(loadArrayElement(context, state, array, index), size));
    }

    private void handleVariableStore(Context context, AbstractFrame<T> state, int variableIndex) {
        AbstractFrame.ValueWithSlots<T> value = state.operandStack().pop();
        state.localVariableTable().put(variableIndex, wrap(storeVariable(context, state, variableIndex, value.value()), value.size()));
    }

    private void handleArrayElementStore(Context context, AbstractFrame<T> state) {
        T value = state.operandStack().pop().value();
        T index = state.operandStack().pop().value();
        T array = state.operandStack().pop().value();
        storeArrayElement(context, state, array, index, value);
    }

    private void handlePop(AbstractFrame<T> state) {
        state.operandStack().pop();
    }

    private void handlePop2(AbstractFrame<T> state) {
        AbstractFrame.ValueWithSlots<T> value = state.operandStack().pop();
        if (value.size() == ONE_SLOT) {
            state.operandStack().pop();
        }
    }

    private void handleDup(AbstractFrame<T> state) {
        state.operandStack().push(state.operandStack().peek());
    }

    private void handleDupX1(AbstractFrame<T> state) {
        AbstractFrame.ValueWithSlots<T> first = state.operandStack().pop();
        AbstractFrame.ValueWithSlots<T> second = state.operandStack().pop();
        state.operandStack().push(first);
        state.operandStack().push(second);
        state.operandStack().push(first);
    }

    private void handleDupX2(AbstractFrame<T> state) {
        AbstractFrame.ValueWithSlots<T> first = state.operandStack().pop();
        AbstractFrame.ValueWithSlots<T> second = state.operandStack().pop();
        if (second.size() == ONE_SLOT) {
            AbstractFrame.ValueWithSlots<T> third = state.operandStack().pop();
            state.operandStack().push(first);
            state.operandStack().push(third);
        } else {
            state.operandStack().push(first);
        }
        state.operandStack().push(second);
        state.operandStack().push(first);
    }

    private void handleDup2(AbstractFrame<T> state) {
        AbstractFrame.ValueWithSlots<T> first = state.operandStack().pop();
        if (first.size() == ONE_SLOT) {
            AbstractFrame.ValueWithSlots<T> second = state.operandStack().peek();
            state.operandStack().push(first);
            state.operandStack().push(second);
            state.operandStack().push(first);
        } else {
            state.operandStack().push(first);
            state.operandStack().push(first);
        }
    }

    private void handleDup2X1(AbstractFrame<T> state) {
        AbstractFrame.ValueWithSlots<T> first = state.operandStack().pop();
        AbstractFrame.ValueWithSlots<T> second = state.operandStack().pop();
        if (first.size() == ONE_SLOT) {
            AbstractFrame.ValueWithSlots<T> third = state.operandStack().pop();
            state.operandStack().push(second);
            state.operandStack().push(first);
            state.operandStack().push(third);
        } else {
            state.operandStack().push(first);
        }
        state.operandStack().push(second);
        state.operandStack().push(first);
    }

    private void handleDup2X2(AbstractFrame<T> state) {
        AbstractFrame.ValueWithSlots<T> first = state.operandStack().pop();
        AbstractFrame.ValueWithSlots<T> second = state.operandStack().pop();
        if (first.size() == ONE_SLOT) {
            AbstractFrame.ValueWithSlots<T> third = state.operandStack().pop();
            if (third.size() == ONE_SLOT) {
                AbstractFrame.ValueWithSlots<T> fourth = state.operandStack().pop();
                state.operandStack().push(second);
                state.operandStack().push(first);
                state.operandStack().push(fourth);
            } else {
                state.operandStack().push(second);
                state.operandStack().push(first);
            }
            state.operandStack().push(third);
        } else {
            if (second.size() == ONE_SLOT) {
                AbstractFrame.ValueWithSlots<T> third = state.operandStack().pop();
                state.operandStack().push(first);
                state.operandStack().push(third);
            } else {
                state.operandStack().push(first);
            }
        }
        state.operandStack().push(second);
        state.operandStack().push(first);
    }

    private void handleSwap(AbstractFrame<T> state) {
        AbstractFrame.ValueWithSlots<T> first = state.operandStack().pop();
        AbstractFrame.ValueWithSlots<T> second = state.operandStack().pop();
        state.operandStack().push(first);
        state.operandStack().push(second);
    }

    private void handleBinaryOperation(Context context, AbstractFrame<T> state) {
        AbstractFrame.ValueWithSlots<T> right = state.operandStack().pop();
        AbstractFrame.ValueWithSlots<T> left = state.operandStack().pop();
        state.operandStack().push(wrap(binaryOperation(context, state, left.value(), right.value()), left.size()));
    }

    private void handleUnaryOperation(Context context, AbstractFrame<T> state) {
        AbstractFrame.ValueWithSlots<T> value = state.operandStack().pop();
        state.operandStack().push(wrap(unaryOperation(context, state, value.value()), value.size()));
    }

    private void handleIncrement(Context context, AbstractFrame<T> state, int variableIndex, int incrementBy) {
        AbstractFrame.ValueWithSlots<T> value = state.localVariableTable().get(variableIndex);
        state.localVariableTable().put(variableIndex, wrap(incrementVariable(context, state, variableIndex, incrementBy, value.value()), value.size()));
    }

    private void handleCast(Context context, AbstractFrame<T> state, AbstractFrame.ValueWithSlots.Slots size) {
        T value = state.operandStack().pop().value();
        state.operandStack().push(wrap(castOperation(context, state, value), size));
    }

    private void handleCompare(Context context, AbstractFrame<T> state) {
        T right = state.operandStack().pop().value();
        T left = state.operandStack().pop().value();
        state.operandStack().push(wrap(comparisonOperation(context, state, left, right), ONE_SLOT));
    }

    private void handleUnaryConditionalJump(Context context, AbstractFrame<T> state, int ifDestinationBci, int elseDestinationBci) {
        T value = state.operandStack().pop().value();
        unaryConditionalJump(context, state, ifDestinationBci, elseDestinationBci, value);
    }

    private void handleBinaryConditionalJump(Context context, AbstractFrame<T> state, int ifDestinationBci, int elseDestinationBci) {
        T right = state.operandStack().pop().value();
        T left = state.operandStack().pop().value();
        binaryConditionalJump(context, state, ifDestinationBci, elseDestinationBci, left, right);
    }

    private void handleSwitch(Context context, AbstractFrame<T> state, BytecodeSwitch bcSwitch) {
        T value = state.operandStack().pop().value();
        switchJump(context, state, bcSwitch, value);
    }

    private void handleStaticFieldLoad(Context context, AbstractFrame<T> state, JavaField field) {
        JavaKind fieldKind = field.getJavaKind();
        AbstractFrame.ValueWithSlots.Slots size = getSizeForKind(fieldKind);
        state.operandStack().push(wrap(loadStaticField(context, state, field), size));
    }

    private void handleStaticFieldStore(Context context, AbstractFrame<T> state, JavaField field) {
        T value = state.operandStack().pop().value();
        storeStaticField(context, state, field, value);
    }

    private void handleFieldLoad(Context context, AbstractFrame<T> state, JavaField field) {
        JavaKind fieldKind = field.getJavaKind();
        AbstractFrame.ValueWithSlots.Slots size = getSizeForKind(fieldKind);
        T object = state.operandStack().pop().value();
        state.operandStack().push(wrap(loadField(context, state, field, object), size));
    }

    private void handleFieldStore(Context context, AbstractFrame<T> state, JavaField field) {
        T value = state.operandStack().pop().value();
        T object = state.operandStack().pop().value();
        storeField(context, state, field, object, value);
    }

    private void handleInvoke(Context context, AbstractFrame<T> state, JavaMethod method, JavaConstant appendix) {
        if (appendix != null) {
            state.operandStack().push(wrap(pushAppendix(method, appendix), ONE_SLOT));
        }

        /*
         * HotSpot can rewrite some (method handle related) invocations, which can potentially lead
         * to an INVOKEVIRTUAL instruction actually invoking a static method. This means that we
         * cannot rely on the opcode to determine if the call has a receiver.
         *
         * https://wiki.openjdk.org/display/HotSpot/Method+handles+and+invokedynamic
         */
        boolean hasReceiver;
        if (context.opcode == INVOKEVIRTUAL && method instanceof ResolvedJavaMethod resolved) {
            hasReceiver = resolved.hasReceiver();
        } else {
            hasReceiver = context.opcode != INVOKESTATIC && context.opcode != INVOKEDYNAMIC;
        }

        Signature signature = method.getSignature();

        int operandCount = signature.getParameterCount(hasReceiver);
        List<T> operands = new ArrayList<>(operandCount);
        for (int i = 0; i < operandCount; i++) {
            operands.add(state.operandStack().pop().value());
        }
        operands = operands.reversed();

        JavaKind returnKind = signature.getReturnKind();
        if (returnKind.equals(JavaKind.Void)) {
            invokeVoidMethod(context, state, method, operands);
        } else {
            AbstractFrame.ValueWithSlots.Slots size = getSizeForKind(returnKind);
            state.operandStack().push(wrap(invokeMethod(context, state, method, operands), size));
        }
    }

    private void handleNew(Context context, AbstractFrame<T> state, JavaType type) {
        state.operandStack().push(wrap(newObject(context, state, type), ONE_SLOT));
    }

    private void handleNewArray(Context context, AbstractFrame<T> state, JavaType type, int dimensions) {
        List<T> counts = new ArrayList<>(dimensions);
        for (int i = 0; i < dimensions; i++) {
            counts.add(state.operandStack().pop().value());
        }
        counts = counts.reversed();
        state.operandStack().push(wrap(newArray(context, state, type, counts), ONE_SLOT));
    }

    /**
     * Type codes as defined by the <a
     * href=https://docs.oracle.com/javase/specs/jvms/se24/html/jvms-6.html#jvms-6.5.newarray>JVM
     * specification</a>.
     */
    private enum PrimitiveTypeArrayCode {
        T_BOOLEAN(boolean.class),
        T_CHAR(char.class),
        T_FLOAT(float.class),
        T_DOUBLE(double.class),
        T_BYTE(byte.class),
        T_SHORT(short.class),
        T_INT(int.class),
        T_LONG(long.class);

        private static final int TYPE_CODE_OFFSET = 4;
        private final Class<?> typeClass;

        PrimitiveTypeArrayCode(Class<?> typeClass) {
            this.typeClass = typeClass;
        }

        static Class<?> getType(int typeCode) {
            int typeIndex = typeCode - TYPE_CODE_OFFSET;
            if (typeIndex < 0 || typeIndex >= values().length) {
                throw GraalError.shouldNotReachHere("Unexpected primitive type code: " + typeCode);
            }
            return values()[typeIndex].typeClass;
        }
    }

    private JavaType getJavaTypeFromPrimitiveArrayCode(int typeCode) {
        return providers.getMetaAccess().lookupJavaType(PrimitiveTypeArrayCode.getType(typeCode));
    }

    private void handleArrayLength(Context context, AbstractFrame<T> state) {
        T array = state.operandStack().pop().value();
        state.operandStack().push(wrap(arrayLength(context, state, array), ONE_SLOT));
    }

    private void handleCastCheck(Context context, AbstractFrame<T> state, JavaType type) {
        T object = state.operandStack().pop().value();
        state.operandStack().push(wrap(castCheckOperation(context, state, type, object), ONE_SLOT));
    }

    private static AbstractFrame.ValueWithSlots.Slots getSizeForKind(JavaKind kind) {
        return kind.needsTwoSlots() ? TWO_SLOTS : ONE_SLOT;
    }

    /**
     * Represents the execution context for an instruction.
     */
    protected record Context(ResolvedJavaMethod method, int bci, int opcode) {

        public static Context create(ResolvedJavaMethod method, int bci, int opcode) {
            return new Context(method, bci, opcode);
        }
    }

    /**
     * @return The default abstract value. This value usually represents an over saturated value
     *         from which no useful information can be inferred.
     */
    protected abstract T bottom();

    /**
     * Merge two matching operand stack or local variable table values from divergent control flow
     * paths.
     *
     * @return The merged value.
     */
    protected abstract T merge(T left, T right);

    /**
     * Put a variable corresponding to the analyzed method's arguments in the local variable table.
     * This happens when constructing the initial abstract execution frame at the method's entry
     * point.
     *
     * @param method The method being analyzed.
     * @param argumentIndex The index of the argument being stored. If the method is non-static, the
     *            argument index for the receiver is set to -1.
     * @param variableIndex The index of the local variable table entry the value is being stored
     *            to.
     * @return The value to store in the local variable table.
     */
    protected T storeMethodArgument(ResolvedJavaMethod method, int argumentIndex, int variableIndex) {
        return bottom();
    }

    /**
     * Push an exception object on the operand stack upon entering an exception handler.
     *
     * @param exceptionTypes The possible types of the exception object.
     * @return The value representing the exception object pushed on the operand stack.
     */
    protected T pushExceptionObject(List<JavaType> exceptionTypes) {
        return bottom();
    }

    /**
     * Create a constant value to be pushed on the operand stack. This handler is called for the
     * ACONST_NULL, ICONST_M1, ICONST_0, ..., ICONST_5, LCONST_0, LCONST_1, FCONST_0, ..., FCONST_2,
     * DCONST_0, DCONST_1, BIPUSH, SIPUSH, LDC, LDC_W and LDC2_W instructions.
     *
     * @param context The execution context for this handler.
     * @param state The abstract frame being modified by this instruction.
     * @param constant The constant being pushed onto the operand stack.
     * @return The abstract value to be pushed on the operand stack.
     */
    protected T pushConstant(Context context, AbstractFrame<T> state, Constant constant) {
        return bottom();
    }

    /**
     * Create a constant type reference to be pushed on the operand stack. This handler is called
     * for the LDC and LDC_W instructions.
     *
     * @param context The execution context for this handler.
     * @param state The abstract frame being modified by this instruction.
     * @param type The type being pushed onto the operand stack.
     * @return The abstract value to be pushed on the operand stack.
     */
    protected T pushType(Context context, AbstractFrame<T> state, JavaType type) {
        return bottom();
    }

    /**
     * Load a variable from the local variable table and push its value on the operand stack. This
     * handler is called for the various LOAD instructions.
     *
     * @param context The execution context for this handler.
     * @param state The abstract frame being modified by this instruction.
     * @param variableIndex The local variable table index of the variable being accessed.
     * @param value The abstract value currently stored in the accessed local variable.
     * @return The abstract value to be pushed on the operand stack.
     */
    protected T loadVariable(Context context, AbstractFrame<T> state, int variableIndex, T value) {
        return value;
    }

    /**
     * Push an element value stored in the target array on the operand stack. This handler is called
     * for the IALOAD, LALOAD, FALOAD, DALOAD, AALOAD, BALOAD, CALOAD and SALOAD instructions.
     *
     * @param context The execution context for this handler.
     * @param state The abstract frame being modified by this instruction.
     * @param array The abstract value representing the accessed array.
     * @param index The abstract value representing the array index.
     * @return The abstract value representing the loaded element to be pushed on the operand stack.
     */
    protected T loadArrayElement(Context context, AbstractFrame<T> state, T array, T index) {
        return bottom();
    }

    /**
     * Store a value in the local variable table. This handler is called for the various STORE
     * instructions.
     *
     * @param context The execution context for this handler.
     * @param state The abstract frame being modified by this instruction.
     * @param variableIndex The local variable table index of the variable being accessed.
     * @param value The abstract value from the operand stack.
     * @return The abstract value to be stored in the local variable table.
     */
    protected T storeVariable(Context context, AbstractFrame<T> state, int variableIndex, T value) {
        return value;
    }

    /**
     * Store a value in an array. This handler is called for the IASTORE, LASTORE, FASTORE, DASTORE,
     * AASTORE, BASTORE, CASTORE and SASTORE instructions.
     *
     * @param context The execution context for this handler.
     * @param state The abstract frame being modified by this instruction.
     * @param array The abstract value representing the accessed array.
     * @param index The abstract value representing the array index.
     * @param value The abstract value to be stored in the array.
     */
    protected void storeArrayElement(Context context, AbstractFrame<T> state, T array, T index, T value) {

    }

    /**
     * Handler for various binary operations. It is invoked for the ADD, SUB, MUL, DIV, REM, SHL,
     * SHR, USHR, AND, OR and XOR instructions.
     *
     * @param context The execution context for this handler.
     * @param state The abstract frame being modified by this instruction.
     * @param left The abstract value representing the left operand of the instruction.
     * @param right The abstract value representing the right operand of the instruction.
     * @return The abstract value representing the result of the binary operation.
     */
    protected T binaryOperation(Context context, AbstractFrame<T> state, T left, T right) {
        return bottom();
    }

    /**
     * Handler for unary negation operations. It is invoked for the INEG, LNEG, FNEG and DNEG
     * instructions.
     *
     * @param context The execution context for this handler.
     * @param state The abstract frame being modified by this instruction.
     * @param value The abstract value representing the operand of the instruction.
     * @return The abstract value representing the result of the unary operation.
     */
    protected T unaryOperation(Context context, AbstractFrame<T> state, T value) {
        return bottom();
    }

    /**
     * Increment an integral variable in the local variable table. THis handler is called for the
     * IINC instruction.
     *
     * @param context The execution context for this handler.
     * @param state The abstract frame being modified by this instruction.
     * @param variableIndex The local variable table index of the variable being accessed.
     * @param incrementBy The integer value to increment the variable by.
     * @param value The abstract value currently stored in the accessed local variable.
     * @return The abstract value of the accessed local variable after incrementing.
     */
    protected T incrementVariable(Context context, AbstractFrame<T> state, int variableIndex, int incrementBy, T value) {
        return bottom();
    }

    /**
     * Cast a value into another type. This handler is invoked for the I2F, I2D, L2F, L2D, F2I, F2L,
     * F2D, D2I, D2L, D2F, L2I, I2L, I2B, I2S and I2C instructions.
     *
     * @param context The execution context for this handler.
     * @param state The abstract frame being modified by this instruction.
     * @param value The abstract value from the operand stack to be cast into another type.
     * @return The abstract value after casting.
     */
    protected T castOperation(Context context, AbstractFrame<T> state, T value) {
        return bottom();
    }

    /**
     * Compare two values. This handler is called for the LCMP, FCMPL, FCMPG, DCMPL and DCMPG
     * instructions.
     *
     * @param context The execution context for this handler.
     * @param state The abstract frame being modified by this instruction.
     * @param left The abstract value representing the left operand of the instruction.
     * @param right The abstract value representing the right operand of the instruction.
     * @return The result of the comparison operation.
     */
    protected T comparisonOperation(Context context, AbstractFrame<T> state, T left, T right) {
        return bottom();
    }

    /**
     * Handler for unary conditional jumps. It is called for the IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE,
     * IFNULL and IFNONNULL instructions.
     *
     * @param context The execution context for this handler.
     * @param state The abstract frame being modified by this instruction.
     * @param ifDestinationBci The BCI of the if-then destination instruction.
     * @param elseDestinationBci The BCI of the else destination instruction.
     * @param value The abstract value representing the comparison operand.
     */
    protected void unaryConditionalJump(Context context, AbstractFrame<T> state, int ifDestinationBci, int elseDestinationBci, T value) {

    }

    /**
     * Handler for binary conditional jumps. It is called for the IF_ICMPEQ, IF_ICMPNE, IF_ICMPLT,
     * IF_ICMPGE, IF_ICMPGT, IF_ICMPLE, IF_ACMPEQ and IF_ACMPNE instructions.
     *
     * @param context The execution context for this handler.
     * @param state The abstract frame being modified by this instruction.
     * @param ifDestinationBci The BCI of the if-then destination instruction.
     * @param elseDestinationBci The BCI of the else destination instruction.
     * @param left The abstract value representing the left comparison operand.
     * @param right The abstract value representing the right comparison operand.
     */
    protected void binaryConditionalJump(Context context, AbstractFrame<T> state, int ifDestinationBci, int elseDestinationBci, T left, T right) {

    }

    /**
     * Handler for GOTO and GOTO_W instructions.
     *
     * @param context The execution context for this handler.
     * @param state The abstract frame being modified by this instruction.
     * @param destinationBci The BCI of the destination instruction.
     */
    protected void unconditionalJump(Context context, AbstractFrame<T> state, int destinationBci) {

    }

    /**
     * Handler for the TABLESWITCH and LOOKUPSWITCH instructions.
     *
     * @param context The execution context for this handler.
     * @param state The abstract frame being modified by this instruction.
     * @param bcSwitch The switch instruction.
     * @param value The abstract value representing the operand of the switch instruction.
     */
    protected void switchJump(Context context, AbstractFrame<T> state, BytecodeSwitch bcSwitch, T value) {

    }

    /**
     * Handler for the IRETURN, LRETURN, FRETURN, DRETURN and ARETURN instructions.
     *
     * @param context The execution context for this handler.
     * @param state The abstract frame being modified by this instruction.
     * @param value The abstract value being returned.
     */
    protected void returnValue(Context context, AbstractFrame<T> state, Object value) {

    }

    /**
     * Handler for the RETURN instruction.
     *
     * @param context The execution context for this handler.
     * @param state The abstract frame being modified by this instruction.
     */
    protected void returnVoid(Context context, AbstractFrame<T> state) {

    }

    /**
     * Load value from a static field and push it on the operand stack. This handler is called for
     * the GETSTATIC instruction.
     *
     * @param context The execution context for this handler.
     * @param state The abstract frame being modified by this instruction.
     * @param field The field which is being accessed.
     * @return The abstract representation of the accessed field's value.
     */
    protected T loadStaticField(Context context, AbstractFrame<T> state, JavaField field) {
        return bottom();
    }

    /**
     * Store an abstract value into a static field. This handler is called for the PUTSTATIC
     * instruction.
     *
     * @param context The execution context for this handler.
     * @param state The abstract frame being modified by this instruction.
     * @param field The field which is being accessed.
     * @param value The abstract representation of the value to be stored in the accessed field.
     */
    protected void storeStaticField(Context context, AbstractFrame<T> state, JavaField field, T value) {

    }

    /**
     * Load value from a non-static field and push it on the operand stack. This handler is called
     * for the GETFIELD instruction.
     *
     * @param context The execution context for this handler.
     * @param state The abstract frame being modified by this instruction.
     * @param field The field which is being accessed.
     * @param object The abstract representation of the object the accessed field belongs to.
     * @return The abstract representation of the accessed field's value.
     */
    protected T loadField(Context context, AbstractFrame<T> state, JavaField field, T object) {
        return bottom();
    }

    /**
     * Store an abstract value into a non-static field. This handler is called for the PUTFIELD
     * instruction.
     *
     * @param context The execution context for this handler.
     * @param state The abstract frame being modified by this instruction.
     * @param field The field which is being accessed.
     * @param object The abstract representation of the object the accessed field belongs to.
     * @param value The abstract representation of the value to be stored in the accessed field.
     */
    protected void storeField(Context context, AbstractFrame<T> state, JavaField field, T object, T value) {

    }

    /**
     * Invoke a non-void method. This handler is called for the INVOKEVIRTUAL, INVOKESPECIAL,
     * INVOKESTATIC, INVOKEINTERFACE and INVOKEDYNAMIC instructions.
     *
     * @param context The execution context for this handler.
     * @param state The abstract frame being modified by this instruction.
     * @param method The method which is being invoked.
     * @param operands The abstract representation of the operands. If the method has a receiver,
     *            its abstract value is stored as the first element of the list.
     * @return The abstract representation of the result of the invocation.
     */
    protected T invokeMethod(Context context, AbstractFrame<T> state, JavaMethod method, List<T> operands) {
        return bottom();
    }

    /**
     * Invoke a void method. This handler is called for the INVOKEVIRTUAL, INVOKESPECIAL,
     * INVOKESTATIC, INVOKEINTERFACE and INVOKEDYNAMIC instructions.
     *
     * @param context The execution context for this handler.
     * @param state The abstract frame being modified by this instruction.
     * @param method The method which is being invoked.
     * @param operands The abstract representation of the operands. If the method has a receiver,
     *            its abstract value is stored as the first element of the list.
     */
    protected void invokeVoidMethod(Context context, AbstractFrame<T> state, JavaMethod method, List<T> operands) {

    }

    /**
     * Handler for loading a method's appendix.
     * <a href="https://wiki.openjdk.org/display/HotSpot/Method+handles+and+invokedynamic">This is
     * used in INVOKEDYNAMIC instructions</a>
     *
     * @param method The invoked method.
     * @param appendix The invoked method's appendix.
     * @return Abstract representation of the appendix.
     */
    protected T pushAppendix(JavaMethod method, JavaConstant appendix) {
        return bottom();
    }

    /**
     * Create a new object and push its reference on the operand stack. This handler is called for
     * the NEW instruction.
     *
     * @param context The execution context for this handler.
     * @param state The abstract frame being modified by this instruction.
     * @param type The type of the object whose reference is being pushed to the stack.
     * @return The abstract representation of the object.
     */
    protected T newObject(Context context, AbstractFrame<T> state, JavaType type) {
        return bottom();
    }

    /**
     * Create a new array and push its reference on the operand stack. This handler is called for
     * the NEWARRAY, ANEWARRAY and MULTIANEWARRAY instructions.
     *
     * @param context The execution context for this handler.
     * @param state The abstract frame being modified by this instruction.
     * @param type The type of the array elements.
     * @param counts List of capacities for each of the array's dimensions.
     * @return The abstract representation of the array.
     */
    protected T newArray(Context context, AbstractFrame<T> state, JavaType type, List<T> counts) {
        return bottom();
    }

    /**
     * Handler for the ARRAYLENGTH instruction.
     *
     * @param context The execution context for this handler.
     * @param state The abstract frame being modified by this instruction.
     * @param array The abstract representation of the array.
     * @return The abstract representation of the array's length.
     */
    protected T arrayLength(Context context, AbstractFrame<T> state, T array) {
        return bottom();
    }

    /**
     * Handler for the ATHROW instruction.
     *
     * @param context The execution context for this handler.
     * @param state The abstract frame being modified by this instruction.
     * @param throwable The abstract representation of the object being thrown.
     */
    protected void doThrow(Context context, AbstractFrame<T> state, T throwable) {

    }

    /**
     * Handler for the CHECKCAST and INSTANCEOF instructions.
     *
     * @param context The execution context for this handler.
     * @param state The abstract frame being modified by this instruction.
     * @param type The type being checked against.
     * @param object The abstract representation of the instruction operand.
     * @return The abstract representation of the instruction's result.
     */
    protected T castCheckOperation(Context context, AbstractFrame<T> state, JavaType type, T object) {
        return bottom();
    }

    /**
     * Handler for the MONITORENTER and MONITOREXIT operations.
     *
     * @param context The execution context for this handler.
     * @param state The abstract frame being modified by this instruction.
     * @param object The abstract representation of the instruction operand.
     */
    protected void monitorOperation(Context context, AbstractFrame<T> state, T object) {

    }
}
