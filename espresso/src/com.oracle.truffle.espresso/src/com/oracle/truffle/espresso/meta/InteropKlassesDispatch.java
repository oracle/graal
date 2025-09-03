/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.espresso.runtime.dispatch.staticobject.BaseInterop;
import com.oracle.truffle.espresso.runtime.dispatch.staticobject.ByteBufferInterop;
import com.oracle.truffle.espresso.runtime.dispatch.staticobject.EspressoInterop;
import com.oracle.truffle.espresso.runtime.dispatch.staticobject.ForeignExceptionInterop;
import com.oracle.truffle.espresso.runtime.dispatch.staticobject.InterruptedExceptionInterop;
import com.oracle.truffle.espresso.runtime.dispatch.staticobject.IterableInterop;
import com.oracle.truffle.espresso.runtime.dispatch.staticobject.IteratorInterop;
import com.oracle.truffle.espresso.runtime.dispatch.staticobject.ListInterop;
import com.oracle.truffle.espresso.runtime.dispatch.staticobject.MapEntryInterop;
import com.oracle.truffle.espresso.runtime.dispatch.staticobject.MapInterop;
import com.oracle.truffle.espresso.runtime.dispatch.staticobject.ThrowableInterop;

public class InteropKlassesDispatch {
    public static final int BASE_INTEROP_ID = 0;
    public static final int ESPRESSO_INTEROP_ID = 1;
    public static final int FOREIGN_EXCEPTION_INTEROP_ID = 2;
    public static final int INTERRUPTED_EXCEPTION_INTEROP_ID = 3;
    public static final int ITERABLE_INTEROP_ID = 4;
    public static final int ITERATOR_INTEROP_ID = 5;
    public static final int LIST_INTEROP_ID = 6;
    public static final int MAP_ENTRY_INTEROP_ID = 7;
    public static final int MAP_INTEROP_ID = 8;
    public static final int THROWABLE_INTEROP_ID = 9;
    public static final int BYTE_BUFFER_INTEROP_ID = 10;

    public static final int DISPATCH_TOTAL = BYTE_BUFFER_INTEROP_ID + 1;

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
     * {@link EspressoInterop}.
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
                        new Pair[]{Pair.create(meta.java_nio_ByteBuffer, ByteBufferInterop.class)},
                        new Pair[]{Pair.create(meta.java_lang_InterruptedException, InterruptedExceptionInterop.class), Pair.create(meta.java_lang_Throwable, ThrowableInterop.class)}
        };
    }

    public Class<?> resolveDispatch(Klass k) {
        Class<?> result = null;
        if (k.isPrimitive()) {
            result = BaseInterop.class;
        } else if (k.isArray()) {
            result = EspressoInterop.class;
        } else {
            // ForeignException is not injected until post system init, meaning we can't
            // put in into the static dispatch pair mappings.
            if (k.getMeta().polyglot != null && k.getMeta().polyglot.ForeignException == k) {
                return ForeignExceptionInterop.class;
            }

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

    public static int dispatchToId(Class<?> dispatch) {
        if (dispatch == BaseInterop.class) {
            return BASE_INTEROP_ID;
        } else if (dispatch == EspressoInterop.class) {
            return ESPRESSO_INTEROP_ID;
        } else if (dispatch == ForeignExceptionInterop.class) {
            return FOREIGN_EXCEPTION_INTEROP_ID;
        } else if (dispatch == InterruptedExceptionInterop.class) {
            return INTERRUPTED_EXCEPTION_INTEROP_ID;
        } else if (dispatch == IterableInterop.class) {
            return ITERABLE_INTEROP_ID;
        } else if (dispatch == IteratorInterop.class) {
            return ITERATOR_INTEROP_ID;
        } else if (dispatch == ListInterop.class) {
            return LIST_INTEROP_ID;
        } else if (dispatch == MapEntryInterop.class) {
            return MAP_ENTRY_INTEROP_ID;
        } else if (dispatch == MapInterop.class) {
            return MAP_INTEROP_ID;
        } else if (dispatch == ThrowableInterop.class) {
            return THROWABLE_INTEROP_ID;
        } else if (dispatch == ByteBufferInterop.class) {
            return BYTE_BUFFER_INTEROP_ID;
        } else {
            throw EspressoError.shouldNotReachHere();
        }
    }
}
