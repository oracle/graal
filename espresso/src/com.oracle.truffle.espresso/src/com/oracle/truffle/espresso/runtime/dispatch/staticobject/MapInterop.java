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
import com.oracle.truffle.api.interop.UnknownKeyException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.interop.InvokeEspressoNode;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.dispatch.messages.InteropMessage;
import com.oracle.truffle.espresso.runtime.dispatch.messages.InteropMessageFactory;
import com.oracle.truffle.espresso.vm.InterpreterToVM;

/**
 * Note:
 *
 * The implementation of the hashes protocol is independent from the guest implementation. As such,
 * even if isHashEntryModifiable() returns true, trying to modify the guest map may result in a
 * guest exception (in the case of unmodifiable map, for example).
 */
@ExportLibrary(value = InteropLibrary.class, receiverType = StaticObject.class)
@SuppressWarnings("truffle-abstract-export") // TODO GR-44080 Adopt BigInteger Interop
public class MapInterop extends EspressoInterop {
    // region ### Hashes

    private static boolean containsKey(StaticObject receiver, Object key,
                    InvokeEspressoNode invokeContains) {
        Meta meta = receiver.getKlass().getMeta();
        Method containsKey = getInteropKlass(receiver).itableLookup(meta.java_util_Map, meta.java_util_Map_containsKey.getITableIndex());
        try {
            return (boolean) invokeContains.execute(containsKey, receiver, new Object[]{key});
        } catch (UnsupportedTypeException | ArityException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    @ExportMessage
    public static boolean hasHashEntries(StaticObject receiver) {
        assert InterpreterToVM.instanceOf(receiver, receiver.getKlass().getMeta().java_util_Map);
        return true;
    }

    @ExportMessage(name = "isHashEntryReadable")
    @ExportMessage(name = "isHashEntryModifiable")
    @ExportMessage(name = "isHashEntryRemovable")
    public static boolean isHashEntryReadable(StaticObject receiver, Object key,
                    @Cached.Shared("contains") @Cached InvokeEspressoNode invoke) {
        return containsKey(receiver, key, invoke);
    }

    @SuppressWarnings("unused")
    @ExportMessage
    public static boolean isHashEntryInsertable(StaticObject receiver, Object key,
                    @Cached.Shared("contains") @Cached InvokeEspressoNode invoke) {
        return !containsKey(receiver, key, invoke);
    }

    @ExportMessage
    public static long getHashSize(StaticObject receiver,
                    @Cached.Exclusive @Cached InvokeEspressoNode invoke) throws UnsupportedMessageException {
        if (!hasHashEntries(receiver)) {
            throw UnsupportedMessageException.create();
        }
        Meta meta = receiver.getKlass().getMeta();
        Method size = getInteropKlass(receiver).itableLookup(meta.java_util_Map, meta.java_util_Map_size.getITableIndex());
        try {
            return (int) invoke.execute(size, receiver, EMPTY_ARRAY);
        } catch (UnsupportedTypeException | ArityException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    @ExportMessage
    public static Object readHashValue(StaticObject receiver, Object key,
                    @Cached.Exclusive @Cached InvokeEspressoNode invoke,
                    @Cached.Shared("contains") @Cached InvokeEspressoNode contains) throws UnsupportedMessageException, UnknownKeyException {
        if (!isHashEntryReadable(receiver, key, contains)) {
            throw UnsupportedMessageException.create();
        }
        Meta meta = receiver.getKlass().getMeta();
        Method get = getInteropKlass(receiver).itableLookup(meta.java_util_Map, meta.java_util_Map_get.getITableIndex());
        try {
            return invoke.execute(get, receiver, new Object[]{key});
        } catch (UnsupportedTypeException e) {
            throw UnknownKeyException.create(key);
        } catch (ArityException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    @ExportMessage
    public static void writeHashEntry(StaticObject receiver, Object key, Object value,
                    @Cached.Exclusive @Cached InvokeEspressoNode invoke) throws UnknownKeyException {
        Meta meta = receiver.getKlass().getMeta();
        Method put = getInteropKlass(receiver).itableLookup(meta.java_util_Map, meta.java_util_Map_put.getITableIndex());
        try {
            invoke.execute(put, receiver, new Object[]{key, value});
        } catch (UnsupportedTypeException e) {
            throw UnknownKeyException.create(key);
        } catch (ArityException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    @ExportMessage
    public static void removeHashEntry(StaticObject receiver, Object key,
                    @Cached.Exclusive @Cached InvokeEspressoNode invoke,
                    @Cached.Shared("contains") @Cached InvokeEspressoNode contains) throws UnsupportedMessageException, UnknownKeyException {
        if (!isHashEntryReadable(receiver, key, contains)) {
            throw UnsupportedMessageException.create();
        }
        Meta meta = receiver.getKlass().getMeta();
        Method remove = getInteropKlass(receiver).itableLookup(meta.java_util_Map, meta.java_util_Map_remove.getITableIndex());
        try {
            invoke.execute(remove, receiver, new Object[]{key});
        } catch (UnsupportedTypeException e) {
            throw UnknownKeyException.create(key);
        } catch (ArityException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    public static Object getHashEntriesIterator(StaticObject receiver,
                    @CachedLibrary(limit = "1") InteropLibrary setLibrary,
                    @Cached.Exclusive @Cached InvokeEspressoNode invoke) throws UnsupportedMessageException {
        if (!hasHashEntries(receiver)) {
            throw UnsupportedMessageException.create();
        }
        Meta meta = receiver.getKlass().getMeta();
        Method entrySet = getInteropKlass(receiver).itableLookup(meta.java_util_Map, meta.java_util_Map_entrySet.getITableIndex());
        Object set = null;
        try {
            set = invoke.execute(entrySet, receiver, EMPTY_ARRAY);
        } catch (ArityException | UnsupportedTypeException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere(e);
        }
        assert set != null;
        assert setLibrary.hasIterator(set);
        return setLibrary.getIterator(set);
    }

    // endregion ### Hashes

    public static class Nodes {

        static {
            Nodes.registerMessages(MapInterop.class);
        }

        public static void ensureInitialized() {
        }

        private static void registerMessages(Class<? extends MapInterop> cls) {
            EspressoInterop.Nodes.registerMessages(cls);
            InteropMessageFactory.register(cls, "isHashEntryReadable", MapInteropFactory.NodesFactory.IsHashEntryReadableNodeGen::create);
            InteropMessageFactory.register(cls, "isHashEntryModifiable", MapInteropFactory.NodesFactory.IsHashEntryModifiableNodeGen::create);
            InteropMessageFactory.register(cls, "isHashEntryRemovable", MapInteropFactory.NodesFactory.IsHashEntryRemovableNodeGen::create);
            InteropMessageFactory.register(cls, "isHashEntryInsertable", MapInteropFactory.NodesFactory.IsHashEntryInsertableNodeGen::create);
            InteropMessageFactory.register(cls, "getHashSize", MapInteropFactory.NodesFactory.GetHashSizeNodeGen::create);
            InteropMessageFactory.register(cls, "readHashValue", MapInteropFactory.NodesFactory.ReadHashValueNodeGen::create);
            InteropMessageFactory.register(cls, "writeHashEntry", MapInteropFactory.NodesFactory.WriteHashEntryNodeGen::create);
            InteropMessageFactory.register(cls, "removeHashEntry", MapInteropFactory.NodesFactory.RemoveHashEntryNodeGen::create);
            InteropMessageFactory.register(cls, "getHashEntriesIterator", MapInteropFactory.NodesFactory.GetHashEntriesIteratorNodeGen::create);
        }

        abstract static class HasHashEntriesNode extends InteropMessage.HasHashEntries {
            @Specialization
            public boolean doStaticObject(StaticObject receiver) {
                assert InterpreterToVM.instanceOf(receiver, receiver.getKlass().getMeta().java_util_Map);
                return true;
            }
        }

        abstract static class IsHashEntryReadableNode extends InteropMessage.IsHashEntryReadable {
            @Specialization
            public boolean doStaticObject(StaticObject receiver, Object key,
                            @Cached InvokeEspressoNode invoke) {
                return containsKey(receiver, key, invoke);
            }
        }

        abstract static class IsHashEntryModifiableNode extends InteropMessage.IsHashEntryModifiable {
            @Specialization
            public boolean doStaticObject(StaticObject receiver, Object key,
                            @Cached InvokeEspressoNode invoke) {
                return containsKey(receiver, key, invoke);
            }
        }

        abstract static class IsHashEntryRemovableNode extends InteropMessage.IsHashEntryRemovable {
            @Specialization
            public boolean doStaticObject(StaticObject receiver, Object key,
                            @Cached InvokeEspressoNode invoke) {
                return containsKey(receiver, key, invoke);
            }
        }

        abstract static class IsHashEntryInsertableNode extends InteropMessage.IsHashEntryInsertable {
            @Specialization
            public boolean doStaticObject(StaticObject receiver, Object key,
                            @Cached InvokeEspressoNode invoke) {
                return !containsKey(receiver, key, invoke);
            }
        }

        abstract static class GetHashSizeNode extends InteropMessage.GetHashSize {
            @Specialization
            public long doStaticObject(StaticObject receiver,
                            @Cached InvokeEspressoNode invoke) throws UnsupportedMessageException {
                if (!hasHashEntries(receiver)) {
                    throw UnsupportedMessageException.create();
                }
                Meta meta = receiver.getKlass().getMeta();
                Method size = getInteropKlass(receiver).itableLookup(meta.java_util_Map, meta.java_util_Map_size.getITableIndex());
                try {
                    return (int) invoke.execute(size, receiver, EMPTY_ARRAY);
                } catch (UnsupportedTypeException | ArityException e) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw EspressoError.shouldNotReachHere(e);
                }
            }
        }

        abstract static class ReadHashValueNode extends InteropMessage.ReadHashValue {
            @Specialization
            public Object doStaticObject(StaticObject receiver, Object key,
                            @Cached InvokeEspressoNode invoke,
                            @Cached InvokeEspressoNode contains) throws UnsupportedMessageException, UnknownKeyException {
                if (!isHashEntryReadable(receiver, key, contains)) {
                    throw UnsupportedMessageException.create();
                }
                Meta meta = receiver.getKlass().getMeta();
                Method get = getInteropKlass(receiver).itableLookup(meta.java_util_Map, meta.java_util_Map_get.getITableIndex());
                try {
                    return invoke.execute(get, receiver, new Object[]{key});
                } catch (UnsupportedTypeException e) {
                    throw UnknownKeyException.create(key);
                } catch (ArityException e) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw EspressoError.shouldNotReachHere(e);
                }
            }
        }

        abstract static class WriteHashEntryNode extends InteropMessage.WriteHashEntry {
            @Specialization
            public void doStaticObject(StaticObject receiver, Object key, Object value,
                            @Cached InvokeEspressoNode invoke) throws UnknownKeyException {
                Meta meta = receiver.getKlass().getMeta();
                Method put = getInteropKlass(receiver).itableLookup(meta.java_util_Map, meta.java_util_Map_put.getITableIndex());
                try {
                    invoke.execute(put, receiver, new Object[]{key, value});
                } catch (UnsupportedTypeException e) {
                    throw UnknownKeyException.create(key);
                } catch (ArityException e) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw EspressoError.shouldNotReachHere(e);
                }
            }
        }

        abstract static class RemoveHashEntryNode extends InteropMessage.RemoveHashEntry {
            @Specialization
            public void doStaticObject(StaticObject receiver, Object key,
                            @Cached InvokeEspressoNode invoke,
                            @Cached InvokeEspressoNode contains) throws UnsupportedMessageException, UnknownKeyException {
                if (!isHashEntryReadable(receiver, key, contains)) {
                    throw UnsupportedMessageException.create();
                }
                Meta meta = receiver.getKlass().getMeta();
                Method remove = getInteropKlass(receiver).itableLookup(meta.java_util_Map, meta.java_util_Map_remove.getITableIndex());
                try {
                    invoke.execute(remove, receiver, new Object[]{key});
                } catch (UnsupportedTypeException e) {
                    throw UnknownKeyException.create(key);
                } catch (ArityException e) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw EspressoError.shouldNotReachHere(e);
                }
            }
        }

        abstract static class GetHashEntriesIteratorNode extends InteropMessage.GetHashEntriesIterator {
            @Specialization
            public Object doStaticObject(StaticObject receiver,
                            @CachedLibrary(limit = "1") InteropLibrary setLibrary,
                            @Cached InvokeEspressoNode invoke) throws UnsupportedMessageException {
                if (!hasHashEntries(receiver)) {
                    throw UnsupportedMessageException.create();
                }
                Meta meta = receiver.getKlass().getMeta();
                Method entrySet = getInteropKlass(receiver).itableLookup(meta.java_util_Map, meta.java_util_Map_entrySet.getITableIndex());
                Object set = null;
                try {
                    set = invoke.execute(entrySet, receiver, EMPTY_ARRAY);
                } catch (ArityException | UnsupportedTypeException e) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw EspressoError.shouldNotReachHere(e);
                }
                assert set != null;
                assert setLibrary.hasIterator(set);
                return setLibrary.getIterator(set);
            }
        }
    }
}
