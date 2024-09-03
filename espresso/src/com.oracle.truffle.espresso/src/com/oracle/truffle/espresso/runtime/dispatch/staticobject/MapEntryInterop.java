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

package com.oracle.truffle.espresso.runtime.dispatch.staticobject;

import static com.oracle.truffle.espresso.runtime.staticobject.StaticObject.EMPTY_ARRAY;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.interop.InvokeEspressoNode;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.runtime.dispatch.messages.GenerateInteropNodes;
import com.oracle.truffle.espresso.runtime.dispatch.messages.Shareable;

@GenerateInteropNodes
@ExportLibrary(value = InteropLibrary.class, receiverType = StaticObject.class)
public class MapEntryInterop extends EspressoInterop {
    @ExportMessage
    @Shareable
    static boolean hasArrayElements(@SuppressWarnings("unused") StaticObject receiver) {
        return true;
    }

    @ExportMessage
    @Shareable
    static long getArraySize(@SuppressWarnings("unused") StaticObject receiver) {
        return 2;
    }

    @ExportMessage
    @Shareable
    static boolean isArrayElementModifiable(@SuppressWarnings("unused") StaticObject receiver, long index) {
        return index == 1;
    }

    @ExportMessage
    @Shareable
    static boolean isArrayElementReadable(@SuppressWarnings("unused") StaticObject receiver, long index) {
        return index == 0 || index == 1;
    }

    @ExportMessage
    public static void writeArrayElement(StaticObject receiver, long index, Object value,
                    @Cached.Exclusive @Cached InvokeEspressoNode invoke) throws InvalidArrayIndexException {
        if (index != 1) {
            throw InvalidArrayIndexException.create(index);
        }
        Meta meta = receiver.getKlass().getMeta();
        Method m = doLookup(receiver, meta.java_util_Map_Entry, meta.java_util_Map_Entry_setValue);
        try {
            invoke.execute(m, receiver, new Object[]{value});
        } catch (ArityException | UnsupportedTypeException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    @ExportMessage
    public static Object readArrayElement(StaticObject receiver, long index,
                    @Cached.Exclusive @Cached InvokeEspressoNode invoke) throws InvalidArrayIndexException {
        Meta meta = receiver.getKlass().getMeta();
        Method m;
        if (index == 0) {
            m = doLookup(receiver, meta.java_util_Map_Entry, meta.java_util_Map_Entry_getKey);
        } else if (index == 1) {
            m = doLookup(receiver, meta.java_util_Map_Entry, meta.java_util_Map_Entry_getValue);
        } else {
            throw InvalidArrayIndexException.create(index);
        }
        try {
            return invoke.execute(m, receiver, EMPTY_ARRAY);
        } catch (ArityException | UnsupportedTypeException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    static Method doLookup(StaticObject receiver, ObjectKlass k, Method m) {
        assert k.isInterface() && m.getDeclaringKlass() == k;
        return getInteropKlass(receiver).itableLookup(k, m.getITableIndex());
    }
}
