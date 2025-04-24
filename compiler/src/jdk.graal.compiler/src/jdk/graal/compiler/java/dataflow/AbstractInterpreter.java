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
import static jdk.graal.compiler.java.dataflow.AbstractFrame.SizedValue.wrap;

/**
 * A data flow analyzer where the data flow state is represented by an abstract
 * bytecode execution frame.
 *
 * @param <T> The abstract representation of values pushed and popped from the operand stack and stored in the local variable table.
 */
public abstract class AbstractInterpreter<T> extends DataFlowAnalyzer<AbstractFrame<T>> {

    private final CoreProviders providers;

    public AbstractInterpreter(CoreProviders providers) {
        this.providers = providers;
    }

    public CoreProviders getProviders() {
        return providers;
    }

    @Override
    protected AbstractFrame<T> createInitialState(ResolvedJavaMethod method) {
        AbstractFrame<T> state = new AbstractFrame<>();

        int variableIndex = 0;
        if (!method.isStatic()) {
            state.getLocalVariableTable().put(variableIndex, wrap(storeMethodArgument(method, -1, variableIndex), AbstractFrame.SizedValue.Slots.ONE_SLOT));
            variableIndex++;
        }

        Signature signature = method.getSignature();
        int numParameters = signature.getParameterCount(false);
        for (int parameterIndex = 0; parameterIndex < numParameters; parameterIndex++) {
            JavaKind kind = signature.getParameterKind(parameterIndex);
            AbstractFrame.SizedValue.Slots size = getSizeForKind(kind);
            state.getLocalVariableTable().put(variableIndex, wrap(storeMethodArgument(method, parameterIndex, variableIndex), size));
            variableIndex += kind.needsTwoSlots() ? 2 : 1;
        }

        return state;
    }

    @Override
    protected AbstractFrame<T> createExceptionState(AbstractFrame<T> inState, List<JavaType> exceptionTypes) {
        /*
         * The initial frame state when entering an exception handler is created
         * by clearing the operand stack and placing only one object, representing the
         * caught exception, on it.
         */
        AbstractFrame<T> exceptionPathState = new AbstractFrame<>(inState);
        exceptionPathState.getOperandStack().clear();
        AbstractFrame.SizedValue<T> exceptionObject = wrap(pushExceptionObject(exceptionTypes), AbstractFrame.SizedValue.Slots.ONE_SLOT);
        exceptionPathState.getOperandStack().push(exceptionObject);
        return exceptionPathState;
    }

    @Override
    protected AbstractFrame<T> copyState(AbstractFrame<T> state) {
        return new AbstractFrame<>(state);
    }

    @Override
    protected AbstractFrame<T> mergeStates(AbstractFrame<T> left, AbstractFrame<T> right) throws DataFlowAnalysisException {
        AbstractFrame<T> merged = copyState(left);
        merged.mergeWith(right, this::merge);
        return merged;
    }

