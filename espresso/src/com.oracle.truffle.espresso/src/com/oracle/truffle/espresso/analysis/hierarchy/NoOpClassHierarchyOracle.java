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

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.utilities.AlwaysValidAssumption;
import com.oracle.truffle.api.utilities.NeverValidAssumption;
import com.oracle.truffle.espresso.impl.ObjectKlass;

/**
 * An implementation of {@link ClassHierarchyOracle} which simply checks {@code final} modifier of a
 * class.
 */
public class NoOpClassHierarchyOracle implements ClassHierarchyOracle {
    protected static class ClassHierarchyAssumptionImpl implements ClassHierarchyAssumption {
        private final Assumption underlying;

        // Used only to create never valid and always valid instances
        private ClassHierarchyAssumptionImpl(Assumption underlyingAssumption) {
            underlying = underlyingAssumption;
        }

        private ClassHierarchyAssumptionImpl(String assumptionName) {
            underlying = Truffle.getRuntime().createAssumption(assumptionName);
        }

        ClassHierarchyAssumptionImpl(ObjectKlass klass) {
            this(klass.getNameAsString() + " has no concrete subclasses");
        }

        @Override
        public Assumption getAssumption() {
            return underlying;
        }
    }

    protected static final ClassHierarchyAccessor classHierarchyInfoAccessor = new ClassHierarchyAccessor();

    protected static final ClassHierarchyAssumption AlwaysValid = new ClassHierarchyAssumptionImpl(AlwaysValidAssumption.INSTANCE);
    protected static final ClassHierarchyAssumption NeverValid = new ClassHierarchyAssumptionImpl(NeverValidAssumption.INSTANCE);

    protected static final AssumptionGuardedValue<ObjectKlass> NotSingleImplementor = new AssumptionGuardedValue<>(NeverValidAssumption.INSTANCE, null);

    @Override
    public ClassHierarchyAssumption createAssumptionForNewKlass(ObjectKlass newKlass) {
        if (newKlass.isFinalFlagSet()) {
            return AlwaysValid;
        }
        return NeverValid;
    }

    @Override
    public ClassHierarchyAssumption isLeaf(ObjectKlass klass) {
        if (klass.isConcrete()) {
            return klass.getNoConcreteSubclassesAssumption(classHierarchyInfoAccessor);
        } else {
            return NeverValid;
        }
    }

    @Override
    public ClassHierarchyAssumption hasNoImplementors(ObjectKlass klass) {
        if (klass.isAbstract() || klass.isInterface()) {
            return klass.getNoConcreteSubclassesAssumption(classHierarchyInfoAccessor);
        } else {
            return NeverValid;
        }
    }

    @Override
    public SingleImplementor initializeImplementorForNewKlass(ObjectKlass klass) {
        return SingleImplementor.MultipleImplementors;
    }

    @Override
    public AssumptionGuardedValue<ObjectKlass> readSingleImplementor(ObjectKlass klass) {
        return NotSingleImplementor;
    }
}
