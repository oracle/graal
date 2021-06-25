/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.verifier;

import static com.oracle.truffle.espresso.bytecode.Bytecodes.AALOAD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.AASTORE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ACONST_NULL;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ALOAD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ALOAD_0;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ALOAD_1;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ALOAD_2;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ALOAD_3;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ANEWARRAY;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ARETURN;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ARRAYLENGTH;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ASTORE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ASTORE_0;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ASTORE_1;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ASTORE_2;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ASTORE_3;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ATHROW;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.BALOAD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.BASTORE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.BIPUSH;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.BREAKPOINT;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.CALOAD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.CASTORE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.CHECKCAST;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.D2F;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.D2I;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.D2L;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DADD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DALOAD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DASTORE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DCMPG;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DCMPL;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DCONST_0;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DCONST_1;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DDIV;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DLOAD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DLOAD_0;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DLOAD_1;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DLOAD_2;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DLOAD_3;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DMUL;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DNEG;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DREM;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DRETURN;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DSTORE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DSTORE_0;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DSTORE_1;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DSTORE_2;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DSTORE_3;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DSUB;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DUP;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DUP2;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DUP2_X1;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DUP2_X2;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DUP_X1;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DUP_X2;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.F2D;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.F2I;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.F2L;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FADD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FALOAD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FASTORE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FCMPG;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FCMPL;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FCONST_0;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FCONST_1;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FCONST_2;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FDIV;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FLOAD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FLOAD_0;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FLOAD_1;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FLOAD_2;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FLOAD_3;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FMUL;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FNEG;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FREM;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FRETURN;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FSTORE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FSTORE_0;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FSTORE_1;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FSTORE_2;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FSTORE_3;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FSUB;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.GETFIELD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.GETSTATIC;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.GOTO;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.GOTO_W;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.I2B;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.I2C;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.I2D;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.I2F;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.I2L;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.I2S;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IADD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IALOAD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IAND;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IASTORE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ICONST_0;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ICONST_1;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ICONST_2;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ICONST_3;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ICONST_4;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ICONST_5;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ICONST_M1;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IDIV;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IFEQ;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IFGE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IFGT;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IFLE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IFLT;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IFNE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IFNONNULL;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IFNULL;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IF_ACMPEQ;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IF_ACMPNE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IF_ICMPEQ;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IF_ICMPGE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IF_ICMPGT;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IF_ICMPLE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IF_ICMPLT;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IF_ICMPNE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IINC;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ILOAD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ILOAD_0;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ILOAD_1;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ILOAD_2;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ILOAD_3;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IMUL;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.INEG;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.INSTANCEOF;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.INVOKEDYNAMIC;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.INVOKEINTERFACE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.INVOKESPECIAL;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.INVOKESTATIC;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.INVOKEVIRTUAL;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IOR;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IREM;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IRETURN;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ISHL;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ISHR;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ISTORE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ISTORE_0;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ISTORE_1;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ISTORE_2;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ISTORE_3;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ISUB;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IUSHR;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IXOR;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.JSR;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.JSR_W;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.L2D;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.L2F;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.L2I;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LADD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LALOAD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LAND;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LASTORE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LCMP;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LCONST_0;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LCONST_1;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LDC;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LDC2_W;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LDC_W;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LDIV;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LLOAD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LLOAD_0;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LLOAD_1;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LLOAD_2;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LLOAD_3;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LMUL;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LNEG;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LOOKUPSWITCH;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LOR;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LREM;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LRETURN;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LSHL;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LSHR;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LSTORE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LSTORE_0;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LSTORE_1;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LSTORE_2;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LSTORE_3;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LSUB;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LUSHR;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LXOR;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.MONITORENTER;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.MONITOREXIT;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.MULTIANEWARRAY;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.NEW;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.NEWARRAY;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.NOP;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.POP;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.POP2;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.PUTFIELD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.PUTSTATIC;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.QUICK;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.RET;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.RETURN;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.SALOAD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.SASTORE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.SIPUSH;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.SLIM_QUICK;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.SWAP;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.TABLESWITCH;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.WIDE;
import static com.oracle.truffle.espresso.classfile.ConstantPool.Tag.CLASS;
import static com.oracle.truffle.espresso.classfile.ConstantPool.Tag.INTERFACE_METHOD_REF;
import static com.oracle.truffle.espresso.classfile.Constants.APPEND_FRAME_BOUND;
import static com.oracle.truffle.espresso.classfile.Constants.CHOP_BOUND;
import static com.oracle.truffle.espresso.classfile.Constants.FULL_FRAME;
import static com.oracle.truffle.espresso.classfile.Constants.ITEM_Bogus;
import static com.oracle.truffle.espresso.classfile.Constants.ITEM_Double;
import static com.oracle.truffle.espresso.classfile.Constants.ITEM_Float;
import static com.oracle.truffle.espresso.classfile.Constants.ITEM_InitObject;
import static com.oracle.truffle.espresso.classfile.Constants.ITEM_Integer;
import static com.oracle.truffle.espresso.classfile.Constants.ITEM_Long;
import static com.oracle.truffle.espresso.classfile.Constants.ITEM_NewObject;
import static com.oracle.truffle.espresso.classfile.Constants.ITEM_Null;
import static com.oracle.truffle.espresso.classfile.Constants.ITEM_Object;
import static com.oracle.truffle.espresso.classfile.Constants.SAME_FRAME_BOUND;
import static com.oracle.truffle.espresso.classfile.Constants.SAME_FRAME_EXTENDED;
import static com.oracle.truffle.espresso.classfile.Constants.SAME_LOCALS_1_STACK_ITEM_BOUND;
import static com.oracle.truffle.espresso.classfile.Constants.SAME_LOCALS_1_STACK_ITEM_EXTENDED;

import java.util.Arrays;

import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.bytecode.BytecodeLookupSwitch;
import com.oracle.truffle.espresso.bytecode.BytecodeStream;
import com.oracle.truffle.espresso.bytecode.BytecodeTableSwitch;
import com.oracle.truffle.espresso.bytecode.Bytecodes;
import com.oracle.truffle.espresso.classfile.ClassfileParser;
import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.classfile.RuntimeConstantPool;
import com.oracle.truffle.espresso.classfile.attributes.CodeAttribute;
import com.oracle.truffle.espresso.classfile.attributes.StackMapTableAttribute;
import com.oracle.truffle.espresso.classfile.constantpool.ClassConstant;
import com.oracle.truffle.espresso.classfile.constantpool.DynamicConstant;
import com.oracle.truffle.espresso.classfile.constantpool.FieldRefConstant;
import com.oracle.truffle.espresso.classfile.constantpool.InvokeDynamicConstant;
import com.oracle.truffle.espresso.classfile.constantpool.MethodRefConstant;
import com.oracle.truffle.espresso.classfile.constantpool.PoolConstant;
import com.oracle.truffle.espresso.descriptors.Signatures;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.descriptors.Validation;
import com.oracle.truffle.espresso.impl.ContextAccess;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.ExceptionHandler;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.perf.DebugCloseable;
import com.oracle.truffle.espresso.perf.DebugTimer;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;

/**
 * Should be a complete bytecode verifier. Given the version of the classfile from which the method
 * is taken, the type-checking or type-infering verifier is used.
 *
 * Note that stack map tables are used only for classfile version >= 50, and even if stack maps are
 * given for lesser versions, they are ignored. No fallback for classfile v.50
 */
public final class MethodVerifier implements ContextAccess {
    public static final DebugTimer VERIFIER_TIMER = DebugTimer.create("verifier");

    // Class info
    private final Klass thisKlass;
    private final RuntimeConstantPool pool;
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
    private final StackFrame[] stackFrames;
    private final byte[] handlerStatus;

    // <init> method validation
    private boolean calledConstructor = false;

    // Instruction stack to visit
    private final WorkingQueue queue = new WorkingQueue();

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

    Klass getThisKlass() {
        return thisKlass;
    }

    Symbol<Name> getMethodName() {
        return methodName;
    }

    @Override
    public EspressoContext getContext() {
        return thisKlass.getContext();
    }

    private boolean earlierThan49() {
        return majorVersion < ClassfileParser.JAVA_1_5_VERSION;
    }

    private boolean earlierThan51() {
        return majorVersion < ClassfileParser.JAVA_7_VERSION;
    }

    private boolean earlierThan55() {
        return majorVersion < ClassfileParser.JAVA_11_VERSION;
    }

    private boolean version51OrEarlier() {
        return majorVersion <= ClassfileParser.JAVA_7_VERSION;
    }

    private boolean version51OrLater() {
        return majorVersion >= ClassfileParser.JAVA_7_VERSION;
    }

    // TODO(garcia) intern Operands.