    @Override
    protected AbstractFrame<T> processInstruction(AbstractFrame<T> inState, BytecodeStream stream, Bytecode code) throws DataFlowAnalysisException {
        AbstractFrame<T> outState = copyState(inState);

        int bci = stream.currentBCI();
        int opcode = stream.currentBC();

        // @formatter:off
        // Checkstyle: stop
        switch (opcode) {
            case NOP            : break;
            case ACONST_NULL    : handleConstant(opcode, bci, outState, JavaConstant.NULL_POINTER); break;
            case ICONST_M1      : handleConstant(opcode, bci, outState, JavaConstant.forInt(-1)); break;
            case ICONST_0       : // fall through
            case ICONST_1       : // fall through
            case ICONST_2       : // fall through
            case ICONST_3       : // fall through
            case ICONST_4       : // fall through
            case ICONST_5       : handleConstant(opcode, bci, outState, JavaConstant.forInt(opcode - ICONST_0)); break;
            case LCONST_0       : // fall through
            case LCONST_1       : handleConstant(opcode, bci, outState, JavaConstant.forLong((long) opcode - LCONST_0)); break;
            case FCONST_0       : // fall through
            case FCONST_1       : // fall through
            case FCONST_2       : handleConstant(opcode, bci, outState, JavaConstant.forFloat((float) opcode - FCONST_0)); break;
            case DCONST_0       : // fall through
            case DCONST_1       : handleConstant(opcode, bci, outState, JavaConstant.forDouble((double) opcode - DCONST_0)); break;
            case BIPUSH         : handleConstant(opcode, bci, outState, JavaConstant.forByte(stream.readByte())); break;
            case SIPUSH         : handleConstant(opcode, bci, outState, JavaConstant.forShort(stream.readShort())); break;
            case LDC            : // fall through
            case LDC_W          : // fall through
            case LDC2_W         : handleConstant(opcode, bci, outState, getConstant(code, stream)); break;
            case ILOAD          : // fall through
            case LLOAD          : // fall through
            case FLOAD          : // fall through
            case DLOAD          : // fall through
            case ALOAD          : handleVariableLoad(opcode, bci, outState, stream.readLocalIndex()); break;
            case ILOAD_0        : // fall through
            case ILOAD_1        : // fall through
            case ILOAD_2        : // fall through
            case ILOAD_3        : handleVariableLoad(opcode, bci, outState, opcode - ILOAD_0); break;
            case LLOAD_0        : // fall through
            case LLOAD_1        : // fall through
            case LLOAD_2        : // fall through
            case LLOAD_3        : handleVariableLoad(opcode, bci, outState, opcode - LLOAD_0); break;
            case FLOAD_0        : // fall through
            case FLOAD_1        : // fall through
            case FLOAD_2        : // fall through
            case FLOAD_3        : handleVariableLoad(opcode, bci, outState, opcode - FLOAD_0); break;
            case DLOAD_0        : // fall through
            case DLOAD_1        : // fall through
            case DLOAD_2        : // fall through
            case DLOAD_3        : handleVariableLoad(opcode, bci, outState, opcode - DLOAD_0); break;
            case ALOAD_0        : // fall through
            case ALOAD_1        : // fall through
            case ALOAD_2        : // fall through
            case ALOAD_3        : handleVariableLoad(opcode, bci, outState, opcode - ALOAD_0); break;
            case IALOAD         : // fall through
            case LALOAD         : // fall through
            case FALOAD         : // fall through
            case DALOAD         : // fall through
            case AALOAD         : // fall through
            case BALOAD         : // fall through
            case CALOAD         : // fall through
            case SALOAD         : handleArrayElementLoad(opcode, bci, outState); break;
            case ISTORE         : // fall through
            case LSTORE         : // fall through
            case FSTORE         : // fall through
            case DSTORE         : // fall through
            case ASTORE         : handleVariableStore(opcode, bci, outState, stream.readLocalIndex()); break;
            case ISTORE_0       : // fall through
            case ISTORE_1       : // fall through
            case ISTORE_2       : // fall through
            case ISTORE_3       : handleVariableStore(opcode, bci, outState, opcode - ISTORE_0); break;
            case LSTORE_0       : // fall through
            case LSTORE_1       : // fall through
            case LSTORE_2       : // fall through
            case LSTORE_3       : handleVariableStore(opcode, bci, outState, opcode - LSTORE_0); break;
            case FSTORE_0       : // fall through
            case FSTORE_1       : // fall through
            case FSTORE_2       : // fall through
            case FSTORE_3       : handleVariableStore(opcode, bci, outState, opcode - FSTORE_0); break;
            case DSTORE_0       : // fall through
            case DSTORE_1       : // fall through
            case DSTORE_2       : // fall through
            case DSTORE_3       : handleVariableStore(opcode, bci, outState, opcode - DSTORE_0); break;
            case ASTORE_0       : // fall through
            case ASTORE_1       : // fall through
            case ASTORE_2       : // fall through+
            case ASTORE_3       : handleVariableStore(opcode, bci, outState, opcode - ASTORE_0); break;
            case IASTORE        : // fall through
            case LASTORE        : // fall through
            case FASTORE        : // fall through
            case DASTORE        : // fall through
            case AASTORE        : // fall through
            case BASTORE        : // fall through
            case CASTORE        : // fall through
            case SASTORE        : handleArrayElementStore(opcode, bci, outState); break;
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
            case DREM           : handleBinaryOperation(opcode, bci, outState); break;
            case INEG           : // fall through
            case LNEG           : // fall through
            case FNEG           : // fall through
            case DNEG           : handleUnaryOperation(opcode, bci, outState); break;
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
            case LXOR           : handleBinaryOperation(opcode, bci, outState); break;
            case IINC           : handleIncrement(opcode, bci, outState, stream.readLocalIndex(), stream.readIncrement()); break;
            case I2F            : // fall through
            case I2D            : // fall through
            case L2F            : // fall through
            case L2D            : // fall through
            case F2I            : // fall through
            case F2L            : // fall through
            case F2D            : // fall through
            case D2I            : // fall through
            case D2L            : // fall through
            case D2F            : // fall through
            case L2I            : // fall through
            case I2L            : // fall through
            case I2B            : // fall through
            case I2S            : // fall through
            case I2C            : handleCast(opcode, bci, outState); break;
            case LCMP           : // fall through
            case FCMPL          : // fall through
            case FCMPG          : // fall through
            case DCMPL          : // fall through
            case DCMPG          : handleCompare(opcode, bci, outState); break;
            case IFEQ           : // fall through
            case IFNE           : // fall through
            case IFLT           : // fall through
            case IFGE           : // fall through
            case IFGT           : // fall through
            case IFLE           : handleUnaryConditionalJump(opcode, bci, outState, stream.readBranchDest(), stream.nextBCI()); break;
            case IF_ICMPEQ      : // fall through
            case IF_ICMPNE      : // fall through
            case IF_ICMPLT      : // fall through
            case IF_ICMPGE      : // fall through
            case IF_ICMPGT      : // fall through
            case IF_ICMPLE      : // fall through
            case IF_ACMPEQ      : // fall through
            case IF_ACMPNE      : handleBinaryConditionalJump(opcode, bci, outState, stream.readBranchDest(), stream.nextBCI()); break;
            case GOTO           : unconditionalJump(opcode, bci, outState, stream.readBranchDest()); break;
            case JSR            : // fall through
            case RET            : throw new DataFlowAnalysisException("Unsupported opcode " + opcode);
            case TABLESWITCH    : handleSwitch(opcode, bci, outState, new BytecodeTableSwitch(stream, bci)); break;
            case LOOKUPSWITCH   : handleSwitch(opcode, bci, outState, new BytecodeLookupSwitch(stream, bci)); break;
            case IRETURN        : // fall through
            case LRETURN        : // fall through
            case FRETURN        : // fall through
            case DRETURN        : // fall through
            case ARETURN        : returnValue(opcode, bci, outState, outState.getOperandStack().pop().value()); break;
            case RETURN         : returnVoid(opcode, bci, outState); break;
            case GETSTATIC      : handleStaticFieldLoad(opcode, bci, outState, getJavaField(code, stream)); break;
            case PUTSTATIC      : handleStaticFieldStore(opcode, bci, outState, getJavaField(code, stream)); break;
            case GETFIELD       : handleFieldLoad(opcode, bci, outState, getJavaField(code, stream)); break;
            case PUTFIELD       : handleFieldStore(opcode, bci, outState, getJavaField(code, stream)); break;
            case INVOKEVIRTUAL  : handleInvoke(opcode, bci, outState, getJavaMethod(code, stream), getAppendix(code, stream)); break;
            case INVOKESPECIAL  : // fall through
            case INVOKESTATIC   : // fall through
            case INVOKEINTERFACE: handleInvoke(opcode, bci, outState, getJavaMethod(code, stream), null); break;
            case INVOKEDYNAMIC  : handleInvoke(opcode, bci, outState, getJavaMethod(code, stream), getAppendix(code, stream)); break;
            case NEW            : handleNew(opcode, bci, outState, getJavaType(code, stream)); break;
            case NEWARRAY       : handleNewArray(opcode, bci, outState, getJavaTypeFromPrimitiveArrayCode(stream.readLocalIndex()), 1); break;
            case ANEWARRAY      : handleNewArray(opcode, bci, outState, getJavaType(code, stream), 1); break;
            case ARRAYLENGTH    : handleArrayLength(opcode, bci, outState); break;
            case ATHROW         : doThrow(opcode, bci, outState, outState.getOperandStack().pop().value()); break;
            case CHECKCAST      : // fall through
            case INSTANCEOF     : handleCastCheck(opcode, bci, outState, getJavaType(code, stream)); break;
            case MONITORENTER   : // fall through
            case MONITOREXIT    : monitorOperation(opcode, bci, outState, outState.getOperandStack().pop().value()); break;
            case MULTIANEWARRAY : handleNewArray(opcode, bci, outState, getJavaType(code, stream), stream.readUByte(bci + 3)); break;
            case IFNULL         : // fall through
            case IFNONNULL      : handleUnaryConditionalJump(opcode, bci, outState, stream.readBranchDest(), stream.nextBCI()); break;
            case GOTO_W         : unconditionalJump(opcode, bci, outState, stream.readBranchDest()); break;
            case JSR_W          : // fall through
            case BREAKPOINT     : // fall through
            default             : throw new DataFlowAnalysisException("Unsupported opcode " + opcode);
        }
        // @formatter:on
        // Checkstyle: resume

        return outState;
    }

