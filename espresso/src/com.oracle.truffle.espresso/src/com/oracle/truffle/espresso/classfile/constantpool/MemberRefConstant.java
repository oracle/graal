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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.classfile.RuntimeConstantPool;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Descriptor;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Member;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.Meta;

/**
 * Interface denoting a field or method entry in a constant pool.
 */
public interface MemberRefConstant extends PoolConstant {

    /**
     * Gets the class in which this method or field is declared. Note that the actual holder after
     * resolution may be a super class of the class described by the one returned by this method.
     */
    Symbol<Name> getHolderKlassName(ConstantPool pool);

    /**
     * Gets the name of this field or method.
     *
     * @param pool the constant pool that maybe be required to convert a constant pool index to a
     *            name
     */
    Symbol<Name> getName(ConstantPool pool);

    /**
     * Gets the descriptor (type or signature) of this field or method.
     *
     * @param pool the constant pool that maybe be required to convert a constant pool index to a
     *            name
     */
    Symbol<? extends Descriptor> getDescriptor(ConstantPool pool);

    @Override
    default String toString(ConstantPool pool) {
        return getHolderKlassName(pool) + "." + getName(pool) + getDescriptor(pool);
    }

    abstract class Indexes implements MemberRefConstant {

        final char classIndex;

        final char nameAndTypeIndex;

        Indexes(int classIndex, int nameAndTypeIndex) {
            this.classIndex = PoolConstant.u2(classIndex);
            this.nameAndTypeIndex = PoolConstant.u2(nameAndTypeIndex);
        }

        @Override
        public Symbol<Name> getHolderKlassName(ConstantPool pool) {
            return pool.classAt(classIndex).getName(pool);
        }

        public Klass getResolvedHolderKlass(Klass accessingKlass, RuntimeConstantPool pool) {
            return pool.resolvedKlassAt(accessingKlass, classIndex);
        }

        @Override
        public Symbol<Name> getName(ConstantPool pool) {
            return pool.nameAndTypeAt(nameAndTypeIndex).getName(pool);
        }

        @Override
        public Symbol<? extends Descriptor> getDescriptor(ConstantPool pool) {
            return pool.nameAndTypeAt(nameAndTypeIndex).getDescriptor(pool);
        }

        @Override
        public void validate(ConstantPool pool) {
            pool.classAt(classIndex).validate(pool);
            pool.nameAndTypeAt(nameAndTypeIndex).validate(pool);
        }

        @Override
        public void dump(ByteBuffer buf) {
            buf.putChar(classIndex);
            buf.putChar(nameAndTypeIndex);
        }
    }

    @TruffleBoundary
    static void doAccessCheck(Klass accessingKlass, Klass resolvedKlass, Member<? extends Descriptor> member, Meta meta) {
        if (!MemberRefConstant.checkAccess(accessingKlass, resolvedKlass, member)) {
            String message = "Class " + accessingKlass.getExternalName() + " cannot access method " + resolvedKlass.getExternalName() + "#" + member.getName();
            throw meta.throwExceptionWithMessage(meta.java_lang_IllegalAccessError, meta.toGuestString(message));
        }
    }

    /**
     * <h3>5.4.4. Access Control</h3>
     *
     * A field or method R is accessible to a class or interface D if and only if any of the
     * following is true:
     * <ul>
     * <li>R is public.
     * <li>R is protected and is declared in a class C, and D is either a subclass of C or C itself.
     * Furthermore, if R is not static, then the symbolic reference to R must contain a symbolic
     * reference to a class T, such that T is either a subclass of D, a superclass of D, or D
     * itself.
     * <li>R is either protected or has default access (that is, neither public nor protected nor
     * private), and is declared by a class in the same run-time package as D.
     * <li>R is private and is declared in D.
     * </ul>
     */
    static boolean checkAccess(Klass accessingKlass, Klass resolvedKlass, Member<? extends Descriptor> member) {
        if (member.isPublic()) {
            return true;
        }
        Klass memberKlass = member.getDeclaringKlass();
        if (member instanceof Method && Name.clone.equals(member.getName()) && memberKlass.isJavaLangObject()) {
            if (resolvedKlass.isArray()) {
                return true;
            }
        }
        if (member.isProtected()) {
            if (!member.isStatic()) {
                if (resolvedKlass.isAssignableFrom(accessingKlass) || accessingKlass.isAssignableFrom(resolvedKlass)) {
                    return true;
                }
            } else {
                if (memberKlass.isAssignableFrom(accessingKlass)) {
                    return true;
                }
            }
        }
        if (member.isProtected() || member.isPackagePrivate()) {
            if (accessingKlass.sameRuntimePackage(memberKlass)) {
                return true;
            }
        }
        if (member.isPrivate() && nestMateTest(accessingKlass, memberKlass)) {
            return true;
        }
        // MagicAccessorImpl marks internal reflection classes that have access to everything.
        if (accessingKlass.getMeta().sun_reflect_MagicAccessorImpl.isAssignableFrom(accessingKlass)) {
            return true;
        }

        if (accessingKlass.getHostClass() != null) {
            return checkAccess(accessingKlass.getHostClass(), resolvedKlass, member);
        }
        return false;
    }

    static boolean nestMateTest(Klass k1, Klass k2) {
        return k1 == k2 || k1.nest() == k2.nest();
    }

}
