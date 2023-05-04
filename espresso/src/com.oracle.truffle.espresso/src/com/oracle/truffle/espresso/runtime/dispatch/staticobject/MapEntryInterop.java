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

import static com.oracle.truffle.espresso.runtime.StaticObject.EMPTY_ARRAY;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
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
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.dispatch.messages.InteropMessage;
import com.oracle.truffle.espresso.runtime.dispatch.messages.InteropMessageFactory;
import com.oracle.truffle.espresso.runtime.dispatch.messages.InteropNodes;
import com.oracle.truffle.espresso.substitutions.Collect;

@ExportLibrary(value = InteropLibrary.class, receiverType = StaticObject.class)
@SuppressWarnings("truffle-abstract-export") // TODO GR-44080 Adopt BigInteger Interop
public class MapEntryInterop extends EspressoInterop {
    @ExportMessage
    static boolean hasArrayElements(@SuppressWarnings("unused") StaticObject receiver) {
        return true;
    }

    @ExportMessage
    static long getArraySize(@SuppressWarnings("unused") StaticObject receiver) {
        return 2;
    }

    @ExportMessage
    static boolean isArrayElementModifiable(@SuppressWarnings("unused") StaticObject receiver, long index) {
        return index == 1;
    }

    @ExportMessage
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

    @SuppressWarnings("unused")
    @Collect(value = InteropNodes.class, getter = "getInstance")
    public static class Nodes extends InteropNodes {

        private static final InteropNodes INSTANCE = new Nodes();

        public static InteropNodes getInstance() {
            return INSTANCE;
        }

        public Nodes() {
            super(MapEntryInterop.class, EspressoInterop.Nodes.getInstance());
        }

        public void registerMessages(Class<?> cls) {
            InteropMessageFactory.register(cls, "hasArrayElements", MapEntryInteropFactory.NodesFactory.HasArrayElementsNodeGen::create);
            InteropMessageFactory.register(cls, "getArraySize", MapEntryInteropFactory.NodesFactory.GetArraySizeNodeGen::create);
            InteropMessageFactory.register(cls, "isArrayElementModifiable", MapEntryInteropFactory.NodesFactory.IsArrayElementModifiableNodeGen::create);
            InteropMessageFactory.register(cls, "isArrayElementReadable", MapEntryInteropFactory.NodesFactory.IsArrayElementReadableNodeGen::create);
            InteropMessageFactory.register(cls, "writeArrayElement", MapEntryInteropFactory.NodesFactory.WriteArrayElementNodeGen::create);
            InteropMessageFactory.register(cls, "readArrayElement", MapEntryInteropFactory.NodesFactory.ReadArrayElementNodeGen::create);
        }

        abstract static class HasArrayElementsNode extends InteropMessage.HasArrayElements {
            @Specialization
            boolean doStaticObject(StaticObject receiver) {
                return true;
            }
        }

        abstract static class GetArraySizeNode extends InteropMessage.GetArraySize {
            @Specialization
            long doStaticObject(StaticObject receiver) {
                return 2;
            }
        }

        abstract static class IsArrayElementModifiableNode extends InteropMessage.IsArrayElementModifiable {
            @Specialization
            boolean doStaticObject(StaticObject receiver, long index) {
                return index == 1;
            }
        }

        abstract static class IsArrayElementReadableNode extends InteropMessage.IsArrayElementReadable {
            @Specialization
            boolean doStaticObject(StaticObject receiver, long index) {
                return index == 0 || index == 1;
            }
        }

        abstract static class WriteArrayElementNode extends InteropMessage.WriteArrayElement {
            @Specialization
            void doStaticObject(StaticObject receiver, long index, Object value,
                            @Cached InvokeEspressoNode invoke) throws InvalidArrayIndexException {
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
        }

        abstract static class ReadArrayElementNode extends InteropMessage.ReadArrayElement {
            @Specialization
            Object doStaticObject(StaticObject receiver, long index,
                            @Cached InvokeEspressoNode invoke) throws InvalidArrayIndexException {
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
        }
    }
}
