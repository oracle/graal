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

import com.oracle.truffle.espresso.bytecode.BytecodeLookupSwitch;
import com.oracle.truffle.espresso.bytecode.BytecodeStream;
import com.oracle.truffle.espresso.bytecode.BytecodeTableSwitch;
import com.oracle.truffle.espresso.bytecode.Bytecodes;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.impl.ContextAccess;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;

/**
 * Should be a complete bytecode Verifier. Given the version of the clqssfile from which the method
 * is taken, the type-checking or type-infering Verifier is used.
 */
public final class MethodVerifier implements ContextAccess {
    private final boolean USE_STACK_MAP_FRAMES;
    private final Klass thisKlass;
    private final BytecodeStream code;
    private final RuntimeConstantPool pool;
    private final Symbol<Type>[] sig;
    private final Symbol<Name> methodName;
    private final boolean isStatic;
    private final int maxStack;
    private final int maxLocals;
    private final byte[] verified;
    private final StackFrame[] stackFrames;
    private final StackMapTableAttribute stackMapTableAttribute;

    @Override
    public EspressoContext getContext() {
        return thisKlass.getContext();
    }

    static abstract class Operand {
        static public Operand[] EMPTY_ARRAY = new Operand[0];

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

        boolean isNull() {
            return false;
        }

        abstract boolean canMerge(Operand other);

        // Called only after canMerge returned false, as finding common superType is expensive.
        abstract Operand mergeInto(Operand other);
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
            return (other == Invalid) || (other.isPrimitive() && other.getKind() == this.kind);
        }

        @Override
        Operand mergeInto(Operand other) {
            return this == other ? this : null;
        }

