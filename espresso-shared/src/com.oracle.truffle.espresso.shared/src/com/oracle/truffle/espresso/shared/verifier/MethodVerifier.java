/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.shared.verifier;

import static com.oracle.truffle.espresso.classfile.ConstantPool.Tag.CLASS;
import static com.oracle.truffle.espresso.classfile.ConstantPool.Tag.INTERFACE_METHOD_REF;
import static com.oracle.truffle.espresso.classfile.Constants.ITEM_Bogus;
import static com.oracle.truffle.espresso.classfile.Constants.ITEM_Double;
import static com.oracle.truffle.espresso.classfile.Constants.ITEM_Float;
import static com.oracle.truffle.espresso.classfile.Constants.ITEM_InitObject;
import static com.oracle.truffle.espresso.classfile.Constants.ITEM_Integer;
import static com.oracle.truffle.espresso.classfile.Constants.ITEM_Long;
import static com.oracle.truffle.espresso.classfile.Constants.ITEM_NewObject;
import static com.oracle.truffle.espresso.classfile.Constants.ITEM_Null;
import static com.oracle.truffle.espresso.classfile.Constants.ITEM_Object;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.AALOAD;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.AASTORE;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ACONST_NULL;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ALOAD;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ALOAD_0;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ALOAD_1;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ALOAD_2;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ALOAD_3;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ANEWARRAY;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ARETURN;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ARRAYLENGTH;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ASTORE;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ASTORE_0;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ASTORE_1;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ASTORE_2;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ASTORE_3;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ATHROW;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.BALOAD;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.BASTORE;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.BIPUSH;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.BREAKPOINT;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.CALOAD;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.CASTORE;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.CHECKCAST;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.D2F;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.D2I;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.D2L;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.DADD;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.DALOAD;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.DASTORE;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.DCMPG;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.DCMPL;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.DCONST_0;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.DCONST_1;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.DDIV;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.DLOAD;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.DLOAD_0;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.DLOAD_1;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.DLOAD_2;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.DLOAD_3;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.DMUL;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.DNEG;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.DREM;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.DRETURN;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.DSTORE;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.DSTORE_0;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.DSTORE_1;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.DSTORE_2;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.DSTORE_3;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.DSUB;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.DUP;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.DUP2;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.DUP2_X1;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.DUP2_X2;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.DUP_X1;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.DUP_X2;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.F2D;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.F2I;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.F2L;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.FADD;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.FALOAD;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.FASTORE;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.FCMPG;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.FCMPL;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.FCONST_0;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.FCONST_1;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.FCONST_2;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.FDIV;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.FLOAD;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.FLOAD_0;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.FLOAD_1;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.FLOAD_2;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.FLOAD_3;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.FMUL;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.FNEG;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.FREM;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.FRETURN;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.FSTORE;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.FSTORE_0;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.FSTORE_1;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.FSTORE_2;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.FSTORE_3;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.FSUB;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.GETFIELD;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.GETSTATIC;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.GOTO;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.GOTO_W;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.I2B;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.I2C;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.I2D;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.I2F;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.I2L;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.I2S;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.IADD;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.IALOAD;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.IAND;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.IASTORE;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ICONST_0;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ICONST_1;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ICONST_2;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ICONST_3;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ICONST_4;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ICONST_5;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ICONST_M1;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.IDIV;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.IFEQ;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.IFGE;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.IFGT;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.IFLE;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.IFLT;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.IFNE;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.IFNONNULL;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.IFNULL;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.IF_ACMPEQ;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.IF_ACMPNE;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.IF_ICMPEQ;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.IF_ICMPGE;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.IF_ICMPGT;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.IF_ICMPLE;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.IF_ICMPLT;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.IF_ICMPNE;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.IINC;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ILOAD;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ILOAD_0;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ILOAD_1;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ILOAD_2;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ILOAD_3;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.IMUL;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.INEG;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.INSTANCEOF;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.INVOKEDYNAMIC;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.INVOKEINTERFACE;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.INVOKESPECIAL;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.INVOKESTATIC;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.INVOKEVIRTUAL;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.IOR;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.IREM;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.IRETURN;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ISHL;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ISHR;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ISTORE;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ISTORE_0;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ISTORE_1;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ISTORE_2;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ISTORE_3;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ISUB;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.IUSHR;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.IXOR;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.JSR;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.JSR_W;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.L2D;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.L2F;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.L2I;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LADD;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LALOAD;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LAND;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LASTORE;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LCMP;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LCONST_0;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LCONST_1;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LDC;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LDC2_W;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LDC_W;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LDIV;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LLOAD;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LLOAD_0;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LLOAD_1;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LLOAD_2;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LLOAD_3;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LMUL;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LNEG;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LOOKUPSWITCH;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LOR;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LREM;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LRETURN;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LSHL;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LSHR;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LSTORE;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LSTORE_0;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LSTORE_1;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LSTORE_2;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LSTORE_3;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LSUB;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LUSHR;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LXOR;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.MONITORENTER;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.MONITOREXIT;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.MULTIANEWARRAY;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.NEW;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.NEWARRAY;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.NOP;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.POP;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.POP2;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.PUTFIELD;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.PUTSTATIC;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.QUICK;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.RET;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.RETURN;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.SALOAD;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.SASTORE;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.SIPUSH;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.SLIM_QUICK;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.SWAP;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.TABLESWITCH;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.WIDE;

import java.util.Arrays;
import java.util.Objects;

import com.oracle.truffle.espresso.classfile.ClassfileParser;
import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.classfile.ExceptionHandler;
import com.oracle.truffle.espresso.classfile.JavaKind;
import com.oracle.truffle.espresso.classfile.ParserConstantPool;
import com.oracle.truffle.espresso.classfile.ParserException;
import com.oracle.truffle.espresso.classfile.attributes.CodeAttribute;
import com.oracle.truffle.espresso.classfile.attributes.StackMapTableAttribute;
import com.oracle.truffle.espresso.classfile.bytecode.BytecodeLookupSwitch;
import com.oracle.truffle.espresso.classfile.bytecode.BytecodeStream;
import com.oracle.truffle.espresso.classfile.bytecode.BytecodeSwitch;
import com.oracle.truffle.espresso.classfile.bytecode.BytecodeTableSwitch;
import com.oracle.truffle.espresso.classfile.bytecode.Bytecodes;
import com.oracle.truffle.espresso.classfile.descriptors.Name;
import com.oracle.truffle.espresso.classfile.descriptors.ParserSymbols.ParserNames;
import com.oracle.truffle.espresso.classfile.descriptors.ParserSymbols.ParserTypes;
import com.oracle.truffle.espresso.classfile.descriptors.Signature;
import com.oracle.truffle.espresso.classfile.descriptors.SignatureSymbols;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.Type;
import com.oracle.truffle.espresso.classfile.descriptors.TypeSymbols;
import com.oracle.truffle.espresso.classfile.descriptors.Validation;
import com.oracle.truffle.espresso.classfile.descriptors.ValidationException;
import com.oracle.truffle.espresso.shared.meta.FieldAccess;
import com.oracle.truffle.espresso.shared.meta.KnownTypes;
import com.oracle.truffle.espresso.shared.meta.MemberAccess;
import com.oracle.truffle.espresso.shared.meta.MethodAccess;
import com.oracle.truffle.espresso.shared.meta.RuntimeAccess;
import com.oracle.truffle.espresso.shared.meta.TypeAccess;

/**
 * Should be a complete bytecode verifier. Given the version of the classfile from which the method
 * is taken, the type-checking or type-infering verifier is used.
 * <p>
 * Note that stack map tables are used only for classfile version >= 50, and even if stack maps are
 * given for lesser versions, they are ignored. No fallback for classfile v.50
 */
