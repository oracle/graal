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

import java.nio.ByteBuffer;

import com.oracle.truffle.espresso.EspressoOptions;
import com.oracle.truffle.espresso.classfile.ClassfileParser;
import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.classfile.ConstantPool.Tag;
import com.oracle.truffle.espresso.classfile.RuntimeConstantPool;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;

public interface MethodHandleConstant extends PoolConstant {

    static MethodHandleConstant create(int refKind, int refIndex) {
        return new Index(refKind, refIndex);
    }

    default Tag tag() {
        return Tag.METHODHANDLE;
    }

    enum RefKind {
        GETFIELD(1),
        GETSTATIC(2),
        PUTFIELD(3),
        PUTSTATIC(4),
        INVOKEVIRTUAL(5),
        INVOKESTATIC(6),
        INVOKESPECIAL(7),
        NEWINVOKESPECIAL(8),
        INVOKEINTERFACE(9);

        public final int value;

        RefKind(int value) {
            this.value = value;
        }

        public static RefKind forValue(int value) {
            // @formatter:off
            switch(value) {
                case 1: return GETFIELD;
                case 2: return GETSTATIC;
                case 3: return PUTFIELD;
                case 4: return PUTSTATIC;
                case 5: return INVOKEVIRTUAL;
                case 6: return INVOKESTATIC;
                case 7: return INVOKESPECIAL;
                case 8: return NEWINVOKESPECIAL;
                case 9: return INVOKEINTERFACE;
                default: return null;
            }
            // @formatter:on
        }
    }

    RefKind getRefKind();

    char getRefIndex();

    @Override
    default String toString(ConstantPool pool) {
        return getRefKind() + " " + pool.at(getRefIndex()).toString(pool);
    }

    final class Index implements MethodHandleConstant, Resolvable {

        private final byte refKind;
        private final char refIndex;

        Index(int refKind, int refIndex) {
            this.refKind = PoolConstant.u1(refKind);
            this.refIndex = PoolConstant.u2(refIndex);
        }

        @Override
        public RefKind getRefKind() {
            // not yet validated! Could be null
            return RefKind.forValue(refKind);
        }

        @Override
        public char getRefIndex() {
            return refIndex;
        }

        @Override
        public ResolvedConstant resolve(RuntimeConstantPool pool, int thisIndex, Klass accessingKlass) {
            Meta meta = pool.getContext().getMeta();
            if (meta.getContext().SpecCompliancyMode == EspressoOptions.SpecCompliancyMode.STRICT || meta.getJavaVersion().java9OrLater()) {
                return specCompliantResolution(pool, accessingKlass, meta);
            } else {
                return hotspotResolutionBehavior(pool, accessingKlass, meta);
            }
        }

        private ResolvedConstant specCompliantResolution(RuntimeConstantPool pool, Klass accessingKlass, Meta meta) {
            StaticObject mtype;
            Klass mklass;
            Symbol<Name> refName;

            Tag refTag = pool.tagAt(getRefIndex());
            if (refTag == Tag.METHOD_REF || refTag == Tag.INTERFACE_METHOD_REF) {
                Method target = pool.resolvedMethodAt(accessingKlass, refIndex);
                Symbol<Symbol.Type>[] parsed = target.getParsedSignature();

                mtype = MethodTypeConstant.signatureToMethodType(parsed, accessingKlass, false, meta);
                mklass = target.getDeclaringKlass();
                refName = target.getName();
            } else {
                assert refTag == Tag.FIELD_REF;
                Field field = pool.resolvedFieldAt(accessingKlass, refIndex);
                mtype = meta.resolveSymbolAndAccessCheck(field.getType(), accessingKlass).mirror();
                mklass = field.getDeclaringKlass();
                refName = field.getName();
            }

            return linkMethodHandleConstant(accessingKlass, meta, mtype, mklass, refName);
        }

        /**
         * Resolves a method handle without resolving the method reference, which is not the
         * behavior described in the specs {5.4.3.5. Method Type and Method Handle Resolution }
         * <p>
         * see {@code JDK-8188145}
         */
        private ResolvedConstant hotspotResolutionBehavior(RuntimeConstantPool pool, Klass accessingKlass, Meta meta) {
            StaticObject mtype;
            Klass mklass;
            Symbol<Name> refName;

            Tag refTag = pool.tagAt(getRefIndex());
            if (refTag == Tag.METHOD_REF || refTag == Tag.INTERFACE_METHOD_REF) {
                MethodRefConstant ref = pool.methodAt(getRefIndex());
                Symbol<Symbol.Signature> signature = ref.getSignature(pool);
                Symbol<Symbol.Type>[] parsed = meta.getSignatures().parsed(signature);

                mtype = MethodTypeConstant.signatureToMethodType(parsed, accessingKlass, false, meta);
                mklass = pool.resolvedKlassAt(accessingKlass, ((MemberRefConstant.Indexes) ref).classIndex);
                refName = ref.getName(pool);
            } else {
                assert refTag == Tag.FIELD_REF;
                assert pool.fieldAt(getRefIndex()) instanceof FieldRefConstant.Indexes;
                FieldRefConstant.Indexes ref = (FieldRefConstant.Indexes) pool.fieldAt(getRefIndex());

                Symbol<Symbol.Type> type = ref.getType(pool);
                mtype = meta.resolveSymbolAndAccessCheck(type, accessingKlass).mirror();
                mklass = ref.getResolvedHolderKlass(accessingKlass, pool);
                refName = ref.getName(pool);
            }

            return linkMethodHandleConstant(accessingKlass, meta, mtype, mklass, refName);
        }

