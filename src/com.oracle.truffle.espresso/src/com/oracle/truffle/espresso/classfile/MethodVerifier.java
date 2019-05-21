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
package com.oracle.truffle.espresso.classfile;

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
import static com.oracle.truffle.espresso.bytecode.Bytecodes.SWAP;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.TABLESWITCH;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.WIDE;

import java.util.Arrays;

import com.oracle.truffle.espresso.bytecode.BytecodeLookupSwitch;
import com.oracle.truffle.espresso.bytecode.BytecodeStream;
import com.oracle.truffle.espresso.bytecode.BytecodeTableSwitch;
import com.oracle.truffle.espresso.bytecode.Bytecodes;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.impl.ContextAccess;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.runtime.EspressoContext;

/**
 * Extremely light-weight java bytecode verifier. Performs only very basic verifications.
 *
 * In particular, it checks stack consistency (ie: checks that instructions do not pop an empty
 * stack, or that they do not push further than maxStack) This verifier also performs primitive type
 * checks, where all references are considered to be of the Object class.
 *
 * The primary goal of this verifier is to detect class files that are blatantly ill-formed, and
 * does not replace a complete bytecode verifier.
 */
public class MethodVerifier implements ContextAccess {
    private final Klass thisKlass;
    private final BytecodeStream code;
    private final RuntimeConstantPool pool;
    private final Symbol<Signature> sig;
    private final Symbol<Name> methodName;
    private final boolean isStatic;
    private final int maxStack;
    private final int maxLocals;
    private final byte[] verified;
    private final StackFrame[] stackFrames;

    @Override
    public EspressoContext getContext() {
        return thisKlass.getContext();
    }

    static abstract class Operand {
        protected JavaKind kind;

        Operand(JavaKind kind) {
            this.kind = kind;
        }

        JavaKind getKind() {
            return kind;
        }

        boolean isArrayType() {
            return false;
        }

        boolean isReference() {
            return false;
        }

        boolean isPrimitive() {
            return false;
        }

        Operand getComponent() {
            return null;
        }

        Operand getElemental() {
            return null;
        }

        Symbol<Type> getType() {
            return null;
        }

        Klass getKlass() {
            return null;
        }

        int getDimensions() {
            return -1;
        }

        boolean isUninit() {
            return false;
        }

        abstract boolean canMerge(Operand other);
    }

    static class PrimitiveOperand extends Operand {
        PrimitiveOperand(JavaKind kind) {
            super(kind);
        }

        @Override
        boolean isPrimitive() {
            return true;
        }

        @Override
        boolean canMerge(Operand other) {
            return other.isPrimitive() && other.getKind() == this.kind;
        }

        @Override
        public String toString() {
            return kind.toString();
        }
    }

    static class ReferenceOperand extends Operand {
        protected Symbol<Type> type;
        protected Klass thisKlass;

        // Load if needed.
        protected Klass klass = null;

        ReferenceOperand(Symbol<Type> type, Klass thisKlass) {
            super(JavaKind.Object);
            assert type == null || !Types.isPrimitive(type);
            this.type = type;
            this.thisKlass = thisKlass;
        }

        ReferenceOperand(Klass klass, Klass thisKlass) {
            super(JavaKind.Object);
            assert type == null || !Types.isPrimitive(type);
            this.type = klass.getType();
            this.klass = klass;
            this.thisKlass = thisKlass;
        }

        @Override
        boolean isReference() {
            return true;
        }

        @Override
        Symbol<Type> getType() {
            return type;
        }

        @Override
        Klass getKlass() {
            if (klass == null) {
                klass = thisKlass.getMeta().loadKlass(type, thisKlass.getDefiningClassLoader());
                if (klass == null) {
                    throw new NoClassDefFoundError(type.toString());
                }
            }
            return klass;
        }

        @Override
        boolean canMerge(Operand other) {
            if (other.isReference()) {
                if (type == null || other.getType() == this.type) {
                    return true;
                }
                return other.getKlass().isAssignableFrom(getKlass());
            }
            return false;
        }

        @Override
        public String toString() {
            return type == null ? "null" : type.toString();
        }
    }

    static class ArrayOperand extends Operand {
        private int dimensions;
        private Operand elemental;
        private Operand component = null;

        ArrayOperand(Operand elemental, int dimensions) {
            super(JavaKind.Object);
            assert !elemental.isArrayType();
            this.dimensions = dimensions;
            this.elemental = elemental;
        }

        ArrayOperand(Operand elemental) {
            this(elemental, 1);
        }

        @Override
        boolean canMerge(Operand other) {
            if (other.isArrayType()) {
                if (other.getDimensions() < getDimensions()) {
                    return other.getElemental().isReference() && other.getElemental().getType() == Type.Object;
                } else if (other.getDimensions() == getDimensions()) {
                    return elemental.canMerge(other.getElemental());
                }
                return false;
            }
            return other.isReference() && other.getType() == Type.Object;
        }

        @Override
        boolean isReference() {
            return true;
        }

        @Override
        boolean isArrayType() {
            return true;
        }

