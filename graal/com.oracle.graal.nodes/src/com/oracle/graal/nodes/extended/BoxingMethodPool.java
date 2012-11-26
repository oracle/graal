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
package com.oracle.graal.nodes.extended;

import java.lang.reflect.*;
import java.util.*;
import java.util.Map.Entry;

import com.oracle.graal.api.meta.*;

public class BoxingMethodPool {
    private static final Map<Kind, BoxingMethod> boxings = new HashMap<>();
    private static class BoxingMethod {
        final Class<?> type;
        final String unboxMethod;
        public BoxingMethod(Class< ? > type, String unboxMethod) {
            this.type = type;
            this.unboxMethod = unboxMethod;
        }
    }
    static {
        boxings.put(Kind.Boolean, new BoxingMethod(Boolean.class, "booleanValue"));
        boxings.put(Kind.Byte, new BoxingMethod(Byte.class, "byteValue"));
        boxings.put(Kind.Char, new BoxingMethod(Character.class, "charValue"));
        boxings.put(Kind.Short, new BoxingMethod(Short.class, "shortValue"));
        boxings.put(Kind.Int, new BoxingMethod(Integer.class, "intValue"));
        boxings.put(Kind.Long, new BoxingMethod(Long.class, "longValue"));
        boxings.put(Kind.Float, new BoxingMethod(Float.class, "floatValue"));
        boxings.put(Kind.Double, new BoxingMethod(Double.class, "doubleValue"));
    }

    private final Set<JavaMethod> specialMethods = new HashSet<>();
    private final MetaAccessProvider runtime;
    private final ResolvedJavaMethod[] boxingMethods = new ResolvedJavaMethod[Kind.values().length];
    private final ResolvedJavaMethod[] unboxingMethods = new ResolvedJavaMethod[Kind.values().length];
    private final ResolvedJavaField[] boxFields = new ResolvedJavaField[Kind.values().length];

    public BoxingMethodPool(MetaAccessProvider runtime) {
        this.runtime = runtime;
        initialize();
    }

    private void initialize() {
        try {
            for (Entry<Kind, BoxingMethod> entry : boxings.entrySet()) {
                Kind kind = entry.getKey();
                BoxingMethod boxing = entry.getValue();
                initialize(kind, boxing.type, boxing.unboxMethod);
            }
        } catch (SecurityException e) {
            throw new RuntimeException(e);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private void initialize(Kind kind, Class<?> type, String unboxMethod) throws SecurityException, NoSuchMethodException {

        // Get boxing method from runtime.
        ResolvedJavaMethod boxingMethod = runtime.lookupJavaMethod(type.getDeclaredMethod("valueOf", kind.toJavaClass()));
        specialMethods.add(boxingMethod);
        boxingMethods[kind.ordinal()] = boxingMethod;

        // Get unboxing method from runtime.
        ResolvedJavaMethod unboxingMethod = runtime.lookupJavaMethod(type.getDeclaredMethod(unboxMethod));
        unboxingMethods[kind.ordinal()] = unboxingMethod;
        specialMethods.add(unboxingMethod);

        // Get the field that contains the boxed value.
        ResolvedJavaField[] fields = runtime.lookupJavaType(type).getInstanceFields(false);
        ResolvedJavaField boxField = fields[0];
        assert fields.length == 1 && boxField.getKind() == kind;
        boxFields[kind.ordinal()] = boxField;
    }

    public boolean isSpecialMethod(ResolvedJavaMethod method) {
        return specialMethods.contains(method);
    }

    public boolean isBoxingMethod(ResolvedJavaMethod method) {
        return isSpecialMethod(method) && method.getSignature().getReturnKind() == Kind.Object;
    }

    public boolean isUnboxingMethod(ResolvedJavaMethod method) {
        return isSpecialMethod(method) && method.getSignature().getReturnKind() != Kind.Object;
    }

    public ResolvedJavaMethod getBoxingMethod(Kind kind) {
        return boxingMethods[kind.ordinal()];
    }

    public ResolvedJavaMethod getUnboxingMethod(Kind kind) {
        return unboxingMethods[kind.ordinal()];
    }

    public ResolvedJavaField getBoxField(Kind kind) {
        return boxFields[kind.ordinal()];
    }

    public static boolean isSpecialMethodStatic(ResolvedJavaMethod method) {
        return isUnboxingMethodStatic(method) || isBoxingMethodStatic(method);
    }

    public static boolean isBoxingMethodStatic(ResolvedJavaMethod method) {
        Signature signature = method.getSignature();
        if (!Modifier.isStatic(method.getModifiers())
                        || signature.getReturnKind() == Kind.Object
                        || signature.getParameterCount(false) != 1) {
            return false;
        }
        Kind kind = signature.getParameterKind(0);
        BoxingMethod boxing = boxings.get(kind);
        if (boxing == null) {
            return false;
        }
        return method.getDeclaringClass().isClass(boxing.type) && method.getName().equals("valueOf");
    }

    public static boolean isUnboxingMethodStatic(ResolvedJavaMethod method) {
        Signature signature = method.getSignature();
        if (signature.getReturnKind() == Kind.Object
                        || signature.getParameterCount(false) != 0
                        || Modifier.isStatic(method.getModifiers())) {
            return false;
        }
        Kind kind = signature.getReturnKind();
        BoxingMethod boxing = boxings.get(kind);
        if (boxing == null) {
            return false;
        }
        return method.getDeclaringClass().toJava() == boxing.type && method.getName().equals(boxing.unboxMethod);
    }
}