    // Define all operands that can appear on the stack / locals.
    static final PrimitiveOperand Int = new PrimitiveOperand(JavaKind.Int);
    static final PrimitiveOperand Byte = new PrimitiveOperand(JavaKind.Byte);
    static final PrimitiveOperand Char = new PrimitiveOperand(JavaKind.Char);
    static final PrimitiveOperand Short = new PrimitiveOperand(JavaKind.Short);
    static final PrimitiveOperand Float = new PrimitiveOperand(JavaKind.Float);
    static final PrimitiveOperand Double = new PrimitiveOperand(JavaKind.Double);
    static final PrimitiveOperand Long = new PrimitiveOperand(JavaKind.Long);
    static final PrimitiveOperand Void = new PrimitiveOperand(JavaKind.Void);
    static final PrimitiveOperand Invalid = new PrimitiveOperand(JavaKind.Illegal);

    /*
     * Special handling:
     *
     * is same as Byte for java 8 or earlier, becomes its own operand for Java >= 9
     */
    final PrimitiveOperand booleanOperand;

    /* Special operand used for BA{LOAD, STORE}. Should never be pushed to stack */
    static final PrimitiveOperand ByteOrBoolean = new PrimitiveOperand(JavaKind.Byte) {
        @Override
        boolean compliesWith(Operand other) {
            return other.isTopOperand() || other.getKind() == JavaKind.Boolean || other.getKind() == JavaKind.Byte;
        }

        @Override
        Operand mergeWith(Operand other) {
            if (other == this) {
                throw EspressoError.shouldNotReachHere("Invalid invariant: ByteOrBoolean operand in stack.");
            }
            if (other.isPrimitive()) {
                if (other == Byte) {
                    return Byte;
                }
                if (other.getKind() == JavaKind.Boolean) {
                    return other;
                }
            }
            return null;
        }

        @Override
        public PrimitiveOperand toStack() {
            return Byte;
        }
    };

    // We want to be able to share this instance between context, so its resolution must be
    // context-agnostic.
    static final ReferenceOperand jlObject = new ReferenceOperand(Type.java_lang_Object, null) {
        @Override
        Klass getKlass() {
            // this particular j.l.Object instance does not cache its resolved klass, as most
            // getKlass calls checks beforehand for Type Object.
            return EspressoLanguage.getCurrentContext().getMeta().java_lang_Object;
        }
    };

