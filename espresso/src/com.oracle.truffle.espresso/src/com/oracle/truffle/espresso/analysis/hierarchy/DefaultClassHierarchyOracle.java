/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.analysis.hierarchy;

import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.impl.ObjectKlass;

/**
 * Computes the classes that are effectively final by keeping track of currently loaded classes. To
 * compute currently leaf classes, it creates {@code noConcreteSubclassesAssumption} in the
 * {@link ObjectKlass} constructor and invalidates it when a descendant of this class is
 * initialized.
 */
public class DefaultClassHierarchyOracle implements ClassHierarchyOracle {
    @Override
    public ClassHierarchyAssumption createAssumptionForNewKlass(ObjectKlass.KlassVersion newKlass) {
        if (newKlass.isConcrete()) {
            addImplementorToAncestors(newKlass.getKlass());
        }
        if (newKlass.isFinalFlagSet()) {
            return ClassHierarchyAssumptionImpl.AlwaysValid;
        }
        return new ClassHierarchyAssumptionImpl(newKlass.getKlass());
    }

    @Override
    public ClassHierarchyAssumption isLeaf(ObjectKlass klass) {
        if (klass.isConcrete()) {
            return klass.getNoConcreteSubclassesAssumption(ClassHierarchyAccessor.accessor);
        } else {
            return ClassHierarchyAssumptionImpl.NeverValid;
        }
    }

    @Override
    public ClassHierarchyAssumption hasNoImplementors(ObjectKlass klass) {
        if (klass.isAbstract() || klass.isInterface()) {
            return klass.getNoConcreteSubclassesAssumption(ClassHierarchyAccessor.accessor);
        } else {
            return ClassHierarchyAssumptionImpl.NeverValid;
        }
    }

    /**
     * Recursively adds {@code implementor} as an implementor of {@code superInterface} and its
     * parent interfaces.
     */
    private void addImplementor(ObjectKlass superInterface, ObjectKlass implementor) {
        superInterface.getNoConcreteSubclassesAssumption(ClassHierarchyAccessor.accessor).getAssumption().invalidate();
        superInterface.getImplementor(ClassHierarchyAccessor.accessor).addImplementor(implementor);
        for (ObjectKlass ancestorInterface : superInterface.getSuperInterfaces()) {
            addImplementor(ancestorInterface, implementor);
        }
    }

    private void addImplementorToAncestors(ObjectKlass newKlass) {
        for (ObjectKlass superInterface : newKlass.getSuperInterfaces()) {
            addImplementor(superInterface, newKlass);
        }

        ObjectKlass currentKlass = newKlass.getSuperKlass();
        while (currentKlass != null) {
            currentKlass.getNoConcreteSubclassesAssumption(ClassHierarchyAccessor.accessor).getAssumption().invalidate();
            currentKlass.getImplementor(ClassHierarchyAccessor.accessor).addImplementor(newKlass);

            for (ObjectKlass superInterface : currentKlass.getSuperInterfaces()) {
                addImplementor(superInterface, newKlass);
            }
            currentKlass = currentKlass.getSuperKlass();
        }
    }

    @Override
    public SingleImplementor initializeImplementorForNewKlass(ObjectKlass.KlassVersion klass) {
        // java.io.Serializable and java.lang.Cloneable are always implemented by all arrays
        if (klass.getKlass().getType() == Symbol.Type.java_io_Serializable || klass.getKlass().getType() == Symbol.Type.java_lang_Cloneable) {
            return SingleImplementor.MultipleImplementors;
        }
        if (klass.isAbstract() || klass.isInterface()) {
            return new SingleImplementor();
        }
        return new SingleImplementor(klass.getKlass());
    }

    @Override
    public AssumptionGuardedValue<ObjectKlass> readSingleImplementor(ObjectKlass klass) {
        return klass.getImplementor(ClassHierarchyAccessor.accessor).read();
    }
}
