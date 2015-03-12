/*
 * Copyright (c) 2011, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.replacements;

import static com.oracle.graal.nodes.extended.BranchProbabilityNode.*;

import java.lang.reflect.*;

import sun.misc.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.replacements.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;

/**
 * Substitutions for {@link sun.misc.Unsafe} methods.
 */
@ClassSubstitution(sun.misc.Unsafe.class)
public class UnsafeSubstitutions {

    @MethodSubstitution(isStatic = false)
    public static Object allocateInstance(@SuppressWarnings("unused") final Unsafe thisObj, Class<?> clazz) {
        if (probability(SLOW_PATH_PROBABILITY, clazz.isPrimitive())) {
            DeoptimizeNode.deopt(DeoptimizationAction.None, DeoptimizationReason.RuntimeConstraint);
        }
        if (probability(SLOW_PATH_PROBABILITY, clazz.isArray() || clazz.isInterface() || Modifier.isAbstract(clazz.getModifiers()))) {
            DeoptimizeNode.deopt(DeoptimizationAction.None, DeoptimizationReason.RuntimeConstraint);
        }
        if (probability(SLOW_PATH_PROBABILITY, clazz == Class.class)) {
            DeoptimizeNode.deopt(DeoptimizationAction.None, DeoptimizationReason.RuntimeConstraint);
        }
        return DynamicNewInstanceNode.allocateInstance(clazz, true);
    }
}