final class MethodVerifier<R extends RuntimeAccess<C, M, F>, C extends TypeAccess<C, M, F>, M extends MethodAccess<C, M, F>, F extends FieldAccess<C, M, F>>
                implements StackMapFrameParser.FrameBuilder<StackFrame<R, C, M, F>, MethodVerifier<R, C, M, F>> {
    final R runtime;

    final ParserConstantPool pool;

    // Class info
    private final C thisKlass;
    private final boolean useStackMaps;
    private final int majorVersion;

    // Method info
    private final Symbol<Name> methodName;
    private final boolean isStatic;
    private final Symbol<Type>[] sig;

    // Code info
    private final BytecodeStream code;
    private final int maxStack;
    private final int maxLocals;
    private final StackMapTableAttribute stackMapTableAttribute;
    private final ExceptionHandler[] exceptionHandlers;

    // Internal info
    private final int[] bciStates;
    private final StackFrame<R, C, M, F>[] stackFrames;
    private final byte[] handlerStatus;
    private boolean stackMapInitialized = false;

    // <init> method validation
    private boolean calledConstructor = false;

    // Instruction stack to visit
    private final WorkingQueue<R, C, M, F> queue = new WorkingQueue<>();

    Symbol<Type>[] getSig() {
        return sig;
    }

    boolean isStatic() {
        return isStatic;
    }

    int getMaxStack() {
        return maxStack;
    }

    int getMaxLocals() {
        return maxLocals;
    }

    C getThisKlass() {
        return thisKlass;
    }

    Symbol<Name> getMethodName() {
        return methodName;
    }

    private boolean earlierThan49() {
        return majorVersion < ClassfileParser.JAVA_1_5_VERSION;
    }

    private boolean earlierThan51() {
        return majorVersion < ClassfileParser.JAVA_7_VERSION;
    }

    private boolean version55OrLater() {
        return majorVersion >= ClassfileParser.JAVA_11_VERSION;
    }

    private boolean version51OrEarlier() {
        return majorVersion <= ClassfileParser.JAVA_7_VERSION;
    }

    private boolean version51OrLater() {
        return majorVersion >= ClassfileParser.JAVA_7_VERSION;
    }

    // Define all operands that can appear on the stack / locals.
    final PrimitiveOperand<R, C, M, F> intOp = new PrimitiveOperand<>(JavaKind.Int);
    final PrimitiveOperand<R, C, M, F> byteOp = new PrimitiveOperand<>(JavaKind.Byte);
    final PrimitiveOperand<R, C, M, F> charOp = new PrimitiveOperand<>(JavaKind.Char);
    final PrimitiveOperand<R, C, M, F> shortOp = new PrimitiveOperand<>(JavaKind.Short);
    final PrimitiveOperand<R, C, M, F> floatOp = new PrimitiveOperand<>(JavaKind.Float);
    final PrimitiveOperand<R, C, M, F> doubleOp = new PrimitiveOperand<>(JavaKind.Double);
    final PrimitiveOperand<R, C, M, F> longOp = new PrimitiveOperand<>(JavaKind.Long);
    final PrimitiveOperand<R, C, M, F> voidOp = new PrimitiveOperand<>(JavaKind.Void);
    final PrimitiveOperand<R, C, M, F> invalidOp = new PrimitiveOperand<>(JavaKind.Illegal);

    /*
     * Special handling:
     *
     * is same as Byte for java 8 or earlier, becomes its own operand for Java >= 9
     */
    final PrimitiveOperand<R, C, M, F> booleanOperand;

    /* Special operand used for BA{LOAD, STORE}. Should never be pushed to stack */
    final PrimitiveOperand<R, C, M, F> byteOrBooleanOp = new PrimitiveOperand<>(JavaKind.Byte) {
        @Override
        boolean compliesWith(Operand<R, C, M, F> other, MethodVerifier<R, C, M, F> methodVerifier) {
            return other.isTopOperand() || other.getKind() == JavaKind.Boolean || other.getKind() == JavaKind.Byte;
        }

        @Override
        Operand<R, C, M, F> mergeWith(Operand<R, C, M, F> other, MethodVerifier<R, C, M, F> methodVerifier) {
            assert !compliesWithInMerge(other, methodVerifier) : "mergeWith method should only be called for non-compatible operands.";
            if (other == this) {
                throw VerifierError.fatal("Invalid invariant: ByteOrBoolean operand in stack.");
            }
            if (other.isPrimitive()) {
                if (other == byteOp) {
                    return byteOp;
                }
                if (other.getKind() == JavaKind.Boolean) {
                    return other;
                }
            }
            return null;
        }

        @Override
        public PrimitiveOperand<R, C, M, F> toStack() {
            return byteOp;
        }
    };

    final Operand<R, C, M, F> nullOp = new Operand<>(JavaKind.Object) {
        @Override
        boolean compliesWith(Operand<R, C, M, F> other, MethodVerifier<R, C, M, F> methodVerifier) {
            return other.isTopOperand() || other.isReference();
        }

        @Override
        Operand<R, C, M, F> mergeWith(Operand<R, C, M, F> other, MethodVerifier<R, C, M, F> methodVerifier) {
            assert !compliesWithInMerge(other, methodVerifier) : "mergeWith method should only be called for non-compatible operands.";
            if (!other.isReference()) {
                return null;
            }
            return other;
        }

        @Override
        boolean isReference() {
            return true;
        }

        @Override
        boolean isNull() {
            return true;
        }

        @Override
        public String toString() {
            return "null";
        }

        @Override
        Operand<R, C, M, F> getComponent() {
            // jvms-4.7.6: aaload
            // We define the component type of null to be null.
            return this;
        }
    };

    final ReferenceOperand<R, C, M, F> jlObject;
    private final Operand<R, C, M, F> jlClass;
    private final Operand<R, C, M, F> jlString;
    private final Operand<R, C, M, F> jliMethodType;
    private final Operand<R, C, M, F> jliMethodHandle;
    private final Operand<R, C, M, F> jlThrowable;

    // Return type of the method
    private final Operand<R, C, M, F> returnOperand;
    // Declaring Klass of the method (already loaded)
    private final Operand<R, C, M, F> thisOperand;

    // Regular BCI states

    // Indicates that a particular BCI should never be reached by normal control flow (e.g.: the
    // bytecode of a WIDE instruction, or any BCI between two successive instructions)
    private static final int UNREACHABLE = 0;
    // Indicates a BCI that has not yet been reached by control flow. After verification, if such a
    // BCI still exists, it means that this BCI will never be reached during execution.
    private static final int UNSEEN = 1;
    // Indicates previous iteration of a verification successfully verified this particular BCI.
    // Further verification can therefore stop their execution if merging its state into the state
    // of this BCI is successful.
    private static final int DONE = 2;
    // Indicates that a particular BCI is the target of a jump, therefore requiring a stack map to
    // be provided fo this BCI.
    private static final int JUMP_TARGET = 4;

    // Exception handler target states
    private static final byte UNENCOUNTERED = 1;
    private static final byte NONVERIFIED = 2;
    private static final byte VERIFIED = 4;
    private static final byte CALLEDCONSTRUCTOR = 8;
    private static final byte NOCONSTRUCTORCALLED = 16;

    // JSR BCI states
    // This state is accompanied by the BCI of the RET instruction that caused it.
    // It is of the form (ret_bci << 16) | RETURNED_TO
    private static final byte RETURNED_TO = 64;

    private static final int RETURN_MASK = 0xFFFF0000;

    private static boolean checkStatus(int status, int toCheck) {
        return (status & toCheck) != 0;
    }

    private static int setStatus(int status, int toSet) {
        return (status & RETURN_MASK) | toSet;
    }

    private void checkAndSetReturnedTo(int target, int retBCI) {
        if ((bciStates[target] & RETURNED_TO) == RETURNED_TO) {
            verifyGuarantee((bciStates[target] >>> 16) == retBCI, "Multiple returns to single jsr ");
        }
        bciStates[target] = RETURNED_TO | (retBCI << 16);
    }

    /**
     * Construct the data structure to perform verification.
     *
     * @param codeAttribute the code attribute of the method
     * @param m the Espresso method
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private MethodVerifier(R runtime, CodeAttribute codeAttribute, M m, boolean useStackMaps) {
        this.runtime = runtime;
        // Extract info from codeAttribute
        this.code = new BytecodeStream(codeAttribute.getOriginalCode());
        this.maxStack = codeAttribute.getMaxStack();
        this.maxLocals = codeAttribute.getMaxLocals();
        this.bciStates = new int[code.endBCI()];
        this.stackFrames = new StackFrame[code.endBCI()];
        this.stackMapTableAttribute = codeAttribute.getStackMapFrame();
        /*
         * TODO(peterssen): Get major/minor version from the MethodVersion and not from the
         * declaring class to properly support class re-definition.
         */
        this.majorVersion = m.getDeclaringClass().getConstantPool().getMajorVersion();
        this.useStackMaps = useStackMaps;

        // Extract method info
        /*
         * TODO(peterssen): Get constant pool from the MethodVersion and not from the declaring
         * class to properly support class re-definition.
         */
        this.pool = m.getDeclaringClass().getConstantPool().getParserConstantPool();
        this.sig = m.getParsedSymbolicSignature(runtime.getSymbolPool());
        this.isStatic = m.isStatic();
        this.thisKlass = m.getDeclaringClass();
        this.methodName = m.getSymbolicName();
        this.exceptionHandlers = m.getSymbolicExceptionHandlers();

        this.handlerStatus = new byte[exceptionHandlers.length];
        Arrays.fill(handlerStatus, UNENCOUNTERED);

        booleanOperand = runtime.getJavaVersion().java9OrLater() ? new PrimitiveOperand<>(JavaKind.Boolean) : byteOp;

        KnownTypes<C, M, F> knownTypes = runtime.getKnownTypes();
        jlObject = new ReferenceOperand<>(knownTypes.java_lang_Object());
        jlClass = new ReferenceOperand<>(knownTypes.java_lang_Class());
        jlString = new ReferenceOperand<>(knownTypes.java_lang_String());
        jliMethodType = new ReferenceOperand<>(knownTypes.java_lang_invoke_MethodType());
        jliMethodHandle = new ReferenceOperand<>(knownTypes.java_lang_invoke_MethodHandle());
        jlThrowable = new ReferenceOperand<>(knownTypes.java_lang_Throwable());

        thisOperand = new ReferenceOperand<>(thisKlass);
        returnOperand = kindToOperand(SignatureSymbols.returnType(sig));
    }

    private MethodVerifier(R runtime, CodeAttribute codeAttribute, M m) {
        this(runtime, codeAttribute, m, useStackMaps(m.getDeclaringClass().getConstantPool().getMajorVersion()));
    }

    private static boolean useStackMaps(int majorVersion) {
        return majorVersion >= ClassfileParser.JAVA_6_VERSION;
    }

    static void formatGuarantee(boolean guarantee, String s) {
        if (!guarantee) {
            throw failFormat(s);
        }
    }

    static void verifyGuarantee(boolean guarantee, String s) {
        if (!guarantee) {
            throw failVerify(s);
        }
    }

    static RuntimeException failFormat(String s) {
        throw sneakyThrow(new VerificationException(s, VerificationException.Kind.ClassFormat));
    }

    static RuntimeException failFormatNoFallback(String s) {
        throw sneakyThrow(new VerificationException(s, VerificationException.Kind.ClassFormat, false));
    }

    static RuntimeException failVerify(String s) {
        throw sneakyThrow(new VerificationException(s, VerificationException.Kind.Verify));
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> RuntimeException sneakyThrow(Throwable ex) throws T {
        throw (T) ex;
    }

    /**
     * @param m the method to verify
     *
     * @throws VerificationException if method verification fails, with
     *             {@link VerificationException#kind()} alluding to why verification failed.
     */
    @SuppressWarnings("try")
    public static <R extends RuntimeAccess<C, M, F>, C extends TypeAccess<C, M, F>, M extends MethodAccess<C, M, F>, F extends FieldAccess<C, M, F>> void verify(R runtime, M m)
                    throws VerificationException {
        CodeAttribute codeAttribute = m.getCodeAttribute();
        assert !((m.isAbstract() || m.isNative()) && codeAttribute != null) : "Abstract method has code: " + m;
        if (codeAttribute == null) {
            if (m.isAbstract() || m.isNative()) {
                return;
            }
            throw failFormat("Concrete method has no code attribute: " + m);
        }
        MethodVerifier<R, C, M, F> verifier = new MethodVerifier<>(runtime, codeAttribute, m);
        try {
            verifier.verify();
        } catch (VerificationException e) {
            if (verifier.shouldFallBack(e)) {
                verifier = new MethodVerifier<>(runtime, codeAttribute, m, false);
                verifier.verify();
            } else {
                throw e;
            }
        } catch (ParserException.ClassFormatError e) {
            // There was an error during parsing e.g. invalid StackMapTable attribute.
            throw failFormat(e.getMessage());
        }
    }

    private boolean shouldFallBack(VerificationException e) {
        if (!e.allowFallback()) {
            return false;
        }
        if (majorVersion == ClassfileParser.JAVA_6_VERSION) {
            if (stackMapInitialized) {
                return e.kind() != VerificationException.Kind.ClassFormat;
            }
            return true;
        }
        return false;
    }

    private void initVerifier() {
        Arrays.fill(bciStates, UNREACHABLE);
        // Mark all reachable code
        int bci = 0;
        int opcode;
        while (bci < code.endBCI()) {
            opcode = code.currentBC(bci);
            verifyGuarantee(opcode < QUICK, "invalid bytecode: " + opcode);
            verifyEnoughBytecodes(opcode, bci);
            bciStates[bci] = setStatus(bciStates[bci], UNSEEN);
            bci = code.nextBCI(bci);
            // Check instruction has enough bytes after it
            verifyGuarantee(bci <= code.endBCI(), "Incomplete bytecode");
        }
        bci = 0;
        if (!useStackMaps) {
            // Mark all jump targets. We do not need to if method has stack frames, since all jump
            // targets have one stack frame declared in the attribute, and if it doesn't, we will
            // see it later.
            while (bci < code.endBCI()) {
                opcode = code.currentBC(bci);
                if (Bytecodes.isBranch(opcode)) {
                    int target = code.readBranchDest(bci);
                    validateBCI(target);
                    bciStates[target] = setStatus(bciStates[bci], JUMP_TARGET);
                }
                if (opcode == TABLESWITCH || opcode == LOOKUPSWITCH) {
                    initSwitch(bci, opcode);
                }
                bci = code.nextBCI(bci);
                if (opcode == JSR || opcode == JSR_W) {
                    bciStates[bci] = JUMP_TARGET;
                }
            }
        }
    }

    /**
     * Verifies that there is enough bytecodes left in the code array to compute the size of
     * variable-length instructions.
     */
    private void verifyEnoughBytecodes(int opcode, int curBCI) {
        switch (opcode) {
            case Bytecodes.TABLESWITCH:
                verifyGuarantee(BytecodeSwitch.getAlignedBci(curBCI)  //
                                + 8 /* To kigh key */
                                + 4 /* To read an int */ < code.endBCI(),
                                "SWITCH instruction does not have enough follow-up bytes to be valid.");
                return;
            case Bytecodes.LOOKUPSWITCH:
                verifyGuarantee(BytecodeSwitch.getAlignedBci(curBCI)  //
                                + 4 /* To number of cases */
                                + 4 /* To read an int */ < code.endBCI(),
                                "SWITCH instruction does not have enough follow-up bytes to be valid.");
                return;
            case Bytecodes.WIDE:
                verifyGuarantee(curBCI + 1 < code.endBCI(), "WIDE bytecode does not have a follow up instruction.");
                return;
            default:
        }
    }

    // Traverses the switch to mark jump targets. Also checks that lookup switch keys are sorted.
    private void initSwitch(int bci, int opCode) {
        if (opCode == LOOKUPSWITCH) {
            BytecodeLookupSwitch switchHelper = BytecodeLookupSwitch.INSTANCE;
            int low = 0;
            int high = switchHelper.numberOfCases(code, bci);
            verifyGuarantee(high >= 0, "number of keys in LOOKUPSWITCH less than 0");
            int oldKey = 0;
            boolean init = false;
            int target;
            for (int i = low; i < high; i++) {
                int newKey = switchHelper.keyAt(code, bci, i - low);
                if (init) {
                    verifyGuarantee(newKey > oldKey, "Unsorted keys in LOOKUPSWITCH");
                }
                init = true;
                oldKey = newKey;
                target = switchHelper.targetAt(code, bci, i - low);
                validateBCI(target);
                bciStates[target] = setStatus(bciStates[bci], JUMP_TARGET);
            }
            target = switchHelper.defaultTarget(code, bci);
            validateBCI(target);
            bciStates[target] = setStatus(bciStates[bci], JUMP_TARGET);
        } else if (opCode == TABLESWITCH) {
            BytecodeTableSwitch switchHelper = BytecodeTableSwitch.INSTANCE;
            int low = switchHelper.lowKey(code, bci);
            int high = switchHelper.highKey(code, bci);
            verifyGuarantee(low <= high, "low must be less than or equal to high in TABLESWITCH.");
            verifyGuarantee(high - low + 1 >= 0, "too many keys in tableswitch");
            int target;
            // if high == MAX_INT, i < high will always be true. This loop condition is to avoid
            // an infinite loop in this case.
            for (int i = low; i != high + 1; i++) {
                target = switchHelper.targetAt(code, bci, i - low);
                validateBCI(target);
                bciStates[target] = setStatus(bciStates[bci], JUMP_TARGET);
            }
            target = switchHelper.defaultTarget(code, bci);
            validateBCI(target);
        } else {
            throw VerifierError.fatal("Unrecognized switch bytecode: " + opCode);
        }
    }

    /**
     * Constructs the initial stack frame table given the stackMapTableAttribute. If no such
     * attribute exists, it still constructs the first implicit StackFrame, with the method
     * arguments in the locals and an empty stack.
     */
    private void initStackFrames() throws VerificationException {
        // First implicit stack frame.
        StackFrame<R, C, M, F> previous = new StackFrame<>(this);
        assert stackFrames.length > 0;
        int bci = 0;
        registerStackMapFrame(bci, previous);
        if (!useStackMaps || stackMapTableAttribute == null) {
            return;
        }
        if (stackMapTableAttribute == StackMapTableAttribute.EMPTY) {
            throw VerifierError.fatal("Class " + thisKlass.getJavaName() + " needs stack map verification, but the stack map table attribute was not filled.");
        }
        StackMapFrameParser.parse(this, stackMapTableAttribute, previous, computeLastLocal(previous));
    }

    private int computeLastLocal(StackFrame<R, C, M, F> previous) {
        int last = isStatic() ? 0 : 1;
        for (int i = 0; i < getSig().length - 1; i++) {
            Operand<?, ?, ?, ?> k = previous.locals[last++];
            if (k.isType2()) {
                last++;
            }
        }
        return last - 1;
    }

    /**
     * Registers a stack frame from the stack map frame parser to the given BCI.
     */
    public void registerStackMapFrame(int bci, StackFrame<R, C, M, F> frame) {
        validateFrameBCI(bci);
        stackFrames[bci] = frame;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public StackMapFrameParser.FrameAndLocalEffect<StackFrame<R, C, M, F>, MethodVerifier<R, C, M, F>> newFullFrame(VerificationTypeInfo[] stack, VerificationTypeInfo[] locals, int lastLocal) {
        OperandStack<R, C, M, F> fullStack = new OperandStack<>(this, maxStack);
        int stackPos = 0;
        for (VerificationTypeInfo vti : stack) {
            Operand<R, C, M, F> op = getOperandFromVerificationType(vti);
            stackPos += op.slots();
            formatGuarantee(stackPos <= maxStack, "Full frame entry has a bigger stack than maxStack.");
            fullStack.push(op);
        }
        Operand<R, C, M, F>[] newLocals = new Operand[maxLocals];
        Arrays.fill(newLocals, invalidOp);
        int pos = -1;
        for (VerificationTypeInfo vti : locals) {
            Operand<R, C, M, F> op = getOperandFromVerificationType(vti);
            setLocal(newLocals, op, ++pos, "Full frame entry in stack map has more locals than allowed.");
            boolean result;
            result = op.isType2();
            if (result) {
                setLocal(newLocals, invalidOp, ++pos, "Full frame entry in stack map has more locals than allowed.");
            }
        }
        return new StackMapFrameParser.FrameAndLocalEffect<>(new StackFrame<>(this, fullStack, newLocals), pos - lastLocal);
    }

    void setLocal(Operand<R, C, M, F>[] locals, Operand<R, C, M, F> op, int pos, String message) {
        formatGuarantee(pos >= 0 && pos < locals.length, message);
        locals[pos] = op;
    }

    Operand<R, C, M, F> getOperandFromVerificationType(VerificationTypeInfo vti) {
        // Note: JSR/RET is mutually exclusive with stack maps.
        switch (vti.getTag()) {
            case ITEM_Bogus:
                return invalidOp;
            case ITEM_Integer:
                return intOp;
            case ITEM_Float:
                return floatOp;
            case ITEM_Double:
                return doubleOp;
            case ITEM_Long:
                return longOp;
            case ITEM_Null:
                return nullOp;
            case ITEM_InitObject:
                return new UninitReferenceOperand<>(thisKlass);
            case ITEM_Object:
                assert vti.hasType();
                return spawnFromType(vti.getType(pool, getTypes(), code), vti.getConstantPoolOffset());
            case ITEM_NewObject:
                int newOffset = vti.getNewOffset();
                validateFormatBCI(newOffset);
                formatGuarantee(code.currentBC(newOffset) == NEW, "NewObject in stack map not referencing a NEW instruction! " + Bytecodes.nameOf(code.currentBC(newOffset)));
                assert vti.hasType();
                return new UninitReferenceOperand<>(vti.getType(pool, getTypes(), code), newOffset);
            default:
                throw VerifierError.fatal("Unrecognized VerificationTypeInfo: " + vti);
        }
    }

    private static class WorkingQueue<R extends RuntimeAccess<C, M, F>, C extends TypeAccess<C, M, F>, M extends MethodAccess<C, M, F>, F extends FieldAccess<C, M, F>> {
        QueueElement<R, C, M, F> first;
        QueueElement<R, C, M, F> last;

        WorkingQueue() {
            /* nop */
        }

        boolean isEmpty() {
            return first == null;
        }

        void push(int bci, QueueElement<R, C, M, F> elem) {
            QueueElement<R, C, M, F> current = lookup(bci);
            if (current == null) {
                if (first == null) {
                    first = elem;
                    last = first;
                } else {
                    last.next = elem;
                    elem.prev = last;
                    last = last.next;
                }
            } else {
                // if we are here, we already failed to merge.
                replace(current, elem);
            }
        }

        QueueElement<R, C, M, F> pop() {
            assert first != null;
            QueueElement<R, C, M, F> res = first;
            first = first.next;
            return res;
        }

        void replace(QueueElement<R, C, M, F> oldElem, QueueElement<R, C, M, F> newElem) {
            if (first == oldElem) {
                first = newElem;
            }
            if (last == oldElem) {
                last = newElem;
            }
            newElem.next = oldElem.next;
            newElem.prev = oldElem.prev;
            if (oldElem.prev != null) {
                oldElem.prev.next = newElem;
            }
            if (oldElem.next != null) {
                oldElem.next.prev = newElem;
            }
        }

        QueueElement<R, C, M, F> lookup(int bci) {
            QueueElement<R, C, M, F> current = first;
            while (current != null && current.bci != bci) {
                current = current.next;
            }
            return current;
        }
    }

    private static class QueueElement<R extends RuntimeAccess<C, M, F>, C extends TypeAccess<C, M, F>, M extends MethodAccess<C, M, F>, F extends FieldAccess<C, M, F>> {
        final int bci;
        final StackFrame<R, C, M, F> frame;
        final boolean constructorCalled;

        QueueElement<R, C, M, F> prev;
        QueueElement<R, C, M, F> next;

        QueueElement(int bci, StackFrame<R, C, M, F> frame, boolean calledConstructor) {
            this.bci = bci;
            this.frame = frame;
            this.constructorCalled = calledConstructor;
        }
    }

    /**
     * Performs the verification for the method associated with this MethodVerifier instance.
     */
    @SuppressWarnings("all") // Verification exception is thrown sneakily.
    private void verify() throws VerificationException {
        // At least one bytecode in the method.
        verifyGuarantee(code.endBCI() > 0, "Control flow falls through code end");

        // Marks BCIs in-between opcodes, and marks jump targets.
        initVerifier();

        // Extract the initial stack frame, and extract stack maps if available.
        initStackFrames();
        stackMapInitialized = true;

        // Check that BCIs in exception handlers are legal.
        validateExceptionHandlers();

        // Check that unconditional jumps have stack maps following them.
        validateUnconditionalJumps();

        // Perform verification following control-flow.
        verifyReachableCode();

        // Force verification of unreachable stack maps.
        verifyUnreachableStackMaps();

    }

    private void validateUnconditionalJumps() {
        if (useStackMaps) {
            int bci = 0;
            int nextBCI;
            while (bci < code.endBCI()) {
                nextBCI = code.nextBCI(bci);
                if (Bytecodes.isStop(code.currentBC(bci))) {
                    if (nextBCI < code.endBCI()) {
                        verifyGuarantee(stackFrames[nextBCI] != null, "Control flow stop does not have a stack map at next instruction!");
                    }
                }
                bci = nextBCI;
            }
        }
    }

    private void validateExceptionHandlers() {
        verifyGuarantee(exceptionHandlers.length == 0 || maxStack >= 1, "Method with exception handlers has a zero max stack value.");
        for (ExceptionHandler handler : exceptionHandlers) {
            validateFormatBCI(handler.getHandlerBCI());
            int startBCI = handler.getStartBCI();
            validateFormatBCI(startBCI);
            int endBCI = handler.getEndBCI();
            formatGuarantee(endBCI > startBCI, "End BCI of handler is before start BCI");
            formatGuarantee(endBCI >= 0, "negative branch target: " + endBCI);
            // handler end BCI can be equal to code end.
            formatGuarantee(endBCI <= code.endBCI(), "Control flow falls through code end");
            if (handler.catchTypeCPI() != 0) {
                C catchType = thisKlass.resolveClassConstantInPool(handler.catchTypeCPI());
                ReferenceOperand<R, C, M, F> catchTypeOperand = new ReferenceOperand<>(catchType);
                verifyGuarantee(catchTypeOperand.compliesWith(jlThrowable, this), "Illegal exception handler catch type: " + catchType);
            }

            if (endBCI != code.endBCI()) {
                formatGuarantee(bciStates[endBCI] != UNREACHABLE, "Jump to the middle of an instruction: " + endBCI);
            }
        }
    }

    private void processQueue() {
        Locals<R, C, M, F> locals;
        while (!queue.isEmpty()) {
            QueueElement<R, C, M, F> toVerify = queue.pop();
            calledConstructor = toVerify.constructorCalled;
            locals = toVerify.frame.extractLocals();
            locals.subRoutineModifications = toVerify.frame.subroutineModificationStack;
            startVerify(toVerify.bci, toVerify.frame.extractStack(maxStack), locals);
        }
    }

    private void verifyReachableCode() {
        // Perform verification of reachable executable code
        OperandStack<R, C, M, F> stack = new OperandStack<>(this, maxStack);
        Locals<R, C, M, F> locals = new Locals<>(this);
        startVerify(0, stack, locals);
        do {
            processQueue();
            verifyExceptionHandlers();
        } while (!queue.isEmpty());
    }

    private void verifyUnreachableStackMaps() {
        for (int stackBCI = 0; stackBCI < stackFrames.length; stackBCI++) {
            if (stackFrames[stackBCI] != null && checkStatus(bciStates[stackBCI], UNSEEN)) {
                queue.push(stackBCI, new QueueElement<>(stackBCI, stackFrames[stackBCI], true));
            }
        }
        while (!queue.isEmpty()) {
            processQueue();
            verifyExceptionHandlers();
        }
    }

    // Exception handler status management
    private static byte setStatus(byte oldStatus, byte newStatus) {
        return (byte) (newStatus | (oldStatus & (CALLEDCONSTRUCTOR | NOCONSTRUCTORCALLED)));
    }

    private static boolean isStatus(byte status, byte toCheck) {
        return (status & toCheck) != 0;
    }

    private static byte setConstructorStatus(byte oldStatus, byte constructorStatus) {
        // If there is a path to the handler that has not called a constructor, consider the handler
        // to have an uninitialized this.
        if ((oldStatus & NOCONSTRUCTORCALLED) > 0) {
            return oldStatus;
        }
        return (byte) (oldStatus | constructorStatus);
    }

    private static boolean isCalledConstructor(byte status) {
        return (status & CALLEDCONSTRUCTOR) > 0;
    }

    /**
     * Verifies actually reachable exception handlers, and the handlers they encounter if they were
     * not already verified.
     * <p>
     * This method reaches a fixed point. After execution, all handler will either have been
     * verified, or are completely unreachable.
     */
    private void verifyExceptionHandlers() {
        boolean redo;
        boolean updated;
        do {
            redo = false;
            updated = false;
            for (int i = 0; i < exceptionHandlers.length; i++) {
                ExceptionHandler handler = exceptionHandlers[i];
                if (isStatus(handlerStatus[i], NONVERIFIED)) {
                    updated = redo;
                    boolean constructorStatus = calledConstructor;
                    if (isCalledConstructor(handlerStatus[i])) {
                        calledConstructor = true;
                    }
                    verifyHandler(handler);
                    calledConstructor = constructorStatus;
                    handlerStatus[i] = setStatus(handlerStatus[i], VERIFIED);
                } else if (isStatus(handlerStatus[i], UNENCOUNTERED)) {
                    redo = true;
                }
            }
        } while (redo && updated);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void verifyHandler(ExceptionHandler handler) {
        int handlerBCI = handler.getHandlerBCI();
        Locals<R, C, M, F> locals;
        OperandStack<R, C, M, F> stack;
        StackFrame<R, C, M, F> frame = stackFrames[handlerBCI];
        if (frame == null) {
            // If there is no stack map when verifying a handler, all locals are illegal.
            Operand<R, C, M, F>[] registers = new Operand[maxLocals];
            Arrays.fill(registers, invalidOp);
            locals = new Locals<>(this, registers);
            stack = new OperandStack<>(this, maxStack);
            Symbol<Type> catchType = handler.getCatchType();
            stack.push(catchType == null ? jlThrowable : new ReferenceOperand<>(catchType, handler.catchTypeCPI()));
        } else {
            stack = frame.extractStack(maxStack);
            locals = frame.extractLocals();
        }
        startVerify(handlerBCI, stack, locals);
    }

    private void branch(int bci, OperandStack<R, C, M, F> stack, Locals<R, C, M, F> locals) {
        validateBCI(bci);
        // Try merge
        StackFrame<R, C, M, F> frame = mergeFrames(stack, locals, stackFrames[bci]);
        if (frame != stackFrames[bci] || !checkStatus(bciStates[bci], DONE)) {
            // merge failed or not yet verified bci. mark the bci as not yet verified since
            // state can change.
            bciStates[bci] = setStatus(bciStates[bci], JUMP_TARGET);
            stackFrames[bci] = frame;
            QueueElement<R, C, M, F> toPush = new QueueElement<>(bci, frame, calledConstructor);
            queue.push(bci, toPush);
        }
    }

    private void validateBCI(int bci) {
        verifyGuarantee(bci < code.endBCI(), "Control flow falls through code end");
        verifyGuarantee(bci >= 0, "negative branch target: " + bci);
        verifyGuarantee(bciStates[bci] != UNREACHABLE, "Jump to the middle of an instruction: " + bci);
    }

    private void validateFormatBCI(int bci) {
        formatGuarantee(bci < code.endBCI(), "Control flow falls through code end");
        formatGuarantee(bci >= 0, "negative branch target: " + bci);
        formatGuarantee(bciStates[bci] != UNREACHABLE, "Jump to the middle of an instruction: " + bci);
    }

    private void validateFrameBCI(int bci) {
        verifyGuarantee(bci < code.endBCI(), "StackFrame offset falls outside of method");
        verifyGuarantee(bci >= 0, "negative stack frame offset: " + bci);
        verifyGuarantee(bciStates[bci] != UNREACHABLE, "StackFrame offset falls to the middle of an instruction: " + bci);
    }

    /**
     * Performs the verification loop, starting from bci.
     * <p>
     * for each verified bytecode, three verifications are performed:
     * <p>
     * - Current stack and locals can merge into the stack frame corresponding to current bytecode.
     * If not, compute the merge if classfile version < 50
     * <p>
     * - Current locals can merge into all current bytecode's exception handlers.
     * <p>
     * - Stack and Locals<R, C, M, F> state are legal according to the bytecode.
     *
     * @param bci The bci at which we wish to start performing verification
     * @param seedStack the state of the stack at bci
     * @param seedLocals the state of the local variables at bci
     */
    private void startVerify(int bci, OperandStack<R, C, M, F> seedStack, Locals<R, C, M, F> seedLocals) {
        OperandStack<R, C, M, F> stack = seedStack;
        Locals<R, C, M, F> locals = seedLocals;
        int nextBCI = bci;
        int previousBCI;

        // Check if constructor was called prior to this branch.
        boolean constructorCalledStatus = calledConstructor;
        do {
            previousBCI = nextBCI;
            if (stackFrames[nextBCI] != null || checkStatus(bciStates[nextBCI], JUMP_TARGET)) {
                // Try merge
                StackFrame<R, C, M, F> frame = mergeFrames(stack, locals, stackFrames[nextBCI]);
                if (!(frame == stackFrames[nextBCI])) {
                    // merge failed, mark the bci as not yet verified as state changed
                    bciStates[nextBCI] = setStatus(bciStates[bci], JUMP_TARGET);
                    stackFrames[nextBCI] = frame;
                }
                // Always use the stack frame state
                stack = frame.extractStack(maxStack);
                locals = frame.extractLocals();
                // Propagate subroutine modifications (here, arrays are shared).
                locals.subRoutineModifications = seedLocals.subRoutineModifications;
            }
            // Return condition: a successful merge into an already verified branch target.
            if (stackFrames[nextBCI] != null && checkStatus(bciStates[nextBCI], DONE)) {
                // Reset constructor status.
                calledConstructor = constructorCalledStatus;
                return;
            }
            checkExceptionHandlers(nextBCI, locals);
            nextBCI = verifySafe(nextBCI, stack, locals);
            validateBCI(nextBCI);
        } while (previousBCI != nextBCI);
        // Reset constructor status.
        calledConstructor = constructorCalledStatus;
    }

    static void validateOrFailVerification(int cpi, ConstantPool pool) {
        try {
            pool.validateConstantAt(cpi);
        } catch (ValidationException e) {
            throw failVerify(e.getMessage());
        }
    }

    /**
     * Checks that an instruction can merge into all its handlers.
     */
    private void checkExceptionHandlers(int nextBCI, Locals<R, C, M, F> locals) {
        for (int i = 0; i < exceptionHandlers.length; i++) {
            ExceptionHandler handler = exceptionHandlers[i];
            if (handler.covers(nextBCI)) {
                OperandStack<R, C, M, F> stack = new OperandStack<>(this, 1);
                Symbol<Type> catchType = handler.getCatchType();
                stack.push(catchType == null ? jlThrowable : new ReferenceOperand<>(catchType, handler.catchTypeCPI()));
                StackFrame<R, C, M, F> oldFrame = stackFrames[handler.getHandlerBCI()];
                StackFrame<R, C, M, F> newFrame = mergeFrames(stack, locals, oldFrame);
                if (isStatus(handlerStatus[i], UNENCOUNTERED) || oldFrame != newFrame) {
                    handlerStatus[i] = setStatus(handlerStatus[i], NONVERIFIED);
                }
                if (calledConstructor) {
                    handlerStatus[i] = setConstructorStatus(handlerStatus[i], CALLEDCONSTRUCTOR);
                } else {
                    handlerStatus[i] = setConstructorStatus(handlerStatus[i], NOCONSTRUCTORCALLED);
                }
                stackFrames[handler.getHandlerBCI()] = newFrame;
            }
        }
    }

    private int verifySafe(int bci, OperandStack<R, C, M, F> stack, Locals<R, C, M, F> locals) {
        try {
            return verify(bci, stack, locals);
        } catch (IndexOutOfBoundsException e) {
            // At this point, the only appearance of an IndexOutOfBounds should be from stack and
            // locals access (bci bound checks are done beforehand).
            throw failVerify("Inconsistent Stack/Local access: " + e.getMessage() + ", in: " + thisKlass.getSymbolicType() + "." + methodName);
        }
    }

    private Operand<R, C, M, F> ldcFromTag(char cpi) {
        // @formatter:off
        // checkstyle: stop
        switch (pool.tagAt(cpi)) {
            case INTEGER:       return intOp;
            case FLOAT:         return floatOp;
            case LONG:          return longOp;
            case DOUBLE:        return doubleOp;
            case CLASS:         return jlClass;
            case STRING:        return jlString;
            case METHODHANDLE:
                formatGuarantee(version51OrLater(), "LDC for MethodHandleConstant in classfile version < 51");
                return jliMethodHandle;
            case METHODTYPE:
                formatGuarantee(version51OrLater(), "LDC for MethodType in classfile version < 51");
                return jliMethodType;
            case DYNAMIC:
                formatGuarantee(version55OrLater(), "LDC for Dynamic in classfile version < 55");
                return kindToOperand(pool.dynamicType(cpi));
            default:
                throw failVerify("invalid CP load: " + pool.tagAt(cpi));
        }
        // checkstyle: resume
        // @formatter:on
    }

    /**
     * Core of the verifier. Performs verification for a single bci, according (mostly) to the JVM
     * specs
     *
     * @param bci The bci of the opcode being verified
     * @param stack The current state of the stack at the point of verification
     * @param locals The current state of the local variables at the point of verification
     * @return The index of the next opcode to verify, or bci if there is no next opcode to verify
     *         (in case of a return bytecode, for example).
     */
    private int verify(int bci, OperandStack<R, C, M, F> stack, Locals<R, C, M, F> locals) {
        verifyGuarantee(bciStates[bci] != UNREACHABLE, "Jump to the middle of an instruction: " + bci);
        bciStates[bci] = setStatus(bciStates[bci], DONE);
        int curOpcode;
        curOpcode = code.opcode(bci);
        verifyGuarantee(curOpcode < SLIM_QUICK, "invalid bytecode: " + code.readUByte(bci));
        // @formatter:off
        // Checkstyle: stop

        // EXTREMELY dirty trick to handle WIDE. This is not supposed to be a loop ! (returns on
        // first iteration)
        // Executes a second time ONLY for wide instruction, with curOpcode being the opcode of the
        // widened instruction.
        wideEscape:
        while (true) {
            switch (curOpcode) {
                case NOP: break;
                case ACONST_NULL: stack.push(nullOp); break;

                case ICONST_M1:
                case ICONST_0:
                case ICONST_1:
                case ICONST_2:
                case ICONST_3:
                case ICONST_4:
                case ICONST_5: stack.pushInt(); break;

                case LCONST_0:
                case LCONST_1: stack.pushLong(); break;

                case FCONST_0:
                case FCONST_1:
                case FCONST_2: stack.pushFloat(); break;

                case DCONST_0:
                case DCONST_1: stack.pushDouble(); break;

                case BIPUSH: stack.pushInt(); break;
                case SIPUSH: stack.pushInt(); break;

                case LDC:
                case LDC_W: {
                    char cpi = code.readCPI(bci);
                    validateOrFailVerification(cpi, pool);
                    Operand<R, C, M, F> op = ldcFromTag(cpi);
                    verifyGuarantee(!op.isType2(), "Loading Long or Double with LDC or LDC_W, please use LDC2_W.");
                    if (earlierThan49()) {
                        verifyGuarantee(op == intOp || op == floatOp || op.getType() == jlString.getType(), "Loading non Int, Float or String with LDC in classfile version < 49.0");
                    }
                    stack.push(op);
                    break;
                }
                case LDC2_W: {
                    char cpi = code.readCPI(bci);
                    validateOrFailVerification(cpi, pool);
                    Operand<R, C, M, F> op = ldcFromTag(cpi);
                    verifyGuarantee(op.isType2(), "Loading non-Long or Double with LDC2_W, please use LDC or LDC_W.");
                    stack.push(op);
                    break;
                }

                case ILOAD: locals.load(code.readLocalIndex(bci), intOp);     stack.pushInt();    break;
                case LLOAD: locals.load(code.readLocalIndex(bci), longOp);    stack.pushLong();   break;
                case FLOAD: locals.load(code.readLocalIndex(bci), floatOp);   stack.pushFloat();  break;
                case DLOAD: locals.load(code.readLocalIndex(bci), doubleOp);  stack.pushDouble(); break;
                case ALOAD: stack.push(locals.loadRef(code.readLocalIndex(bci))); break;

                case ILOAD_0:
                case ILOAD_1:
                case ILOAD_2:
                case ILOAD_3: locals.load(curOpcode - ILOAD_0, intOp); stack.pushInt(); break;

                case LLOAD_0:
                case LLOAD_1:
                case LLOAD_2:
                case LLOAD_3: locals.load(curOpcode - LLOAD_0, longOp); stack.pushLong(); break;

                case FLOAD_0:
                case FLOAD_1:
                case FLOAD_2:
                case FLOAD_3: locals.load(curOpcode - FLOAD_0, floatOp); stack.pushFloat(); break;

                case DLOAD_0:
                case DLOAD_1:
                case DLOAD_2:
                case DLOAD_3: locals.load(curOpcode - DLOAD_0, doubleOp); stack.pushDouble(); break;

                case ALOAD_0:
                case ALOAD_1:
                case ALOAD_2:
                case ALOAD_3: stack.push(locals.loadRef(curOpcode - ALOAD_0)); break;


                case IALOAD: xaload(stack, intOp);    break;
                case LALOAD: xaload(stack, longOp);   break;
                case FALOAD: xaload(stack, floatOp);  break;
                case DALOAD: xaload(stack, doubleOp); break;

                case AALOAD: {
                    stack.popInt();
                    Operand<R, C, M, F> op = stack.popArray();
                    verifyGuarantee(op == nullOp || op.getComponent().isReference(), "Loading reference from " + op + " array.");
                    stack.push(op.getComponent());
                    break;
                }

                case BALOAD: xaload(stack, byteOrBooleanOp);  break;
                case CALOAD: xaload(stack, charOp);  break;
                case SALOAD: xaload(stack, shortOp); break;

                case ISTORE: stack.popInt();     locals.store(code.readLocalIndex(bci), intOp);    break;
                case LSTORE: stack.popLong();    locals.store(code.readLocalIndex(bci), longOp);   break;
                case FSTORE: stack.popFloat();   locals.store(code.readLocalIndex(bci), floatOp);  break;
                case DSTORE: stack.popDouble();  locals.store(code.readLocalIndex(bci), doubleOp); break;

                case ASTORE: locals.store(code.readLocalIndex(bci), stack.popObjOrRA()); break;

                case ISTORE_0:
                case ISTORE_1:
                case ISTORE_2:
                case ISTORE_3: stack.popInt(); locals.store(curOpcode - ISTORE_0, intOp); break;

                case LSTORE_0:
                case LSTORE_1:
                case LSTORE_2:
                case LSTORE_3: stack.popLong(); locals.store(curOpcode - LSTORE_0, longOp); break;

                case FSTORE_0:
                case FSTORE_1:
                case FSTORE_2:
                case FSTORE_3: stack.popFloat(); locals.store(curOpcode - FSTORE_0, floatOp); break;

                case DSTORE_0:
                case DSTORE_1:
                case DSTORE_2:
                case DSTORE_3: stack.popDouble(); locals.store(curOpcode - DSTORE_0, doubleOp); break;

                case ASTORE_0:
                case ASTORE_1:
                case ASTORE_2:
                case ASTORE_3: locals.store(curOpcode - ASTORE_0, stack.popObjOrRA()); break;

                case IASTORE: xastore(stack, intOp);      break;
                case LASTORE: xastore(stack, longOp);     break;
                case FASTORE: xastore(stack, floatOp);    break;
                case DASTORE: xastore(stack, doubleOp);   break;

                case AASTORE: {
                    Operand<R, C, M, F> toStore = stack.popRef();
                    stack.popInt();
                    Operand<R, C, M, F> array = stack.popArray();
                    verifyGuarantee(array == nullOp || array.getComponent().isReference(), "Trying to store " + toStore + " in " + array);
                    // Other checks are done at runtime
                    break;
                }

                case BASTORE: xastore(stack, byteOrBooleanOp); break;
                case CASTORE: xastore(stack, charOp); break;
                case SASTORE: xastore(stack, shortOp); break;

                case POP:   stack.pop();    break;
                case POP2:  stack.pop2();   break;

                case DUP:     stack.dup();      break;
                case DUP_X1:  stack.dupx1();    break;
                case DUP_X2:  stack.dupx2();    break;
                case DUP2:    stack.dup2();     break;
                case DUP2_X1: stack.dup2x1();   break;
                case DUP2_X2: stack.dup2x2();   break;
                case SWAP:    stack.swap();     break;

                case IADD: stack.popInt(); stack.popInt(); stack.pushInt(); break;
                case LADD: stack.popLong(); stack.popLong(); stack.pushLong(); break;
                case FADD: stack.popFloat(); stack.popFloat(); stack.pushFloat(); break;
                case DADD: stack.popDouble(); stack.popDouble(); stack.pushDouble(); break;

                case ISUB: stack.popInt(); stack.popInt(); stack.pushInt(); break;
                case LSUB: stack.popLong(); stack.popLong(); stack.pushLong(); break;
                case FSUB: stack.popFloat(); stack.popFloat(); stack.pushFloat(); break;
                case DSUB: stack.popDouble(); stack.popDouble(); stack.pushDouble(); break;

                case IMUL: stack.popInt(); stack.popInt(); stack.pushInt(); break;
                case LMUL: stack.popLong(); stack.popLong(); stack.pushLong(); break;
                case FMUL: stack.popFloat(); stack.popFloat(); stack.pushFloat(); break;
                case DMUL: stack.popDouble(); stack.popDouble(); stack.pushDouble(); break;

                case IDIV: stack.popInt(); stack.popInt(); stack.pushInt(); break;
                case LDIV: stack.popLong(); stack.popLong(); stack.pushLong(); break;
                case FDIV: stack.popFloat(); stack.popFloat(); stack.pushFloat(); break;
                case DDIV: stack.popDouble(); stack.popDouble(); stack.pushDouble(); break;

                case IREM: stack.popInt(); stack.popInt(); stack.pushInt(); break;
                case LREM: stack.popLong(); stack.popLong(); stack.pushLong(); break;
                case FREM: stack.popFloat(); stack.popFloat(); stack.pushFloat(); break;
                case DREM: stack.popDouble(); stack.popDouble(); stack.pushDouble(); break;

                case INEG: stack.popInt(); stack.pushInt(); break;
                case LNEG: stack.popLong(); stack.pushLong(); break;
                case FNEG: stack.popFloat(); stack.pushFloat(); break;
                case DNEG: stack.popDouble(); stack.pushDouble(); break;

                case ISHL: stack.popInt(); stack.popInt(); stack.pushInt(); break;
                case LSHL: stack.popInt(); stack.popLong(); stack.pushLong(); break;
                case ISHR: stack.popInt(); stack.popInt(); stack.pushInt(); break;
                case LSHR: stack.popInt(); stack.popLong(); stack.pushLong(); break;
                case IUSHR: stack.popInt(); stack.popInt(); stack.pushInt(); break;
                case LUSHR: stack.popInt(); stack.popLong(); stack.pushLong(); break;

                case IAND: stack.popInt(); stack.popInt(); stack.pushInt(); break;
                case LAND: stack.popLong(); stack.popLong(); stack.pushLong(); break;

                case IOR: stack.popInt(); stack.popInt(); stack.pushInt(); break;
                case LOR: stack.popLong(); stack.popLong(); stack.pushLong(); break;

                case IXOR: stack.popInt(); stack.popInt(); stack.pushInt(); break;
                case LXOR: stack.popLong(); stack.popLong(); stack.pushLong(); break;

                case IINC: locals.load(code.readLocalIndex(bci), intOp); break;

                case I2L: stack.popInt(); stack.pushLong(); break;
                case I2F: stack.popInt(); stack.pushFloat(); break;
                case I2D: stack.popInt(); stack.pushDouble(); break;

                case L2I: stack.popLong(); stack.pushInt(); break;
                case L2F: stack.popLong(); stack.pushFloat(); break;
                case L2D: stack.popLong(); stack.pushDouble(); break;

                case F2I: stack.popFloat(); stack.pushInt(); break;
                case F2L: stack.popFloat(); stack.pushLong(); break;
                case F2D: stack.popFloat(); stack.pushDouble(); break;

                case D2I: stack.popDouble(); stack.pushInt(); break;
                case D2L: stack.popDouble(); stack.pushLong(); break;
                case D2F: stack.popDouble(); stack.pushFloat(); break;

                case I2B: stack.popInt(); stack.pushInt(); break;
                case I2C: stack.popInt(); stack.pushInt(); break;
                case I2S: stack.popInt(); stack.pushInt(); break;

                case LCMP: stack.popLong(); stack.popLong(); stack.pushInt(); break;

                case FCMPL:
                case FCMPG: stack.popFloat(); stack.popFloat(); stack.pushInt(); break;

                case DCMPL:
                case DCMPG: stack.popDouble(); stack.popDouble(); stack.pushInt(); break;

                case IFEQ: // fall through
                case IFNE: // fall through
                case IFLT: // fall through
                case IFGE: // fall through
                case IFGT: // fall through
                case IFLE: stack.popInt(); branch(code.readBranchDest(bci), stack, locals); break;

                case IF_ICMPEQ: // fall through
                case IF_ICMPNE: // fall through
                case IF_ICMPLT: // fall through
                case IF_ICMPGE: // fall through
                case IF_ICMPGT: // fall through
                case IF_ICMPLE: stack.popInt(); stack.popInt(); branch(code.readBranchDest(bci), stack, locals); break;

                case IF_ACMPEQ: // fall through
                case IF_ACMPNE: stack.popRef(); stack.popRef(); branch(code.readBranchDest(bci), stack, locals); break;

                case GOTO:
                case GOTO_W: branch(code.readBranchDest(bci), stack, locals); return bci;

                case IFNULL: // fall through
                case IFNONNULL: stack.popRef(); branch(code.readBranchDest(bci), stack, locals); break;

                case JSR: // fall through
                case JSR_W: verifyJSR(bci, stack, locals); return bci;

                case RET: verifyRET(bci, stack, locals); return bci;

                case TABLESWITCH:  return verifyTableSwitch(bci, stack, locals);
                case LOOKUPSWITCH: return verifyLookupSwitch(bci, stack, locals);

                case IRETURN: {
                    stack.pop(intOp);
                    verifyGuarantee(returnOperand.getKind().isStackInt(), "Found an IRETURN when return type is " + returnOperand);
                    return bci;
                }
                case LRETURN: doReturn(stack, longOp);       return bci;
                case FRETURN: doReturn(stack, floatOp);      return bci;
                case DRETURN: doReturn(stack, doubleOp);     return bci;
                case ARETURN: stack.popRef(returnOperand); return bci;
                case RETURN:
                    verifyGuarantee(returnOperand == voidOp, "Encountered RETURN, but method return type is not void: " + returnOperand);
                    // Only j.l.Object.<init> can omit calling another initializer.
                    if (isInstanceInit(methodName) && thisKlass.getSymbolicType() != ParserTypes.java_lang_Object) {
                        verifyGuarantee(calledConstructor, "Did not call super() or this() in constructor " + thisKlass.getJavaName() + "." + methodName);
                    }
                    return bci;

                case GETSTATIC:
                case GETFIELD: verifyGetField(bci, stack, curOpcode); break;

                case PUTSTATIC:
                case PUTFIELD: verifyPutField(bci, stack, curOpcode); break;

                case INVOKEVIRTUAL:   verifyInvokeVirtual(bci, stack);         break;
                case INVOKESPECIAL:   verifyInvokeSpecial(bci, stack, locals); break;
                case INVOKESTATIC:    verifyInvokeStatic(bci, stack);          break;
                case INVOKEINTERFACE: verifyInvokeInterface(bci, stack);       break;

                case NEW:       verifyNew(bci, stack);               break;
                case NEWARRAY:  verifyNewPrimitiveArray(bci, stack); break;
                case ANEWARRAY: verifyNewObjectArray(bci, stack);    break;

                case ARRAYLENGTH: stack.popArray(); stack.pushInt(); break;

                case ATHROW: stack.popRef(jlThrowable); return bci;

                case CHECKCAST:  verifyCheckCast(bci, stack);  break;
                case INSTANCEOF: verifyInstanceOf(bci, stack); break;

                case MONITORENTER: stack.popRef(); break;
                case MONITOREXIT: stack.popRef(); break;

                case WIDE:
                    curOpcode = code.currentBC(bci);
                    verifyGuarantee(wideOpcodes(curOpcode), "invalid widened opcode: " + Bytecodes.nameOf(curOpcode));
                    continue wideEscape;

                case MULTIANEWARRAY: verifyMultiNewArray(bci, stack); break;

                case BREAKPOINT: break;

                case INVOKEDYNAMIC: verifyInvokeDynamic(bci, stack); break;

                case QUICK: break;
                case SLIM_QUICK: break;
                default:
            }
            return code.nextBCI(bci);
        }
        // Checkstyle: resume
        // @formatter:on
    }

    private void verifyInvokeDynamic(int bci, OperandStack<R, C, M, F> stack) {
        // Check padding
        verifyGuarantee(code.readByte(bci + 2) == 0 && code.readByte(bci + 3) == 0, "bytes 3 and 4 after invokedynamic must be 0.");
        int indyIndex = code.readCPI(bci);
        ConstantPool.Tag tag = tagAt(indyIndex);

        // Check CP validity
        verifyGuarantee(tag == ConstantPool.Tag.INVOKEDYNAMIC, "Invalid CP constant for INVOKEDYNAMIC: " + tag);
        validateOrFailVerification(indyIndex, pool);

        Symbol<Name> indyName = pool.invokeDynamicName(indyIndex);
        Symbol<Signature> indySignature = pool.invokeDynamicSignature(indyIndex);

        // Check invokedynamic does not call initializers
        verifyGuarantee(!isInstanceInit(indyName) && !isClassInit(indyName), "Invalid bootstrap method name: " + indyName);

        // Check and pop arguments
        Operand<R, C, M, F>[] parsedSig = getOperandSig(indySignature);
        assert parsedSig.length > 0 : "Empty descriptor for method";
        for (int i = parsedSig.length - 2; i >= 0; i--) {
            stack.pop(parsedSig[i]);
        }

        // push result
        Operand<R, C, M, F> returnKind = parsedSig[parsedSig.length - 1];
        if (returnKind != voidOp) {
            stack.push(returnKind);
        }
    }

    private Symbol<Type> getTypeFromPool(int classIndex, String s) {
        ConstantPool.Tag tag = tagAt(classIndex);
        verifyGuarantee(tag == CLASS, s + tag);
        validateOrFailVerification(classIndex, pool);
        Symbol<Name> className = pool.className(classIndex);
        assert Validation.validClassNameEntry(className);
        return runtime.getSymbolPool().getTypes().fromClassNameEntry(className);
    }

    private void verifyMultiNewArray(int bci, OperandStack<R, C, M, F> stack) {
        // Check CP validity
        Symbol<Type> type = getTypeFromPool(code.readCPI(bci), "Invalid CP constant for MULTIANEWARRAY: ");
        verifyGuarantee(TypeSymbols.isArray(type), "Class " + type + " for MULTINEWARRAY is not an array type.");

        // Check dimensions
        int dim = code.readUByte(bci + 3);
        verifyGuarantee(dim > 0, "Negative or 0 dimension for MULTIANEWARRAY: " + dim);
        verifyGuarantee(TypeSymbols.getArrayDimensions(type) >= dim, "Incompatible dimensions from constant pool: " + TypeSymbols.getArrayDimensions(type) + " and instruction: " + dim);

        // Pop lengths
        for (int i = 0; i < dim; i++) {
            stack.popInt();
        }

        // push result
        stack.push(kindToOperand(type));
    }

    private void verifyInstanceOf(int bci, OperandStack<R, C, M, F> stack) {
        // pop receiver
        stack.popRef();

        // Check CP validity
        Symbol<Type> type = getTypeFromPool(code.readCPI(bci), "Invalid CP constant for INSTANCEOF: ");
        verifyGuarantee(!TypeSymbols.isPrimitive(type), "Primitive type for INSTANCEOF: " + type);

        // push result
        stack.pushInt();
    }

    private void verifyCheckCast(int bci, OperandStack<R, C, M, F> stack) {
        // pop receiver
        Operand<R, C, M, F> stacKOp = stack.popRef();

        // Check CP validity
        int cpi = code.readCPI(bci);
        Symbol<Type> type = getTypeFromPool(cpi, "Invalid CP constant for CHECKCAST: ");
        verifyGuarantee(!TypeSymbols.isPrimitive(type), "Primitive type for CHECKCAST: " + type);

        // push new type
        Operand<R, C, M, F> castOp = spawnFromType(type, cpi);
        if (stacKOp.isUninit() && !castOp.isArrayType()) {
            stack.push(new UninitReferenceOperand<>(type, ((UninitReferenceOperand<R, C, M, F>) stacKOp).newBCI));
        } else {
            stack.push(castOp);
        }
    }

    private void verifyNewObjectArray(int bci, OperandStack<R, C, M, F> stack) {
        // Check CP validity
        int cpi = code.readCPI(bci);
        Symbol<Type> type = getTypeFromPool(cpi, "Invalid CP constant for ANEWARRAY: ");
        verifyGuarantee(!TypeSymbols.isPrimitive(type), "Primitive type for ANEWARRAY: " + type);

        // Pop length
        stack.popInt();

        // push result
        Operand<R, C, M, F> ref = spawnFromType(type, cpi);
        if (ref.isArrayType()) {
            stack.push(new ArrayOperand<>(ref.getElemental(), ref.getDimensions() + 1));
        } else {
            stack.push(new ArrayOperand<>(ref));
        }
    }

    private ConstantPool.Tag tagAt(int cpi) {
        verifyGuarantee(cpi < pool.length() && cpi > 0, "Invalid constant pool access at " + cpi + ", pool length: " + pool.length());
        return pool.tagAt(cpi);
    }

    private void verifyNewPrimitiveArray(int bci, OperandStack<R, C, M, F> stack) {
        byte jvmType = code.readByte(bci);
        verifyGuarantee(jvmType >= 4 && jvmType <= 11, "invalid jvmPrimitiveType for NEWARRAY: " + jvmType);
        stack.popInt();
        stack.push(fromJVMType(jvmType));
    }

    private void verifyNew(int bci, OperandStack<R, C, M, F> stack) {
        // Check CP validity
        Symbol<Type> type = getTypeFromPool(code.readCPI(bci), "Invalid CP constant for NEW: ");
        verifyGuarantee(!TypeSymbols.isPrimitive(type) && !TypeSymbols.isArray(type), "use NEWARRAY for creating array or primitive type: " + type);

        // push result
        Operand<R, C, M, F> op = new UninitReferenceOperand<>(type, bci);
        stack.push(op);
    }

    private void verifyPutField(int bci, OperandStack<R, C, M, F> stack, int curOpcode) {
        // Check CP validity
        int fieldIndex = code.readCPI(bci);
        ConstantPool.Tag tag = tagAt(fieldIndex);
        verifyGuarantee(tag == ConstantPool.Tag.FIELD_REF, "Invalid CP constant for PUTFIELD: " + tag);
        validateOrFailVerification(fieldIndex, pool);

        // Obtain field info
        Symbol<Type> fieldType = pool.fieldType(fieldIndex);
        assert Validation.validFieldDescriptor(fieldType);
        Operand<R, C, M, F> toPut = stack.pop(kindToOperand(fieldType));

        checkInit(toPut);
        if (curOpcode == PUTFIELD) {
            // Pop and check verifier
            Symbol<Name> holderClassName = pool.memberClassName(fieldIndex);
            assert Validation.validClassNameEntry(holderClassName);
            Symbol<Type> fieldHolderType = runtime.getSymbolPool().getTypes().fromClassNameEntry(holderClassName);
            Operand<R, C, M, F> fieldHolder = kindToOperand(fieldHolderType);
            Operand<R, C, M, F> receiver = checkInitAccess(stack.popRef(fieldHolder), fieldHolder);
            verifyGuarantee(!receiver.isArrayType(), "Trying to access field of an array type: " + receiver);
            if (!receiver.isUninitThis()) {
                checkProtectedMember(receiver, fieldHolderType, fieldIndex, false);
            }
        }
    }

    private void verifyGetField(int bci, OperandStack<R, C, M, F> stack, int curOpcode) {
        // Check CP validity
        int fieldIndex = code.readCPI(bci);
        ConstantPool.Tag tag = tagAt(fieldIndex);
        verifyGuarantee(tag == ConstantPool.Tag.FIELD_REF, "Invalid CP constant for GETFIELD: " + tag);
        validateOrFailVerification(fieldIndex, pool);

        // Obtain field info
        Symbol<Type> fieldType = pool.fieldType(fieldIndex);
        assert Validation.validFieldDescriptor(fieldType);
        Symbol<Type> type = fieldType;
        if (curOpcode == GETFIELD) {
            // Pop and check receiver
            Symbol<Name> holderClassName = pool.memberClassName(fieldIndex);
            assert Validation.validClassNameEntry(holderClassName);
            Symbol<Type> fieldHolderType = runtime.getSymbolPool().getTypes().fromClassNameEntry(holderClassName);
            Operand<R, C, M, F> fieldHolder = kindToOperand(fieldHolderType);
            Operand<R, C, M, F> receiver = checkInitAccess(stack.popRef(fieldHolder), fieldHolder);
            checkProtectedMember(receiver, fieldHolderType, fieldIndex, false);
            verifyGuarantee(!receiver.isArrayType(), "Trying to access field of an array type: " + receiver);
        }

        // push result
        Operand<R, C, M, F> op = kindToOperand(type);
        stack.push(op);
    }

    private int verifyLookupSwitch(int bci, OperandStack<R, C, M, F> stack, Locals<R, C, M, F> locals) {
        stack.popInt();
        BytecodeLookupSwitch switchHelper = BytecodeLookupSwitch.INSTANCE;
        // Padding checks
        if (version51OrEarlier()) {
            for (int j = bci + 1; j < BytecodeSwitch.getAlignedBci(bci); j++) {
                verifyGuarantee(code.readUByte(j) == 0, "non-zero padding for LOOKUPSWITCH");
            }
        }
        final int low = 0;
        int high = switchHelper.numberOfCases(code, bci) - 1;
        int previousKey = 0;
        if (high > 0) {
            previousKey = switchHelper.keyAt(code, bci, low);
        }

        // Verify all branches
        for (int i = low; i <= high; i++) {
            int thisKey = switchHelper.keyAt(code, bci, i);
            if (i > low) {
                verifyGuarantee(thisKey > previousKey, "Unsorted keys in LookupSwitch");
            }
            branch(bci + switchHelper.offsetAt(code, bci, i), stack, locals);
            previousKey = thisKey;
        }

        // Verify default branch
        return switchHelper.defaultTarget(code, bci);
    }

    private int verifyTableSwitch(int bci, OperandStack<R, C, M, F> stack, Locals<R, C, M, F> locals) {
        stack.popInt();
        BytecodeTableSwitch switchHelper = BytecodeTableSwitch.INSTANCE;
        // Padding checks
        if (version51OrEarlier()) {
            for (int j = bci + 1; j < BytecodeSwitch.getAlignedBci(bci); j++) {
                verifyGuarantee(code.readUByte(j) == 0, "non-zero padding for TABLESWITCH");
            }
        }
        int low = switchHelper.lowKey(code, bci);
        int high = switchHelper.highKey(code, bci);

        // Verify all branches
        for (int i = low; i != high + 1; i++) {
            branch(switchHelper.targetAt(code, bci, i - low), stack, locals);
        }

        // Verify default branch
        return switchHelper.defaultTarget(code, bci);
    }

    private void verifyJSR(int bci, OperandStack<R, C, M, F> stack, Locals<R, C, M, F> locals) {
        verifyGuarantee(earlierThan51(), "JSR/RET bytecode in version >= 51");
        if (stackFrames[bci] == null) {
            stackFrames[bci] = spawnStackFrame(stack, locals);
        }
        // Push bit vector
        int targetBCI = code.readBranchDest(bci);
        stack.push(new ReturnAddressOperand<>(bci, targetBCI));
        locals.subRoutineModifications = new SubroutineModificationStack(locals.subRoutineModifications, new boolean[maxLocals], bci);
        branch(targetBCI, stack, locals);
        bciStates[bci] = setStatus(bciStates[bci], DONE);
    }

    private void verifyRET(int bci, OperandStack<R, C, M, F> stack, Locals<R, C, M, F> locals) {
        verifyGuarantee(earlierThan51(), "JSR/RET bytecode in version >= 51");
        int pos = 0;
        ReturnAddressOperand<R, C, M, F> ra = locals.loadReturnAddress(code.readLocalIndex(bci));
        ReturnAddressOperand<R, C, M, F> prev = null;
        while (pos < ra.targetBCIs.size()) {
            prev = ra;
            int target = ra.targetBCIs.get(pos++);
            checkAndSetReturnedTo(target, bci);
            Locals<R, C, M, F> toMerge = getSubroutineReturnLocals(target, locals);
            branch(code.nextBCI(target), stack, toMerge);
            // Sanity check: branching did not overwrite the return address being
            // verified
            ra = locals.loadReturnAddress(code.readLocalIndex(bci));
            if (ra != prev) {
                pos = 0;
            }
        }
    }

    private void validateMethodRefIndex(int methodIndex) {
        ConstantPool.Tag tag = tagAt(methodIndex);
        verifyGuarantee(tag == ConstantPool.Tag.METHOD_REF || tag == INTERFACE_METHOD_REF, "Invalid CP constant for a MethodRef: " + tag);
        validateOrFailVerification(methodIndex, pool);
    }

    private static boolean isClassInit(Symbol<Name> calledMethodName) {
        return ParserNames._clinit_.equals(calledMethodName);
    }

    private static boolean isInstanceInit(Symbol<Name> calledMethodName) {
        return ParserNames._init_.equals(calledMethodName);
    }

    private Operand<R, C, M, F> popSignatureGetReturnOp(OperandStack<R, C, M, F> stack, int methodIndex) {
        Symbol<Signature> calledMethodSignature = pool.methodSignature(methodIndex);
        Operand<R, C, M, F>[] parsedSig = getOperandSig(calledMethodSignature);

        assert parsedSig.length > 0 : "Method ref with no return value !";

        // Pop arguments
        for (int i = parsedSig.length - 2; i >= 0; i--) {
            stack.pop(parsedSig[i]);
        }
        return parsedSig[parsedSig.length - 1];
    }

    private void verifyInvokeInterface(int bci, OperandStack<R, C, M, F> stack) {
        // Check padding.
        verifyGuarantee(code.readUByte(bci + 4) == 0, "4th byte after INVOKEINTERFACE must be 0.");

        // Check CP validity
        int methodIndex = code.readCPI(bci);
        validateMethodRefIndex(methodIndex);

        // Checks versioning
        Symbol<Name> calledMethodName = pool.methodName(methodIndex);

        // Check guest is not invoking <clinit>
        verifyGuarantee(!isClassInit(calledMethodName), "Invocation of class initializer!");

        // Only INVOKESPECIAL can call <init>
        verifyGuarantee(!isInstanceInit(calledMethodName), "Invocation of instance initializer with opcode other than INVOKESPECIAL");

        Symbol<Signature> calledMethodSignature = pool.methodSignature(methodIndex);
        Operand<R, C, M, F>[] parsedSig = getOperandSig(calledMethodSignature);

        // Check signature is well formed.
        assert parsedSig.length > 0 : "Method ref with no return value !";

        // Pop arguments
        // Check signature conforms with count argument
        int count = code.readUByte(bci + 3);
        verifyGuarantee(count > 0, "Invalid count argument for INVOKEINTERFACE: " + count);
        int descCount = 1; // Has a receiver.
        for (int i = parsedSig.length - 2; i >= 0; i--) {
            descCount++;
            if (parsedSig[i].isType2()) {
                descCount++;
            }
            stack.pop(parsedSig[i]);
        }
        verifyGuarantee(count == descCount, "Inconsistent redundant argument count for INVOKEINTERFACE.");

        Symbol<Name> holderClassName = pool.memberClassName(methodIndex);
        assert Validation.validClassNameEntry(holderClassName);
        Symbol<Type> methodHolder = runtime.getSymbolPool().getTypes().fromClassNameEntry(holderClassName);

        Operand<R, C, M, F> methodHolderOp = kindToOperand(methodHolder);

        checkInit(stack.popRef(methodHolderOp));

        Operand<R, C, M, F> returnOp = parsedSig[parsedSig.length - 1];
        if (!(returnOp == voidOp)) {
            stack.push(returnOp);
        }
    }

    private void verifyInvokeStatic(int bci, OperandStack<R, C, M, F> stack) {
        // Check CP validity
        int methodIndex = code.readCPI(bci);
        validateMethodRefIndex(methodIndex);

        // Checks versioning
        if (version51OrEarlier()) {
            verifyGuarantee(pool.tagAt(methodIndex) != INTERFACE_METHOD_REF, "invokeStatic refers to an interface method with classfile version " + majorVersion);
        }
        Symbol<Name> calledMethodName = pool.methodName(methodIndex);

        // Check guest is not invoking <clinit>
        verifyGuarantee(!isClassInit(calledMethodName), "Invocation of class initializer!");

        // Only INVOKESPECIAL can call <init>
        verifyGuarantee(!isInstanceInit(calledMethodName), "Invocation of instance initializer with opcode other than INVOKESPECIAL");

        Operand<R, C, M, F> returnOp = popSignatureGetReturnOp(stack, methodIndex);
        Symbol<Name> holderClassName = pool.memberClassName(methodIndex);
        assert Validation.validClassNameEntry(holderClassName);

        if (!(returnOp == voidOp)) {
            stack.push(returnOp);
        }
    }

    private void verifyInvokeSpecial(int bci, OperandStack<R, C, M, F> stack, Locals<R, C, M, F> locals) {
        // Check CP validity
        int methodIndex = code.readCPI(bci);
        validateMethodRefIndex(methodIndex);

        // Checks versioning
        if (version51OrEarlier()) {
            verifyGuarantee(pool.tagAt(methodIndex) != INTERFACE_METHOD_REF, "invokeSpecial refers to an interface method with classfile version " + majorVersion);
        }
        Symbol<Name> calledMethodName = pool.methodName(methodIndex);

        // Check guest is not invoking <clinit>
        verifyGuarantee(!isClassInit(calledMethodName), "Invocation of class initializer!");

        Operand<R, C, M, F> returnOp = popSignatureGetReturnOp(stack, methodIndex);

        Symbol<Name> holderClassName = pool.memberClassName(methodIndex);
        assert Validation.validClassNameEntry(holderClassName);
        Symbol<Type> methodHolder = runtime.getSymbolPool().getTypes().fromClassNameEntry(holderClassName);
        Operand<R, C, M, F> methodHolderOp = kindToOperand(methodHolder);

        if (isInstanceInit(calledMethodName)) {
            UninitReferenceOperand<R, C, M, F> toInit = (UninitReferenceOperand<R, C, M, F>) stack.popUninitRef(methodHolderOp);
            if (toInit.isUninitThis()) {
                verifyGuarantee(ParserNames._init_.equals(methodName), "Encountered UninitializedThis outside of Constructor: " + toInit);
                boolean isValidInitThis = toInit.getType() == methodHolder ||
                                // Here, the superKlass cannot be null, as the j.l.Object case would
                                // have been handled by the previous check.
                                toInit.getKlass(this).getSuperClass().getSymbolicType() == methodHolder;
                verifyGuarantee(isValidInitThis, "<init> method must call this.<init> or super.<init>");
                calledConstructor = true;
            } else {
                verifyGuarantee(code.opcode(toInit.newBCI) == NEW, "There is no NEW bytecode at bci: " + toInit.newBCI);
                // according to JCK's "vm/classfmt/ins/instr_03608m1" :
                //
                // Calling parent's initializer of uninitialized new object is
                // illegal, but serialization does just that.
                //
                // In particular, it generates a class whose method executes NEW
                // j.l.Number (an abstract class), then calls j.l.Object.<init> on
                // it.
                //
                // A workaround would be to check if the underlying class is
                // abstract/interface, but that would feel really silly...
                verifyGuarantee(toInit.getType() == methodHolder, "Calling wrong initializer for a new object.");
            }
            Operand<R, C, M, F> stackOp = stack.initUninit(toInit);
            locals.initUninit(toInit, stackOp);

            checkProtectedMember(stackOp, methodHolder, methodIndex, true);
        } else {
            verifyGuarantee(checkMethodSpecialAccess(methodHolderOp), "invokespecial must specify a method in this class or a super class");
            Operand<R, C, M, F> stackOp = checkInit(stack.popRef(methodHolderOp));
            /*
             * 4.10.1.9.invokespecial:
             *
             * invokespecial, for other than an instance initialization method, must name a method
             * in the current class/interface or a superclass / superinterface.
             */
            verifyGuarantee(checkReceiverSpecialAccess(stackOp), "Invalid use of INVOKESPECIAL");
        }
        if (!(returnOp == voidOp)) {
            stack.push(returnOp);
        }
    }

    private void verifyInvokeVirtual(int bci, OperandStack<R, C, M, F> stack) {
        // Check CP validity
        int methodIndex = code.readCPI(bci);
        validateMethodRefIndex(methodIndex);

        Symbol<Name> calledMethodName = pool.methodName(methodIndex);

        // Check guest is not invoking <clinit>
        verifyGuarantee(!isClassInit(calledMethodName), "Invocation of class initializer!");

        // Only INVOKESPECIAL can call <init>
        verifyGuarantee(!isInstanceInit(calledMethodName), "Invocation of instance initializer with opcode other than INVOKESPECIAL");

        Operand<R, C, M, F> returnOp = popSignatureGetReturnOp(stack, methodIndex);

        Symbol<Name> holderClassName = pool.memberClassName(methodIndex);
        assert Validation.validClassNameEntry(holderClassName);
        Symbol<Type> methodHolder = runtime.getSymbolPool().getTypes().fromClassNameEntry(holderClassName);

        Operand<R, C, M, F> methodHolderOp = kindToOperand(methodHolder);
        Operand<R, C, M, F> stackOp = checkInit(stack.popRef(methodHolderOp));

        // Perform protected method access checks
        checkProtectedMember(stackOp, methodHolder, methodIndex, true);

        if (!(returnOp == voidOp)) {
            stack.push(returnOp);
        }
    }

    /**
     * This is the delicate part for dealing with JSR/RET.
     * <p>
     * The main idea is that multiple JSR can lead to a single RET. It is therefore necessary that
     * the state of the subroutine is the merging from all JSR that lead to it. However, the
     * opposite is not true: We do not want to merge the subroutine state directly into the state of
     * the caller. The semantics of JSR say that execution should resume almost as if the subroutine
     * did not happen.
     * <p>
     * In practice, that means that variables that were untouched by the subroutine should be used
     * as-is when returning.
     * <p>
     * Thus, we need to keep track of the variables a subroutine actually modifies, which
     * corresponds to the subRoutineModifications field in the locals. It is similar to a bit array.
     * If bit at index i is set, that means that the corresponding subroutine modified local
     * variable number i.
     * <p>
     * The problem is, what if a subroutine calls another one (In case of multiply nested finally
     * clauses). In order to take that into account, the data structure used is a stack of bit
     * arrays. Starting a subroutine pushes a new clean bit array on the stack. When returning, the
     * bit array is popped (call it b1) to obtain the wanted local variables, and once done, if
     * there is another bit array on the stack (call it b2), merge the two of them (ie: for each
     * raised bit in b1, raise the corresponding one in b2).
     * <p>
     * If the returns jumps over multiple JSRs, pop the stack accordingly. For example:
     *
     * <pre>
     * 0: jsr 4
     * 3: return
     * 4: astore_0
     * 5: jsr 8
     * 8: astore_1
     * 9: ret 0
     * </pre>
     *
     * @param target BCI of the JSR instruction we will merge into
     * @param locals The state of the local variables at the time of the RET instruction
     * @return the local variables that will be merged into the state at target.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Locals<R, C, M, F> getSubroutineReturnLocals(int target, Locals<R, C, M, F> locals) {
        SubroutineModificationStack subRoutineModifications = locals.subRoutineModifications;
        verifyGuarantee(subRoutineModifications != null, "RET outside of a subroutine");
        boolean[] subroutineBitArray = subRoutineModifications.subRoutineModifications;
        SubroutineModificationStack nested = subRoutineModifications.next;

        Locals<R, C, M, F> jsrLocals = stackFrames[target].extractLocals();
        Operand<R, C, M, F>[] registers = new Operand[maxLocals];

        boolean nestedRet = jsrLocals.subRoutineModifications != null;

        int depthRoutine = locals.subRoutineModifications.depth();
        int depthRet = nestedRet ? jsrLocals.subRoutineModifications.depth() : 0;

        verifyGuarantee(depthRet < depthRoutine, "RET increases subroutine depth.");

        while (depthRoutine - depthRet > 1) {
            for (int i = 0; i < subroutineBitArray.length; i++) {
                if (subroutineBitArray[i]) {
                    nested.subRoutineModifications[i] = true;
                } else if (nested.subRoutineModifications[i]) {
                    subroutineBitArray[i] = true;
                }
            }
            nested = nested.next;
            depthRoutine--;
        }

        for (int i = 0; i < maxLocals; i++) {
            if (subroutineBitArray[i]) {
                registers[i] = locals.registers[i];
                if (nested != null) {
                    nested.subRoutineModifications[i] = true;
                }
            } else {
                registers[i] = jsrLocals.registers[i];
            }
        }

        Locals<R, C, M, F> res = new Locals<>(this, registers);
        res.subRoutineModifications = nested;
        return res;
    }

    // Various access checks

    private boolean checkReceiverSpecialAccess(Operand<R, C, M, F> stackOp) {
        return stackOp.compliesWith(thisOperand, this) || isMagicAccessor() || checkReceiverHostAccess(stackOp);
    }

    private boolean checkReceiverHostAccess(Operand<R, C, M, F> stackOp) {
        if (thisKlass.getHostType() != null) {
            return thisKlass.getHostType().isAssignableFrom(stackOp.getKlass(this));
        }
        return false;
    }

    private boolean checkMethodSpecialAccess(Operand<R, C, M, F> methodHolder) {
        return thisOperand.compliesWith(methodHolder, this) || isMagicAccessor() || checkHostAccess(methodHolder);
    }

    private boolean isMagicAccessor() {
        return thisKlass.isMagicAccessor();
    }

    /**
     * Anonymous classes defined on the fly can call protected members of other classes that are not
     * in their hierarchy. Use their host class to check access.
     */
    private boolean checkHostAccess(Operand<R, C, M, F> methodHolder) {
        if (thisKlass.getHostType() != null) {
            return methodHolder.getKlass(this).isAssignableFrom(thisKlass.getHostType());
        }
        return false;
    }

    // Helper methods

    private void checkProtectedMember(Operand<R, C, M, F> stackOp, Symbol<Type> holderType, int memberIndex, boolean method) {
        /*
         * 4.10.1.8.
         *
         * If the name of a class is not the name of any superclass, it cannot be a superclass, and
         * so it can safely be ignored.
         */
        if (stackOp.getType() == thisKlass.getSymbolicType()) {
            return;
        }
        /*
         * If the MemberClassName is the same as the name of a superclass, the class being resolved
         * may indeed be a superclass. In this case, if no superclass named MemberClassName in a
         * different run-time package has a protected member named MemberName with descriptor
         * MemberDescriptor, the protected check does not apply.
         */
        C superKlass = thisKlass.getSuperClass();
        while (superKlass != null) {
            if (superKlass.getSymbolicType() == holderType) {
                Operand<R, C, M, F> holderOp = kindToOperand(holderType);
                MemberAccess<C, M, F> member;
                if (method) {
                    /* Non-failing method lookup. */
                    Symbol<Name> name = pool.methodName(memberIndex);
                    Symbol<Signature> methodSignature = pool.methodSignature(memberIndex);
                    member = holderOp.getKlass(this).lookupMethod(name, methodSignature);
                } else {
                    /* Non-failing field lookup. */
                    Symbol<Name> fieldName = pool.fieldName(memberIndex);
                    Symbol<Type> fieldType = pool.fieldType(memberIndex);
                    member = holderOp.getKlass(this).lookupField(fieldName, fieldType);
                }
                /*
                 * If there does exist a protected superclass member in a different run-time
                 * package, then load MemberClassName; if the member in question is not protected,
                 * the check does not apply. (Using a superclass member that is not protected is
                 * trivially correct.)
                 */
                if (member == null || !member.isProtected()) {
                    return;
                }
                if (!thisKlass.getSymbolicRuntimePackage().contentEquals(TypeSymbols.getRuntimePackage(holderType))) {
                    if (method) {
                        if (stackOp.isArrayType() && ParserTypes.java_lang_Object.equals(holderType) && ParserNames.clone.equals(member.getSymbolicName())) {
                            // Special case: Arrays pretend to implement Object.clone().
                            return;
                        }
                    }
                    verifyGuarantee(stackOp.compliesWith(thisOperand, this), "Illegal protected field access");
                }
                return;
            }
            superKlass = superKlass.getSuperClass();
        }
    }

    // various helper methods

    private void doReturn(OperandStack<R, C, M, F> stack, Operand<R, C, M, F> toReturn) {
        Operand<R, C, M, F> op = stack.pop(toReturn);
        verifyGuarantee(op.compliesWith(returnOperand, this), "Invalid return: " + op + ", expected: " + returnOperand);
    }

    /**
     * Checks that a given operand is initialized when accessing fields/methods.
     */
    private Operand<R, C, M, F> checkInitAccess(Operand<R, C, M, F> op, Operand<R, C, M, F> holder) {
        if (op.isUninit()) {
            if (isInstanceInit(methodName) && holder.getType() == thisKlass.getSymbolicType()) {
                return op;
            }
            throw failVerify("Accessing field or calling method of an uninitialized reference.");
        }
        return op;
    }

    private Operand<R, C, M, F> checkInit(Operand<R, C, M, F> op) {
        verifyGuarantee(!op.isUninit(), "Accessing field or calling method of an uninitialized reference.");
        return op;
    }

    private Operand<R, C, M, F> fromJVMType(byte jvmType) {
        // @formatter:off
        switch (jvmType) {
            case 4  : return new ArrayOperand<>(booleanOperand);
            case 5  : return new ArrayOperand<>(charOp);
            case 6  : return new ArrayOperand<>(floatOp);
            case 7  : return new ArrayOperand<>(doubleOp);
            case 8  : return new ArrayOperand<>(byteOp);
            case 9  : return new ArrayOperand<>(shortOp);
            case 10 : return new ArrayOperand<>(intOp);
            case 11 : return new ArrayOperand<>(longOp);
            default:
                throw VerifierError.fatal("Unrecognized JVM array byte: " + jvmType);
        }
        // @formatter:on
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    Operand<R, C, M, F>[] getOperandSig(Symbol<Type>[] toParse) {
        Operand<R, C, M, F>[] operandSig = new Operand[toParse.length];
        for (int i = 0; i < operandSig.length; i++) {
            Symbol<Type> type = toParse[i];
            operandSig[i] = kindToOperand(type);
        }
        return operandSig;
    }

    private Operand<R, C, M, F>[] getOperandSig(Symbol<Signature> toParse) {
        return getOperandSig(getSignatures().parsed(toParse));
    }

    /**
     * Generates an operand from a type.
     */
    private Operand<R, C, M, F> kindToOperand(Symbol<Type> type) {
        // @formatter:off
        switch (TypeSymbols.getJavaKind(type)) {
            case Boolean: return booleanOperand;
            case Byte   : return byteOp;
            case Short  : return shortOp;
            case Char   : return charOp;
            case Int    : return intOp;
            case Float  : return floatOp;
            case Long   : return longOp;
            case Double : return doubleOp;
            case Void   : return voidOp;
            case Object : return spawnFromType(type, ReferenceOperand.CPI_UNKNOWN);
            default:
                throw VerifierError.fatal("Obtaining an operand with an unrecognized type: " + type);
        }
        // @formatter:on
    }

    private Operand<R, C, M, F> spawnFromType(Symbol<Type> type, int cpi) {
        if (TypeSymbols.isArray(type)) {
            return new ArrayOperand<>(kindToOperand(runtime.getSymbolPool().getTypes().getElementalType(type)), TypeSymbols.getArrayDimensions(type));
        } else {
            return new ReferenceOperand<>(type, cpi);
        }
    }

    private void xaload(OperandStack<R, C, M, F> stack, PrimitiveOperand<R, C, M, F> kind) {
        stack.popInt();
        Operand<R, C, M, F> op = stack.popArray();
        verifyGuarantee(op == nullOp || kind.compliesWith(op.getComponent(), this), "Loading " + kind + " from " + op + " array.");
        stack.push(kind.toStack());
    }

    private void xastore(OperandStack<R, C, M, F> stack, PrimitiveOperand<R, C, M, F> kind) {
        stack.pop(kind);
        stack.popInt();
        Operand<R, C, M, F> array = stack.popArray();
        verifyGuarantee(array == nullOp || kind.compliesWith(array.getComponent(), this), "got array of type: " + array + ", while storing a " + kind);
    }

    private static boolean wideOpcodes(int op) {
        return (op >= ILOAD && op <= ALOAD) || (op >= ISTORE && op <= ASTORE) || (op == RET) || (op == IINC);
    }

    /**
     * Computes the merging of the current stack/local status with the stack frame store in the
     * table.
     *
     * @return if merge succeeds, returns the given stackFrame, else returns a new StackFrame that
     *         represents the merging.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    StackFrame<R, C, M, F> mergeFrames(OperandStack<R, C, M, F> stack, Locals<R, C, M, F> locals, StackFrame<R, C, M, F> stackMap) {
        if (stackMap == null) {
            verifyGuarantee(!useStackMaps, "No stack frame on jump target");
            return spawnStackFrame(stack, locals);
        }
        // Merge stacks
        Operand<R, C, M, F>[] mergedStack = null;
        int mergeIndex = stack.mergeInto(stackMap);
        if (mergeIndex != -1) {
            verifyGuarantee(!useStackMaps, "Wrong stack map frames in class file.");
            mergedStack = new Operand[stackMap.stack.length];
            System.arraycopy(stackMap.stack, 0, mergedStack, 0, mergeIndex);
            for (int i = mergeIndex; i < mergedStack.length; i++) {
                Operand<R, C, M, F> stackOp = stack.stack[i];
                Operand<R, C, M, F> frameOp = stackMap.stack[i];
                if (!stackOp.compliesWithInMerge(frameOp, this)) {
                    Operand<R, C, M, F> result = stackOp.mergeWith(frameOp, this);
                    verifyGuarantee(result != null, "Cannot merge " + stackOp + " with " + frameOp);
                    mergedStack[i] = result;
                } else {
                    mergedStack[i] = frameOp;
                }
            }
        }
        // Merge locals
        Operand<R, C, M, F>[] mergedLocals = null;
        mergeIndex = locals.mergeInto(stackMap);
        if (mergeIndex != -1) {
            verifyGuarantee(!useStackMaps, "Wrong local map frames in class file: " + thisKlass + '.' + methodName);

            mergedLocals = new Operand[maxLocals];
            Operand<R, C, M, F>[] frameLocals = stackMap.locals;
            System.arraycopy(frameLocals, 0, mergedLocals, 0, mergeIndex);
            for (int i = mergeIndex; i < mergedLocals.length; i++) {
                Operand<R, C, M, F> localsOp = locals.registers[i];
                Operand<R, C, M, F> frameOp = frameLocals[i];
                if (!localsOp.compliesWithInMerge(frameOp, this)) {
                    Operand<R, C, M, F> result = localsOp.mergeWith(frameOp, this);
                    // We can ALWAYS merge locals. just put Invalid if failure
                    mergedLocals[i] = Objects.requireNonNullElse(result, invalidOp);
                } else {
                    mergedLocals[i] = frameOp;
                }
            }
        }
        // Merge subroutines
        stackMap.mergeSubroutines(locals.subRoutineModifications);
        if (mergedStack == null && mergedLocals == null) {
            // Merge success
            return stackMap;
        }
        // Merge failed
        if (mergedStack == null) {
            return new StackFrame<>(this, stack, mergedLocals, stackMap.subroutineModificationStack);
        }
        return new StackFrame<>(this, mergedStack, stack.size, stack.top, mergedLocals == null ? locals.registers : mergedLocals, stackMap.subroutineModificationStack);
    }

    private StackFrame<R, C, M, F> spawnStackFrame(OperandStack<R, C, M, F> stack, Locals<R, C, M, F> locals) {
        return new StackFrame<>(this, stack, locals);
    }

    @Override
    public String toExternalString() {
        return getThisKlass().getHostType() + "." + getMethodName();
    }

    private TypeSymbols getTypes() {
        return runtime.getSymbolPool().getTypes();
    }

    private SignatureSymbols getSignatures() {
        return runtime.getSymbolPool().getSignatures();
    }
}
