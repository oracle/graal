/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.vm.npe;

import static com.oracle.truffle.espresso.bytecode.Bytecodes.AALOAD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ACONST_NULL;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ALOAD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ALOAD_0;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ALOAD_1;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ALOAD_2;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ALOAD_3;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ANEWARRAY;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ARRAYLENGTH;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.BALOAD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.BIPUSH;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.CALOAD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.D2F;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.D2I;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.D2L;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DADD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DALOAD;
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
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DSUB;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.F2D;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.F2I;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.F2L;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FADD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FALOAD;
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
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FSUB;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.I2B;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.I2C;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.I2D;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.I2F;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.I2L;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.I2S;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IADD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IALOAD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IAND;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ICONST_0;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ICONST_1;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ICONST_2;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ICONST_3;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ICONST_4;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ICONST_5;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ICONST_M1;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IDIV;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ILOAD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ILOAD_0;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ILOAD_1;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ILOAD_2;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ILOAD_3;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IMUL;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.INEG;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.INSTANCEOF;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IOR;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IREM;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ISHL;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ISHR;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ISUB;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IUSHR;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IXOR;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.L2D;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.L2F;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.L2I;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LADD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LALOAD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LAND;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LCMP;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LCONST_0;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LCONST_1;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LDIV;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LLOAD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LLOAD_0;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LLOAD_1;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LLOAD_2;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LLOAD_3;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LMUL;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LNEG;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LOR;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LREM;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LSHL;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LSHR;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LSUB;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LUSHR;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LXOR;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.MULTIANEWARRAY;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.NEW;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.NEWARRAY;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.SALOAD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.SIPUSH;

import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;

enum StackType {
    VOID(0),
    INT(1),
    LONG(2),
    FLOAT(1),
    DOUBLE(2),
    OBJECT(1),
    ARRAY(1),
    ADDRESS(1),
    CONFLICT(1);

    private final int slots;

    StackType(int slots) {
        this.slots = slots;
    }

    boolean hasTwoSlots() {
        return slots() == 2;
    }

    int slots() {
        return slots;
    }

    static StackType forType(Symbol<Type> type) {
        if (Types.isArray(type)) {
            return ARRAY;
        }
        return forJavaKind(Types.getJavaKind(type));
    }

    private static StackType forJavaKind(JavaKind javaKind) {
        switch (javaKind) {
            case Boolean:
            case Byte:
            case Short:
            case Char:
            case Int:
                return INT;
            case Float:
                return FLOAT;
            case Long:
                return LONG;
            case Double:
                return DOUBLE;
            case Object:
                return OBJECT;
            case Void:
                return VOID;
            default:
                throw EspressoError.shouldNotReachHere("Invalid JavaKind: " + javaKind);
        }
    }

    static StackType forTag(ConstantPool.Tag tag) {
        switch (tag) {
            case INTEGER:
                return INT;
            case FLOAT:
                return FLOAT;
            case LONG:
                return LONG;
            case DOUBLE:
                return DOUBLE;
            case CLASS:
            case STRING:
            case METHODHANDLE:
            case METHODTYPE:
            case DYNAMIC:
                return OBJECT;
            default:
                throw EspressoError.shouldNotReachHere("Invalid tag for LDC: " + tag);
        }
    }

    /**
     * For bytecode instructions that pushes a single value on the stack, returns the
     * {@link StackType} of the pushed value.
     */
    static StackType rtype(int opcode) {
        switch (opcode) {
            case ACONST_NULL:
            case ALOAD:
            case ALOAD_0:
            case ALOAD_1:
            case ALOAD_2:
            case ALOAD_3:
            case AALOAD:
            case NEW:
                return OBJECT;
            case NEWARRAY:
            case ANEWARRAY:
            case MULTIANEWARRAY:
                return ARRAY;
            case ICONST_M1:
            case ICONST_0:
            case ICONST_1:
            case ICONST_2:
            case ICONST_3:
            case ICONST_4:
            case ICONST_5:
            case BIPUSH:
            case SIPUSH:
            case ILOAD:
            case ILOAD_0:
            case ILOAD_1:
            case ILOAD_2:
            case ILOAD_3:
            case BALOAD:
            case CALOAD:
            case SALOAD:
            case IALOAD:
            case IADD:
            case ISUB:
            case IMUL:
            case IDIV:
            case IREM:
            case INEG:
            case ISHL:
            case ISHR:
            case IUSHR:
            case IAND:
            case IOR:
            case IXOR:
            case L2I:
            case F2I:
            case D2I:
            case I2B:
            case I2C:
            case I2S:
            case LCMP:
            case FCMPL:
            case FCMPG:
            case DCMPL:
            case DCMPG:
            case ARRAYLENGTH:
            case INSTANCEOF:
                return INT;
            case LCONST_0:
            case LCONST_1:
            case LLOAD:
            case LLOAD_0:
            case LLOAD_1:
            case LLOAD_2:
            case LLOAD_3:
            case LALOAD:
            case LADD:
            case LSUB:
            case LMUL:
            case LDIV:
            case LREM:
            case LNEG:
            case LSHL:
            case LSHR:
            case LUSHR:
            case LAND:
            case LOR:
            case LXOR:
            case I2L:
            case F2L:
            case D2L:
                return LONG;
            case FCONST_0:
            case FCONST_1:
            case FCONST_2:
            case FLOAD:
            case FLOAD_0:
            case FLOAD_1:
            case FLOAD_2:
            case FLOAD_3:
            case FALOAD:
            case FNEG:
            case FADD:
            case FSUB:
            case FMUL:
            case FDIV:
            case FREM:
            case I2F:
            case L2F:
            case D2F:
                return FLOAT;
            case DCONST_0:
            case DCONST_1:
            case DLOAD:
            case DLOAD_0:
            case DLOAD_1:
            case DLOAD_2:
            case DLOAD_3:
            case DALOAD:
            case DADD:
            case DSUB:
            case DMUL:
            case DDIV:
            case DREM:
            case DNEG:
            case I2D:
            case L2D:
            case F2D:
                return DOUBLE;
            default:
                return VOID;
        }
    }
}
