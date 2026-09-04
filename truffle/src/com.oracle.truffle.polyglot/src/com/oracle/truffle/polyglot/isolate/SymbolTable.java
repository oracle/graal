/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.polyglot.isolate;

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.Message;

import java.util.Arrays;
import java.util.BitSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

/**
 * Replaces frequently used interop member names with compact ids across an isolate boundary. Each
 * communication direction has a {@link Source} that assigns ids and a {@link Sink} that resolves
 * them.
 */
final class SymbolTable {

    private static final boolean[] MEMBER_NAME_MESSAGES = SymbolTable.createMemberNameMessageTable();

    private static boolean[] createMemberNameMessageTable() {
        boolean[] nameMessages = new boolean[InteropLibrary.getFactory().getMessages().size()];
        insert(nameMessages, "hasMemberReadSideEffects", Object.class, String.class);
        insert(nameMessages, "hasMemberWriteSideEffects", Object.class, String.class);
        insert(nameMessages, "isMemberInsertable", Object.class, String.class);
        insert(nameMessages, "isMemberInternal", Object.class, String.class);
        insert(nameMessages, "isMemberInvocable", Object.class, String.class);
        insert(nameMessages, "isMemberModifiable", Object.class, String.class);
        insert(nameMessages, "isMemberReadable", Object.class, String.class);
        insert(nameMessages, "isMemberRemovable", Object.class, String.class);
        insert(nameMessages, "invokeMember", Object.class, String.class, Object[].class);
        insert(nameMessages, "readMember", Object.class, String.class);
        insert(nameMessages, "removeMember", Object.class, String.class);
        insert(nameMessages, "writeMember", Object.class, String.class, Object.class);
        return nameMessages;
    }

    private static void insert(boolean[] into, String messageName, Class<?>... parameterTypes) {
        into[Message.resolveExact(InteropLibrary.class, messageName, parameterTypes).getId()] = true;
    }

    private SymbolTable() {
    }

    /** A source-side symbol pinned while it is used by a dispatch. */
    static final class Symbol {

        private static final int RETIRED = Integer.MIN_VALUE;
        private static final AtomicIntegerFieldUpdater<Symbol> ACTIVE_UPDATER = AtomicIntegerFieldUpdater.newUpdater(Symbol.class, "active");

        private final String name;
        private final int id;
        /*
         * Intentionally not volatile nor atomic. The flag is cleared only after a successful
         * dispatch, so any thread observing false is guaranteed that the peer has already
         * registered the symbol and a reference is safe to send. A thread that does not observe the
         * update, or that races with it, merely re-sends the definition, which the peer accepts
         * idempotently in Sink.registerSymbol. Both outcomes are correct, therefore the read does
         * not need to be synchronized.
         */
        private boolean pending;
        /* Approximate accesses since the last compaction. */
        private int lru;
        /*
         * Number of active uses. A value greater than zero prevents Source.compact() from removing
         * the symbol, while zero makes it eligible for retirement. RETIRED marks a removed symbol,
         * which Source.acquireSymbol() must not return.
         */
        private volatile int active;

        private Symbol(String name, int id, boolean pending) {
            this.name = name;
            this.id = id;
            this.pending = pending;
        }

        String getName() {
            return name;
        }

        int getId() {
            return id;
        }

        /** Returns whether the next dispatch must send the name together with the id. */
        boolean isPending() {
            return pending;
        }

        /** Marks the symbol definition as delivered to the sink. */
        void finishRegistration() {
            pending = false;
        }

        /** Releases one use acquired by {@link Source#acquireSymbol(String)}. */
        void release() {
            int current = ACTIVE_UPDATER.decrementAndGet(this);
            assert current >= 0;
        }

        @Override
        public int hashCode() {
            return id;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null || obj.getClass() != Symbol.class) {
                return false;
            }
            return id == ((Symbol) obj).id;
        }

        private boolean tryAcquire() {
            int current;
            do {
                current = active;
                if (current == RETIRED) {
                    return false;
                }
            } while (!ACTIVE_UPDATER.compareAndSet(this, current, current + 1));
            lru++;
            return true;
        }