    static final Operand Null = new Operand(JavaKind.Object) {
        @Override
        boolean compliesWith(Operand other) {
            return other.isTopOperand() || other.isReference();
        }

        @Override
        Operand mergeWith(Operand other) {
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
        Operand getComponent() {
            // jvms-4.7.6: aaload
            // We define the component type of null to be null.
            return this;
        }
    };

    private final Operand jlClass;
    private final Operand jlString;
    private final Operand jliMethodType;
    private final Operand jliMethodHandle;
    private final Operand jlThrowable;

    // Return type of the method
    private final Operand returnOperand;
    // Declaring Klass of the method (already loaded)
    private final Operand thisOperand;

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
            if ((bciStates[target] >>> 16) != retBCI) {
                throw new VerifyError("Multiple returns to single jsr ");
            }
        }
        bciStates[target] = RETURNED_TO | (retBCI << 16);
    }

    /**
     * Construct the data structure to perform verification.
     *
     * @param codeAttribute the code attribute of the method
     * @param m the Espresso method
     */
    private MethodVerifier(CodeAttribute codeAttribute, Method m, boolean useStackMaps) {
        // Extract info from codeAttribute
        this.code = new BytecodeStream(codeAttribute.getOriginalCode());
        this.maxStack = codeAttribute.getMaxStack();
        this.maxLocals = codeAttribute.getMaxLocals();
        this.bciStates = new int[code.endBCI()];
        this.stackFrames = new StackFrame[code.endBCI()];
        this.stackMapTableAttribute = codeAttribute.getStackMapFrame();
        this.majorVersion = codeAttribute.getMajorVersion();
        this.useStackMaps = useStackMaps;

        // Extract method info
        this.pool = m.getRuntimeConstantPool();
        this.sig = m.getParsedSignature();
        this.isStatic = m.isStatic();
        this.thisKlass = m.getDeclaringKlass();
        this.methodName = m.getName();
        this.exceptionHandlers = m.getExceptionHandlers();

        this.handlerStatus = new byte[exceptionHandlers.length];
        Arrays.fill(handlerStatus, UNENCOUNTERED);

        jlClass = new ReferenceOperand(Type.java_lang_Class, thisKlass);
        jlString = new ReferenceOperand(Type.java_lang_String, thisKlass);
        jliMethodType = new ReferenceOperand(Type.java_lang_invoke_MethodType, thisKlass);
        jliMethodHandle = new ReferenceOperand(Type.java_lang_invoke_MethodHandle, thisKlass);
        jlThrowable = new ReferenceOperand(Type.java_lang_Throwable, thisKlass);

        booleanOperand = getJavaVersion().java9OrLater() ? new PrimitiveOperand(JavaKind.Boolean) : Byte;

        thisOperand = new ReferenceOperand(thisKlass, thisKlass);
        returnOperand = kindToOperand(Signatures.returnType(sig));
    }

    private MethodVerifier(CodeAttribute codeAttribute, Method m) {
        this(codeAttribute, m, codeAttribute.useStackMaps());
    }

    /**
     * Utility for ease of use in Espresso.
     *
     * @param m the method to verify
     *
     * @throws VerifyError if verification fails
     * @throws NoClassDefFoundError if Class loading of an operand fails at any point
     * @throws ClassFormatError if classfile is malformed
     */
    @SuppressWarnings("try")
    public static void verify(Method m) {
        CodeAttribute codeAttribute = m.getCodeAttribute();
        assert !((m.isAbstract() || m.isNative()) && codeAttribute != null) : "Abstract method has code: " + m;
        if (codeAttribute == null) {
            if (m.isAbstract() || m.isNative()) {
                return;
            }
            throw new ClassFormatError("Concrete method has no code attribute: " + m);
        }
        try (DebugCloseable t = VERIFIER_TIMER.scope(m.getContext().getTimers())) {
            new MethodVerifier(codeAttribute, m).verify();
        }
    }

    private void initVerifier() {
        Arrays.fill(bciStates, UNREACHABLE);
        // Mark all reachable code
        int bci = 0;
        int opcode;
        while (bci < code.endBCI()) {
            opcode = code.currentBC(bci);
            if (opcode > SLIM_QUICK) {
                throw new VerifyError("invalid bytecode: " + opcode);
            }
            bciStates[bci] = setStatus(bciStates[bci], UNSEEN);
            bci = code.nextBCI(bci);
            // Check instruction has enough bytes after it
            if (bci - 1 >= code.endBCI()) {
                throw new VerifyError("Incomplete bytecode");
            }
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
                    if (bciStates[target] == UNREACHABLE) {
                        throw new VerifyError("Jump to the middle of an instruction: " + target);
                    }
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

    // Traverses the switch to mark jump targets. Also checks that lookup switch keys are sorted.
    private void initSwitch(int bci, int opCode) {
        if (opCode == LOOKUPSWITCH) {
            BytecodeLookupSwitch switchHelper = BytecodeLookupSwitch.INSTANCE;
            int low = 0;
            int high = switchHelper.numberOfCases(code, bci);
            int oldKey = 0;
            boolean init = false;
            int target;
            for (int i = low; i < high; i++) {
                int newKey = switchHelper.keyAt(code, bci, i - low);
                if (init && newKey <= oldKey) {
                    throw new VerifyError("Unsorted keys in LOOKUPSWITCH");
                }
                init = true;
                oldKey = newKey;
                target = switchHelper.targetAt(code, bci, i - low);
                if (bciStates[target] == UNREACHABLE) {
                    throw new VerifyError("Jump to the middle of an instruction: " + target);
                }
                bciStates[target] = setStatus(bciStates[bci], JUMP_TARGET);
            }
            target = switchHelper.defaultTarget(code, bci);
            if (bciStates[target] == UNREACHABLE) {
                throw new VerifyError("Jump to the middle of an instruction: " + target);
            }
            bciStates[target] = setStatus(bciStates[bci], JUMP_TARGET);
        } else if (opCode == TABLESWITCH) {
            BytecodeTableSwitch switchHelper = BytecodeTableSwitch.INSTANCE;
            int low = switchHelper.lowKey(code, bci);
            int high = switchHelper.highKey(code, bci);
            int target;
            // if high == MAX_INT, i < high will always be true. This loop condition is to avoid
            // an infinite loop in this case.
            for (int i = low; i != high + 1; i++) {
                target = switchHelper.targetAt(code, bci, i - low);
                if (bciStates[target] == UNREACHABLE) {
                    throw new VerifyError("Jump to the middle of an instruction: " + target);
                }
                bciStates[target] = setStatus(bciStates[bci], JUMP_TARGET);
            }
            target = switchHelper.defaultTarget(code, bci);
            if (bciStates[target] == UNREACHABLE) {
                throw new VerifyError("Jump to the middle of an instruction: " + target);
            }
        } else {
            throw EspressoError.shouldNotReachHere();
        }
    }

    /**
     * Constructs the initial stack frame table given the stackMapTableAttribute. If no such
     * attribute exists, it still constructs the first implicit StackFrame, with the method
     * arguments in the locals and an empty stack.
     */
    private void initStackFrames() {
        // First implicit stack frame.
        StackFrame previous = new StackFrame(this);
        assert stackFrames.length > 0;
        int bci = 0;
        registerStackMapFrame(bci, previous);
        if (!useStackMaps || stackMapTableAttribute == null) {
            return;
        }
        if (stackMapTableAttribute == StackMapTableAttribute.EMPTY) {
            throw EspressoError.shouldNotReachHere("Class " + thisKlass.getExternalName() + " was determined to not need verification, but verification was invoked.");
        }
        StackMapFrameParser.parse(this, stackMapTableAttribute, previous);
    }

    /**
     * Registers a stack frame from the stack map frame parser to the given BCI.
     */
    void registerStackMapFrame(int bci, StackFrame frame) {
        validateFrameBCI(bci);
        stackFrames[bci] = frame;
    }

    /**
     * Constructs a StackFrame object for the verifier from a StackMapFrame obtained from the
     * parser.
     */
    StackFrame getStackFrame(StackMapFrame smf, StackFrame previous) {
        int frameType = smf.getFrameType();
        if (frameType < SAME_FRAME_BOUND) {
            if (previous.top == 0) {
                return previous;
            }
            StackFrame res = new StackFrame(Operand.EMPTY_ARRAY, 0, 0, previous.locals);
            res.lastLocal = previous.lastLocal;
            return res;
        }
        if (frameType < SAME_LOCALS_1_STACK_ITEM_BOUND) {
            OperandStack stack = new OperandStack(2);
            stack.push(getOperandFromVerificationType(smf.getStackItem()));
            StackFrame res = new StackFrame(stack, previous.locals);
            res.lastLocal = previous.lastLocal;
            return res;
        }
        if (frameType < SAME_LOCALS_1_STACK_ITEM_EXTENDED) {
            // [128, 246] is reserved and still unused
            throw new ClassFormatError("Encountered reserved StackMapFrame tag: " + frameType);
        }
        if (frameType == SAME_LOCALS_1_STACK_ITEM_EXTENDED) {
            OperandStack stack = new OperandStack(2);
            stack.push(getOperandFromVerificationType(smf.getStackItem()));
            StackFrame res = new StackFrame(stack, previous.locals);
            res.lastLocal = previous.lastLocal;
            return res;
        }
        if (frameType < CHOP_BOUND) {
            Operand[] newLocals = previous.locals.clone();
            int pos = previous.lastLocal;
            for (int i = 0; i < smf.getChopped(); i++) {
                Operand op = newLocals[pos];
                if (op.isTopOperand() && (pos > 0)) {
                    if (isType2(newLocals[pos - 1])) {
                        pos--;
                    }
                }
                newLocals[pos] = Invalid;
                pos--;
            }
            StackFrame res = new StackFrame(Operand.EMPTY_ARRAY, 0, 0, newLocals);
            res.lastLocal = pos;
            return res;
        }
        if (frameType == SAME_FRAME_EXTENDED) {
            if (previous.top == 0) {
                return previous;
            }
            StackFrame res = new StackFrame(Operand.EMPTY_ARRAY, 0, 0, previous.locals);
            res.lastLocal = previous.lastLocal;
            return res;
        }
        if (frameType < APPEND_FRAME_BOUND) {
            if (smf.getLocals().length == 0) {
                throw new VerifyError("Empty Append Frame in the StackmapTable");
            }
            Operand[] newLocals = previous.locals.clone();
            int pos = previous.lastLocal;
            for (VerificationTypeInfo vti : smf.getLocals()) {
                Operand op = getOperandFromVerificationType(vti);
                newLocals[++pos] = op;
                if (isType2(op)) {
                    newLocals[++pos] = Invalid;
                }
            }
            StackFrame res = new StackFrame(Operand.EMPTY_ARRAY, 0, 0, newLocals);
            res.lastLocal = pos;
            return res;
        }
        if (frameType == FULL_FRAME) {
            OperandStack fullStack = new OperandStack(maxStack);
            for (VerificationTypeInfo vti : smf.getStack()) {
                fullStack.push(getOperandFromVerificationType(vti));
            }
            Operand[] locals = new Operand[maxLocals];
            Arrays.fill(locals, Invalid);
            int pos = -1;
            for (VerificationTypeInfo vti : smf.getLocals()) {
                Operand op = getOperandFromVerificationType(vti);
                locals[++pos] = op;
                if (isType2(op)) {
                    locals[++pos] = Invalid;
                }
            }
            StackFrame res = new StackFrame(fullStack, locals);
            res.lastLocal = pos;
            return res;
        }
        throw EspressoError.shouldNotReachHere();
    }

    private Operand getOperandFromVerificationType(VerificationTypeInfo vti) {
        // Note: JSR/RET is mutually exclusive with stack maps.
        switch (vti.getTag()) {
            case ITEM_Bogus:
                return Invalid;
            case ITEM_Integer:
                return Int;
            case ITEM_Float:
                return Float;
            case ITEM_Double:
                return Double;
            case ITEM_Long:
                return Long;
            case ITEM_Null:
                return Null;
            case ITEM_InitObject:
                return new UninitReferenceOperand(thisKlass, thisKlass);
            case ITEM_Object:
                return spawnFromType(getTypes().fromName(pool.classAt(vti.getConstantPoolOffset()).getName(pool)));
            case ITEM_NewObject:
                int newOffset = vti.getNewOffset();
                if (newOffset < 0 || newOffset >= code.endBCI() || bciStates[newOffset] == UNREACHABLE) {
                    throw new ClassFormatError("Invalid BCI reference in stack map!");
                }
                if (code.currentBC(newOffset) != NEW) {
                    throw new ClassFormatError("NewObject in stack map not referencing a NEW instruction! " + Bytecodes.nameOf(code.currentBC(newOffset)));
                }
                return new UninitReferenceOperand(getTypes().fromName(pool.classAt(code.readCPI(newOffset)).getName(pool)), thisKlass, newOffset);
            default:
                throw EspressoError.shouldNotReachHere("Unrecognized VerificationTypeInfo: " + vti);
        }
    }

    private static class WorkingQueue {
        QueueElement first;
        QueueElement last;

        WorkingQueue() {
            /* nop */
        }

        boolean isEmpty() {
            return first == null;
        }

        void push(int bci, QueueElement elem) {
            QueueElement current = lookup(bci);
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

        QueueElement pop() {
            assert first != null;
            QueueElement res = first;
            first = first.next;
            return res;
        }

        void replace(QueueElement oldElem, QueueElement newElem) {
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

        QueueElement lookup(int bci) {
            QueueElement current = first;
            while (current != null && current.bci != bci) {
                current = current.next;
            }
            return current;
        }
    }

    private static class QueueElement {
        final int bci;
        final StackFrame frame;
        final boolean constructorCalled;

        QueueElement prev;
        QueueElement next;

        QueueElement(int bci, StackFrame frame, boolean calledConstructor) {
            this.bci = bci;
            this.frame = frame;
            this.constructorCalled = calledConstructor;
        }
    }

    /**
     * Performs the verification for the method associated with this MethodVerifier instance.
     */
    private synchronized void verify() {
        if (code.endBCI() == 0) {
            throw new VerifyError("Control flow falls through code end");
        }
        try {
            // Marks BCIs in-between opcodes, and marks jump targets.
            initVerifier();
        } catch (IndexOutOfBoundsException e) {
            throw new VerifyError("Invalid jump: " + e.getMessage() + " in method " + thisKlass.getName() + "." + methodName);
        }
        try {
            // Extract the initial stack frame, and extract stack maps if available.
            initStackFrames();
        } catch (IndexOutOfBoundsException e) {
            throw new ClassFormatError("Could not construct stackFrames due to invalid maxStack or maxLocals value: " + e.getMessage());
        }

        // Check that unconditional jumps have stack maps following them.
        validateUnconditionalJumps();

        // Check that BCIs in exception handlers are legal.
        validateExceptionHandlers();

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
                    if (nextBCI < code.endBCI() && stackFrames[nextBCI] == null) {
                        throw new VerifyError("Control flow stop does not have a stack map at next instruction!");
                    }
                }
                bci = nextBCI;
            }
        }
    }

    private void validateExceptionHandlers() {
        for (ExceptionHandler handler : exceptionHandlers) {
            int startBCI = handler.getHandlerBCI();
            validateFormatBCI(startBCI);
            startBCI = handler.getStartBCI();
            validateFormatBCI(startBCI);
            int endBCI = handler.getEndBCI();
            if (endBCI <= startBCI) {
                throw new ClassFormatError("End BCI of handler is before start BCI");
            }
            if (endBCI > code.endBCI()) {
                throw new ClassFormatError("Control flow falls through code end");
            }
            if (endBCI < 0) {
                throw new ClassFormatError("negative branch target: " + endBCI);
            }
            if (handler.catchTypeCPI() != 0) {
                Klass catchType = pool.resolvedKlassAt(thisKlass, handler.catchTypeCPI());
                if (!getMeta().java_lang_Throwable.isAssignableFrom(catchType)) {
                    throw new VerifyError("Illegal exception handler catch type: " + catchType);
                }
            }

            if (endBCI != code.endBCI()) {
                if (bciStates[endBCI] == UNREACHABLE) {
                    throw new ClassFormatError("Jump to the middle of an instruction: " + endBCI);
                }
            }
        }
    }

    private void processQueue() {
        Locals locals;
        while (!queue.isEmpty()) {
            QueueElement toVerify = queue.pop();
            calledConstructor = toVerify.constructorCalled;
            locals = toVerify.frame.extractLocals();
            locals.subRoutineModifications = toVerify.frame.subroutineModificationStack;
            startVerify(toVerify.bci, toVerify.frame.extractStack(maxStack), locals);
        }
    }

    private void verifyReachableCode() {
        // Perform verification of reachable executable code
        OperandStack stack = new OperandStack(maxStack);
        Locals locals = new Locals(this);
        startVerify(0, stack, locals);
        do {
            processQueue();
            verifyExceptionHandlers();
        } while (!queue.isEmpty());
    }

    private void verifyUnreachableStackMaps() {
        for (int stackBCI = 0; stackBCI < stackFrames.length; stackBCI++) {
            if (stackFrames[stackBCI] != null && checkStatus(bciStates[stackBCI], UNSEEN)) {
                queue.push(stackBCI, new QueueElement(stackBCI, stackFrames[stackBCI], true));
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

    private void verifyHandler(ExceptionHandler handler) {
        int handlerBCI = handler.getHandlerBCI();
        Locals locals;
        OperandStack stack;
        StackFrame frame = stackFrames[handlerBCI];
        if (frame == null) {
            // If there is no stack map when verifying a handler, all locals are illegal.
            Operand[] registers = new Operand[maxLocals];
            Arrays.fill(registers, Invalid);
            locals = new Locals(registers);
            stack = new OperandStack(maxStack);
            stack.push(new ReferenceOperand(handler.getCatchType(), thisKlass));
        } else {
            stack = frame.extractStack(maxStack);
            locals = frame.extractLocals();
        }
        startVerify(handlerBCI, stack, locals);
    }

    private void branch(int bci, OperandStack stack, Locals locals) {
        validateBCI(bci);
        // Try merge
        StackFrame frame = mergeFrames(stack, locals, stackFrames[bci]);
        if (frame != stackFrames[bci] || !checkStatus(bciStates[bci], DONE)) {
            // merge failed or not yet verified bci. mark the bci as not yet verified since
            // state can change.
            bciStates[bci] = setStatus(bciStates[bci], JUMP_TARGET);
            stackFrames[bci] = frame;
            QueueElement toPush = new QueueElement(bci, frame, calledConstructor);
            queue.push(bci, toPush);
        }
    }

    private void validateBCI(int bci) {
        if (bci >= code.endBCI()) {
            throw new VerifyError("Control flow falls through code end");
        }
        if (bci < 0) {
            throw new VerifyError("negative branch target: " + bci);
        }
        if (bciStates[bci] == UNREACHABLE) {
            throw new VerifyError("Jump to the middle of an instruction: " + bci);
        }
    }

    private void validateFormatBCI(int bci) {
        if (bci >= code.endBCI()) {
            throw new ClassFormatError("Control flow falls through code end");
        }
        if (bci < 0) {
            throw new ClassFormatError("negative branch target: " + bci);
        }
        if (bciStates[bci] == UNREACHABLE) {
            throw new ClassFormatError("Jump to the middle of an instruction: " + bci);
        }
    }

    private void validateFrameBCI(int bci) {
        if (bci >= code.endBCI()) {
            throw new VerifyError("StackFrame offset falls outside of method");
        }
        if (bci < 0) {
            throw new VerifyError("negative stack frame offset: " + bci);
        }
        if (bciStates[bci] == UNREACHABLE) {
            throw new VerifyError("StackFrame offset falls to the middle of an instruction: " + bci);
        }
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
     * - Stack and Locals state are legal according to the bytecode.
     *
     * @param bci The bci at which we wish to start performing verification
     * @param seedStack the state of the stack at bci
     * @param seedLocals the state of the local variables at bci
     */
    private void startVerify(int bci, OperandStack seedStack, Locals seedLocals) {
        OperandStack stack = seedStack;
        Locals locals = seedLocals;
        int nextBCI = bci;
        int previousBCI;

        // Check if constructor was called prior to this branch.
        boolean constructorCalledStatus = calledConstructor;
        do {
            previousBCI = nextBCI;
            if (stackFrames[nextBCI] != null || checkStatus(bciStates[nextBCI], JUMP_TARGET)) {
                // Try merge
                StackFrame frame = mergeFrames(stack, locals, stackFrames[nextBCI]);
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

    /**
     * Checks that an instruction can merge into all its handlers.
     */
    private void checkExceptionHandlers(int nextBCI, Locals locals) {
        for (int i = 0; i < exceptionHandlers.length; i++) {
            ExceptionHandler handler = exceptionHandlers[i];
            if (nextBCI >= handler.getStartBCI() && nextBCI < handler.getEndBCI()) {
                OperandStack stack = new OperandStack(1);
                Symbol<Type> catchType = handler.getCatchType();
                stack.push(catchType == null ? jlThrowable : new ReferenceOperand(catchType, thisKlass));
                StackFrame oldFrame = stackFrames[handler.getHandlerBCI()];
                StackFrame newFrame = mergeFrames(stack, locals, oldFrame);
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

    private int verifySafe(int bci, OperandStack stack, Locals locals) {
        try {
            return verify(bci, stack, locals);
        } catch (IndexOutOfBoundsException e) {
            // At this point, the only appearance of an IndexOutOfBounds should be from stack and
            // locals access (bci bound checks are done beforehand).
            throw new VerifyError("Inconsistent Stack/Local access: " + e.getMessage() + ", in: " + thisKlass.getType() + "." + methodName);
        }
    }

    private Operand ldcFromTag(PoolConstant pc) {
        // @formatter:off
        // checkstyle: stop
        switch (pc.tag()) {
            case INTEGER:       return Int;
            case FLOAT:         return Float;
            case LONG:          return Long;
            case DOUBLE:        return Double;
            case CLASS:         return jlClass;
            case STRING:        return jlString;
            case METHODHANDLE:
                if (earlierThan51()) {
                    throw new ClassFormatError("LDC for MethodHandleConstant in classfile version < 51");
                }
                return jliMethodHandle;
            case METHODTYPE:
                if (earlierThan51()) {
                    throw new ClassFormatError("LDC for MethodType in classfile version < 51");
                }
                return jliMethodType;
            case DYNAMIC:
                if (earlierThan55()) {
                    throw new ClassFormatError("LDC for Dynamic in classfile version < 55");
                }
                DynamicConstant constant = (DynamicConstant) pc;
                return kindToOperand(constant.getTypeSymbol(pool));
            default:
                throw new VerifyError("invalid CP load: " + pc.tag());
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
    private int verify(int bci, OperandStack stack, Locals locals) {
        if (bciStates[bci] == UNREACHABLE) {
            throw new VerifyError("Jump to the middle of an instruction: " + bci);
        }
        bciStates[bci] = setStatus(bciStates[bci], DONE);
        int curOpcode;
        curOpcode = code.opcode(bci);
        if (!(curOpcode <= SLIM_QUICK)) {
            throw new VerifyError("invalid bytecode: " + code.readUByte(bci));
        }
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
                case ACONST_NULL: stack.push(Null); break;

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
                    PoolConstant pc = poolAt(code.readCPI(bci));
                    pc.validate(pool);
                    Operand op = ldcFromTag(pc);
                    if (isType2(op)) {
                        throw new VerifyError("Loading Long or Double with LDC or LDC_W, please use LDC2_W.");
                    }
                    if (earlierThan49() && !(op == Int || op == Float || op.getType() == jlString.getType())) {
                        throw new VerifyError("Loading non Int, Float or String with LDC in classfile version < 49.0");
                    }
                    stack.push(op);
                    break;
                }
                case LDC2_W: {
                    PoolConstant pc = poolAt(code.readCPI(bci));
                    pc.validate(pool);
                    Operand op = ldcFromTag(pc);
                    if (!isType2(op)) {
                        throw new VerifyError("Loading non-Long or Double with LDC2_W, please use LDC or LDC_W.");
                    }
                    stack.push(op);
                    break;
                }

                case ILOAD: locals.load(code.readLocalIndex(bci), Int);     stack.pushInt();    break;
                case LLOAD: locals.load(code.readLocalIndex(bci), Long);    stack.pushLong();   break;
                case FLOAD: locals.load(code.readLocalIndex(bci), Float);   stack.pushFloat();  break;
                case DLOAD: locals.load(code.readLocalIndex(bci), Double);  stack.pushDouble(); break;
                case ALOAD: stack.push(locals.loadRef(code.readLocalIndex(bci))); break;

                case ILOAD_0:
                case ILOAD_1:
                case ILOAD_2:
                case ILOAD_3: locals.load(curOpcode - ILOAD_0, Int); stack.pushInt(); break;

                case LLOAD_0:
                case LLOAD_1:
                case LLOAD_2:
                case LLOAD_3: locals.load(curOpcode - LLOAD_0, Long); stack.pushLong(); break;

                case FLOAD_0:
                case FLOAD_1:
                case FLOAD_2:
                case FLOAD_3: locals.load(curOpcode - FLOAD_0, Float); stack.pushFloat(); break;

                case DLOAD_0:
                case DLOAD_1:
                case DLOAD_2:
                case DLOAD_3: locals.load(curOpcode - DLOAD_0, Double); stack.pushDouble(); break;

                case ALOAD_0:
                case ALOAD_1:
                case ALOAD_2:
                case ALOAD_3: stack.push(locals.loadRef(curOpcode - ALOAD_0)); break;


                case IALOAD: xaload(stack, Int);    break;
                case LALOAD: xaload(stack, Long);   break;
                case FALOAD: xaload(stack, Float);  break;
                case DALOAD: xaload(stack, Double); break;

                case AALOAD: {
                    stack.popInt();
                    Operand op = stack.popArray();
                    if (op != Null && !op.getComponent().isReference()) {
                        throw new VerifyError("Loading reference from " + op + " array.");
                    }
                    stack.push(op.getComponent());
                    break;
                }

                case BALOAD: xaload(stack, ByteOrBoolean);  break;
                case CALOAD: xaload(stack, Char);  break;
                case SALOAD: xaload(stack, Short); break;

                case ISTORE: stack.popInt();     locals.store(code.readLocalIndex(bci), Int);    break;
                case LSTORE: stack.popLong();    locals.store(code.readLocalIndex(bci), Long);   break;
                case FSTORE: stack.popFloat();   locals.store(code.readLocalIndex(bci), Float);  break;
                case DSTORE: stack.popDouble();  locals.store(code.readLocalIndex(bci), Double); break;

                case ASTORE: locals.store(code.readLocalIndex(bci), stack.popObjOrRA()); break;

                case ISTORE_0:
                case ISTORE_1:
                case ISTORE_2:
                case ISTORE_3: stack.popInt(); locals.store(curOpcode - ISTORE_0, Int); break;

                case LSTORE_0:
                case LSTORE_1:
                case LSTORE_2:
                case LSTORE_3: stack.popLong(); locals.store(curOpcode - LSTORE_0, Long); break;

                case FSTORE_0:
                case FSTORE_1:
                case FSTORE_2:
                case FSTORE_3: stack.popFloat(); locals.store(curOpcode - FSTORE_0, Float); break;

                case DSTORE_0:
                case DSTORE_1:
                case DSTORE_2:
                case DSTORE_3: stack.popDouble(); locals.store(curOpcode - DSTORE_0, Double); break;

                case ASTORE_0:
                case ASTORE_1:
                case ASTORE_2:
                case ASTORE_3: locals.store(curOpcode - ASTORE_0, stack.popObjOrRA()); break;

                case IASTORE: xastore(stack, Int);      break;
                case LASTORE: xastore(stack, Long);     break;
                case FASTORE: xastore(stack, Float);    break;
                case DASTORE: xastore(stack, Double);   break;

                case AASTORE: {
                    Operand toStore = stack.popRef();
                    stack.popInt();
                    Operand array = stack.popArray();
                    if (array != Null && !array.getComponent().isReference()) {
                        throw new VerifyError("Trying to store " + toStore + " in " + array);
                    }
                    // Other checks are done at runtime
                    break;
                }

                case BASTORE: xastore(stack, ByteOrBoolean); break;
                case CASTORE: xastore(stack, Char); break;
                case SASTORE: xastore(stack, Short); break;

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

                case IINC: locals.load(code.readLocalIndex(bci), Int); break;

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
                    stack.pop(Int);
                    if (!returnOperand.getKind().isStackInt()) {
                        throw new VerifyError("Found an IRETURN when return type is " + returnOperand);
                    }
                    return bci;
                }
                case LRETURN: doReturn(stack, Long);       return bci;
                case FRETURN: doReturn(stack, Float);      return bci;
                case DRETURN: doReturn(stack, Double);     return bci;
                case ARETURN: stack.popRef(returnOperand); return bci;
                case RETURN:
                    if (returnOperand != Void) {
                        throw new VerifyError("Encountered RETURN, but method return type is not void: " + returnOperand);
                    }
                    // Only j.l.Object.<init> can omit calling another initializer.
                    if (isInstanceInit(methodName) && thisKlass.getType() != Type.java_lang_Object) {
                        if (!calledConstructor) {
                            throw new VerifyError("Did not call super() or this() in constructor " + thisKlass.getType() + "." + methodName);
                        }
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
                    if (!wideOpcodes(curOpcode)) {
                        throw new VerifyError("invalid widened opcode: " + Bytecodes.nameOf(curOpcode));
                    }
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

    private void verifyInvokeDynamic(int bci, OperandStack stack) {
        // Check padding
        if (code.readByte(bci + 2) != 0 || code.readByte(bci + 3) != 0) {
            throw new VerifyError("bytes 3 and 4 after invokedynamic must be 0.");
        }
        PoolConstant pc = poolAt(code.readCPI(bci));

        // Check CP validity
        if (pc.tag() != ConstantPool.Tag.INVOKEDYNAMIC) {
            throw new VerifyError("Invalid CP constant for INVOKEDYNAMIC: " + pc.toString());
        }
        pc.validate(pool);

        InvokeDynamicConstant idc = (InvokeDynamicConstant) pc;
        Symbol<Name> name = idc.getName(pool);

        // Check invokedynamic does not call initializers
        if (isInstanceInit(name) || isClassInit(name)) {
            throw new VerifyError("Invalid bootstrap method name: " + name);
        }

        // Check and pop arguments
        Operand[] parsedSig = getOperandSig(idc.getSignature(pool));
        assert parsedSig.length > 0 : "Empty descriptor for method";
        for (int i = parsedSig.length - 2; i >= 0; i--) {
            stack.pop(parsedSig[i]);
        }

        // push result
        Operand returnKind = parsedSig[parsedSig.length - 1];
        if (returnKind != Void) {
            stack.push(returnKind);
        }
    }

    private Symbol<Type> getTypeFromPool(int c, String s) {
        PoolConstant pc = poolAt(c);
        if (pc.tag() != CLASS) {
            throw new VerifyError(s + pc.toString());
        }
        pc.validate(pool);
        ClassConstant cc = (ClassConstant) pc;
        assert Validation.validClassNameEntry(cc.getName(pool));
        Symbol<Type> type = getTypes().fromName(cc.getName(pool));
        return type;
    }

    private void verifyMultiNewArray(int bci, OperandStack stack) {
        // Check CP validity
        Symbol<Type> type = getTypeFromPool(code.readCPI(bci), "Invalid CP constant for MULTIANEWARRAY: ");
        if (!Types.isArray(type)) {
            throw new VerifyError("Class " + type + " for MULTINEWARRAY is not an array type.");
        }

        // Check dimensions
        int dim = code.readUByte(bci + 3);
        if (dim <= 0) {
            throw new VerifyError("Negative or 0 dimension for MULTIANEWARRAY: " + dim);
        }
        if (Types.getArrayDimensions(type) < dim) {
            throw new VerifyError("Incompatible dimensions from constant pool: " + Types.getArrayDimensions(type) + " and instruction: " + dim);
        }

        // Pop lengths
        for (int i = 0; i < dim; i++) {
            stack.popInt();
        }

        // push result
        stack.push(kindToOperand(type));
    }

    private void verifyInstanceOf(int bci, OperandStack stack) {
        // pop receiver
        stack.popRef();

        // Check CP validity
        Symbol<Type> type = getTypeFromPool(code.readCPI(bci), "Invalid CP constant for INSTANCEOF: ");
        if (Types.isPrimitive(type)) {
            throw new VerifyError("Primitive type for INSTANCEOF: " + type);
        }

        // push result
        stack.pushInt();
    }

    private void verifyCheckCast(int bci, OperandStack stack) {
        // pop receiver
        Operand stacKOp = stack.popRef();

        // Check CP validity
        Symbol<Type> type = getTypeFromPool(code.readCPI(bci), "Invalid CP constant for CHECKCAST: ");
        if (Types.isPrimitive(type)) {
            throw new VerifyError("Primitive type for CHECKCAST: " + type);
        }

        // push new type
        Operand castOp = spawnFromType(type);
        if (stacKOp.isUninit() && !castOp.isArrayType()) {
            stack.push(new UninitReferenceOperand(type, thisKlass, ((UninitReferenceOperand) stacKOp).newBCI));
        } else {
            stack.push(castOp);
        }
    }

    private void verifyNewObjectArray(int bci, OperandStack stack) {
        // Check CP validity
        int cpi = code.readCPI(bci);
        Symbol<Type> type = getTypeFromPool(cpi, "Invalid CP constant for ANEWARRAY: ");
        if (Types.isPrimitive(type)) {
            throw new VerifyError("Primitive type for ANEWARRAY: " + type);
        }

        // Pop length
        stack.popInt();

        // push result
        Operand ref = spawnFromType(type);
        if (ref.isArrayType()) {
            stack.push(new ArrayOperand(ref.getElemental(), ref.getDimensions() + 1));
        } else {
            stack.push(new ArrayOperand(ref));
        }
    }

    private PoolConstant poolAt(int cpi) {
        if (cpi >= pool.length() || cpi < 0) {
            throw new VerifyError("Invalid constant pool access at " + cpi + ", pool length: " + pool.length());
        }
        return pool.at(cpi);
    }

    private void verifyNewPrimitiveArray(int bci, OperandStack stack) {
        byte jvmType = code.readByte(bci);
        if (jvmType < 4 || jvmType > 11) {
            throw new VerifyError("invalid jvmPrimitiveType for NEWARRAY: " + jvmType);
        }
        stack.popInt();
        stack.push(fromJVMType(jvmType));
    }

    private void verifyNew(int bci, OperandStack stack) {
        // Check CP validity
        Symbol<Type> type = getTypeFromPool(code.readCPI(bci), "Invalid CP constant for NEW: ");
        if (Types.isPrimitive(type) || Types.isArray(type)) {
            throw new VerifyError("use NEWARRAY for creating array or primitive type: " + type);
        }

        // push result
        Operand op = new UninitReferenceOperand(type, thisKlass, bci);
        stack.push(op);
    }

    private void verifyPutField(int bci, OperandStack stack, int curOpcode) {
        // Check CP validity
        PoolConstant pc = poolAt(code.readCPI(bci));
        if (pc.tag() != ConstantPool.Tag.FIELD_REF) {
            throw new VerifyError("Invalid CP constant for PUTFIELD: " + pc.toString());
        }
        pc.validate(pool);

        // Obtain field info
        FieldRefConstant frc = (FieldRefConstant) pc;
        assert Validation.validFieldDescriptor(frc.getType(pool));
        Symbol<Type> fieldDesc = frc.getType(pool);
        Operand toPut = stack.pop(kindToOperand(fieldDesc));

        checkInit(toPut);
        if (curOpcode == PUTFIELD) {
            // Pop and check verifier
            assert Validation.validClassNameEntry(frc.getHolderKlassName(pool));
            Symbol<Type> fieldHolderType = getTypes().fromName(frc.getHolderKlassName(pool));
            Operand fieldHolder = kindToOperand(fieldHolderType);
            Operand receiver = checkInitAccess(stack.popRef(fieldHolder), fieldHolder);
            if (receiver.isArrayType()) {
                throw new VerifyError("Trying to access field of an array type: " + receiver);
            }
            if (!receiver.isUninitThis()) {
                checkProtectedField(receiver, fieldHolderType, code.readCPI(bci));
            }
        }
    }

    private void verifyGetField(int bci, OperandStack stack, int curOpcode) {
        // Check CP validity
        PoolConstant pc = poolAt(code.readCPI(bci));
        if (pc.tag() != ConstantPool.Tag.FIELD_REF) {
            throw new VerifyError("Invalid CP constant for GETFIELD: " + pc.toString());
        }
        pc.validate(pool);

        // Obtain field info
        FieldRefConstant frc = (FieldRefConstant) pc;
        assert Validation.validFieldDescriptor(frc.getType(pool));
        Symbol<Type> type = frc.getType(pool);
        if (curOpcode == GETFIELD) {
            // Pop and check receiver
            assert Validation.validClassNameEntry(frc.getHolderKlassName(pool));
            Symbol<Type> fieldHolderType = getTypes().fromName(frc.getHolderKlassName(pool));
            Operand fieldHolder = kindToOperand(fieldHolderType);
            Operand receiver = checkInitAccess(stack.popRef(fieldHolder), fieldHolder);
            checkProtectedField(receiver, fieldHolderType, code.readCPI(bci));
            if (receiver.isArrayType()) {
                throw new VerifyError("Trying to access field of an array type: " + receiver);
            }
        }

        // push result
        Operand op = kindToOperand(type);
        stack.push(op);
    }

    private int verifyLookupSwitch(int bci, OperandStack stack, Locals locals) {
        stack.popInt();
        BytecodeLookupSwitch switchHelper = BytecodeLookupSwitch.INSTANCE;
        // Padding checks
        for (int j = bci + 1; j < switchHelper.getAlignedBci(bci); j++) {
            if (version51OrEarlier() && code.readUByte(j) != 0) {
                throw new VerifyError("non-zero padding for LOOKUPSWITCH");
            }
        }
        int low = 0;
        int high = switchHelper.numberOfCases(code, bci) - 1;
        int previousKey = 0;
        if (high > 0) {
            previousKey = switchHelper.keyAt(code, bci, low);
        }

        // Verify all branches
        for (int i = low; i <= high; i++) {
            int thisKey = switchHelper.keyAt(code, bci, i);
            if (i > 0 && thisKey <= previousKey) {
                throw new VerifyError("Unsorted keys in LookupSwitch");
            }
            branch(bci + switchHelper.offsetAt(code, bci, i), stack, locals);
            previousKey = thisKey;
        }

        // Verify default branch
        return switchHelper.defaultTarget(code, bci);
    }

    private int verifyTableSwitch(int bci, OperandStack stack, Locals locals) {
        stack.popInt();
        BytecodeTableSwitch switchHelper = BytecodeTableSwitch.INSTANCE;
        // Padding checks
        for (int j = bci + 1; j < switchHelper.getAlignedBci(bci); j++) {
            if (version51OrEarlier() && code.readUByte(j) != 0) {
                throw new VerifyError("non-zero padding for TABLESWITCH");
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

    private void verifyJSR(int bci, OperandStack stack, Locals locals) {
        if (version51OrLater()) {
            throw new VerifyError("JSR/RET bytecode in version >= 51");
        }
        if (stackFrames[bci] == null) {
            stackFrames[bci] = spawnStackFrame(stack, locals);
        }
        // Push bit vector
        int targetBCI = code.readBranchDest(bci);
        stack.push(new ReturnAddressOperand(bci, targetBCI));
        locals.subRoutineModifications = new SubroutineModificationStack(locals.subRoutineModifications, new boolean[maxLocals], bci);
        branch(targetBCI, stack, locals);
        bciStates[bci] = setStatus(bciStates[bci], DONE);
    }

    private void verifyRET(int bci, OperandStack stack, Locals locals) {
        if (version51OrLater()) {
            throw new VerifyError("JSR/RET bytecode in version >= 51");
        }
        int pos = 0;
        ReturnAddressOperand ra = locals.loadReturnAddress(code.readLocalIndex(bci));
        ReturnAddressOperand prev = null;
        while (pos < ra.targetBCIs.size()) {
            prev = ra;
            int target = ra.targetBCIs.get(pos++);
            checkAndSetReturnedTo(target, bci);
            Locals toMerge = getSubroutineReturnLocals(target, locals);
            branch(code.nextBCI(target), stack, toMerge);
            // Sanity check: branching did not overwrite the return address being
            // verified
            ra = locals.loadReturnAddress(code.readLocalIndex(bci));
            if (ra != prev) {
                pos = 0;
            }
        }
    }

    private MethodRefConstant getMethodRefConstant(int bci) {
        PoolConstant pc = poolAt(code.readCPI(bci));
        if (!(pc instanceof MethodRefConstant)) {
            throw new VerifyError("Invalid CP constant for a MethodRef: " + pc.getClass().getName());
        }
        pc.validate(pool);
        return (MethodRefConstant) pc;
    }

    private static boolean isClassInit(Symbol<Name> calledMethodName) {
        return Name._clinit_.equals(calledMethodName);
    }

    private static boolean isInstanceInit(Symbol<Name> calledMethodName) {
        return Name._init_.equals(calledMethodName);
    }

    private Operand popSignatureGetReturnOP(OperandStack stack, MethodRefConstant mrc) {
        Symbol<Signature> calledMethodSignature = mrc.getSignature(pool);
        Operand[] parsedSig = getOperandSig(calledMethodSignature);

        assert parsedSig.length > 0 : "Method ref with no return value !";

        // Pop arguments
        for (int i = parsedSig.length - 2; i >= 0; i--) {
            stack.pop(parsedSig[i]);
        }
        return parsedSig[parsedSig.length - 1];
    }

    private void verifyInvokeInterface(int bci, OperandStack stack) {
        // Check padding.
        if (code.readUByte(bci + 4) != 0) {
            throw new VerifyError("4th byte after INVOKEINTERFACE must be 0.");
        }

        // Check CP validity
        MethodRefConstant mrc = getMethodRefConstant(bci);

        // Checks versioning
        Symbol<Name> calledMethodName = mrc.getName(pool);

        // Check guest is not invoking <clinit>
        if (isClassInit(calledMethodName)) {
            throw new VerifyError("Invocation of class initializer!");
        }

        // Only INVOKESPECIAL can call <init>
        if (isInstanceInit(calledMethodName)) {
            throw new VerifyError("Invocation of instance initializer with opcode other than INVOKESPECIAL");
        }

        Symbol<Signature> calledMethodSignature = mrc.getSignature(pool);
        Operand[] parsedSig = getOperandSig(calledMethodSignature);

        // Check signature is well formed.
        assert parsedSig.length > 0 : "Method ref with no return value !";

        // Pop arguments
        // Check signature conforms with count argument
        int count = code.readUByte(bci + 3);
        if (count <= 0) {
            throw new VerifyError("Invalid count argument for INVOKEINTERFACE: " + count);
        }
        int descCount = 1; // Has a receiver.
        for (int i = parsedSig.length - 2; i >= 0; i--) {
            descCount++;
            if (isType2(parsedSig[i])) {
                descCount++;
            }
            stack.pop(parsedSig[i]);
        }
        if (count != descCount) {
            throw new VerifyError("Inconsistent redundant argument count for INVOKEINTERFACE.");
        }

        assert Validation.validClassNameEntry(mrc.getHolderKlassName(pool));
        Symbol<Type> methodHolder = getTypes().fromName(mrc.getHolderKlassName(pool));

        Operand methodHolderOp = kindToOperand(methodHolder);

        checkInit(stack.popRef(methodHolderOp));

        Operand returnOp = parsedSig[parsedSig.length - 1];
        if (!(returnOp == Void)) {
            stack.push(returnOp);
        }
    }

    private void verifyInvokeStatic(int bci, OperandStack stack) {
        // Check CP validity
        MethodRefConstant mrc = getMethodRefConstant(bci);

        // Checks versioning
        if (version51OrEarlier() && mrc.tag() == INTERFACE_METHOD_REF) {
            throw new VerifyError("invokeStatic refers to an interface method with classfile version " + majorVersion);
        }
        Symbol<Name> calledMethodName = mrc.getName(pool);

        // Check guest is not invoking <clinit>
        assert !isClassInit(calledMethodName) : "Invocation of class initializer!";

        // Only INVOKESPECIAL can call <init>
        if (isInstanceInit(calledMethodName)) {
            throw new VerifyError("Invocation of instance initializer with opcode other than INVOKESPECIAL");
        }

        Operand returnOp = popSignatureGetReturnOP(stack, mrc);
        assert Validation.validClassNameEntry(mrc.getHolderKlassName(pool));

        if (!(returnOp == Void)) {
            stack.push(returnOp);
        }
    }

    private void verifyInvokeSpecial(int bci, OperandStack stack, Locals locals) {
        // Check CP validity
        MethodRefConstant mrc = getMethodRefConstant(bci);

        // Checks versioning
        if (version51OrEarlier() && mrc.tag() == INTERFACE_METHOD_REF) {
            throw new VerifyError("invokeSpecial refers to an interface method with classfile version " + majorVersion);
        }
        Symbol<Name> calledMethodName = mrc.getName(pool);

        // Check guest is not invoking <clinit>
        assert !isClassInit(calledMethodName) : "Invocation of class initializer!";

        Operand returnOp = popSignatureGetReturnOP(stack, mrc);

        assert Validation.validClassNameEntry(mrc.getHolderKlassName(pool));
        Symbol<Type> methodHolder = getTypes().fromName(mrc.getHolderKlassName(pool));
        Operand methodHolderOp = kindToOperand(methodHolder);

        if (isInstanceInit(calledMethodName)) {
            UninitReferenceOperand toInit = (UninitReferenceOperand) stack.popUninitRef(methodHolderOp);
            if (toInit.isUninitThis()) {
                if (!Name._init_.equals(methodName)) {
                    throw new VerifyError("Encountered UninitializedThis outside of Constructor: " + toInit);
                }
                calledConstructor = true;
            } else {
                if (code.opcode(toInit.newBCI) != NEW) {
                    throw new VerifyError("There is no NEW bytecode at bci: " + toInit.newBCI);
                }
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
                if (toInit.getType() != methodHolder) {
                    throw new VerifyError("Calling wrong initializer for a new object.");
                }
            }
            Operand stackOp = stack.initUninit(toInit);
            locals.initUninit(toInit, stackOp);

            checkProtectedMethod(stackOp, methodHolder, code.readCPI(bci));
        } else {
            if (!checkMethodSpecialAccess(methodHolderOp)) {
                throw new VerifyError("invokespecial must specify a method in this class or a super class");
            }
            Operand stackOp = checkInit(stack.popRef(methodHolderOp));
            /**
             * 4.10.1.9.invokespecial:
             *
             * invokespecial, for other than an instance initialization method, must name a method
             * in the current class/interface or a superclass / superinterface.
             */
            if (!checkReceiverSpecialAccess(stackOp)) {
                throw new VerifyError("Invalid use of INVOKESPECIAL");
            }
        }
        if (!(returnOp == Void)) {
            stack.push(returnOp);
        }
    }

    private void verifyInvokeVirtual(int bci, OperandStack stack) {
        // Check CP validity
        MethodRefConstant mrc = getMethodRefConstant(bci);

        Symbol<Name> calledMethodName = mrc.getName(pool);

        // Check guest is not invoking <clinit>
        assert !isClassInit(calledMethodName) : "Invocation of class initializer!";

        // Only INVOKESPECIAL can call <init>
        if (isInstanceInit(calledMethodName)) {
            throw new VerifyError("Invocation of instance initializer with opcode other than INVOKESPECIAL");
        }

        Operand returnOp = popSignatureGetReturnOP(stack, mrc);

        assert Validation.validClassNameEntry(mrc.getHolderKlassName(pool));
        Symbol<Type> methodHolder = getTypes().fromName(mrc.getHolderKlassName(pool));

        Operand methodHolderOp = kindToOperand(methodHolder);
        Operand stackOp = checkInit(stack.popRef(methodHolderOp));

        // Perform protected method access checks
        checkProtectedMethod(stackOp, methodHolder, code.readCPI(bci));

        if (!(returnOp == Void)) {
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
    private Locals getSubroutineReturnLocals(int target, Locals locals) {
        SubroutineModificationStack subRoutineModifications = locals.subRoutineModifications;
        if (subRoutineModifications == null) {
            throw new VerifyError("RET outside of a subroutine");
        }
        boolean[] subroutineBitArray = subRoutineModifications.subRoutineModifications;
        SubroutineModificationStack nested = subRoutineModifications.next;

        Locals jsrLocals = stackFrames[target].extractLocals();
        Operand[] registers = new Operand[maxLocals];

        boolean nestedRet = jsrLocals.subRoutineModifications != null;

        int depthRoutine = locals.subRoutineModifications.depth();
        int depthRet = nestedRet ? jsrLocals.subRoutineModifications.depth() : 0;

        if (depthRet >= depthRoutine) {
            throw new VerifyError("RET increases subroutine depth.");
        }

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

        Locals res = new Locals(registers);
        res.subRoutineModifications = nested;
        return res;
    }

    // Various access checks

    private boolean checkReceiverSpecialAccess(Operand stackOp) {
        return stackOp.compliesWith(thisOperand) || isMagicAccessor() || checkReceiverHostAccess(stackOp);
    }

    private boolean checkReceiverHostAccess(Operand stackOp) {
        if (thisKlass.getHostClass() != null) {
            return thisKlass.getHostClass().isAssignableFrom(stackOp.getKlass());
        }
        return false;
    }

    private boolean checkMethodSpecialAccess(Operand methodHolder) {
        return thisOperand.compliesWith(methodHolder) || isMagicAccessor() || checkHostAccess(methodHolder);
    }

    private boolean isMagicAccessor() {
        return getMeta().sun_reflect_MagicAccessorImpl.isAssignableFrom(thisOperand.getKlass());
    }

    /**
     * Anonymous classes defined on the fly can call protected members of other classes that are not
     * in their hierarchy. Use their host class to check access.
     */
    private boolean checkHostAccess(Operand methodHolder) {
        if (thisKlass.getHostClass() != null) {
            return methodHolder.getKlass().isAssignableFrom(thisKlass.getHostClass());
        }
        return false;
    }

    // Helper methods

    private void checkProtectedField(Operand stackOp, Symbol<Type> fieldHolderType, int fieldCPI) {
        /**
         * 4.10.1.8.
         *
         * If the name of a class is not the name of any superclass, it cannot be a superclass, and
         * so it can safely be ignored.
         */
        if (stackOp.getType() == thisKlass.getType()) {
            return;
        }
        /**
         * If the MemberClassName is the same as the name of a superclass, the class being resolved
         * may indeed be a superclass. In this case, if no superclass named MemberClassName in a
         * different run-time package has a protected member named MemberName with descriptor
         * MemberDescriptor, the protected check does not apply.
         */
        Klass superKlass = thisKlass.getSuperKlass();
        while (superKlass != null) {
            if (superKlass.getType() == fieldHolderType) {
                final Field field;
                try {
                    field = pool.resolvedFieldAt(thisKlass, fieldCPI).getField();
                } catch (EspressoException e) {
                    if (getMeta().java_lang_IllegalArgumentException.isAssignableFrom(e.getExceptionObject().getKlass())) {
                        throw new VerifyError(EspressoException.getMessage(e.getExceptionObject()));
                    }
                    throw e;
                }
                /**
                 * If there does exist a protected superclass member in a different run-time
                 * package, then load MemberClassName; if the member in question is not protected,
                 * the check does not apply. (Using a superclass member that is not protected is
                 * trivially correct.)
                 */
                if (!field.isProtected()) {
                    return;
                }
                if (!thisKlass.getRuntimePackage().contentEquals(Types.getRuntimePackage(fieldHolderType))) {
                    if (!stackOp.compliesWith(thisOperand)) {
                        /**
                         * Otherwise, use of a member of an object of type Target requires that
                         * Target be assignable to the type of the current class.
                         */
                        throw new VerifyError("Illegal protected field access");
                    }
                }
            }
            superKlass = superKlass.getSuperKlass();
        }
    }

    private void checkProtectedMethod(Operand stackOp, Symbol<Type> methodHolderType, int methodCPI) {
        /**
         * 4.10.1.8.
         *
         * If the name of a class is not the name of any superclass, it cannot be a superclass, and
         * so it can safely be ignored.
         */
        if (stackOp.getType() == thisKlass.getType()) {
            return;
        }
        /**
         * If the MemberClassName is the same as the name of a superclass, the class being resolved
         * may indeed be a superclass. In this case, if no superclass named MemberClassName in a
         * different run-time package has a protected member named MemberName with descriptor
         * MemberDescriptor, the protected check does not apply.
         */
        Klass superKlass = thisKlass.getSuperKlass();
        while (superKlass != null) {
            if (superKlass.getType() == methodHolderType) {
                final Method method;
                try {
                    method = pool.resolvedMethodAt(thisKlass, methodCPI);
                } catch (EspressoException e) {
                    if (getMeta().java_lang_IllegalArgumentException.isAssignableFrom(e.getExceptionObject().getKlass())) {
                        throw new VerifyError(EspressoException.getMessage(e.getExceptionObject()));
                    }
                    throw e;
                }
                /**
                 * If there does exist a protected superclass member in a different run-time
                 * package, then load MemberClassName; if the member in question is not protected,
                 * the check does not apply. (Using a superclass member that is not protected is
                 * trivially correct.)
                 */
                if (!method.isProtected()) {
                    return;
                }
                if (!thisKlass.getRuntimePackage().contentEquals(Types.getRuntimePackage(methodHolderType))) {
                    if (stackOp.isArrayType() && Type.java_lang_Object.equals(methodHolderType) && Name.clone.equals(method.getName())) {
                        // Special case: Arrays pretend to implement Object.clone().
                        return;
                    }
                    if (!stackOp.compliesWith(thisOperand)) {
                        /**
                         * Otherwise, use of a member of an object of type Target requires that
                         * Target be assignable to the type of the current class.
                         */
                        throw new VerifyError("Illegal protected field access");
                    }
                }
                return;
            }
            superKlass = superKlass.getSuperKlass();
        }
    }

    // various helper methods

    private void doReturn(OperandStack stack, Operand toReturn) {
        Operand op = stack.pop(toReturn);
        if (!op.compliesWith(returnOperand)) {
            throw new VerifyError("Invalid return: " + op + ", expected: " + returnOperand);
        }
    }

    static boolean isType2(Operand k) {
        return k == Long || k == Double;
    }

    /**
     * Checks that a given operand is initialized when accessing fields/methods.
     */
    private Operand checkInitAccess(Operand op, Operand holder) {
        if (op.isUninit()) {
            if (isInstanceInit(methodName) && holder.getType() == thisKlass.getType()) {
                return op;
            }
            throw new VerifyError("Accessing field or calling method of an uninitialized reference.");
        }
        return op;
    }

    private static Operand checkInit(Operand op) {
        if (op.isUninit()) {
            throw new VerifyError("Accessing field or calling method of an uninitialized reference.");
        }
        return op;
    }

    private Operand fromJVMType(byte jvmType) {
        // @formatter:off
        switch (jvmType) {
            case 4  : return new ArrayOperand(booleanOperand);
            case 5  : return new ArrayOperand(Char);
            case 6  : return new ArrayOperand(Float);
            case 7  : return new ArrayOperand(Double);
            case 8  : return new ArrayOperand(Byte);
            case 9  : return new ArrayOperand(Short);
            case 10 : return new ArrayOperand(Int);
            case 11 : return new ArrayOperand(Long);
            default:
                throw EspressoError.shouldNotReachHere();
        }
        // @formatter:on
    }

    Operand[] getOperandSig(Symbol<Type>[] toParse) {
        Operand[] operandSig = new Operand[toParse.length];
        for (int i = 0; i < operandSig.length; i++) {
            Symbol<Type> type = toParse[i];
            operandSig[i] = kindToOperand(type);
        }
        return operandSig;
    }

    private Operand[] getOperandSig(Symbol<Signature> toParse) {
        return getOperandSig(getSignatures().parsed(toParse));
    }

    /**
     * Generates an operand from a type.
     */
    private Operand kindToOperand(Symbol<Type> type) {
        // @formatter:off
        switch (Types.getJavaKind(type)) {
            case Boolean: return booleanOperand;
            case Byte   : return Byte;
            case Short  : return Short;
            case Char   : return Char;
            case Int    : return Int;
            case Float  : return Float;
            case Long   : return Long;
            case Double : return Double;
            case Void   : return Void;
            case Object : return spawnFromType(type);
            default:
                throw EspressoError.shouldNotReachHere();
        }
        // @formatter:on
    }

    private Operand spawnFromType(Symbol<Type> type) {
        if (Types.isArray(type)) {
            return new ArrayOperand(kindToOperand(getTypes().getElementalType(type)), Types.getArrayDimensions(type));
        } else {
            return new ReferenceOperand(type, thisKlass);
        }
    }

    private static void xaload(OperandStack stack, PrimitiveOperand kind) {
        stack.popInt();
        Operand op = stack.popArray();
        if (op != Null && !kind.compliesWith(op.getComponent())) {
            throw new VerifyError("Loading " + kind + " from " + op + " array.");
        }
        stack.push(kind.toStack());
    }

    private static void xastore(OperandStack stack, PrimitiveOperand kind) {
        stack.pop(kind);
        stack.popInt();
        Operand array = stack.popArray();
        if (array != Null && !kind.compliesWith(array.getComponent())) {
            throw new VerifyError("got array of type: " + array + ", while storing a " + kind);
        }
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
    public StackFrame mergeFrames(OperandStack stack, Locals locals, StackFrame stackMap) {
        if (stackMap == null) {
            if (useStackMaps) {
                throw new VerifyError("No stack frame on jump target");
            }
            return spawnStackFrame(stack, locals);
        }
        // Merge stacks
        Operand[] mergedStack = null;
        int mergeIndex = stack.mergeInto(stackMap);
        if (mergeIndex != -1) {
            if (useStackMaps) {
                throw new VerifyError("Wrong stack map frames in class file.");
            }
            mergedStack = new Operand[stackMap.stack.length];
            System.arraycopy(stackMap.stack, 0, mergedStack, 0, mergeIndex);
            for (int i = mergeIndex; i < mergedStack.length; i++) {
                Operand stackOp = stack.stack[i];
                Operand frameOp = stackMap.stack[i];
                if (!stackOp.compliesWithInMerge(frameOp)) {
                    Operand result = stackOp.mergeWith(frameOp);
                    if (result == null) {
                        throw new VerifyError("Cannot merge " + stackOp + " with " + frameOp);
                    }
                    mergedStack[i] = result;
                } else {
                    mergedStack[i] = frameOp;
                }
            }
        }
        // Merge locals
        Operand[] mergedLocals = null;
        mergeIndex = locals.mergeInto(stackMap);
        if (mergeIndex != -1) {
            if (useStackMaps) {
                throw new VerifyError("Wrong local map frames in class file: " + thisKlass + '.' + methodName);
            }

            mergedLocals = new Operand[maxLocals];
            Operand[] frameLocals = stackMap.locals;
            System.arraycopy(frameLocals, 0, mergedLocals, 0, mergeIndex);
            for (int i = mergeIndex; i < mergedLocals.length; i++) {
                Operand localsOp = locals.registers[i];
                Operand frameOp = frameLocals[i];
                if (!localsOp.compliesWithInMerge(frameOp)) {
                    Operand result = localsOp.mergeWith(frameOp);
                    if (result == null) {
                        // We can ALWAYS merge locals. just put Invalid if failure
                        mergedLocals[i] = Invalid;
                    } else {
                        mergedLocals[i] = result;
                    }
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
            return new StackFrame(stack, mergedLocals, stackMap.subroutineModificationStack);
        }
        return new StackFrame(mergedStack, stack.size, stack.top, mergedLocals == null ? locals.registers : mergedLocals, stackMap.subroutineModificationStack);
    }

    private static StackFrame spawnStackFrame(OperandStack stack, Locals locals) {
        return new StackFrame(stack, locals);
    }
}