    protected Object lookupConstant(Bytecode code, int cpi, int opcode) {
        tryToResolve(code.getConstantPool(), cpi, opcode);
        return code.getConstantPool().lookupConstant(cpi);
    }

    protected JavaType lookupType(Bytecode code, int cpi, int opcode) {
        tryToResolve(code.getConstantPool(), cpi, opcode);
        return code.getConstantPool().lookupType(cpi, opcode);
    }

    protected JavaField lookupField(Bytecode code, int cpi, int opcode) {
        tryToResolve(code.getConstantPool(), cpi, opcode);
        return code.getConstantPool().lookupField(cpi, code.getMethod(), opcode);
    }

    protected JavaMethod lookupMethod(Bytecode code, int cpi, int opcode) {
        tryToResolve(code.getConstantPool(), cpi, opcode);
        return code.getConstantPool().lookupMethod(cpi, opcode, code.getMethod());
    }

    protected static void tryToResolve(ConstantPool constantPool, int cpi, int opcode) {
        try {
            constantPool.loadReferencedType(cpi, opcode, false);
        } catch (Throwable t) {
            // Ignore and leave the type unresolved.
        }
    }

    private Object getConstant(Bytecode code, BytecodeStream stream) {
        return lookupConstant(code, stream.readCPI(), stream.currentBC());
    }

    private JavaType getJavaType(Bytecode code, BytecodeStream stream) {
        return lookupType(code, stream.readCPI(), stream.currentBC());
    }

    private JavaField getJavaField(Bytecode code, BytecodeStream stream) {
        return lookupField(code, stream.readCPI(), stream.currentBC());
    }

    private JavaMethod getJavaMethod(Bytecode code, BytecodeStream stream) {
        int opcode = stream.currentBC();
        int cpi = opcode == INVOKEDYNAMIC ? stream.readCPI4() : stream.readCPI();
        return lookupMethod(code, cpi, opcode);
    }

    private static JavaConstant getAppendix(Bytecode code, BytecodeStream stream) {
        int opcode = stream.currentBC();
        int cpi = opcode == INVOKEDYNAMIC ? stream.readCPI4() : stream.readCPI();
        return code.getConstantPool().lookupAppendix(cpi, stream.currentBC());
    }

    private void handleConstant(int opcode, int bci, AbstractFrame<T> state, Object value) {
        AbstractFrame.SizedValue.Slots size = value instanceof JavaConstant constant
                ? getSizeForKind(constant.getJavaKind())
                : AbstractFrame.SizedValue.Slots.ONE_SLOT;
        if (value instanceof Constant constant) {
            state.getOperandStack().push(wrap(pushConstant(opcode, bci, state, constant), size));
        } else if (value instanceof JavaType type) {
            state.getOperandStack().push(wrap(pushType(opcode, bci, state, type), size));
        }
    }

    private void handleVariableLoad(int opcode, int bci, AbstractFrame<T> state, int variableIndex) throws DataFlowAnalysisException {
        AbstractFrame.SizedValue<T> value = state.getLocalVariableTable().get(variableIndex);
        state.getOperandStack().push(wrap(loadVariable(opcode, bci, state, variableIndex, value.value()), value.size()));
    }

    private void handleArrayElementLoad(int opcode, int bci, AbstractFrame<T> state) throws DataFlowAnalysisException {
        T index = state.getOperandStack().pop().value();
        T array = state.getOperandStack().pop().value();
        AbstractFrame.SizedValue.Slots size = opcode == LALOAD || opcode == DALOAD
                ? AbstractFrame.SizedValue.Slots.TWO_SLOTS
                : AbstractFrame.SizedValue.Slots.ONE_SLOT;
        state.getOperandStack().push(wrap(loadArrayElement(opcode, bci, state, array, index), size));
    }

    private void handleVariableStore(int opcode, int bci, AbstractFrame<T> state, int variableIndex) throws DataFlowAnalysisException {
        AbstractFrame.SizedValue<T> value = state.getOperandStack().pop();
        state.getLocalVariableTable().put(variableIndex, wrap(storeVariable(opcode, bci, state, variableIndex, value.value()), value.size()));
    }

    private void handleArrayElementStore(int opcode, int bci, AbstractFrame<T> state) throws DataFlowAnalysisException {
        T value = state.getOperandStack().pop().value();
        T index = state.getOperandStack().pop().value();
        T array = state.getOperandStack().pop().value();
        storeArrayElement(opcode, bci, state, array, index, value);
    }

