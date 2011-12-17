/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.nodes.extended;

import java.util.*;

import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

public class BoxingMethodPool {

    private static final String BOX_METHOD = "valueOf";
    private final Set<RiMethod> specialMethods = new HashSet<RiMethod>();
    private final RiRuntime runtime;
    private final RiResolvedMethod[] boxingMethods = new RiResolvedMethod[CiKind.values().length];
    private final RiResolvedMethod[] unboxingMethods = new RiResolvedMethod[CiKind.values().length];
    private final RiResolvedField[] boxFields = new RiResolvedField[CiKind.values().length];

    public BoxingMethodPool(RiRuntime runtime) {
        this.runtime = runtime;
        initialize();
    }

    private void initialize() {
        try {
            initialize(CiKind.Boolean, Boolean.class, "booleanValue");
            initialize(CiKind.Byte, Byte.class, "byteValue");
            initialize(CiKind.Char, Character.class, "charValue");
            initialize(CiKind.Short, Short.class, "shortValue");
            initialize(CiKind.Int, Integer.class, "intValue");
            initialize(CiKind.Long, Long.class, "longValue");
            initialize(CiKind.Float, Float.class, "floatValue");
            initialize(CiKind.Double, Double.class, "doubleValue");
        } catch (SecurityException e) {
            throw new RuntimeException(e);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private void initialize(CiKind kind, Class<?> type, String unboxMethod) throws SecurityException, NoSuchMethodException {

        // Get boxing method from runtime.
        RiResolvedMethod boxingMethod = runtime.getRiMethod(type.getDeclaredMethod("valueOf", kind.toJavaClass()));
        specialMethods.add(boxingMethod);
        boxingMethods[kind.ordinal()] = boxingMethod;

        // Get unboxing method from runtime.
        RiResolvedMethod unboxingMethod = runtime.getRiMethod(type.getDeclaredMethod(unboxMethod));
        unboxingMethods[kind.ordinal()] = unboxingMethod;
        specialMethods.add(unboxingMethod);

        // Get the field that contains the boxed value.
        RiResolvedField[] fields = runtime.getType(type).declaredFields();
        RiResolvedField boxField = fields[0];
        assert fields.length == 1 && boxField.kind(false) == kind;
        boxFields[kind.ordinal()] = boxField;
    }

    public boolean isSpecialMethod(RiResolvedMethod method) {
        return specialMethods.contains(method);
    }

    public boolean isBoxingMethod(RiResolvedMethod method) {
        return isSpecialMethod(method) && method.signature().returnKind(false) == CiKind.Object;
    }

    public boolean isUnboxingMethod(RiResolvedMethod method) {
        return isSpecialMethod(method) && method.signature().returnKind(false) != CiKind.Object;
    }

    public RiResolvedMethod getBoxingMethod(CiKind kind) {
        return boxingMethods[kind.ordinal()];
    }

    public RiResolvedMethod getUnboxingMethod(CiKind kind) {
        return unboxingMethods[kind.ordinal()];
    }

    public RiResolvedField getBoxField(CiKind kind) {
        return boxFields[kind.ordinal()];
    }
}
