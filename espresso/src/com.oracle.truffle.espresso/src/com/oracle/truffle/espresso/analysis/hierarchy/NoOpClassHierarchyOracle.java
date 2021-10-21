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
    protected static class LeafTypeAssumptionImpl implements LeafTypeAssumption {
        private final Assumption underlying;

        // Used only to create never valid and always valid instances
        private LeafTypeAssumptionImpl(Assumption underlyingAssumption) {
            underlying = underlyingAssumption;
        }

        private LeafTypeAssumptionImpl(String assumptionName) {
            underlying = Truffle.getRuntime().createAssumption(assumptionName);
        }

        LeafTypeAssumptionImpl(ObjectKlass klass) {
            this(klass.getNameAsString() + " is a leaf type");
        }

        @Override
        public Assumption getAssumption() {
            return underlying;
        }
    }

    protected static final ClassHierarchyAccessor classHierarchyInfoAccessor = new ClassHierarchyAccessor();

    protected static final LeafTypeAssumption FinalIsAlwaysLeaf = new LeafTypeAssumptionImpl(AlwaysValidAssumption.INSTANCE);
    protected static final LeafTypeAssumption NotLeaf = new LeafTypeAssumptionImpl(NeverValidAssumption.INSTANCE);

    @Override
    public LeafTypeAssumption createAssumptionForNewKlass(ObjectKlass newKlass) {
        if (newKlass.isFinalFlagSet()) {
            return FinalIsAlwaysLeaf;
        }
        return NotLeaf;
    }

    @Override
    public LeafTypeAssumption isLeafClass(ObjectKlass klass) {
        return klass.getLeafTypeAssumption(classHierarchyInfoAccessor);
    }

    @Override
    public SingleImplementor initializeImplementorForNewKlass(ObjectKlass klass) {
        return SingleImplementor.MultipeImplementors;
    }

    @Override
    public SingleImplementorSnapshot readSingleImplementor(ObjectKlass klass) {
        return SingleImplementorSnapshot.Invalid;
    }
}
