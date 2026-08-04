/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.test.clinit.typereached;

import java.util.HashMap;
import java.util.Map;

/**
 * Models the Berkeley DB {@code TupleBinding} initialization shape.
 *
 * The colocated {@code reachability-metadata.json} registers a {@code typeReached} condition for
 * {@code TupleBinding}. Initializing {@code TupleBinding} constructs subclasses whose initialization
 * checks form a simulation-cluster cycle. The required check for {@code TupleBinding} must remain in the
 * decoded graph: at run time it records the type-reached transition and enables the conditional metadata.
 * Multiple leaf bindings are intentional: one is sufficient for the cycle, but independent simulation
 * paths make the concurrent publication race observable.
 */
public final class TypeReachedSimulationCycleReproducer {
    public static void verify() {
        TupleBinding binding = TupleBinding.getPrimitiveBinding(String.class);
        if (!(binding instanceof StringBinding)) {
            throw new AssertionError(binding);
        }
    }
}

class TupleBase {
}

abstract class TupleBinding extends TupleBase {
    private static final Map<Class<?>, TupleBinding> PRIMITIVES = new HashMap<>();

    static {
        addPrimitive(String.class, StringBinding.class, new StringBinding());
        addPrimitive(Character.class, CharacterBinding.class, new CharacterBinding());
        addPrimitive(Boolean.class, BooleanBinding.class, new BooleanBinding());
        addPrimitive(Byte.class, ByteBinding.class, new ByteBinding());
        addPrimitive(Short.class, ShortBinding.class, new ShortBinding());
        addPrimitive(Integer.class, IntegerBinding.class, new IntegerBinding());
        addPrimitive(Long.class, LongBinding.class, new LongBinding());
        addPrimitive(Float.class, FloatBinding.class, new FloatBinding());
        addPrimitive(Double.class, DoubleBinding.class, new DoubleBinding());
    }

    private static void addPrimitive(Class<?> primitive, Class<? extends TupleBinding> bindingType, TupleBinding binding) {
        PRIMITIVES.put(primitive, binding);
        PRIMITIVES.put(bindingType, binding);
    }

    static TupleBinding getPrimitiveBinding(Class<?> type) {
        return PRIMITIVES.get(type);
    }
}

final class StringBinding extends TupleBinding {
}

final class CharacterBinding extends TupleBinding {
}

final class BooleanBinding extends TupleBinding {
}

final class ByteBinding extends TupleBinding {
}

final class ShortBinding extends TupleBinding {
}

final class IntegerBinding extends TupleBinding {
}

final class LongBinding extends TupleBinding {
}

final class FloatBinding extends TupleBinding {
}

final class DoubleBinding extends TupleBinding {
}