        private ResolvedConstant linkMethodHandleConstant(Klass accessingKlass, Meta meta, StaticObject mtype, Klass mklass, Symbol<Name> refName) {
            StaticObject mname = meta.toGuestString(refName);
            return new Resolved((StaticObject) meta.java_lang_invoke_MethodHandleNatives_linkMethodHandleConstant.invokeDirect(
                            null,
                            accessingKlass.mirror(), (int) refKind,
                            mklass.mirror(), mname, mtype));
        }

        @Override
        public void validate(ConstantPool pool) {
            pool.memberAt(refIndex).validate(pool);

            RefKind kind = getRefKind();

            Symbol<Name> memberName = pool.memberAt(refIndex).getName(pool);
            if (Name._clinit_.equals(memberName)) {
                throw ConstantPool.classFormatError("Ill-formed constant: " + tag());
            }

            // If the value is 8 (REF_newInvokeSpecial), the name of the method represented by a
            // CONSTANT_Methodref_info structure must be <init>.
            if (Name._init_.equals(memberName) && kind != RefKind.NEWINVOKESPECIAL) {
                throw ConstantPool.classFormatError("Ill-formed constant: " + tag());
            }
            if (getRefKind() == null) {
                throw ConstantPool.classFormatError("Ill-formed constant: " + tag());
            }

            // If the value of the reference_kind item is 5 (REF_invokeVirtual), 6
            // (REF_invokeStatic), 7 (REF_invokeSpecial), or 9 (REF_invokeInterface), the name of
            // the method represented by a CONSTANT_Methodref_info structure or a
            // CONSTANT_InterfaceMethodref_info structure must not be <init> or <clinit>.
            if (memberName.equals(Name._init_) || memberName.equals(Name._clinit_)) {
                if (kind == RefKind.INVOKEVIRTUAL ||
                                kind == RefKind.INVOKESTATIC ||
                                kind == RefKind.INVOKESPECIAL ||
                                kind == RefKind.INVOKEINTERFACE) {
                    throw ConstantPool.classFormatError("Ill-formed constant: " + tag());
                }
            }

            boolean valid = false;
            Tag tag = pool.at(refIndex).tag();
            switch (getRefKind()) {
                case GETFIELD: // fall-through
                case GETSTATIC: // fall-through
                case PUTFIELD: // fall-through
                case PUTSTATIC:
                    // If the value of the reference_kind item is 1 (REF_getField), 2
                    // (REF_getStatic), 3 (REF_putField), or 4 (REF_putStatic), then the
                    // constant_pool entry at that index must be a CONSTANT_Fieldref_info
                    // (&sect;4.4.2)
                    // structure representing a field for which a method handle is to be created.
                    valid = (tag == Tag.FIELD_REF);
                    break;
                case INVOKEVIRTUAL: // fall-through
                case NEWINVOKESPECIAL:
                    // If the value of the reference_kind item is 5 (REF_invokeVirtual) or 8
                    // (REF_newInvokeSpecial), then the constant_pool entry at that index must be a
                    // CONSTANT_Methodref_info structure (&sect;4.4.2) representing a class's method
                    // or constructor (&sect;2.9) for which a method handle is to be created.
                    valid = tag == Tag.METHOD_REF;
                    break;
                case INVOKESTATIC: // fall-through
                case INVOKESPECIAL:
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

                case INVOKEINTERFACE:
                    // If the value of the reference_kind item is 9 (REF_invokeInterface), then the
                    // constant_pool entry at that index must be a CONSTANT_InterfaceMethodref_info
                    // structure representing an interface's method for which a method handle is to
                    // be created.
                    valid = (tag == Tag.INTERFACE_METHOD_REF);
                    break;
            }

            if (!valid) {
                throw ConstantPool.classFormatError("Ill-formed constant: " + tag());
            }

        }

        @Override
        public void dump(ByteBuffer buf) {
            buf.put(refKind);
            buf.putChar(refIndex);
        }
    }

    final class Resolved implements Resolvable.ResolvedConstant {
        private StaticObject payload;

        Resolved(StaticObject payload) {
            this.payload = payload;
        }

        @Override
        public Object value() {
            return payload;
        }

        public Tag tag() {
            return Tag.METHODHANDLE;
        }

        @Override
        public String toString(ConstantPool pool) {
            return payload.toString();
        }
    }
}