        @Override
        Operand getComponent() {
            if (component == null) {
                if (dimensions == 1) {
                    component = elemental;
                } else {
                    component = new ArrayOperand(elemental, dimensions - 1);
                }
            }
            return component;
        }

        @Override
        Operand getElemental() {
            return elemental;
        }

        @Override
        public String toString() {
            if (dimensions == 1) {
                return "[" + getElemental();
            }
            return "[dim:" + dimensions + "]" + getElemental();
        }

        @Override
        int getDimensions() {
            return dimensions;
        }
    }

    static class UninitReferenceOperand extends ReferenceOperand {
        UninitReferenceOperand(Symbol<Type> type, Klass thisKlass) {
            super(type, thisKlass);
        }

        UninitReferenceOperand(Klass klass, Klass thisKlass) {
            super(klass, thisKlass);
        }

        @Override
        boolean isUninit() {
            return true;
        }

        ReferenceOperand init() {
            if (klass == null) {
                return new ReferenceOperand(type, thisKlass);
            } else {
                return new ReferenceOperand(klass, thisKlass);
            }
        }
    }

    static boolean isType2(Operand k) {
        return k == Long || k == Double;
    }

    static private final PrimitiveOperand Int = new PrimitiveOperand(JavaKind.Int);
    static private final PrimitiveOperand Byte = new PrimitiveOperand(JavaKind.Byte);
    static private final PrimitiveOperand Char = new PrimitiveOperand(JavaKind.Char);
    static private final PrimitiveOperand Short = new PrimitiveOperand(JavaKind.Short);
    static private final PrimitiveOperand Float = new PrimitiveOperand(JavaKind.Float);
    static private final PrimitiveOperand Double = new PrimitiveOperand(JavaKind.Double);
    static private final PrimitiveOperand Long = new PrimitiveOperand(JavaKind.Long);
    static private final PrimitiveOperand Void = new PrimitiveOperand(JavaKind.Void);
    static private final PrimitiveOperand Invalid = new PrimitiveOperand(JavaKind.Illegal);

    static private final Operand ReturnAddress = new PrimitiveOperand(JavaKind.ReturnAddress);

    static private final Operand Null = new Operand(JavaKind.Object) {
        @Override
        boolean canMerge(Operand other) {
            return other.isReference();
        }

        @Override
        boolean isReference() {
            return true;
        }

        @Override
        public String toString() {
            return "null";
        }

        @Override
        boolean isPrimitive() {
            return false;
        }

        @Override
        Operand getComponent() {
            return this;
        }
    };

    private final Operand jlClass;
    private final Operand jlString;
    private final Operand MethodType;
    private final Operand MethodHandle;
    private final Operand Throwable;

    static private final byte UNREACHABLE = 0;
    static private final byte UNSEEN = 1;
    static private final byte DONE = 2;

    /**
     * Instantiates a MethodVerifier for the given method
     *
     * @param maxStack Maximum amount of operand on the stack during execution
     * @param code Raw bytecode representation for the method to verify
     * @param pool The constant pool to be used with this method.
     */
    private MethodVerifier(int maxStack, int maxLocals, byte[] code, RuntimeConstantPool pool, Symbol<Signature> sig, boolean isStatic, Klass thisKlass, Symbol<Name> methodName) {
        this.code = new BytecodeStream(code);
        this.maxStack = maxStack;
        this.maxLocals = maxLocals;
        this.pool = pool;
        this.verified = new byte[code.length];
        this.stackFrames = new StackFrame[code.length];
        this.sig = sig;
        this.isStatic = isStatic;
        this.thisKlass = thisKlass;
        this.methodName = methodName;

        jlClass = new ReferenceOperand(Type.Class, thisKlass);
        jlString = new ReferenceOperand(Type.String, thisKlass);
        MethodType = new ReferenceOperand(Type.MethodType, thisKlass);
        MethodHandle = new ReferenceOperand(Type.MethodHandle, thisKlass);
        Throwable = new ReferenceOperand(Type.Throwable, thisKlass);
    }

    /**
     * Utility for ease of use in Espresso
     *
     * @param m the method to verify
     * @return true, or throws ClassFormatError or VerifyError.
     */
    public static boolean verify(Method m) {
        CodeAttribute codeAttribute = m.getCodeAttribute();
        if (codeAttribute == null) {
            return true;
        }
        return new MethodVerifier(codeAttribute.getMaxStack(), codeAttribute.getMaxLocals(), codeAttribute.getCode(), m.getRuntimeConstantPool(), m.getRawSignature(), m.isStatic(),
                        m.getDeclaringKlass(), m.getName()).verify();
    }

    /**
     * Performs the verification for the method associated with this MethodVerifier.
     *
     * @return true or throws ClassFormatError or VerifyError
     */
    private synchronized boolean verify() {
        clear();
        if (code.endBCI() == 0) {
            throw new VerifyError("Control flow falls through code end");
        }
        int nextBCI = 0;
        Stack stack = new Stack(maxStack);
        Locals locals = new Locals(this, maxLocals, sig, isStatic);
        while (verified[nextBCI] != DONE) {
            nextBCI = verifySafe(nextBCI, stack, locals);
            if (nextBCI >= code.endBCI()) {
                throw new VerifyError("Control flow falls through code end");
            }
        }
        return true;
    }