    private void handlePop(AbstractFrame<T> state) throws DataFlowAnalysisException {
        state.getOperandStack().pop();
    }

    private void handlePop2(AbstractFrame<T> state) throws DataFlowAnalysisException {
        AbstractFrame.SizedValue<T> value = state.getOperandStack().pop();
        if (value.size() == AbstractFrame.SizedValue.Slots.ONE_SLOT) {
            state.getOperandStack().pop();
        }
    }

    private void handleDup(AbstractFrame<T> state) throws DataFlowAnalysisException {
        state.getOperandStack().push(state.getOperandStack().peek());
    }

    private void handleDupX1(AbstractFrame<T> state) throws DataFlowAnalysisException {
        AbstractFrame.SizedValue<T> first = state.getOperandStack().pop();
        AbstractFrame.SizedValue<T> second = state.getOperandStack().pop();
        state.getOperandStack().push(first);
        state.getOperandStack().push(second);
        state.getOperandStack().push(first);
    }

    private void handleDupX2(AbstractFrame<T> state) throws DataFlowAnalysisException {
        AbstractFrame.SizedValue<T> first = state.getOperandStack().pop();
        AbstractFrame.SizedValue<T> second = state.getOperandStack().pop();
        if (second.size() == AbstractFrame.SizedValue.Slots.ONE_SLOT) {
            AbstractFrame.SizedValue<T> third = state.getOperandStack().pop();
            state.getOperandStack().push(first);
            state.getOperandStack().push(third);
        } else {
            state.getOperandStack().push(first);
        }
        state.getOperandStack().push(second);
        state.getOperandStack().push(first);
    }

    private void handleDup2(AbstractFrame<T> state) throws DataFlowAnalysisException {
        AbstractFrame.SizedValue<T> first = state.getOperandStack().pop();
        if (first.size() == AbstractFrame.SizedValue.Slots.ONE_SLOT) {
            AbstractFrame.SizedValue<T> second = state.getOperandStack().peek();
            state.getOperandStack().push(first);
            state.getOperandStack().push(second);
            state.getOperandStack().push(first);
        } else {
            state.getOperandStack().push(first);
            state.getOperandStack().push(first);
        }
    }

    private void handleDup2X1(AbstractFrame<T> state) throws DataFlowAnalysisException {
        AbstractFrame.SizedValue<T> first = state.getOperandStack().pop();
        AbstractFrame.SizedValue<T> second = state.getOperandStack().pop();
        if (first.size() == AbstractFrame.SizedValue.Slots.ONE_SLOT) {
            AbstractFrame.SizedValue<T> third = state.getOperandStack().pop();
            state.getOperandStack().push(second);
            state.getOperandStack().push(first);
            state.getOperandStack().push(third);
        } else {
            state.getOperandStack().push(first);
        }
        state.getOperandStack().push(second);
        state.getOperandStack().push(first);
    }

    private void handleDup2X2(AbstractFrame<T> state) throws DataFlowAnalysisException {
        AbstractFrame.SizedValue<T> first = state.getOperandStack().pop();
        AbstractFrame.SizedValue<T> second = state.getOperandStack().pop();
        if (first.size() == AbstractFrame.SizedValue.Slots.ONE_SLOT) {
            AbstractFrame.SizedValue<T> third = state.getOperandStack().pop();
            if (third.size() == AbstractFrame.SizedValue.Slots.ONE_SLOT) {
                AbstractFrame.SizedValue<T> fourth = state.getOperandStack().pop();
                state.getOperandStack().push(second);
                state.getOperandStack().push(first);
                state.getOperandStack().push(fourth);
            } else {
                state.getOperandStack().push(second);
                state.getOperandStack().push(first);
            }
            state.getOperandStack().push(third);
        } else {
            if (second.size() == AbstractFrame.SizedValue.Slots.ONE_SLOT) {
                AbstractFrame.SizedValue<T> third = state.getOperandStack().pop();
                state.getOperandStack().push(first);
                state.getOperandStack().push(third);
            } else {
                state.getOperandStack().push(first);
            }
        }
        state.getOperandStack().push(second);
        state.getOperandStack().push(first);
    }

    private void handleSwap(AbstractFrame<T> state) throws DataFlowAnalysisException {
        AbstractFrame.SizedValue<T> first = state.getOperandStack().pop();
        AbstractFrame.SizedValue<T> second = state.getOperandStack().pop();
        state.getOperandStack().push(first);
        state.getOperandStack().push(second);
    }

    private void handleBinaryOperation(int opcode, int bci, AbstractFrame<T> state) throws DataFlowAnalysisException {
        AbstractFrame.SizedValue<T> right = state.getOperandStack().pop();
        AbstractFrame.SizedValue<T> left = state.getOperandStack().pop();
        state.getOperandStack().push(wrap(binaryOperation(opcode, bci, state, left.value(), right.value()), left.size()));
    }

    private void handleUnaryOperation(int opcode, int bci, AbstractFrame<T> state) throws DataFlowAnalysisException {
        AbstractFrame.SizedValue<T> value = state.getOperandStack().pop();
        state.getOperandStack().push(wrap(unaryOperation(opcode, bci, state, value.value()), value.size()));
    }

    private void handleIncrement(int opcode, int bci, AbstractFrame<T> state, int variableIndex, int incrementBy) throws DataFlowAnalysisException {
        AbstractFrame.SizedValue<T> value = state.getLocalVariableTable().get(variableIndex);
        state.getLocalVariableTable().put(variableIndex, wrap(incrementVariable(opcode, bci, state, variableIndex, incrementBy, value.value()), value.size()));
    }

