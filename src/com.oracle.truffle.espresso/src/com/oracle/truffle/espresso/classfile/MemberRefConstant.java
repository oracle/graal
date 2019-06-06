/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Descriptor;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;

import static com.oracle.truffle.espresso.classfile.ConstantPool.Tag.CLASS;
import static com.oracle.truffle.espresso.classfile.ConstantPool.Tag.NAME_AND_TYPE;

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

        protected Klass getResolvedHolderKlass(Klass accessingKlass, RuntimeConstantPool pool) {
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
        public void checkValidity(ConstantPool pool) {
            if (pool.at(classIndex).tag() != CLASS || pool.at(nameAndTypeIndex).tag() != NAME_AND_TYPE) {
                throw new VerifyError("Ill-formed constant: " + tag());
            }
            pool.at(classIndex).checkValidity(pool);
            pool.at(nameAndTypeIndex).checkValidity(pool);
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
    static boolean checkAccess(Klass accessingKlass, Klass resolvedKlass, Field f) {
        if (f.isPublic()) {
            return true;
        }
        Klass fieldKlass = f.getDeclaringKlass();
        if (f.isProtected()) {
            if (!f.isStatic()) {
                if (resolvedKlass.isAssignableFrom(accessingKlass) || accessingKlass.isAssignableFrom(resolvedKlass)) {
                    return true;
                }
            } else {
                if (fieldKlass.isAssignableFrom(accessingKlass)) {
                    return true;
                }
            }
        }
        if (f.isProtected() || f.isPackagePrivate()) {
            if (accessingKlass.sameRuntimePackage(fieldKlass)) {
                return true;
            }
        }
        if (f.isPrivate() && fieldKlass == accessingKlass) {
            return true;
        }
        // MagicAccessorImpl marks internal reflection classes that have access to eveything.
        if (accessingKlass.getMeta().MagicAccessorImpl.isAssignableFrom(accessingKlass)) {
            return true;
        }

        if (accessingKlass.getHostClass() != null) {
            return checkAccess(accessingKlass.getHostClass(), resolvedKlass, f);
        }
        return false;
    }

    // Same as above.
    static boolean checkAccess(Klass accessingKlass, Klass resolvedKlass, Method m) {
        Klass methodKlass = m.getDeclaringKlass();

        if (m.isPublic()) {
            return true;
        }

        if (Name.clone.equals(m.getName()) && methodKlass.isJavaLangObject()) {
            if (resolvedKlass.isArray()) {
                return true;
            }
        }

        if (m.isProtected()) {
            if (!m.isStatic()) {
                if (resolvedKlass.isAssignableFrom(accessingKlass) || accessingKlass.isAssignableFrom(resolvedKlass)) {
                    return true;
                }
            } else {
                if (methodKlass.isAssignableFrom(accessingKlass)) {
                    return true;
                }
            }
        }
        if (m.isProtected() || m.isPackagePrivate()) {
            if (accessingKlass.sameRuntimePackage(methodKlass)) {
                return true;
            }
        }
        if (m.isPrivate() && methodKlass == accessingKlass) {
            return true;
        }
        // MagicAccessorImpl marks internal reflection classes that have access to everything.
        if (accessingKlass.getMeta().MagicAccessorImpl.isAssignableFrom(accessingKlass)) {
            return true;
        }

        if (accessingKlass.getHostClass() != null) {
            return checkAccess(accessingKlass.getHostClass(), resolvedKlass, m);
        }

        return false;
    }

}