    private void clear() {
        for (int i = 0; i < verified.length; i++) {
            verified[i] = UNREACHABLE;
        }
        int bci = 0;
        while (bci < code.endBCI()) {
            int bc = code.currentBC(bci);
            if (bc > QUICK) {
                throw new VerifyError("invalid bytecode: " + bc);
            }
            verified[bci] = UNSEEN;
            bci = code.nextBCI(bci);
        }
    }

    private boolean branch(int BCI, Stack stack, Locals locals) {
        if (verified[BCI] == UNREACHABLE) {
            throw new VerifyError("Jump to the middle of an instruction: " + BCI);
        }
        if (verified[BCI] == DONE) {
            return true;
        }
        if (stackFrames[BCI] != null) {
            if (!stack.mergeInto(stackFrames[BCI])) {
                throw new VerifyError();
            }
        } else {
            stackFrames[BCI] = stack.toStackFrame();
        }
        Stack newStack = stack.copy();
        Locals newLocals = locals.copy();
        int nextBCI = BCI;
        if (nextBCI >= code.endBCI()) {
            throw new VerifyError("Control flow falls through code end");
        }
        while (verified[nextBCI] != DONE) {
            nextBCI = verifySafe(nextBCI, newStack, newLocals);
            if (nextBCI >= code.endBCI()) {
                throw new VerifyError("Control flow falls through code end");
            }
        }
        if (verified[BCI] == UNREACHABLE) {
            throw new VerifyError("Control flow falls between instructions: " + BCI);
        }
        return true;
    }

    private int verifySafe(int BCI, Stack stack, Locals locals) {
        try {
            return verify(BCI, stack, locals);
        } catch (IndexOutOfBoundsException e) {
            throw new VerifyError("Inconsistent Stack/Local access at index: " + e.getMessage());
        }
    }

    private Operand fromTag(ConstantPool.Tag tag) {
        // @formatter:off
        // checkstyle: stop
        switch (tag) {
            case INTEGER:       return Int;
            case FLOAT:         return Float;
            case LONG:          return Long;
            case DOUBLE:        return Double;
            case CLASS:         return jlClass;
            case STRING:        return jlString;
            case METHODHANDLE:  return MethodHandle;
            case METHODTYPE:    return MethodType;
            default:
                throw new VerifyError("invalid CP load: " + tag);
        }
        // checkstyle: resume
        // @formatter:on
    }

    private int verify(int BCI, Stack stack, Locals locals) {
        if (verified[BCI] == UNREACHABLE) {
            throw new VerifyError("Jump to the middle of an instruction: " + BCI);
        }
        verified[BCI] = DONE;
        int curOpcode;
        curOpcode = code.opcode(BCI);
        if (!(curOpcode <= QUICK)) {
            throw new VerifyError("invalid bytecode: " + code.readByte(BCI - 1));
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
                case LDC_W:
                case LDC2_W: stack.push(fromTag(pool.at(code.readCPI(BCI)).tag())); break;

                case ILOAD: locals.load(code.readLocalIndex(BCI), Int);     stack.pushInt();    break;
                case LLOAD: locals.load(code.readLocalIndex(BCI), Long);    stack.pushLong();   break;
                case FLOAD: locals.load(code.readLocalIndex(BCI), Float);   stack.pushFloat();  break;
                case DLOAD: locals.load(code.readLocalIndex(BCI), Double);  stack.pushDouble(); break;
                case ALOAD: stack.push(locals.loadRef(code.readLocalIndex(BCI))); break;
                
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
                    if (!op.getComponent().isReference()) {
                        throw new VerifyError("Loading reference from " + op + " array.");
                    }
                    stack.push(op.getComponent());
                    break;
                }
                
                case BALOAD: xaload(stack, Byte);  break;
                case CALOAD: xaload(stack, Char);  break;
                case SALOAD: xaload(stack, Short); break;

                case ISTORE: stack.popInt();     locals.store(code.readLocalIndex(BCI), Int);    break;
                case LSTORE: stack.popLong();    locals.store(code.readLocalIndex(BCI), Long);   break;
                case FSTORE: stack.popFloat();   locals.store(code.readLocalIndex(BCI), Float);  break;
                case DSTORE: stack.popDouble();  locals.store(code.readLocalIndex(BCI), Double); break;
                
                case ASTORE: locals.store(code.readLocalIndex(BCI), stack.popObjOrRA()); break;

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
                    if (!array.getComponent().isReference()) {
                        throw new VerifyError("Trying to store " + toStore + " in " + array);
                    }
                    break;
                }
                
                case BASTORE: xastore(stack, Byte); break;
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

                case IINC: locals.load(code.readLocalIndex(BCI), Int); break;

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
                case IFLE: stack.popInt(); branch(code.readBranchDest(BCI), stack, locals); break;
                