    private void handleCast(int opcode, int bci, AbstractFrame<T> state) throws DataFlowAnalysisException {
        T value = state.getOperandStack().pop().value();
        AbstractFrame.SizedValue.Slots size = List.of(I2D, L2D, F2L, F2D, D2L, I2L).contains(opcode)
                ? AbstractFrame.SizedValue.Slots.TWO_SLOTS
                : AbstractFrame.SizedValue.Slots.ONE_SLOT;
        state.getOperandStack().push(wrap(castOperation(opcode, bci, state, value), size));
    }

    private void handleCompare(int opcode, int bci, AbstractFrame<T> state) throws DataFlowAnalysisException {
        T right = state.getOperandStack().pop().value();
        T left = state.getOperandStack().pop().value();
        state.getOperandStack().push(wrap(comparisonOperation(opcode, bci, state, left, right), AbstractFrame.SizedValue.Slots.ONE_SLOT));
    }

    private void handleUnaryConditionalJump(int opcode, int bci, AbstractFrame<T> state, int ifDestinationBci, int elseDestinationBci) throws DataFlowAnalysisException {
        T value = state.getOperandStack().pop().value();
        unaryConditionalJump(opcode, bci, state, ifDestinationBci, elseDestinationBci, value);
    }

    private void handleBinaryConditionalJump(int opcode, int bci, AbstractFrame<T> state, int ifDestinationBci, int elseDestinationBci) throws DataFlowAnalysisException {
        T right = state.getOperandStack().pop().value();
        T left = state.getOperandStack().pop().value();
        binaryConditionalJump(opcode, bci, state, ifDestinationBci, elseDestinationBci, left, right);
    }

    private void handleSwitch(int opcode, int bci, AbstractFrame<T> state, BytecodeSwitch bcSwitch) throws DataFlowAnalysisException {
        T value = state.getOperandStack().pop().value();
        switchJump(opcode, bci, state, bcSwitch, value);
    }

    private void handleStaticFieldLoad(int opcode, int bci, AbstractFrame<T> state, JavaField field) {
        JavaKind fieldKind = field.getJavaKind();
        AbstractFrame.SizedValue.Slots size = getSizeForKind(fieldKind);
        state.getOperandStack().push(wrap(loadStaticField(opcode, bci, state, field), size));
    }

    private void handleStaticFieldStore(int opcode, int bci, AbstractFrame<T> state, JavaField field) throws DataFlowAnalysisException {
        T value = state.getOperandStack().pop().value();
        storeStaticField(opcode, bci, state, field, value);
    }

    private void handleFieldLoad(int opcode, int bci, AbstractFrame<T> state, JavaField field) throws DataFlowAnalysisException {
        JavaKind fieldKind = field.getJavaKind();
        AbstractFrame.SizedValue.Slots size = getSizeForKind(fieldKind);
        T object = state.getOperandStack().pop().value();
        state.getOperandStack().push(wrap(loadField(opcode, bci, state, field, object), size));
    }

    private void handleFieldStore(int opcode, int bci, AbstractFrame<T> state, JavaField field) throws DataFlowAnalysisException {
        T value = state.getOperandStack().pop().value();
        T object = state.getOperandStack().pop().value();
        storeField(opcode, bci, state, field, object, value);
    }

    private void handleInvoke(int opcode, int bci, AbstractFrame<T> state, JavaMethod method, JavaConstant appendix) throws DataFlowAnalysisException {
        if (appendix != null) {
            state.getOperandStack().push(wrap(pushAppendix(method, appendix), AbstractFrame.SizedValue.Slots.ONE_SLOT));
        }

        Signature signature = method.getSignature();

        /*
         * HotSpot can rewrite some (method handle related) invocations, which can
         * potentially lead to an INVOKEVIRTUAL instruction actually invoking a static method.
         * This means that we cannot rely on the opcode to determine if the call has a receiver.
         */
        boolean hasReceiver;
        if (opcode == INVOKEVIRTUAL && method instanceof ResolvedJavaMethod resolved) {
            hasReceiver = resolved.hasReceiver();
        } else {
            hasReceiver = opcode != INVOKESTATIC && opcode != INVOKEDYNAMIC;
        }

        int operandCount = signature.getParameterCount(hasReceiver);
        List<T> operands = new ArrayList<>(operandCount);
        for (int i = 0; i < operandCount; i++) {
            operands.add(state.getOperandStack().pop().value());
        }
        operands = operands.reversed();

        JavaKind returnKind = signature.getReturnKind();
        if (returnKind.equals(JavaKind.Void)) {
            invokeVoidMethod(opcode, bci, state, method, operands);
        } else {
            AbstractFrame.SizedValue.Slots size = getSizeForKind(returnKind);
            state.getOperandStack().push(wrap(invokeMethod(opcode, bci, state, method, operands), size));
        }
    }

    private void handleNew(int opcode, int bci, AbstractFrame<T> state, JavaType type) {
        state.getOperandStack().push(wrap(newObject(opcode, bci, state, type), AbstractFrame.SizedValue.Slots.ONE_SLOT));
    }

    private void handleNewArray(int opcode, int bci, AbstractFrame<T> state, JavaType type, int dimensions) throws DataFlowAnalysisException {
        List<T> counts = new ArrayList<>(dimensions);
        for (int i = 0; i < dimensions; i++) {
            counts.add(state.getOperandStack().pop().value());
        }
        counts = counts.reversed();
        state.getOperandStack().push(wrap(newArray(opcode, bci, state, type, counts), AbstractFrame.SizedValue.Slots.ONE_SLOT));
    }

    private JavaType getJavaTypeFromPrimitiveArrayCode(int typeCode) {
        Class<?> clazz = switch (typeCode) {
            case 4 -> boolean.class;
            case 5 -> char.class;
            case 6 -> float.class;
            case 7 -> double.class;
            case 8 -> byte.class;
            case 9 -> short.class;
            case 10 -> int.class;
            case 11 -> long.class;
            default -> throw GraalError.shouldNotReachHere("Unexpected primitive type code: " + typeCode);
        };
        return providers.getMetaAccess().lookupJavaType(clazz);
    }

