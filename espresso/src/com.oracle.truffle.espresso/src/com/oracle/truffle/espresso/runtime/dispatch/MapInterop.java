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

package com.oracle.truffle.espresso.runtime.dispatch;

import static com.oracle.truffle.espresso.runtime.StaticObject.EMPTY_ARRAY;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownKeyException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.interop.InvokeEspressoNode;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.vm.InterpreterToVM;

/**
 * Note:
 *
 * The implementation of the hashes protocol is independent from the guest implementation. As such,
 * even if isHashEntryModifiable() returns true, trying to modify the guest map may result in a
 * guest exception (in the case of unmodifiable map, for example).
 */
@ExportLibrary(value = InteropLibrary.class, receiverType = StaticObject.class)
public class MapInterop extends EspressoInterop {
    // region ### Hashes

    @ExportMessage
    public boolean hasHashEntries(StaticObject receiver) {
        assert InterpreterToVM.instanceOf(receiver, receiver.getKlass().getMeta().java_util_Map);
        return true;
    }

    @ExportMessage
    public boolean isHashEntryReadable(StaticObject receiver, Object key,
                    @Cached.Exclusive @Cached InvokeEspressoNode invoke) {
        return hasHashEntries(receiver) && containsKey(receiver, key, invoke);
    }

    @SuppressWarnings("unused")
    @ExportMessage
    public boolean isHashEntryModifiable(StaticObject receiver, Object key,
                    @Cached.Exclusive @Cached InvokeEspressoNode invoke) {
        return hasHashEntries(receiver) && containsKey(receiver, key, invoke);
    }

    @SuppressWarnings("unused")
    @ExportMessage
    public boolean isHashEntryInsertable(StaticObject receiver, Object key,
                    @Cached.Exclusive @Cached InvokeEspressoNode invoke) {
        return hasHashEntries(receiver) && !containsKey(receiver, key, invoke);
    }

    @SuppressWarnings("unused")
    @ExportMessage
    public boolean isHashEntryRemovable(StaticObject receiver, Object key,
                    @Cached.Exclusive @Cached InvokeEspressoNode invoke) {
        return hasHashEntries(receiver) && containsKey(receiver, key, invoke);
    }

    private boolean containsKey(StaticObject receiver, Object key,
                    InvokeEspressoNode invokeContains) {
        Meta meta = receiver.getKlass().getMeta();
        Method containsKey = getInteropKlass(receiver).itableLookup(meta.java_util_Map, meta.java_util_Map_containsKey.getITableIndex());
        try {
            return (boolean) invokeContains.execute(containsKey, this, EMPTY_ARRAY);
        } catch (UnsupportedTypeException | ArityException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    @ExportMessage
    public long getHashSize(StaticObject receiver, @Cached.Exclusive @Cached InvokeEspressoNode invoke) throws UnsupportedMessageException {
        if (!hasHashEntries(receiver)) {
            throw UnsupportedMessageException.create();
        }
        Meta meta = receiver.getKlass().getMeta();
        Method size = getInteropKlass(receiver).itableLookup(meta.java_util_Map, meta.java_util_Map_size.getITableIndex());
        try {
            return (int) invoke.execute(size, this, EMPTY_ARRAY);
        } catch (UnsupportedTypeException | ArityException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    @ExportMessage
    public Object readHashValue(StaticObject receiver, Object key,
                    @Cached.Exclusive @Cached InvokeEspressoNode invoke) throws UnsupportedMessageException, UnknownKeyException {
        if (!hasHashEntries(receiver)) {
            throw UnsupportedMessageException.create();
        }
        Meta meta = receiver.getKlass().getMeta();
        Method get = getInteropKlass(receiver).itableLookup(meta.java_util_Map, meta.java_util_Map_get.getITableIndex());
        try {
            return invoke.execute(get, this, new Object[]{key});
        } catch (UnsupportedTypeException e) {
            throw UnknownKeyException.create(key);
        } catch (ArityException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    @ExportMessage
    public void writeHashEntry(StaticObject receiver, Object key, Object value,
                    @Cached.Exclusive @Cached InvokeEspressoNode invoke) throws UnsupportedMessageException, UnknownKeyException {
        if (!hasHashEntries(receiver)) {
            throw UnsupportedMessageException.create();
        }
        Meta meta = receiver.getKlass().getMeta();
        Method put = getInteropKlass(receiver).itableLookup(meta.java_util_Map, meta.java_util_Map_put.getITableIndex());
        try {
            invoke.execute(put, this, new Object[]{key, value});
        } catch (UnsupportedTypeException e) {
            throw UnknownKeyException.create(key);
        } catch (ArityException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    @ExportMessage
    public void removeHashEntry(StaticObject receiver, Object key,
                    @Cached.Exclusive @Cached InvokeEspressoNode invoke) throws UnsupportedMessageException, UnknownKeyException {
        if (!hasHashEntries(receiver)) {
            throw UnsupportedMessageException.create();
        }
        Meta meta = receiver.getKlass().getMeta();
        Method remove = getInteropKlass(receiver).itableLookup(meta.java_util_Map, meta.java_util_Map_remove.getITableIndex());
        try {
            invoke.execute(remove, this, new Object[]{key});
        } catch (UnsupportedTypeException e) {
            throw UnknownKeyException.create(key);
        } catch (ArityException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    public Object getHashEntriesIterator(StaticObject receiver) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    // endregion ### Hashes
}
