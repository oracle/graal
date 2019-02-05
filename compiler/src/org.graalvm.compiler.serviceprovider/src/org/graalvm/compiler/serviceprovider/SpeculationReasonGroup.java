/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package org.graalvm.compiler.serviceprovider;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.SpeculationLog.SpeculationReason;

/**
 * Facility for creating speculation reasons partitioned in groups.
 */
public final class SpeculationReasonGroup {

    private final int id;

    private static final AtomicInteger nextId = new AtomicInteger(1);

    public SpeculationReasonGroup() {
        this.id = nextId.get();
    }

    /**
     * Creates a speculation reason that is part of this group.
     *
     * @param context the details of the reason instance being created
     */
    public SpeculationReason createSpeculationReason(Object... context) {
        assert checkTypes(context);
        return GraalServices.createSpeculationReason(id, context);
    }

    private static final Set<Class<?>> SUPPORTED_EXACT_TYPES = new HashSet<>(Arrays.asList(
                    String.class,
                    Integer.class,
                    Long.class,
                    Float.class,
                    Double.class,
                    BytecodePosition.class,
                    ResolvedJavaMethod.class,
                    ResolvedJavaType.class));

    private static boolean isOfSupportedType(Class<?> c) {
        if (SUPPORTED_EXACT_TYPES.contains(c)) {
            return true;
        }
        if (Enum.class.isAssignableFrom(c)) {
            // Trust the ordinal of an Enum to be unique
            return true;
        }
        if (ResolvedJavaMethod.class.isAssignableFrom(c) || ResolvedJavaType.class.isAssignableFrom(c)) {
            // Only the JVMCI implementation specific concrete subclasses
            // of these types can be accepted but we cannot test for that
            // here. A violation of this requirement will be caught by
            // GR-13685.
            return true;
        }
        return false;
    }

    Class<?>[] types;

    private boolean checkTypes(Object[] context) {
        if (types == null) {
            types = new Class<?>[context.length];
        } else {
            assert types.length == context.length : types.length + " != " + context.length;
        }
        for (int i = 0; i < context.length; i++) {
            Object o = context[i];
            if (o != null) {
                Class<?> t = types[i];
                if (t == null) {
                    t = o.getClass();
                    assert isOfSupportedType(t) : "Unsupported speculation context type: " + t;
                    types[i] = t;
                } else {
                    assert t == o.getClass() : "context argument " + i + " has inconsistent type: " + t + " != " + o.getClass();
                }
            }
        }
        return true;
    }
}
