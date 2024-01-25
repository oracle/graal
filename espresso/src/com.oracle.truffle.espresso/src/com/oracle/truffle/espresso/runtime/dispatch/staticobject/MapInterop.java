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
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.runtime.dispatch.messages.GenerateInteropNodes;
import com.oracle.truffle.espresso.runtime.dispatch.messages.Shareable;
import com.oracle.truffle.espresso.vm.InterpreterToVM;

/**
 * Note:
 *
 * The implementation of the hashes protocol is independent from the guest implementation. As such,
 * even if isHashEntryModifiable() returns true, trying to modify the guest map may result in a
 * guest exception (in the case of unmodifiable map, for example).
 */
@GenerateInteropNodes
@ExportLibrary(value = InteropLibrary.class, receiverType = StaticObject.class)
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
    @Shareable
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
}
