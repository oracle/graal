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
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;

final class ClassHierarchyAssumptionImpl implements ClassHierarchyAssumption {
    static final ClassHierarchyAssumption AlwaysValid = new ClassHierarchyAssumptionImpl(Assumption.ALWAYS_VALID);
    static final ClassHierarchyAssumption NeverValid = new ClassHierarchyAssumptionImpl(Assumption.NEVER_VALID);

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

    ClassHierarchyAssumptionImpl(Method method) {
        this(method.toString() + " is leaf");
    }

    @Override
    public Assumption getAssumption() {
        return underlying;
    }
}
