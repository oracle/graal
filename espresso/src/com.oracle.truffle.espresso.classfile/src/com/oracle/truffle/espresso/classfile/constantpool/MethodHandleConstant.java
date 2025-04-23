/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.classfile.constantpool;

import static com.oracle.truffle.espresso.classfile.Constants.REF_getField;
import static com.oracle.truffle.espresso.classfile.Constants.REF_getStatic;
import static com.oracle.truffle.espresso.classfile.Constants.REF_invokeInterface;
import static com.oracle.truffle.espresso.classfile.Constants.REF_invokeSpecial;
import static com.oracle.truffle.espresso.classfile.Constants.REF_invokeStatic;
import static com.oracle.truffle.espresso.classfile.Constants.REF_invokeVirtual;
import static com.oracle.truffle.espresso.classfile.Constants.REF_newInvokeSpecial;
import static com.oracle.truffle.espresso.classfile.Constants.REF_putField;
import static com.oracle.truffle.espresso.classfile.Constants.REF_putStatic;

import java.nio.ByteBuffer;

import com.oracle.truffle.espresso.classfile.ClassfileParser;
import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.classfile.ConstantPool.Tag;
import com.oracle.truffle.espresso.classfile.descriptors.Name;
import com.oracle.truffle.espresso.classfile.descriptors.ParserSymbols.ParserNames;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.ValidationException;

public interface MethodHandleConstant extends PoolConstant {

    static Index create(int refKind, int refIndex) {
        return new Index(refKind, refIndex);
    }

    @Override
    default Tag tag() {
        return Tag.METHODHANDLE;
    }

    final class Index implements MethodHandleConstant, ImmutablePoolConstant, Resolvable {
        private final byte refKind;
        private final char refIndex;

        Index(int refKind, int refIndex) {
            this.refKind = PoolConstant.u1(refKind);
            this.refIndex = PoolConstant.u2(refIndex);
        }

        public int getRefKind() {
            return refKind;
        }

        public char getRefIndex() {
            return refIndex;
        }

        @Override
        public void validate(ConstantPool pool) throws ValidationException {
            MemberRefConstant.Indexes member = getMember(pool);
            member.validate(pool);

            Symbol<Name> memberName = member.getName(pool);
            if (ParserNames._clinit_.equals(memberName)) {
                throw ValidationException.raise("Ill-formed constant: " + tag());
            }

            // If the value is 8 (REF_newInvokeSpecial), the name of the method represented by a
            // CONSTANT_Methodref_info structure must be <init>.
            if (ParserNames._init_.equals(memberName) && refKind != REF_newInvokeSpecial) {
                throw ValidationException.raise("Ill-formed constant: " + tag());
            }

            if (!(REF_getField <= refKind && refKind <= REF_invokeInterface)) {
                throw ValidationException.raise("Ill-formed constant: " + tag());
            }

            // If the value of the reference_kind item is 5 (REF_invokeVirtual), 6
            // (REF_invokeStatic), 7 (REF_invokeSpecial), or 9 (REF_invokeInterface), the name of
            // the method represented by a CONSTANT_Methodref_info structure or a
            // CONSTANT_InterfaceMethodref_info structure must not be <init> or <clinit>.
            if (memberName.equals(ParserNames._init_) || memberName.equals(ParserNames._clinit_)) {
                if (refKind == REF_invokeVirtual ||
                                refKind == REF_invokeStatic ||
                                refKind == REF_invokeSpecial ||
                                refKind == REF_invokeInterface) {
                    throw ValidationException.raise("Ill-formed constant: " + tag());
                }
            }

            boolean valid = false;
            Tag tag = pool.at(refIndex).tag();
            switch (getRefKind()) {
                case REF_getField: // fall-through
                case REF_getStatic: // fall-through
                case REF_putField: // fall-through
                case REF_putStatic:
                    // If the value of the reference_kind item is 1 (REF_getField), 2
                    // (REF_getStatic), 3 (REF_putField), or 4 (REF_putStatic), then the
                    // constant_pool entry at that index must be a CONSTANT_Fieldref_info
                    // (&sect;4.4.2)
                    // structure representing a field for which a method handle is to be created.
                    valid = (tag == Tag.FIELD_REF);
                    break;
                case REF_invokeVirtual: // fall-through
                case REF_newInvokeSpecial:
                    // If the value of the reference_kind item is 5 (REF_invokeVirtual) or 8
                    // (REF_newInvokeSpecial), then the constant_pool entry at that index must be a
                    // CONSTANT_Methodref_info structure (&sect;4.4.2) representing a class's method
                    // or constructor (&sect;2.9) for which a method handle is to be created.
                    valid = tag == Tag.METHOD_REF;
                    break;
                case REF_invokeStatic: // fall-through
                case REF_invokeSpecial:
                    // If the value of the reference_kind item is 6 (REF_invokeStatic) or 7
                    // (REF_invokeSpecial), then if the class file version number is less than 52.0,
                    // the constant_pool entry at that index must be a CONSTANT_Methodref_info
                    // structure representing a class's method for which a method handle is to be
                    // created; if the class file version number is 52.0 or above, the constant_pool
                    // entry at that index must be either a CONSTANT_Methodref_info structure or a
                    // CONSTANT_InterfaceMethodref_info structure (&sect;4.4.2) representing a
                    // class's or interface's method for which a method handle is to be created.
                    valid = (tag == Tag.METHOD_REF) ||
                                    (pool.getMajorVersion() >= ClassfileParser.JAVA_8_VERSION && tag == Tag.INTERFACE_METHOD_REF);
                    break;

                case REF_invokeInterface:
                    // If the value of the reference_kind item is 9 (REF_invokeInterface), then the
                    // constant_pool entry at that index must be a CONSTANT_InterfaceMethodref_info
                    // structure representing an interface's method for which a method handle is to
                    // be created.
                    valid = (tag == Tag.INTERFACE_METHOD_REF);
                    break;
            }

            if (!valid) {
                throw ValidationException.raise("Ill-formed constant: " + tag());
            }
        }

        private MemberRefConstant.Indexes getMember(ConstantPool pool) {
            return pool.memberAt(refIndex);
        }

        @Override
        public boolean isSame(ImmutablePoolConstant other, ConstantPool thisPool, ConstantPool otherPool) {
            if (!(other instanceof Index otherConstant)) {
                return false;
            }
            return getRefKind() == otherConstant.getRefKind() && getMember(thisPool).isSame(otherConstant.getMember(otherPool), thisPool, otherPool);
        }

        @Override
        public void dump(ByteBuffer buf) {
            buf.put(refKind);
            buf.putChar(refIndex);
        }

        @Override
        public String toString(ConstantPool pool) {
            return getRefKind() + " " + pool.at(getRefIndex()).toString(pool);
        }
    }

}
