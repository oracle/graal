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
 * optimizations, but not necessarily precise (e.g. may not detect an effectively leaf type).
 * <p>
 * The oracle is only valid within an {@link EspressoContext}.
 */
public interface ClassHierarchyOracle {
    final class LeafTypeAssumptionAccessor {
        protected LeafTypeAssumptionAccessor() {
        }
    }

    /**
     * Must be called to initialize {@code leafTypeAssumption} of {@code newKlass}. In addition, it
     * communicates to the oracle that a new klass has been created and its ancestors are no longer
     * leaves.
     *
     * @param newKlass -- newly created class
     * @return the assumption, indicating whether the class is a leaf in class hierarchy.
     */
    LeafTypeAssumption createAssumptionForNewKlass(ObjectKlass newKlass);

    /**
     * @return the assumption, valid iff {@code klass} is a leaf in class hierarchy. Automatically
     *         invalidated in {@link #createAssumptionForNewKlass(ObjectKlass)} when a child of
     *         {@code klass} is created.
     */
    LeafTypeAssumption isLeafClass(ObjectKlass klass);
}