                case IF_ICMPEQ: // fall through
                case IF_ICMPNE: // fall through
                case IF_ICMPLT: // fall through
                case IF_ICMPGE: // fall through
                case IF_ICMPGT: // fall through
                case IF_ICMPLE: stack.popInt(); stack.popInt(); branch(code.readBranchDest(BCI), stack, locals); break;
                
                case IF_ACMPEQ: // fall through
                case IF_ACMPNE: stack.popRef(); stack.popRef(); branch(code.readBranchDest(BCI), stack, locals); break;

                case GOTO:
                case GOTO_W: branch(code.readBranchDest(BCI), stack, locals); return BCI;
                
                case IFNULL: // fall through
                case IFNONNULL: stack.popRef(); branch(code.readBranchDest(BCI), stack, locals); break;
                
                case JSR: // fall through
                case JSR_W: {
                    stack.push(ReturnAddress);
                    branch(code.readBranchDest(BCI), stack, locals);
                    // RET will need to branch here to finish the job.
                    return BCI;
                }
                case RET: {
                    locals.load(code.readLocalIndex(BCI), ReturnAddress);
                    return BCI;
                }
                
                case TABLESWITCH: {
                    stack.popInt();
                    BytecodeTableSwitch switchHelper = code.getBytecodeTableSwitch();
                    int low = switchHelper.lowKey(BCI);
                    int high = switchHelper.highKey(BCI);
                    for (int i = low; i < high; i++) {
                        branch(switchHelper.targetAt(BCI, i - low), stack, locals);
                    }
                    return switchHelper.defaultTarget(BCI);
                }
                case LOOKUPSWITCH: {
                    stack.popInt();
                    BytecodeLookupSwitch switchHelper = code.getBytecodeLookupSwitch();
                    int low = 0;
                    int high = switchHelper.numberOfCases(BCI) - 1;
                    for (int i = low; i <= high; i++) {
                        branch(BCI + switchHelper.offsetAt(BCI, i), stack, locals);
                    }
                    return switchHelper.defaultTarget(BCI);
                }
                
                case IRETURN: stack.popInt(); return BCI;
                case LRETURN: stack.popLong(); return BCI;
                case FRETURN: stack.popFloat(); return BCI;
                case DRETURN: stack.popDouble(); return BCI;
                case ARETURN: stack.popRef(); return BCI;
                case RETURN: return BCI; 
                
                case GETSTATIC:
                case GETFIELD: {
                    PoolConstant pc = pool.at(code.readCPI(BCI));
                    if (!(pc instanceof FieldRefConstant)) {
                        throw new VerifyError();
                    }
                    FieldRefConstant frc = (FieldRefConstant) pc;
                    Symbol<Type> type = frc.getType(pool);
                    Operand op = kindToOperand(type);
                    if (curOpcode == GETFIELD) {
                        Symbol<Type> holderType = getTypes().fromName(frc.getHolderKlassName(pool));
                        Operand receiver = checkInit(stack, stack.popRef(kindToOperand(holderType)));
                        if (receiver.isArrayType()) {
                            throw new VerifyError("Trying to access field of an array type: " + receiver);
                        }
                    }
                    stack.push(op);
                    break;
                }
                
                case PUTSTATIC:
                case PUTFIELD: {
                    PoolConstant pc = pool.at(code.readCPI(BCI));
                    if (!(pc instanceof FieldRefConstant)) {
                        throw new VerifyError();
                    }
                    FieldRefConstant frc = (FieldRefConstant) pc;
                    Symbol<Type> fieldType = frc.getType(pool);
                    Operand op = kindToOperand(fieldType);
                    stack.pop(op);
                    if (curOpcode == PUTFIELD) {
                        Symbol<Type> holderType = getTypes().fromName(frc.getHolderKlassName(pool));
                        Operand receiver = checkInit(stack, stack.popRef(kindToOperand(holderType)));
                        if (receiver.isArrayType()) {
                            throw new VerifyError("Trying to access field of an array type: " + receiver);
                        }
                    }
                    break;
                }

                case INVOKEVIRTUAL:
                case INVOKESPECIAL:
                case INVOKESTATIC:
                case INVOKEINTERFACE: {
                    PoolConstant pc = pool.at(code.readCPI(BCI));
                    if (!(pc instanceof MethodRefConstant)) {
                        throw new VerifyError("Invalid CP constant for a MethodRef: " + pc.getClass().getName());
                    }
                    MethodRefConstant mrc = (MethodRefConstant) pc;
                    if (mrc.getName(pool) == Symbol.Name.CLINIT) {
                        throw new VerifyError("Invocation of class initializer!");
                    }
                    Operand[] parsedSig = getOperandSig(mrc.getSignature(pool));
                    if (parsedSig.length == 0) {
                        throw new ClassFormatError("Method ref with no return value !");
                    }
                    for (int i = parsedSig.length - 2; i >= 0; i--) {
                        stack.pop(parsedSig[i]);
                    }
                    if (curOpcode == INVOKESPECIAL && mrc.getName(pool) == Symbol.Name.INIT) {
                        Operand op = getOperand(mrc);
                        Operand toInit = stack.popUninitRef(op);
                        stack.initUninit((UninitReferenceOperand)toInit);
                    } else if (curOpcode != INVOKESTATIC) {
                        Operand op = getOperand(mrc);
                        checkInit(stack, stack.popRef(op));
                    }
                    Operand returnKind = parsedSig[parsedSig.length - 1];
                    if (!(returnKind == Void)) {
                        stack.push(returnKind);
                    }
                    break;
                }

