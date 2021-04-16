/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.meta;

import org.graalvm.collections.Pair;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.runtime.dispatch.BaseInterop;
import com.oracle.truffle.espresso.runtime.dispatch.EspressoInterop;
import com.oracle.truffle.espresso.runtime.dispatch.IterableInterop;
import com.oracle.truffle.espresso.runtime.dispatch.IteratorInterop;
import com.oracle.truffle.espresso.runtime.dispatch.ListInterop;
import com.oracle.truffle.espresso.runtime.dispatch.MapEntryInterop;
import com.oracle.truffle.espresso.runtime.dispatch.MapInterop;

public class InteropKlassesDispatch {
    /**
     * Represents all known guest classes with special interop library handling. Each entry in the
     * array represents mutually exclusive groups of classes. Classes within a single entry are
     * related by a subclassing relationship, most specific first.
     * <p>
     * If a given receiver implements multiple mutually exclusive classes, then its assigned interop
     * protocol will be the most basic one possible, effectively disabling special handling.
     * <p>
     * For example, a class implementing both {@link java.util.List} and {@link java.util.Map.Entry}
     * , which are considered mutually exclusive, will dispatch to the regular object interop
     * {@link com.oracle.truffle.espresso.runtime.dispatch.EspressoInterop}.
     */
    @CompilerDirectives.CompilationFinal(dimensions = 2) //
    private final Pair<ObjectKlass, Class<?>>[][] classes;

    @SuppressWarnings({"unchecked", "rawtypes"})
    InteropKlassesDispatch(Meta meta) {
        classes = new Pair[][]{
                        new Pair[]{Pair.create(meta.java_util_List, ListInterop.class), Pair.create(meta.java_lang_Iterable, IterableInterop.class)},
                        new Pair[]{Pair.create(meta.java_util_Map, MapInterop.class)},
                        new Pair[]{Pair.create(meta.java_util_Map_Entry, MapEntryInterop.class)},
                        new Pair[]{Pair.create(meta.java_util_Iterator, IteratorInterop.class)},
        };
    }

    public Class<?> resolveDispatch(Klass k) {
        Class<?> result = null;
        if (k.isPrimitive()) {
            result = BaseInterop.class;
        } else if (k.isArray()) {
            result = EspressoInterop.class;
        } else {
            exclusiveLoop: //
            for (Pair<ObjectKlass, Class<?>>[] exclusive : classes) {
                for (Pair<ObjectKlass, Class<?>> pair : exclusive) {
                    if (pair.getLeft().isAssignableFrom(k)) {
                        if (result != null) {
                            // Class implements multiple mutually exclusive interop classes.
                            result = EspressoInterop.class;
                            break exclusiveLoop;
                        }
                        result = pair.getRight();
                        // Found a match. Keep going to check for mutual exclusivity.
                        continue exclusiveLoop;
                    }
                }
            }
            if (result == null) {
                // No match in known interop classes.
                result = EspressoInterop.class;
            }
        }
        return result;
    }
}