        private boolean tryRetire() {
            return ACTIVE_UPDATER.compareAndSet(this, 0, RETIRED);
        }
    }

    static boolean isMemberNameMessage(int messageId) {
        return MEMBER_NAME_MESSAGES[messageId];
    }

    static Source createSourceTable() {
        return new Source();
    }

    static Sink createSinkTable() {
        return new Sink();
    }

    /** Assigns ids to names on the sending side and recycles infrequently used symbols. */
    static final class Source {

        /*
         * Upper bound on assigned symbol ids. The maximal MAX_SYMBOLS value is dictated by the wire format: symbol ids
         * are written as a short by BinaryProtocol, so an id above Short.MAX_VALUE would be
         * silently truncated and resolve to a wrong member name. It also bounds the table for
         * guests that compute member names dynamically. Once the ids are exhausted, infrequently
         * used symbols are retired and their ids are reused.
         */
        private static final int MAX_SYMBOLS = 1024;
        /*
         * Upper bound for a member name length. Names longer than MAX_SYMBOL_NAME_LENGTH
         * are not cached in the symbol table.
         */
        private static final int MAX_SYMBOL_NAME_LENGTH = 256;
        /* Assigns ids until MAX_SYMBOLS is reached. */
        private final AtomicInteger idGenerator = new AtomicInteger();
        /* Reusable ids produced by compaction; guarded by itself. */
        private final BitSet freeIds = new BitSet(MAX_SYMBOLS);
        private final ConcurrentMap<String, Symbol> nameToSymbol = new ConcurrentHashMap<>();

        private Source() {
        }

        /**
         * Acquires the symbol for a member name, creating one if an id is available. The caller
         * must invoke {@link Symbol#release()} in a {@code finally} block and should invoke
         * {@link Symbol#finishRegistration()} after a successful dispatch:
         *
         * <pre>{@code
         * Symbol symbol = source.acquireSymbol(name);
         * try {
         *     dispatch(symbol != null ? symbol : name);
         *     if (symbol != null) {
         *         symbol.finishRegistration();
         *     }
         * } finally {
         *     if (symbol != null) {
         *         symbol.release();
         *     }
         * }
         * }</pre>
         *
         * @param name the member name
         * @return the acquired symbol, or {@code null} if no id can be reclaimed or {@code name} length is greater than
         * {@link #MAX_SYMBOL_NAME_LENGTH}.
         */
        Symbol acquireSymbol(String name) {
            if (name.length() > MAX_SYMBOL_NAME_LENGTH) {
                return null;
            }
            boolean compacted = false;
            while (true) {
                Symbol s = nameToSymbol.get(name);
                if (s == null) {
                    s = nameToSymbol.computeIfAbsent(name, n -> {
                        int id = generateId();
                        return id != Symbol.RETIRED ? new Symbol(n, id, true) : null;
                    });
                    if (s == null) {
                        if (compacted) {
                            return null;
                        }
                        compact();
                        compacted = true;
                        continue;
                    }
                }
                if (s.tryAcquire()) {
                    return s;
                }
            }
        }

        /** Returns a fresh or recycled id, or {@link Symbol#RETIRED} if none is available. */
        private int generateId() {
            if (idGenerator.get() < MAX_SYMBOLS) {
                int id = idGenerator.getAndIncrement();
                if (id < MAX_SYMBOLS) {
                    return id;
                }
            }
            synchronized (freeIds) {
                int id = freeIds.nextSetBit(0);
                if (id >= 0) {
                    freeIds.clear(id);
                    return id;
                }
            }
            return Symbol.RETIRED;
        }

        /**
         * Attempts to retire the least-used quarter of the table. Active symbols are skipped and
         * observed usage counters are reset.
         */
        private synchronized void compact() {
            synchronized (freeIds) {
                if (!freeIds.isEmpty()) {
                    return;
                }
            }
            Symbol[] symbols = nameToSymbol.values().toArray(new Symbol[0]);
            long[] usages = new long[symbols.length];
            for (int i = 0; i < symbols.length; i++) {
                Symbol symbol = symbols[i];
                int usage = Math.max(0, symbol.lru);
                symbol.lru = 0;
                usages[i] = ((long) usage << Integer.SIZE) | (i & 0xffffffffL);
            }
            Arrays.sort(usages);
            int target = Math.max(1, symbols.length / 4);
            int retired = 0;
            BitSet reclaimedIds = new BitSet(MAX_SYMBOLS);
            for (long usage : usages) {
                Symbol symbol = symbols[(int) usage];
                if (symbol.tryRetire() && nameToSymbol.remove(symbol.name, symbol)) {
                    reclaimedIds.set(symbol.id);
                    retired++;
                    if (retired == target) {
                        break;
                    }
                }
            }
            synchronized (freeIds) {
                freeIds.or(reclaimedIds);
            }
        }
    }

    /** Resolves ids on the receiving side. */
    static final class Sink {

        private static final int INITIAL_SIZE = 64;

        private volatile String[] names = new String[INITIAL_SIZE];

        private Sink() {
        }

        synchronized void registerSymbol(int id, String name) {
            String[] cur = names;
            if (id >= cur.length) {
                cur = Arrays.copyOf(cur, Math.max(id + 1, cur.length * 2));
            }
            cur[id] = name;
            names = cur;
        }

        String lookupSymbolName(int id) {
            String name = names[id];
            assert name != null : "Unknown symbol id " + id;
            return name;
        }
    }
}