                case NEW: {
                    PoolConstant pc = pool.at(code.readCPI(BCI));
                    if (!(pc instanceof ClassConstant)) {
                        throw new VerifyError("Invalid CP constant for a Class: " + pc.toString());
                    }
                    ClassConstant cc = (ClassConstant) pc;
                    Symbol<Type> type = getTypes().fromName(cc.getName(pool));
                    if (Types.isPrimitive(type) || Types.isArray(type)) {
                        throw new VerifyError("use NEWARRAY for creating array or primitive type: " + type);
                    }
                    Operand op = new UninitReferenceOperand(type, thisKlass);
                    stack.push(op);
                    break;
                }
                case NEWARRAY: {
                    byte jvmType = code.readByte(BCI);
                    if (jvmType < 4 || jvmType > 11) {
                        throw new VerifyError("invalid jvmPrimitiveType for NEWARRAY: " + jvmType);
                    }
                    stack.popInt();
                    stack.push(fromJVMType(jvmType));
                    break;
                }
                case ANEWARRAY: {
                    int CPI = code.readCPI(BCI);
                    PoolConstant pc = pool.at(CPI);
                    if (!(pc instanceof ClassConstant)) {
                        throw new VerifyError("Invalid CP constant for ANEWARRAY: " + pc.toString());
                    }
                    ClassConstant cc = (ClassConstant) pc;
                    Symbol<Type> type = getTypes().fromName(cc.getName(pool));
                    if (Types.isPrimitive(type)) {
                        throw new VerifyError("Primitive type for ANEWARRAY: " + type);
                    }
                    stack.popInt();
                    Operand ref = spawnFromType(type);
                    if (ref.isArrayType()) {
                        stack.push(new ArrayOperand(ref.getElemental(), ref.getDimensions() + 1));
                    } else {
                        stack.push(new ArrayOperand(ref));
                    }
                    break;
                }
                
                case ARRAYLENGTH:
                    stack.popArray();
                    stack.pushInt();
                    break;

                case ATHROW:
                    stack.popRef(Throwable);
                    return BCI;

                case CHECKCAST: {
                    stack.popRef();
                    int CPI = code.readCPI(BCI);
                    PoolConstant pc = pool.at(CPI);
                    if (!(pc instanceof ClassConstant)) {
                        throw new VerifyError("Invalid CP constant for ANEWARRAY: " + pc.toString());
                    }
                    ClassConstant cc = (ClassConstant) pc;
                    Symbol<Type> type = getTypes().fromName(cc.getName(pool));
                    if (Types.isPrimitive(type)) {
                        throw new VerifyError("Primitive type for ANEWARRAY: " + type);
                    }
                    stack.push(spawnFromType(type));
                    break;
                }
                
                case INSTANCEOF: {
                    stack.popRef();
                    int CPI = code.readCPI(BCI);
                    PoolConstant pc = pool.at(CPI);
                    if (!(pc instanceof ClassConstant)) {
                        throw new VerifyError("Invalid CP constant for ANEWARRAY: " + pc.toString());
                    }
                    ClassConstant cc = (ClassConstant) pc;
                    Symbol<Type> type = getTypes().fromName(cc.getName(pool));
                    if (Types.isPrimitive(type)) {
                        throw new VerifyError("Primitive type for ANEWARRAY: " + type);
                    }
                    stack.pushInt();
                    break;
                }

                case MONITORENTER:
                    stack.popRef();
                    break;
                case MONITOREXIT:
                    stack.popRef();
                    break;

                case WIDE:
                    curOpcode = code.currentBC(BCI);
                    if (!wideOpcodes(curOpcode)) {
                        throw new VerifyError("invalid widened opcode: " + Bytecodes.nameOf(curOpcode));
                    }
                    continue wideEscape;

                case MULTIANEWARRAY: {
                    PoolConstant pc = pool.at(code.readCPI(BCI));
                    if (!(pc instanceof ClassConstant)) {
                        throw new VerifyError("Invalid CP constant for a Class: " + pc.toString());
                    }
                    ClassConstant cc = (ClassConstant) pc;
                    Symbol<Type> type = getTypes().fromName(cc.getName(pool));
                    if (!Types.isArray(type)) {
                        throw new VerifyError("Class " + type + " for MULTINEWARRAY is not an array type.");
                    }
                    int dim = code.readUByte(BCI + 3);
                    if (dim <= 0) {
                        throw new VerifyError("Negative or 0 dimension for MULTIANEWARRAY: " + dim);
                    }
                    if (Types.getArrayDimensions(type) < dim) {
                        throw new VerifyError("Incompatible dimensions from constant pool: " + Types.getArrayDimensions(type) + " and instruction: " + dim);
                    }
                    for (int i = 0; i < dim; i++) {
                        stack.popInt();
                    }
                    stack.push(kindToOperand(type));
                    break;
                }

