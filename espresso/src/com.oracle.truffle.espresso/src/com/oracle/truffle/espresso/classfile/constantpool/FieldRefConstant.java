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

import java.util.Objects;

import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.classfile.ConstantPool.Tag;
import com.oracle.truffle.espresso.classfile.RuntimeConstantPool;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Descriptor;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.perf.DebugCounter;

public interface FieldRefConstant extends MemberRefConstant {

    /* static final */ DebugCounter FIELDREF_RESOLVE_COUNT = DebugCounter.create("FieldREf.resolve calls");

    static FieldRefConstant create(int classIndex, int nameAndTypeIndex) {
        return new Indexes(classIndex, nameAndTypeIndex);
    }

    @Override
    default Tag tag() {
        return Tag.FIELD_REF;
    }

    @SuppressWarnings("uncheked")
    default Symbol<Type> getType(ConstantPool pool) {
        // TODO(peterssen): Validate type descriptor.
        return Types.fromDescriptor(getDescriptor(pool));
    }

    final class Indexes extends MemberRefConstant.Indexes implements FieldRefConstant, Resolvable {
        Indexes(int classIndex, int nameAndTypeIndex) {
            super(classIndex, nameAndTypeIndex);
        }

        /**
         * <h3>5.4.3.2. Field Resolution</h3>
         *
         * To resolve an unresolved symbolic reference from D to a field in a class or interface C,
         * the symbolic reference to C given by the field reference must first be resolved
         * (&sect;5.4.3.1). Therefore, any exception that can be thrown as a result of failure of
         * resolution of a class or interface reference can be thrown as a result of failure of
         * field resolution. If the reference to C can be successfully resolved, an exception
         * relating to the failure of resolution of the field reference itself can be thrown.
         *
         * When resolving a field reference, field resolution first attempts to look up the
         * referenced field in C and its superclasses:
         * <ol>
         * <li>If C declares a field with the name and descriptor specified by the field reference,
         * field lookup succeeds. The declared field is the result of the field lookup.
         * <li>Otherwise, field lookup is applied recursively to the direct superinterfaces of the
         * specified class or interface C.
         * <li>Otherwise, if C has a superclass S, field lookup is applied recursively to S.
         * <li>Otherwise, field lookup fails.
         * </ol>
         *
         * Then:
         * <ul>
         * <li>If field lookup fails, field resolution throws a NoSuchFieldError.
         * <li>Otherwise, if field lookup succeeds but the referenced field is not accessible
         * (&sect;5.4.4) to D, field resolution throws an IllegalAccessError.
         * <li>Otherwise, let < E, L1 > be the class or interface in which the referenced field is
         * actually declared and let L2 be the defining loader of D.
         * <li>Given that the type of the referenced field is Tf, let T be Tf if Tf is not an array
         * type, and let T be the element type (&sect;2.4) of Tf otherwise.
         * <li>The Java Virtual Machine must impose the loading constraint that TL1 = TL2
         * (&sect;5.3.4).
         * </ul>
         */
        private static Field lookupField(Klass seed, Symbol<Name> name, Symbol<Type> type) {
            Field f = seed.lookupDeclaredField(name, type);
            if (f != null) {
                return f;
            }
            for (Klass i : seed.getSuperInterfaces()) {
                f = lookupField(i, name, type);
                if (f != null) {
                    return f;
                }
            }
            if (seed.getSuperKlass() != null) {
                return lookupField(seed.getSuperKlass(), name, type);
            }
            return null;
        }

        @Override
        public ResolvedConstant resolve(RuntimeConstantPool pool, int thisIndex, Klass accessingKlass) {
            FIELDREF_RESOLVE_COUNT.inc();
            Klass holderKlass = getResolvedHolderKlass(accessingKlass, pool);
            Symbol<Name> name = getName(pool);
            Symbol<Type> type = getType(pool);

            Field field = lookupField(holderKlass, name, type);
            if (field == null) {
                Meta meta = pool.getContext().getMeta();
                throw meta.throwExceptionWithMessage(meta.java_lang_NoSuchFieldError, meta.toGuestString(name));
            }

            MemberRefConstant.doAccessCheck(accessingKlass, holderKlass, field, pool.getContext().getMeta());

            field.checkLoadingConstraints(accessingKlass.getDefiningClassLoader(), field.getDeclaringKlass().getDefiningClassLoader());

            return new Resolved(field);
        }

        @Override
        public void validate(ConstantPool pool) {
            super.validate(pool);
            pool.nameAndTypeAt(nameAndTypeIndex).validateField(pool);
        }
    }

    final class Resolved implements FieldRefConstant, Resolvable.ResolvedConstant {
        private final Field.FieldVersion resolved;

        Resolved(Field resolvedField) {
            Objects.requireNonNull(resolvedField);
            this.resolved = resolvedField.getFieldVersion();
        }

        @Override
        public Symbol<Type> getType(ConstantPool pool) {
            return resolved.getType();
        }

        @Override
        public Field.FieldVersion value() {
            return resolved;
        }

        @Override
        public Symbol<Name> getHolderKlassName(ConstantPool pool) {
            throw EspressoError.shouldNotReachHere("Field already resolved");
        }

        @Override
        public Symbol<Name> getName(ConstantPool pool) {
            return resolved.getField().getName();
        }

        @Override
        public Symbol<? extends Descriptor> getDescriptor(ConstantPool pool) {
            return resolved.getType();
        }
    }
}
