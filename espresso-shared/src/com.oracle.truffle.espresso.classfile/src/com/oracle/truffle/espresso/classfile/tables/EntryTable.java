/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.classfile.tables;

import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.classfile.descriptors.Name;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;

public abstract class EntryTable<T extends EntryTable.NamedEntry, K> {
    protected final HashMap<Symbol<Name>, T> entries = new HashMap<>();

    private final BlockLock readBlock;
    private final BlockLock writeBlock;

    protected EntryTable(ReadWriteLock lock) {
        this.readBlock = new BlockLock(lock.readLock());
        this.writeBlock = new BlockLock(lock.writeLock());
    }

    @SuppressWarnings("try")
    public void collectValues(Consumer<T> consumer) {
        try (BlockLock block = read()) {
            entries.values().forEach(consumer);
        }
    }

    @SuppressWarnings("try")
    @TruffleBoundary
    public void collectEntries(BiConsumer<Symbol<Name>, T> consumer) {
        try (BlockLock block = read()) {
            entries.forEach(consumer::accept);
        }
    }

    public static final class BlockLock implements AutoCloseable {

        private final Lock lock;

        private BlockLock(Lock lock) {
            this.lock = lock;
        }

        private BlockLock enter() {
            lock.lock();
            return this;
        }

        @Override
        public void close() {
            lock.unlock();
        }

    }

    public BlockLock read() {
        return readBlock.enter();
    }

    public BlockLock write() {
        return writeBlock.enter();
    }

    protected abstract T createEntry(Symbol<Name> name, K data);

    public abstract static class NamedEntry {
        protected NamedEntry(Symbol<Name> name) {
            this.name = name;
        }

        protected final Symbol<Name> name;

        public final Symbol<Name> getName() {
            return name;
        }

        public final String getNameAsString() {
            if (name == null) {
                return "unnamed";
            }
            return name.toString();
        }

        @Override
        public final int hashCode() {
            if (name == null) {
                return 0;
            }
            return name.hashCode();
        }

        @Override
        public final boolean equals(Object obj) {
            if (obj instanceof NamedEntry) {
                return Objects.equals(((NamedEntry) obj).getName(), this.getName());
            }
            return false;
        }
    }

    /**
     * Lookups the EntryTable for the given name. Returns the corresponding entry if it exists, null
     * otherwise.
     */
    @SuppressWarnings("try")
    public T lookup(Symbol<Name> name) {
        try (BlockLock block = read()) {
            return entries.get(name);
        }
    }

    /**
     * Lookups the EntryTable for the given name. If an entry is found, returns it. Else, an entry
     * is created and added into the table. This entry is then returned.
     */
    @SuppressWarnings("try")
    public T lookupOrCreate(Symbol<Name> name, K data) {
        T entry = lookup(name);
        if (entry != null) {
            return entry;
        }
        try (BlockLock block = write()) {
            entry = lookup(name);
            if (entry != null) {
                return entry;
            }
            return addEntry(name, data);
        }
    }

    /**
     * Creates and adds an entry in the table. If an entry already exists, this is a nop, and
     * returns null
     */
    @SuppressWarnings("try")
    public T createAndAddEntry(Symbol<Name> name, K data) {
        try (BlockLock block = write()) {
            if (lookup(name) != null) {
                return null;
            }
            return addEntry(name, data);
        }
    }

    private T addEntry(Symbol<Name> name, K data) {
        T entry = createEntry(name, data);
        entries.put(name, entry);
        return entry;
    }

}