    private void handleArrayLength(int opcode, int bci, AbstractFrame<T> state) throws DataFlowAnalysisException {
        T array = state.getOperandStack().pop().value();
        state.getOperandStack().push(wrap(arrayLength(opcode, bci, state, array), AbstractFrame.SizedValue.Slots.ONE_SLOT));
    }

    private void handleCastCheck(int opcode, int bci, AbstractFrame<T> state, JavaType type) throws DataFlowAnalysisException {
        T object = state.getOperandStack().pop().value();
        state.getOperandStack().push(wrap(castCheckOperation(opcode, bci, state, type, object), AbstractFrame.SizedValue.Slots.ONE_SLOT));
    }

    private static AbstractFrame.SizedValue.Slots getSizeForKind(JavaKind kind) {
        return kind.needsTwoSlots() ? AbstractFrame.SizedValue.Slots.TWO_SLOTS : AbstractFrame.SizedValue.Slots.ONE_SLOT;
    }

    /**
     * @return The default abstract value. This value usually represents an over saturated value from which no useful information can be inferred.
     */
    protected abstract T top();

    /**
     * Merge two matching operand stack or local variable table values from divergent control flow paths.
     *
     * @return The merged value.
     */
    protected abstract T merge(T left, T right);

    /**
     * Put a variable corresponding to the analyzed method's arguments in the local variable table. This happens when
     * constructing the initial abstract execution frame at the method's entry point.
     *
     * @param method The method being analyzed.
     * @param argumentIndex The index of the argument being stored. If the method is non-static, the argument index for the receiver is set to -1.
     * @param variableIndex The index of the local variable table entry the value is being stored to.
     * @return The value to store in the local variable table.
     */
    protected T storeMethodArgument(ResolvedJavaMethod method, int argumentIndex, int variableIndex) {
        return top();
    }

    /**
     * Push an exception object on the operand stack upon entering an exception handler.
     *
     * @param exceptionTypes The possible types of the exception object.
     * @return The value representing the exception object pushed on the operand stack.
     */
    protected T pushExceptionObject(List<JavaType> exceptionTypes) {
        return top();
    }

    /**
     * Create a constant value to be pushed on the operand stack. This handler is called for the ACONST_NULL, ICONST_M1, ICONST_0, ..., ICONST_5,
     * LCONST_0, LCONST_1, FCONST_0, ..., FCONST_2, DCONST_0, DCONST_1, BIPUSH, SIPUSH, LDC, LDC_W and LDC2_W instructions.
     *
     * @param opcode The instruction opcode.
     * @param bci The instruction BCI.
     * @param state The abstract frame being modified by this instruction.
     * @param constant The constant being pushed onto the operand stack.
     * @return The abstract value to be pushed on the operand stack.
     */
    protected T pushConstant(int opcode, int bci, AbstractFrame<T> state, Constant constant) {
        return top();
    }

    /**
     * Create a constant type reference to be pushed on the operand stack. This handler is called for the LDC, LDC_W and LDC2_W instructions.
     *
     * @param opcode The instruction opcode.
     * @param bci The instruction BCI.
     * @param state The abstract frame being modified by this instruction.
     * @param type The type being pushed onto the operand stack.
     * @return The abstract value to be pushed on the operand stack.
     */
    protected T pushType(int opcode, int bci, AbstractFrame<T> state, JavaType type) {
        return top();
    }

    /**
     * Load a variable from the local variable table and push its value on the operand stack. This handler is called for the
     * various LOAD instructions.
     *
     * @param opcode The instruction opcode.
     * @param bci The instruction BCI.
     * @param state The abstract frame being modified by this instruction.
     * @param variableIndex The local variable table index of the variable being accessed.
     * @param value The abstract value currently stored in the accessed local variable.
     * @return The abstract value to be pushed on the operand stack.
     */
    protected T loadVariable(int opcode, int bci, AbstractFrame<T> state, int variableIndex, T value) {
        return value;
    }

    /**
     * Push an element value stored in the target array on the operand stack. This handler is called for the
     * IALOAD, LALOAD, FALOAD, DALOAD, AALOAD, BALOAD, CALOAD and SALOAD instructions.
     *
     * @param opcode The instruction opcode.
     * @param bci The instruction BCI.
     * @param state The abstract frame being modified by this instruction.
     * @param array The abstract value representing the accessed array.
     * @param index The abstract value representing the array index.
     * @return The abstract value representing the loaded element to be pushed on the operand stack.
     */
    protected T loadArrayElement(int opcode, int bci, AbstractFrame<T> state, T array, T index) {
        return top();
    }

    /**
     * Store a value in the local variable table. This handler is called for the various STORE instructions.
     *
     * @param opcode The instruction opcode.
     * @param bci The instruction BCI.
     * @param state The abstract frame being modified by this instruction.
     * @param variableIndex The local variable table index of the variable being accessed.
     * @param value The abstract value from the operand stack.
     * @return The abstract value to be stored in the local variable table.
     */
    protected T storeVariable(int opcode, int bci, AbstractFrame<T> state, int variableIndex, T value) {
        return value;
    }

    /**
     * Store a value in an array. This handler is called for the IASTORE, LASTORE, FASTORE, DASTORE, AASTORE, BASTORE,
     * CASTORE and SASTORE instructions.
     *
     * @param opcode The instruction opcode.
     * @param bci The instruction BCI.
     * @param state The abstract frame being modified by this instruction.
     * @param array The abstract value representing the accessed array.
     * @param index The abstract value representing the array index.
     * @param value The abstract value to be stored in the array.
     */
    protected void storeArrayElement(int opcode, int bci, AbstractFrame<T> state, T array, T index, T value) {

    }

    /**
     * Handler for various binary operations. It is invoked for the ADD, SUB, MUL, DIV, REM, SHL, SHR, USHR, AND, OR and XOR instructions.
     *
     * @param opcode The instruction opcode.
     * @param bci The instruction BCI.
     * @param state The abstract frame being modified by this instruction.
     * @param left The abstract value representing the left operand of the instruction.
     * @param right The abstract value representing the right operand of the instruction.
     * @return The abstract value representing the result of the binary operation.
     */
    protected T binaryOperation(int opcode, int bci, AbstractFrame<T> state, T left, T right) {
        return top();
    }

