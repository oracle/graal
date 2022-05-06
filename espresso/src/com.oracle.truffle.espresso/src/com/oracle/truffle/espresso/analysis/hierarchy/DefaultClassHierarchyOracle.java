/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;

/**
 * Computes the classes that are effectively final by keeping track of currently loaded classes. To
 * compute currently leaf classes, it creates {@code noConcreteSubclassesAssumption} in the
 * {@link ObjectKlass} constructor and invalidates it when a descendant of this class is
 * initialized.
 */
public final class DefaultClassHierarchyOracle implements ClassHierarchyOracle {
    @Override
    public void registerNewKlassVersion(ObjectKlass.KlassVersion newVersion) {
        if (newVersion.isConcrete()) {
            addImplementorToAncestors(newVersion);
            updateVirtualAndInterfaceTables(newVersion);
        }
    }

    @Override
    public ClassHierarchyAssumption createAssumptionForNewKlass(ObjectKlass newKlass) {
        return new ClassHierarchyAssumptionImpl(newKlass);
    }

    @Override
    public ClassHierarchyAssumption isLeafKlass(ObjectKlass klass) {
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
    private void addImplementor(ObjectKlass superInterface, ObjectKlass.KlassVersion implementor) {
        superInterface.getNoConcreteSubclassesAssumption(ClassHierarchyAccessor.accessor).invalidate();
        superInterface.getImplementor(ClassHierarchyAccessor.accessor).addImplementor(implementor);
        for (ObjectKlass ancestorInterface : superInterface.getSuperInterfaces()) {
            addImplementor(ancestorInterface, implementor);
        }
    }

    private void addImplementorToAncestors(ObjectKlass.KlassVersion newKlass) {
        for (ObjectKlass superInterface : newKlass.getSuperInterfaces()) {
            addImplementor(superInterface, newKlass);
        }

        ObjectKlass currentKlass = newKlass.getSuperKlass();
        while (currentKlass != null) {
            currentKlass.getNoConcreteSubclassesAssumption(ClassHierarchyAccessor.accessor).invalidate();
            currentKlass.getImplementor(ClassHierarchyAccessor.accessor).addImplementor(newKlass);

            for (ObjectKlass superInterface : currentKlass.getSuperInterfaces()) {
                addImplementor(superInterface, newKlass);
            }
            currentKlass = currentKlass.getSuperKlass();
        }
    }

    private void updateVirtualAndInterfaceTables(ObjectKlass.KlassVersion newKlassVersion) {
        // We only reach here for concrete classes, we need to be careful that there might
        // be some "leaf" methods that have already been overridden in abstract classes
        // those must be taken care of now that we see a concrete subclass
        ObjectKlass newKlass = newKlassVersion.getKlass();
        Method.MethodVersion[] vTable = newKlass.getVTable();
        for (int i = 0; i < vTable.length; i++) {
            Method.MethodVersion m = vTable[i];
            ObjectKlass current = newKlassVersion.getSuperKlass();
            while (current != null) {
                Method.MethodVersion[] superVTable = current.getKlassVersion().getVtable();
                if (i >= superVTable.length) {
                    break;
                }
                Method.MethodVersion overridden = superVTable[i];
                if (overridden != m) {
                    overridden.getMethod().getLeafAssumption(ClassHierarchyAccessor.accessor).invalidate();
                }
                if (current.isConcrete()) {
                    // concrete classes have already been visited by this method.
                    break;
                }
                current = current.getSuperKlass();
            }
        }
        Method.MethodVersion[][] itables = newKlassVersion.getItable();
        for (int tableIndex = 0; tableIndex < itables.length; tableIndex++) {
            updateLeafAssumptions(itables[tableIndex], newKlassVersion.getiKlassTable()[tableIndex].getKlass());
        }
    }

    /**
     * Note: Leaf assumptions are not invalidated on creation of an interface. This means that in
     * the following example:
     *
     * <pre>
     * interface A {
     *     default void m() {
     *     }
     * }
     *
     * interface B extends A {
     *     default void m() {
     *     }
     * }
     * </pre>
     *
     * Unless a concrete class that implements B is loaded, the leaf assumption for A.m() will not
     * be invalidated.
     */
    private void updateLeafAssumptions(Method.MethodVersion[] itable, ObjectKlass currInterface) {
        for (int methodIndex = 0; methodIndex < itable.length; methodIndex++) {
            Method.MethodVersion m = itable[methodIndex];
            // This class' itable entry for this method is not the interface's declared method.
            if (m.getDeclaringKlassRef() != currInterface) {
                Method.MethodVersion intfMethod = currInterface.getInterfaceMethodsTable()[methodIndex];
                // sanity checks
                assert intfMethod.getDeclaringKlassRef() == currInterface;
                assert m.getMethod().canOverride(intfMethod.getMethod()) && m.getName() == intfMethod.getName() && m.getRawSignature() == intfMethod.getRawSignature();
                isLeafMethod(intfMethod).invalidate();
            }
        }
    }

    @Override
    public SingleImplementor initializeImplementorForNewKlass(ObjectKlass klass) {
        // java.io.Serializable and java.lang.Cloneable are always implemented by all arrays
        if (klass.getType() == Symbol.Type.java_io_Serializable || klass.getType() == Symbol.Type.java_lang_Cloneable) {
            return SingleImplementor.MultipleImplementors;
        }
        if (klass.isAbstract() || klass.isInterface()) {
            return new SingleImplementor();
        }
        return new SingleImplementor(klass);
    }

    @Override
    public AssumptionGuardedValue<ObjectKlass> readSingleImplementor(ObjectKlass klass) {
        return klass.getImplementor(ClassHierarchyAccessor.accessor).read();
    }

    @Override
    public ClassHierarchyAssumption createLeafAssumptionForNewMethod(Method newMethod) {
        if (newMethod.isAbstract()) {
            // Disabled for abstract methods to reduce footprint.
            return ClassHierarchyAssumptionImpl.NeverValid;
        }
        // Changing modifiers results in creating a new method, so it is correct to return
        // an AlwaysValid assumption
        if (newMethod.isStatic() || newMethod.isPrivate() || newMethod.isFinalFlagSet()) {
            // Nothing to assume, spare an assumption.
            return ClassHierarchyAssumptionImpl.AlwaysValid;
        }
        return new ClassHierarchyAssumptionImpl(newMethod);
    }

    @Override
    public ClassHierarchyAssumption isLeafMethod(Method method) {
        return method.getLeafAssumption(ClassHierarchyAccessor.accessor);
    }
}
