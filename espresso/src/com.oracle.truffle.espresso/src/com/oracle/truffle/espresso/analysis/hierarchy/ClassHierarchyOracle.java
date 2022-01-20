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

import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.runtime.EspressoContext;

/**
 * {@code ClassHierarchyOracle} provides information about class hierarchy in the guest code,
 * evolving as new classes are loaded. The oracle is safe, i.e. its answers never cause incorrect
 * optimizations, but not necessarily precise (e.g. may not detect that a class has no subclasses).
 * <p>
 * The oracle is only valid within an {@link EspressoContext}.
 */
public interface ClassHierarchyOracle {
    final class ClassHierarchyAccessor {
        static final ClassHierarchyAccessor accessor = new ClassHierarchyAccessor();

        private ClassHierarchyAccessor() {
        }
    }

    /**
     * Must be called to initialize {@code noConcreteSubclassesAssumption} of {@code newKlass}. In
     * addition, it communicates to the oracle that a new klass has been created and its ancestors
     * are no longer leaves.
     *
     * @param newKlass -- newly created class
     * @return the assumption, indicating whether the class has subclasses
     */
    ClassHierarchyAssumption createAssumptionForNewKlass(ObjectKlass.KlassVersion newKlass);

    /**
     * @return the assumption, valid only if {@code klass} is a concrete class and has no concrete
     *         subclasses. Automatically invalidated in
     *         {@link #createAssumptionForNewKlass(ObjectKlass.KlassVersion)} when a concrete child
     *         of {@code klass} is created.
     */

    ClassHierarchyAssumption isLeaf(ObjectKlass klass);

    /**
     * @return the assumption, valid only if {@code klass} has no implementors, including itself
     *         (i.e. it must be abstract or an interface). Automatically invalidated in
     *         {@link #createAssumptionForNewKlass(ObjectKlass.KlassVersion)} when a concrete child
     *         of {@code klass} is created.
     */
    ClassHierarchyAssumption hasNoImplementors(ObjectKlass klass);

    SingleImplementor initializeImplementorForNewKlass(ObjectKlass.KlassVersion klass);

    AssumptionGuardedValue<ObjectKlass> readSingleImplementor(ObjectKlass klass);
}