                case BREAKPOINT: break;

                case INVOKEDYNAMIC: {
                    PoolConstant pc = pool.at(code.readCPI(BCI));
                    if (!(pc instanceof InvokeDynamicConstant)) {
                        throw new VerifyError("Invalid CP constant for an InvokeDynamic: " + pc.getClass().getName());
                    }

                    InvokeDynamicConstant idc = (InvokeDynamicConstant) pc;
                    if (!(pool.at(idc.getNameAndTypeIndex()).tag() == ConstantPool.Tag.NAME_AND_TYPE)) {
                        throw new ClassFormatError("Invalid constant pool !");
                    }
                    Symbol<Symbol.Name> name = idc.getName(pool);
                    if (name == Symbol.Name.INIT || name == Symbol.Name.CLINIT) {
                        throw new VerifyError("Invalid bootstrap method name: " + name);
                    }
                    Operand[] parsedSig = getOperandSig(idc.getSignature(pool));
                    if (parsedSig.length == 0) {
                        throw new ClassFormatError("No return descriptor for method");
                    }
                    for (int i = parsedSig.length - 2; i >= 0; i--) {
                        stack.pop(parsedSig[i]);
                    }
                    Operand returnKind = parsedSig[parsedSig.length - 1];
                    if (returnKind != Void) {
                        stack.push(returnKind);
                    }
                    break;
                }
                case QUICK: break;
                default:
            }
            // Checkstyle: resume
            // @formatter:on
            return code.nextBCI(BCI);
        }
    }

    private Operand checkInit(Stack stack, Operand op) {
        if (op.isUninit()) {
            if (methodName != Name.INIT) {
                throw new VerifyError("Accessing field or calling method of an uninitialized reference.");
            }
            return stack.initUninit((UninitReferenceOperand) op);
        }
        return op;
    }

    private Operand getOperand(MethodRefConstant mrc) {
        Symbol<Type> type = getTypes().fromName(mrc.getHolderKlassName(pool));
        return kindToOperand(type);
    }

    private static Operand fromJVMType(byte jvmType) {
        // @formatter:off
        // Checkstyle: stop
        switch (jvmType) {
            case 4  : return new ArrayOperand(Byte);
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
        // Checkstyle: resume
        // @formatter:on
    }

    private Operand[] getOperandSig(Symbol<Symbol.Signature> toParse) {
        Symbol<Type>[] parsedSig = getSignatures().parsed(toParse);
        Operand[] operandSig = new Operand[parsedSig.length];
        for (int i = 0; i < operandSig.length; i++) {
            Symbol<Type> type = parsedSig[i];
            operandSig[i] = kindToOperand(type);
        }
        return operandSig;
    }

    private Operand kindToOperand(Symbol<Type> type) {
        // @formatter:off
        // Checkstyle: stop
        switch (Types.getJavaKind(type)) {
            case Boolean:return Byte;
            case Byte   :return Byte;
            case Short  :return Short;
            case Char   :return Char;
            case Int    :return Int;
            case Float  :return Float;
            case Long   :return Long;
            case Double :return Double;
            case Void   :return Void;
            case Object :return spawnFromType(type);
            default:
                throw EspressoError.shouldNotReachHere();
        }
        // Checkstyle: resume
        // @formatter:on
    }

    private Operand spawnFromType(Symbol<Type> type) {
        if (Types.isArray(type)) {
            return new ArrayOperand(kindToOperand(getTypes().getElementalType(type)), Types.getArrayDimensions(type));
        } else {
            return new ReferenceOperand(type, thisKlass);
        }
    }

    private static void xaload(Stack stack, PrimitiveOperand kind) {
        stack.popInt();
        Operand op = stack.popArray();
        if (op.getComponent() != kind) {
            throw new VerifyError("Loading " + kind + " from " + op + " array.");
        }
        stack.push(kind);
    }

    private static void xastore(Stack stack, PrimitiveOperand kind) {
        stack.pop(kind);
        stack.popInt();
        Operand array = stack.popArray();
        if (array.getComponent() != kind) {
            throw new VerifyError("got array of type: " + array + ", while storing a " + kind);
        }
    }

    private static boolean wideOpcodes(int op) {
        return (op >= ILOAD && op <= ALOAD) || (op >= ISTORE && op <= ASTORE) || (op == RET) || (op == IINC);
    }

    private static class Locals {
        Operand[] registers;

        Locals(MethodVerifier mv, int maxLocals, Symbol<Symbol.Signature> m, boolean isStatic) {
            Operand[] parsedSig = mv.getOperandSig(m);
            if (parsedSig.length - (isStatic ? 1 : 0) > maxLocals) {
                throw new VerifyError("Too much method arguments for the number of locals !");
            }
            this.registers = new Operand[maxLocals];
            Arrays.fill(registers, Invalid);
            int index = 0;
            if (!isStatic) {
                if (mv.methodName == Name.INIT) {
                    registers[index++] = new UninitReferenceOperand(mv.thisKlass, mv.thisKlass);
                } else {
                    registers[index++] = new ReferenceOperand(mv.thisKlass, mv.thisKlass);
                }
            }
            for (int i = 0; i < parsedSig.length - 1; i++) {
                Operand op = parsedSig[i];
                if (op.getKind().isStackInt()) {
                    registers[index++] = Int;
                } else {
                    registers[index++] = op;
                }
                if (isType2(op)) {
                    index++;
                }
            }
        }

        private Locals(Operand[] registers) {
            this.registers = registers;
        }

        Locals copy() {
            return new Locals((registers.clone()));
        }

        Operand load(int index, Operand expected) {
            Operand op = registers[index];
            if (!op.canMerge(expected)) {
                throw new VerifyError("Incompatible register type. Expected: " + expected + ", found: " + op);
            }
            if (isType2(expected)) {
                if (registers[index + 1] != Invalid) {
                    throw new VerifyError("Loading corrupted long primitive from locals!");
                }
            }
            return op;
        }

        Operand loadRef(int index) {
            Operand op = registers[index];
            if (!op.isReference()) {
                throw new VerifyError("Incompatible register type. Expected a reference" + ", found: " + op);
            }
            return op;
        }

        void store(int index, Operand op) {
            registers[index] = op;
            if (isType2(op)) {
                registers[index + 1] = Invalid;
            }
        }
    }

    private static class StackFrame {
        Operand[] stack;

        StackFrame(Operand[] stack) {
            this.stack = stack;
        }
    }

    private static class Stack {
        private final Operand[] stack;
        private int top;
        private int size;

        private Stack(Stack copy) {
            this.stack = new Operand[copy.stack.length];
            this.top = copy.top;
            this.size = copy.size;
            System.arraycopy(copy.stack, 0, this.stack, 0, top);
        }

        Stack(int maxStack) {
            this.stack = new Operand[maxStack];
            this.top = 0;
            this.size = 0;
        }

        void procSize(int modif) {
            size += modif;
            if (size > stack.length) {
                throw new VerifyError("insufficent stack size: " + stack.length);
            }
            if (size < 0) {
                throw new VerifyError("invalid stack access: " + size);
            }
        }

        Stack copy() {
            return new Stack(this);
        }

        void pushInt() {
            push(Int);
        }

        void pushFloat() {
            push(Float);
        }

        void pushDouble() {
            push(Double);
        }

        void pushLong() {
            push(Long);
        }

        void push(Operand kind) {
            procSize(isType2(kind) ? 2 : 1);
            if (size > stack.length) {
                throw new VerifyError("insufficent stack size: " + stack.length);
            }
            if (kind.getKind().isStackInt()) {
                stack[top++] = Int;
            } else {
                stack[top++] = kind;
            }
        }

        void popInt() {
            pop(Int);
        }

        Operand popRef() {
            procSize(-1);
            Operand op = stack[--top];
            if (!op.isReference()) {
                throw new VerifyError("Invalid operand. Expected a reference, found: " + op);
            }
            return op;
        }

        Operand popRef(Operand kind) {
            procSize(-(isType2(kind) ? 2 : 1));
            Operand op = stack[--top];
            if (!op.canMerge(kind)) {
                throw new VerifyError("Type check error: " + op + " cannot be merged into " + kind);
            }
            return op;
        }

        public Operand popUninitRef(Operand kind) {
            procSize(-(isType2(kind) ? 2 : 1));
            Operand op = stack[--top];
            if (!op.canMerge(kind)) {
                throw new VerifyError("Type check error: " + op + " cannot be merged into " + kind);
            }
            if (!op.isUninit()) {
                throw new VerifyError("Calling initialization method on already initialized reference.");
            }
            return op;
        }

        Operand popArray() {
            procSize(-1);
            Operand op = stack[--top];
            if (!(op == Null || op.isArrayType())) {
                throw new VerifyError("Invalid operand. Expected array, found: " + op);
            }
            return op;
        }

        void popFloat() {
            pop(Float);
        }

        void popDouble() {
            pop(Double);
        }

        void popLong() {
            pop(Long);
        }

        Operand popObjOrRA() {
            procSize(-1);
            Operand op = stack[--top];
            if (!(op.isReference() || op == ReturnAddress)) {
                throw new VerifyError(op + " on stack, required: A or ReturnAddress");
            }
            return op;
        }

        void pop(Operand k) {
            if (!k.getKind().isStackInt() || k == Int) {
                procSize((isType2(k) ? -2 : -1));
                Operand op = stack[--top];
                if (!(op.canMerge(k))) {
                    throw new VerifyError(stack[top] + " on stack, required: " + k);
                }
            } else {
                pop(Int);
            }
        }

        void dup() {
            procSize(1);
            if (isType2(stack[top - 1])) {
                throw new VerifyError("type 2 operand for dup.");
            }
            stack[top] = stack[top - 1];
            top++;
        }

        void pop() {
            procSize(-1);
            Operand v1 = stack[top - 1];
            if (isType2(v1)) {
                throw new VerifyError("type 2 operand for pop.");
            }
            top--;
        }

        void pop2() {
            procSize(-2);
            Operand v1 = stack[top - 1];
            if (isType2(v1)) {
                top--;
                return;
            }
            Operand v2 = stack[top - 2];
            if (isType2(v2)) {
                throw new VerifyError("type 2 second operand for pop2.");
            }
            top = top - 2;
        }

        void dupx1() {
            procSize(1);
            Operand v1 = stack[top - 1];
            if (isType2(v1) || isType2(stack[top - 2])) {
                throw new VerifyError("type 2 operand for dupx1.");
            }
            System.arraycopy(stack, top - 2, stack, top - 1, 2);
            top++;
            stack[top - 3] = v1;
        }

        void dupx2() {
            procSize(1);
            Operand v1 = stack[top - 1];
            if (isType2(v1)) {
                throw new VerifyError("type 2 first operand for dupx2.");
            }
            Operand v2 = stack[top - 2];
            if (isType2(v2)) {
                System.arraycopy(stack, top - 2, stack, top - 1, 2);
                top++;
                stack[top - 3] = v1;
            } else {
                if (isType2(stack[top - 3])) {
                    throw new VerifyError("type 2 third operand for dupx2.");
                }
                System.arraycopy(stack, top - 3, stack, top - 2, 3);
                top++;
                stack[top - 4] = v1;
            }
        }

        void dup2() {
            procSize(2);
            Operand v1 = stack[top - 1];
            if (isType2(v1)) {
                stack[top] = v1;
                top++;
            } else {
                if (isType2(stack[top - 2])) {
                    throw new VerifyError("type 2 second operand for dup2.");
                }
                System.arraycopy(stack, top - 2, stack, top, 2);
                top = top + 2;
            }
        }

        void dup2x1() {
            procSize(2);
            Operand v1 = stack[top - 1];
            Operand v2 = stack[top - 2];
            if (isType2(v2)) {
                throw new VerifyError("type 2 second operand for dup2x1");
            }
            if (isType2(v1)) {
                System.arraycopy(stack, top - 2, stack, top - 1, 2);
                top++;
                stack[top - 3] = v1;
                return;
            }
            if (isType2(stack[top - 3])) {
                throw new VerifyError("type 2 third operand for dup2x1.");
            }
            System.arraycopy(stack, top - 3, stack, top - 1, 3);
            top = top + 2;
            stack[top - 5] = v2;
            stack[top - 4] = v1;
        }

        void dup2x2() {
            procSize(2);
            Operand v1 = stack[top - 1];
            Operand v2 = stack[top - 2];
            boolean b1 = isType2(v1);
            boolean b2 = isType2(v2);

            if (b1 && b2) {
                System.arraycopy(stack, top - 2, stack, top - 1, 2);
                stack[top - 2] = v1;
                top++;
                return;
            }
            Operand v3 = stack[top - 3];
            boolean b3 = isType2(v3);
            if (!b1 && !b2 && b3) {
                System.arraycopy(stack, top - 3, stack, top - 1, 3);
                stack[top - 3] = v2;
                stack[top - 2] = v1;
                top = top + 2;
                return;
            }
            if (b1 && !b2 && !b3) {
                System.arraycopy(stack, top - 3, stack, top - 2, 3);
                stack[top - 3] = v1;
                top++;
                return;
            }
            Operand v4 = stack[top - 4];
            boolean b4 = isType2(v4);
            if (!b1 && !b2 && !b3 && !b4) {
                System.arraycopy(stack, top - 4, stack, top - 2, 4);
                stack[top - 4] = v2;
                stack[top - 3] = v1;
                top = top + 2;
                return;
            }
            throw new VerifyError("Calling dup2x2 with operands: " + v1 + ", " + v2 + ", " + v3 + ", " + v4);

        }

        void swap() {
            Operand v1 = stack[top - 1];
            Operand v2 = stack[top - 2];
            boolean b1 = isType2(v1);
            boolean b2 = isType2(v2);
            if (!b1 && !b2) {
                stack[top - 1] = v2;
                stack[top - 2] = v1;
                return;
            }
            throw new VerifyError("Type 2 operand for SWAP");
        }

        private boolean mergeInto(StackFrame stackFrame) {
            if (top != stackFrame.stack.length) {
                throw new VerifyError("Incompatible stack size.");
            }
            for (int i = 0; i < top; i++) {
                if (!stack[i].canMerge(stackFrame.stack[i])) {
                    throw new VerifyError("Incompatible stack types.");
                }
            }
            return true;
        }

        private StackFrame toStackFrame() {
            Operand[] frameStack = new Operand[top];
            System.arraycopy(stack, 0, frameStack, 0, top);
            return new StackFrame(frameStack);
        }

        private Operand initUninit(UninitReferenceOperand toInit) {
            Operand init = toInit.init();
            for (int i = 0; i < top; i++) {
                if (stack[i] == toInit) {
                    stack[i] = init;
                }
            }
            return init;
        }
    }

}