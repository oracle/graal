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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

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

    static final class Symbol {

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

        boolean isPending() {
            return pending;
        }

        void finishRegistration() {
            pending = false;
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

    static final class Source {

        /*
         * Upper bound on assigned symbol ids. The maximal value is dictated by the wire format: symbol ids
         * are written as a short by BinaryProtocol, so an id above Short.MAX_VALUE would be
         * silently truncated and resolve to a wrong member name. It also bounds the table for
         * guests that compute member names dynamically. Once the ids are exhausted preRegister
         * returns null and the names are sent verbatim again.
         */
        private static final int MAX_SYMBOLS = Short.MAX_VALUE;
        private final AtomicInteger idGenerator = new AtomicInteger();
        private final ConcurrentMap<String, Symbol> nameToSymbol = new ConcurrentHashMap<>();

        private Source() {
        }

        Symbol preRegister(String name) {
            Symbol s = nameToSymbol.get(name);
            if (s != null) {
                return s;
            }
            if (idGenerator.get() > MAX_SYMBOLS) {
                return null;
            }
            return nameToSymbol.computeIfAbsent(name, n -> {
                int id = idGenerator.getAndIncrement();
                return id <= MAX_SYMBOLS ? new Symbol(n, id, true) : null;
            });
        }
    }

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
            assert cur[id] == null || cur[id].equals(name) : "Symbol id " + id + " redefined from " + cur[id] + " to " + name;
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