        @Override
        public String toString() {
            return kind.toString();
        }
    }

    static class ReferenceOperand extends Operand {
        protected Symbol<Type> type;
        Klass thisKlass;

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
                try {
                    klass = thisKlass.getMeta().loadKlass(type, thisKlass.getDefiningClassLoader());
                } catch (Exception e) {
                    // TODO(garcia) fine grain this catch
                }
                if (klass == null) {
                    throw new NoClassDefFoundError(type.toString());
                }
            }
            return klass;
        }

        @Override
        boolean canMerge(Operand other) {
            if (other.isReference()) {
                if (type == null || other.getType() == this.type || other.getType() == Type.Object) {
                    return true;
                }
                if (other.getType() == null) {
                    return false;
                }
                return other.getKlass().isAssignableFrom(getKlass());
            }
            return other == Invalid;
        }

        @Override
        Operand mergeInto(Operand other) {
            if (!other.isReference()) {
                return null;
            }
            if (other.isArrayType()) {
                return jlObject;
            }
            if (other.isNull()) {
                return this;
            }
            Klass result = getKlass().getClosestCommonSupertype(other.getKlass());
            return result == null ? null : new ReferenceOperand(result, thisKlass);
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
            return (other == Invalid) || (other.isReference() && other.getType() == Type.Object);
        }

        @Override
        Operand mergeInto(Operand other) {
            if (!other.isReference()) {
                return null;
            }
            if (other.isNull()) {
                return this;
            }
            if (!other.isArrayType()) {
                return jlObject;
            }
            Operand thisElemental = getElemental();
            Operand otherElemental = other.getElemental();
            int otherDim = other.getDimensions();
            int thisDim = getDimensions();
            if (otherDim == thisDim) {
                if (thisElemental.isPrimitive() || otherElemental.isPrimitive()) {
                    return new ArrayOperand(jlObject, thisDim);
                }
                return new ArrayOperand(thisElemental.mergeInto(otherElemental), thisDim);
            }
            Operand smallestElemental;
            if (thisDim < otherDim) {
                smallestElemental = thisElemental;
            } else {
                smallestElemental = otherElemental;
            }
            if (smallestElemental.isPrimitive()) {
                return new ArrayOperand(jlObject, Math.min(thisDim, otherDim));
            }
            if (smallestElemental.getType() == Type.Cloneable || smallestElemental.getType() == Type.Serializable) {
                return new ArrayOperand(smallestElemental, Math.min(thisDim, otherDim));
            }
            return new ArrayOperand(jlObject, Math.min(thisDim, otherDim));
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
        final int newBCI;

        UninitReferenceOperand(Symbol<Type> type, Klass thisKlass) {
            super(type, thisKlass);
            this.newBCI = -1;
        }

        UninitReferenceOperand(Symbol<Type> type, Klass thisKlass, int newBCI) {
            super(type, thisKlass);
            this.newBCI = newBCI;
        }

        UninitReferenceOperand(Klass klass, Klass thisKlass) {
            super(klass, thisKlass);
            this.newBCI = -1;
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

    private static final ReferenceOperand jlObject = new ReferenceOperand(Type.Object, null) {
        @Override
        Klass getKlass() {
            if (klass == null) {
                klass = thisKlass.getMeta().loadKlass(type, StaticObject.NULL);
                if (klass == null) {
                    throw new NoClassDefFoundError(type.toString());
                }
            }
            return klass;
        }
    };

    static private final Operand ReturnAddress = new PrimitiveOperand(JavaKind.ReturnAddress);

    static private final Operand Null = new Operand(JavaKind.Object) {
        @Override
        boolean canMerge(Operand other) {
            return other == Invalid || other.isReference();
        }

        @Override
        Operand mergeInto(Operand other) {
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
    static private final byte JUMP_TARGET = 3;

    /**
     * Construct the data structure to perform verification
     * 
     * @param codeAttribute the code attribute of the method
     * @param m the Espresso method
     */

    private MethodVerifier(CodeAttribute codeAttribute, Method m) {
        this.code = new BytecodeStream(codeAttribute.getCode());
        this.maxStack = codeAttribute.getMaxStack();
        this.maxLocals = codeAttribute.getMaxLocals();
        this.pool = m.getRuntimeConstantPool();
        this.verified = new byte[code.endBCI()];
        this.stackFrames = new StackFrame[code.endBCI()];
        this.sig = m.getParsedSignature();
        this.isStatic = m.isStatic();
        this.thisKlass = m.getDeclaringKlass();
        this.methodName = m.getName();
        this.stackMapTableAttribute = codeAttribute.getStackMapFrame();
        this.USE_STACK_MAP_FRAMES = codeAttribute.useStackMaps();

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
        return new MethodVerifier(codeAttribute, m).verify();
    }

    private void initVerifier() {
        Arrays.fill(verified, UNREACHABLE);
        int bci = 0;
        while (bci < code.endBCI()) {
            int bc = code.currentBC(bci);
            if (bc > QUICK) {
                throw new VerifyError("invalid bytecode: " + bc);
            }
            verified[bci] = UNSEEN;
            bci = code.nextBCI(bci);
        }
        bci = 0;
        if (!USE_STACK_MAP_FRAMES) {
            while (bci < code.endBCI()) {
                int bc = code.currentBC(bci);
                if (Bytecodes.isBranch(bc)) {
                    int target = code.readBranchDest(bci);
                    if (verified[target] == UNREACHABLE) {
                        throw new VerifyError("Jump to the middle of an instruction: " + target);
                    }
                    verified[target] = JUMP_TARGET;
                }
                if (bc == TABLESWITCH || bc == LOOKUPSWITCH) {
                    initSwitch(bci, bc);
                }
                bci = code.nextBCI(bci);
            }
        }
    }

    private void initSwitch(int bci, int opCode) {
        switch (opCode) {
            case LOOKUPSWITCH: {
                BytecodeLookupSwitch switchHelper = code.getBytecodeLookupSwitch();
                int low = 0;
                int high = switchHelper.numberOfCases(bci);
                int oldKey = 0;
                boolean init = false;
                int target;
                for (int i = low; i < high; i++) {
                    int newKey = switchHelper.keyAt(bci, i - low);
                    if (init && newKey <= oldKey) {
                        throw new VerifyError("Unsorted keys in LOOKUPSWITCH");
                    }
                    init = true;
                    oldKey = newKey;
                    target = switchHelper.targetAt(bci, i - low);
                    if (verified[target] == UNREACHABLE) {
                        throw new VerifyError("Jump to the middle of an instruction: " + target);
                    }
                    verified[target] = JUMP_TARGET;
                }
                target = switchHelper.defaultTarget(bci);
                if (verified[target] == UNREACHABLE) {
                    throw new VerifyError("Jump to the middle of an instruction: " + target);
                }
                verified[target] = JUMP_TARGET;
            }
                return;
            case TABLESWITCH: {
                BytecodeTableSwitch switchHelper = code.getBytecodeTableSwitch();
                int low = switchHelper.lowKey(bci);
                int high = switchHelper.highKey(bci);
                int target;
                for (int i = low; i != high + 1; i++) {
                    target = switchHelper.targetAt(bci, i - low);
                    if (verified[target] == UNREACHABLE) {
                        throw new VerifyError("Jump to the middle of an instruction: " + target);
                    }
                    verified[target] = JUMP_TARGET;
                }
                target = switchHelper.defaultTarget(bci);
                if (verified[target] == UNREACHABLE) {
                    throw new VerifyError("Jump to the middle of an instruction: " + target);
                }
                verified[target] = JUMP_TARGET;
            }
                return;
            default:
                throw EspressoError.shouldNotReachHere();
        }
    }

    private void initStackFrames() {
        // First implicit stack frame.
        StackFrame previous = new StackFrame(this);
        assert stackFrames.length > 0;
        int BCI = 0;
        boolean first = true;
        stackFrames[BCI] = previous;
        if (stackMapTableAttribute == null) {
            return;
        }
        StackMapFrame[] entries = stackMapTableAttribute.getEntries();
        for (StackMapFrame smf : entries) {
            StackFrame frame = getStackFrame(smf, previous);
            BCI = BCI + smf.getOffset() + 1;
            if (first) {
                BCI--;
                first = false;
            }
            stackFrames[BCI] = frame;
            previous = frame;
            first = false;
        }
    }

    private StackFrame getStackFrame(StackMapFrame smf, StackFrame previous) {
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
            Stack stack = new Stack(2);
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
            Stack stack = new Stack(2);
            stack.push(getOperandFromVerificationType(smf.getStackItem()));
            StackFrame res = new StackFrame(stack, previous.locals);
            res.lastLocal = previous.lastLocal;
            return res;
        }
        if (frameType < CHOP_BOUND) {
            Operand[] newLocals = previous.locals.clone();
            int actualLocalOffset = 0;
            for (int i = 0; i < smf.getChopped(); i++) {
                Operand op = newLocals[previous.lastLocal - actualLocalOffset];
                if (op == Invalid && (previous.lastLocal - actualLocalOffset - 1 > 0)) {
                    if (isType2(newLocals[previous.lastLocal - actualLocalOffset - 1])) {
                        actualLocalOffset++;
                    }
                }
                newLocals[previous.lastLocal - actualLocalOffset] = Invalid;
                actualLocalOffset++;
            }
            StackFrame res = new StackFrame(Operand.EMPTY_ARRAY, 0, 0, newLocals);
            res.lastLocal = previous.lastLocal - actualLocalOffset;
            if (res.lastLocal > 0 && newLocals[res.lastLocal] == Invalid && res.lastLocal - 1 > 0 && isType2(newLocals[res.lastLocal - 1])) {
                res.lastLocal = res.lastLocal - 1;
            }
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
            Operand[] newLocals = previous.locals.clone();
            Operand[] appends = new Operand[smf.getLocals().length];
            int i = 0;
            for (VerificationTypeInfo vti : smf.getLocals()) {
                appends[i++] = getOperandFromVerificationType(vti);
            }
            i = previous.lastLocal;
            Operand op = null;
            if (i >= 0) {
                op = newLocals[i];
                if (isType2(op)) {
                    newLocals[++i] = Invalid;
                }
            }
            i++;
            int j;
            for (j = 0; j < appends.length; j++) {
                op = appends[j];
                newLocals[i++] = op;
                if (isType2(op)) {
                    newLocals[i++] = Invalid;
                }
            }
            if (j == 0) {
                throw new VerifyError("Empty Append Frame in the StackmapTable");
            }
            StackFrame res = new StackFrame(Operand.EMPTY_ARRAY, 0, 0, newLocals);
            res.lastLocal = i - (isType2(op) ? 2 : 1);
            return res;
        }
        if (frameType == FULL_FRAME) {
            Stack fullStack = new Stack(maxStack);
            for (VerificationTypeInfo vti : smf.getStack()) {
                fullStack.push(getOperandFromVerificationType(vti));
            }
            Operand[] locals = new Operand[maxLocals];
            Arrays.fill(locals, Invalid);
            int pos = 0;
            for (VerificationTypeInfo vti : smf.getLocals()) {
                Operand op = getOperandFromVerificationType(vti);
                locals[pos++] = op;
                if (isType2(op)) {
                    locals[pos++] = Invalid;
                }
            }
            StackFrame res = new StackFrame(fullStack, locals);
            if (pos == 0) {
                res.lastLocal = 0;
                return res;
            }
            if (locals[pos - 1] == Invalid && pos - 2 > 0 && isType2(locals[pos - 2])) {
                res.lastLocal = pos - 2;
                return res;
            }
            res.lastLocal = pos - 1;
            return res;

        }
        throw EspressoError.shouldNotReachHere();
    }

    private Operand getOperandFromVerificationType(VerificationTypeInfo vti) {
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
                return new UninitReferenceOperand(getTypes().fromName(pool.classAt(code.readCPI(vti.getNewOffset())).getName(pool)), thisKlass, vti.getNewOffset());
            default:
                throw EspressoError.shouldNotReachHere("Unrecognized VerificationTypeInfo: " + vti);
        }
    }

    // TODO(garcia) implement instruction stack to visit, to avoid stack overflows from recursively
    // finding fixed points.

    /**
     * Performs the verification for the method associated with this MethodVerifier.
     *
     * @return true or throws ClassFormatError or VerifyError
     */
    private synchronized boolean verify() {
        try {
            initVerifier();
        } catch (IndexOutOfBoundsException e) {
            throw new VerifyError("Invalid jump: " + e.getMessage() + " in method " + thisKlass.getName() + "." + methodName);
        }
        if (code.endBCI() == 0) {
            throw new VerifyError("Control flow falls through code end");
        }
        int nextBCI = 0;
        if (USE_STACK_MAP_FRAMES) {
            initStackFrames();
        }
        Stack stack = new Stack(maxStack);
        Locals locals = new Locals(this);
        while (verified[nextBCI] != DONE || (stackFrames[nextBCI] != null && stack.mergeInto(stackFrames[nextBCI]) != -1)) {
            if (stackFrames[nextBCI] != null || verified[nextBCI] == JUMP_TARGET) {
                StackFrame frame = mergeFrames(stack, locals, stackFrames[nextBCI]);
                if (!(frame == stackFrames[nextBCI])) {
                    stackFrames[nextBCI] = frame;
                }
                stack = frame.extractStack(maxStack);
                locals = frame.extractLocals();
                nextBCI = verifySafe(nextBCI, stack, locals);
            } else {
                nextBCI = verifySafe(nextBCI, stack, locals);
            }
            if (nextBCI >= code.endBCI()) {
                throw new VerifyError("Control flow falls through code end");
            }
        }
        return true;
    }

    private boolean branch(int BCI, Stack stack, Locals locals) {
        if (BCI >= code.endBCI()) {
            throw new VerifyError("Control flow falls through code end");
        }
        if (BCI < 0) {
            throw new VerifyError("negative branch target: " + BCI);
        }
        if (verified[BCI] == UNREACHABLE) {
            throw new VerifyError("Jump to the middle of an instruction: " + BCI);
        }
        StackFrame frame = mergeFrames(stack, locals, stackFrames[BCI]);
        if (!(frame == stackFrames[BCI])) {
            verified[BCI] = JUMP_TARGET;
            stackFrames[BCI] = frame;
        }
        if (verified[BCI] == DONE) {
            return true;
        }
        Stack newStack = frame.extractStack(maxStack);
        Locals newLocals = frame.extractLocals();
        int nextBCI = BCI;
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
                    if (op != Null && !op.getComponent().isReference()) {
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
                    if (array != Null && !array.getComponent().isReference()) {
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
                    for (int i = low; i != high + 1; i++) {
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
                    if (op.isUninit()) {
                        throw new VerifyError("Storing uninitialized reference in a field.");
                    }
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
                    if (curOpcode == INVOKESPECIAL && mrc.getName(pool) == Name.INIT) {
                        Operand op = getOperand(mrc);
                        UninitReferenceOperand toInit = (UninitReferenceOperand) stack.popUninitRef(op);
                        if (toInit.newBCI != -1) {
                            if (code.opcode(toInit.newBCI) != NEW) {
                                throw new VerifyError("There is no NEW bytecode at BCI: " + toInit.newBCI);
                            }
                        } else {
                            if (methodName != Name.INIT) {
                                throw new VerifyError("Encountered UninitializedThis outside of Constructor: " + toInit);
                            }
                        }
                        stack.initUninit(toInit);
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
                    Operand op = new UninitReferenceOperand(type, thisKlass, BCI);
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

    private Operand[] getOperandSig(Symbol<Type>[] toParse) {
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
        if (op != Null && op.getComponent() != kind) {
            throw new VerifyError("Loading " + kind + " from " + op + " array.");
        }
        stack.push(kind);
    }

    private static void xastore(Stack stack, PrimitiveOperand kind) {
        stack.pop(kind);
        stack.popInt();
        Operand array = stack.popArray();
        if (array != Null && array.getComponent() != kind) {
            throw new VerifyError("got array of type: " + array + ", while storing a " + kind);
        }
    }

    private static boolean wideOpcodes(int op) {
        return (op >= ILOAD && op <= ALOAD) || (op >= ISTORE && op <= ASTORE) || (op == RET) || (op == IINC);
    }

    private static class Locals {
        Operand[] registers;

        Locals(MethodVerifier mv) {
            Operand[] parsedSig = mv.getOperandSig(mv.sig);
            if (parsedSig.length - (mv.isStatic ? 1 : 0) > mv.maxLocals) {
                throw new ClassFormatError("Too many method arguments for the number of locals !");
            }
            this.registers = new Operand[mv.maxLocals];
            int index = 0;
            if (!mv.isStatic) {
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
                    registers[index++] = Invalid;
                }
            }
            for (; index < mv.maxLocals; index++) {
                registers[index] = Invalid;
            }
        }

        private Locals(Operand[] registers) {
            this.registers = registers;
        }

        Operand[] extract() {
            return registers.clone();
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
                throw new VerifyError("Incompatible register type. Expected a reference, found: " + op);
            }
            return op;
        }

        void store(int index, Operand op) {
            registers[index] = op;
            if (isType2(op)) {
                registers[index + 1] = Invalid;
            }
        }

        public int mergeInto(StackFrame frame) {
            assert registers.length == frame.locals.length;
            Operand[] frameLocals = frame.locals;

            for (int i = 0; i < registers.length; i++) {
                if (!registers[i].canMerge(frameLocals[i])) {
                    return i;
                }
            }
            return -1;
        }
    }

    private static class Stack {
        private final Operand[] stack;

        private int top;
        private int size;

        Stack(int maxStack) {
            this.stack = new Operand[maxStack];
            this.top = 0;
            this.size = 0;
        }

        public Operand[] extract() {
            Operand[] result = new Operand[top];
            System.arraycopy(stack, 0, result, 0, top);
            return result;
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

        private int mergeInto(StackFrame stackFrame) {
            if (top != stackFrame.stack.length) {
                throw new VerifyError("Inconsistent stack height: " + top + " != " + stackFrame.stack.length);
            }
            for (int i = 0; i < top; i++) {
                if (!stack[i].canMerge(stackFrame.stack[i])) {
                    return i;
                }
            }
            return -1;
        }

        private Operand initUninit(UninitReferenceOperand toInit) {
            Operand init = toInit.init();
            for (int i = 0; i < top; i++) {
                if (stack[i].isUninit() && ((UninitReferenceOperand) stack[i]).newBCI == toInit.newBCI) {
                    stack[i] = init;
                }
            }
            return init;
        }
    }

    private static class StackFrame {
        final Operand[] stack;
        final int stackSize;
        final int top;
        final Operand[] locals;
        int lastLocal;

        StackFrame(Stack stack, Locals locals) {
            this.stack = stack.extract();
            this.stackSize = stack.size;
            this.top = stack.top;
            this.locals = locals.extract();
        }

        StackFrame(Stack stack, Operand[] locals) {
            this.stack = stack.extract();
            this.stackSize = stack.size;
            this.top = stack.top;
            this.locals = locals;
        }

        StackFrame(MethodVerifier mv) {
            this(new Stack(mv.maxStack), new Locals(mv));
            int last = (mv.isStatic ? -1 : 0);
            for (int i = 0; i < mv.sig.length - 1; i++) {
                if (isType2(locals[++last])) {
                    last++;
                }
            }
            this.lastLocal = last;
        }

        public StackFrame(Operand[] stack, int stackSize, int top, Operand[] locals) {
            this.stack = stack;
            this.stackSize = stackSize;
            this.top = top;
            this.locals = locals;
        }

        Stack extractStack(int maxStack) {
            Stack res = new Stack(maxStack);
            System.arraycopy(stack, 0, res.stack, 0, top);
            res.size = stackSize;
            res.top = top;
            return res;
        }

        Locals extractLocals() {
            return new Locals(locals.clone());
        }
    }

    public StackFrame mergeFrames(Stack stack, Locals locals, StackFrame stackMap) {
        if (stackMap == null) {
            if (USE_STACK_MAP_FRAMES) {
                throw new VerifyError("No stack frame on jump target");
            }
            return spawnStackFrame(stack, locals);
        }
        Operand[] mergedStack = null;
        int mergeIndex = stack.mergeInto(stackMap);
        if (mergeIndex != -1) {
            if (USE_STACK_MAP_FRAMES) {
                throw new VerifyError("Wrong stack map frames in class file.");
            }
            mergedStack = new Operand[stackMap.stack.length];
            System.arraycopy(stackMap.stack, 0, mergedStack, 0, mergeIndex);
            for (int i = mergeIndex; i < mergedStack.length; i++) {
                Operand stackOp = stack.stack[i];
                Operand frameOp = stackMap.stack[i];
                if (!stackOp.canMerge(frameOp)) {
                    Operand result = stackOp.mergeInto(frameOp);
                    if (result == null) {
                        throw new VerifyError("Cannot merge " + stackOp + " with " + frameOp);
                    }
                    mergedStack[i] = result;
                }
            }
        }
        Operand[] mergedLocals = null;
        mergeIndex = locals.mergeInto(stackMap);
        if (mergeIndex != -1) {
            if (USE_STACK_MAP_FRAMES) {
                throw new VerifyError("Wrong local map frames in class file.");
            }
            // We can ALWAYS merge locals.
            mergedLocals = new Operand[maxLocals];
            Operand[] frameLocals = stackMap.locals;
            System.arraycopy(frameLocals, 0, mergedLocals, 0, mergeIndex);
            for (int i = mergeIndex; i < mergedLocals.length; i++) {
                Operand localsOp = locals.registers[i];
                Operand frameOp = frameLocals[i];
                if (!localsOp.canMerge(frameOp)) {
                    Operand result = localsOp.mergeInto(frameOp);
                    if (result == null) {
                        mergedLocals[i] = Invalid;
                    } else {
                        mergedLocals[i] = result;
                    }
                }
            }
        }
        // TODO merge locals
        if (mergedStack == null || mergedLocals == null) {
            return stackMap;
        }
        return new StackFrame(mergedStack, stack.size, stack.top, mergedLocals);
    }

    public static StackFrame spawnStackFrame(Stack stack, Locals locals) {
        return new StackFrame(stack, locals);
    }

}