    /**
     * Handler for unary negation operations. It is invoked for the INEG, LNEG, FNEG and DNEG instructions.
     *
     * @param opcode The instruction opcode.
     * @param bci The instruction BCI.
     * @param state The abstract frame being modified by this instruction.
     * @param value The abstract value representing the operand of the instruction.
     * @return The abstract value representing the result of the unary operation.
     */
    protected T unaryOperation(int opcode, int bci, AbstractFrame<T> state, T value) {
        return top();
    }

    /**
     * Increment an integral variable in the local variable table. THis handler is called for the IINC instruction.
     *
     * @param opcode The instruction opcode.
     * @param bci The instruction BCI.
     * @param state The abstract frame being modified by this instruction.
     * @param variableIndex The local variable table index of the variable being accessed.
     * @param incrementBy The integer value to increment the variable by.
     * @param value The abstract value currently stored in the accessed local variable.
     * @return The abstract value of the accessed local variable after incrementing.
     */
    protected T incrementVariable(int opcode, int bci, AbstractFrame<T> state, int variableIndex, int incrementBy, T value) {
        return top();
    }

    /**
     * Cast a value into another type. This handler is invoked for the I2F, I2D, L2F, L2D, F2I, F2L, F2D, D2I, D2L,
     * D2F, L2I, I2L, I2B, I2S and I2C instructions.
     *
     * @param opcode The instruction opcode.
     * @param bci The instruction BCI.
     * @param state The abstract frame being modified by this instruction.
     * @param value The abstract value from the operand stack to be cast into another type.
     * @return The abstract value after casting.
     */
    protected T castOperation(int opcode, int bci, AbstractFrame<T> state, T value) {
        return top();
    }

    /**
     * Compare two values. This handler is called for the LCMP, FCMPL, FCMPG, DCMPL and DCMPG instructions.
     *
     * @param opcode The instruction opcode.
     * @param bci The instruction BCI.
     * @param state The abstract frame being modified by this instruction.
     * @param left The abstract value representing the left operand of the instruction.
     * @param right The abstract value representing the right operand of the instruction.
     * @return The result of the comparison operation.
     */
    protected T comparisonOperation(int opcode, int bci, AbstractFrame<T> state, T left, T right) {
        return top();
    }

    /**
     * Handler for unary conditional jumps. It is called for the IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE, IFNULL and IFNONNULL instructions.
     *
     * @param opcode The instruction opcode.
     * @param bci The instruction BCI.
     * @param state The abstract frame being modified by this instruction.
     * @param ifDestinationBci The BCI of the if-then destination instruction.
     * @param elseDestinationBci The BCI of the else destination instruction.
     * @param value The abstract value representing the comparison operand.
     */
    protected void unaryConditionalJump(int opcode, int bci, AbstractFrame<T> state, int ifDestinationBci, int elseDestinationBci, T value) {

    }

    /**
     * Handler for binary conditional jumps. It is called for the IF_ICMPEQ, IF_ICMPNE, IF_ICMPLT, IF_ICMPGE, IF_ICMPGT, IF_ICMPLE,
     * IF_ACMPEQ and IF_ACMPNE instructions.
     *
     * @param opcode The instruction opcode.
     * @param bci The instruction BCI.
     * @param state The abstract frame being modified by this instruction.
     * @param ifDestinationBci The BCI of the if-then destination instruction.
     * @param elseDestinationBci The BCI of the else destination instruction.
     * @param left The abstract value representing the left comparison operand.
     * @param right The abstract value representing the right comparison operand.
     */
    protected void binaryConditionalJump(int opcode, int bci, AbstractFrame<T> state, int ifDestinationBci, int elseDestinationBci, T left, T right) {

    }

    /**
     * Handler for GOTO and GOTO_W instructions.
     *
     * @param opcode The instruction opcode.
     * @param bci The instruction BCI.
     * @param state The abstract frame being modified by this instruction.
     * @param destinationBci The BCI of the destination instruction.
     */
    protected void unconditionalJump(int opcode, int bci, AbstractFrame<T> state, int destinationBci) {

    }

    /**
     * Handler for the TABLESWITCH and LOOKUPSWITCH instructions.
     *
     * @param opcode The instruction opcode.
     * @param bci The instruction BCI.
     * @param state The abstract frame being modified by this instruction.
     * @param bcSwitch The switch instruction.
     * @param value The abstract value representing the operand of the switch instruction.
     */
    protected void switchJump(int opcode, int bci, AbstractFrame<T> state, BytecodeSwitch bcSwitch, T value) {

    }

    /**
     * Handler for the IRETURN, LRETURN, FRETURN, DRETURN and ARETURN instructions.
     *
     * @param opcode The instruction opcode.
     * @param bci The instruction BCI.
     * @param state The abstract frame being modified by this instruction.
     * @param value The abstract value being returned.
     */
    protected void returnValue(int opcode, int bci, AbstractFrame<T> state, Object value) {

    }

    /**
     * Handler for the RETURN instruction.
     *
     * @param opcode The instruction opcode.
     * @param bci The instruction BCI.
     * @param state The abstract frame being modified by this instruction.
     */
    protected void returnVoid(int opcode, int bci, AbstractFrame<T> state) {

    }

    /**
     * Load value from a static field and push it on the operand stack. This handler is called for the GETSTATIC instruction.
     *
     * @param opcode The instruction opcode.
     * @param bci The instruction BCI.
     * @param state The abstract frame being modified by this instruction.
     * @param field The field which is being accessed.
     * @return The abstract representation of the accessed field's value.
     */
    protected T loadStaticField(int opcode, int bci, AbstractFrame<T> state, JavaField field) {
        return top();
    }

    /**
     * Store an abstract value into a static field. This handler is called for the PUTSTATIC instruction.
     *
     * @param opcode The instruction opcode.
     * @param bci The instruction BCI.
     * @param state The abstract frame being modified by this instruction.
     * @param field The field which is being accessed.
     * @param value The abstract representation of the value to be stored in the accessed field.
     */
    protected void storeStaticField(int opcode, int bci, AbstractFrame<T> state, JavaField field, T value) {

    }

    /**
     * Load value from a non-static field and push it on the operand stack. This handler is called for the GETFIELD instruction.
     *
     * @param opcode The instruction opcode.
     * @param bci The instruction BCI.
     * @param state The abstract frame being modified by this instruction.
     * @param field The field which is being accessed.
     * @param object The abstract representation of the object the accessed field belongs to.
     * @return The abstract representation of the accessed field's value.
     */
    protected T loadField(int opcode, int bci, AbstractFrame<T> state, JavaField field, T object) {
        return top();
    }

    /**
     * Store an abstract value into a non-static field. This handler is called for the PUTFIELD instruction.
     *
     * @param opcode The instruction opcode.
     * @param bci The instruction BCI.
     * @param state The abstract frame being modified by this instruction.
     * @param field The field which is being accessed.
     * @param object The abstract representation of the object the accessed field belongs to.
     * @param value The abstract representation of the value to be stored in the accessed field.
     */
    protected void storeField(int opcode, int bci, AbstractFrame<T> state, JavaField field, T object, T value) {

    }

    /**
     * Invoke a non-void method. This handler is called for the INVOKEVIRTUAL, INVOKESPECIAL, INVOKESTATIC, INVOKEINTERFACE
     * and INVOKEDYNAMIC instructions.
     *
     * @param opcode The instruction opcode.
     * @param bci The instruction BCI.
     * @param state The abstract frame being modified by this instruction.
     * @param method The method which is being invoked.
     * @param operands The abstract representation of the operands. If the method has a receiver, its abstract value is stored as the first element of the list.
     * @return The abstract representation of the result of the invocation.
     */
    protected T invokeMethod(int opcode, int bci, AbstractFrame<T> state, JavaMethod method, List<T> operands) {
        return top();
    }

    /**
     * Invoke a void method. This handler is called for the INVOKEVIRTUAL, INVOKESPECIAL, INVOKESTATIC, INVOKEINTERFACE
     * and INVOKEDYNAMIC instructions.
     *
     * @param opcode The instruction opcode.
     * @param bci The instruction BCI.
     * @param state The abstract frame being modified by this instruction.
     * @param method The method which is being invoked.
     * @param operands The abstract representation of the operands. If the method has a receiver, its abstract value is stored as the first element of the list.
     */
    protected void invokeVoidMethod(int opcode, int bci, AbstractFrame<T> state, JavaMethod method, List<T> operands) {

    }

    /**
     * Handler for loading a method's appendix.
     * <a href="https://wiki.openjdk.org/display/HotSpot/Method+handles+and+invokedynamic">This is used in INVOKEDYNAMIC instructions</a>
     *
     * @param method The invoked method.
     * @param appendix The invoked method's appendix.
     * @return Abstract representation of the appendix.
     */
    protected T pushAppendix(JavaMethod method, JavaConstant appendix) {
        return top();
    }

    /**
     * Create a new object and push its reference on the operand stack. This handler is called for the NEW instruction.
     *
     * @param opcode The instruction opcode.
     * @param bci The instruction BCI.
     * @param state The abstract frame being modified by this instruction.
     * @param type The type of the object whose reference is being pushed to the stack.
     * @return The abstract representation of the object.
     */
    protected T newObject(int opcode, int bci, AbstractFrame<T> state, JavaType type) {
        return top();
    }

    /**
     * Create a new array and push its reference on the operand stack. This handler is called for the NEWARRAY, ANEWARRAY and MULTIANEWARRAY instructions.
     *
     * @param opcode The instruction opcode.
     * @param bci The instruction BCI.
     * @param state The abstract frame being modified by this instruction.
     * @param type The type of the array elements.
     * @param counts List of capacities for each of the array's dimensions.
     * @return The abstract representation of the array.
     */
    protected T newArray(int opcode, int bci, AbstractFrame<T> state, JavaType type, List<T> counts) {
        return top();
    }

    /**
     * Handler for the ARRAYLENGTH instruction.
     *
     * @param opcode The instruction opcode.
     * @param bci The instruction BCI.
     * @param state The abstract frame being modified by this instruction.
     * @param array The abstract representation of the array.
     * @return The abstract representation of the array's length.
     */
    protected T arrayLength(int opcode, int bci, AbstractFrame<T> state, T array) {
        return top();
    }

    /**
     * Handler for the ATHROW instruction.
     *
     * @param opcode The instruction opcode.
     * @param bci The instruction BCI.
     * @param state The abstract frame being modified by this instruction.
     * @param throwable The abstract representation of the object being thrown.
     */
    protected void doThrow(int opcode, int bci, AbstractFrame<T> state, T throwable) {

    }

    /**
     * Handler for the CHECKCAST and INSTANCEOF instructions.
     *
     * @param opcode The instruction opcode.
     * @param bci The instruction BCI.
     * @param state The abstract frame being modified by this instruction.
     * @param type The type being checked against.
     * @param object The abstract representation of the instruction operand.
     * @return The abstract representation of the instruction's result.
     */
    protected T castCheckOperation(int opcode, int bci, AbstractFrame<T> state, JavaType type, T object) {
        return top();
    }

    /**
     * Handler for the MONITORENTER and MONITOREXIT operations.
     *
     * @param opcode The instruction opcode.
     * @param bci The instruction BCI.
     * @param state The abstract frame being modified by this instruction.
     * @param object The abstract representation of the instruction operand.
     */
    protected void monitorOperation(int opcode, int bci, AbstractFrame<T> state, T object) {

    }
}
