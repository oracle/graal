/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.debug;

import java.util.AbstractList;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;

final class ObjectStructures {

    static Map<Object, Object> asMap(MessageNodes nodes, TruffleObject object) {
        TruffleObject keys;
        try {
            keys = ForeignAccess.sendKeys(nodes.keys, object, true);
            boolean hasSize = ForeignAccess.sendHasSize(nodes.hasSize, keys);
            if (!hasSize) {
                return null;
            }
        } catch (UnsupportedMessageException ex) {
            return null;
        }
        return new ObjectMap(nodes, object, keys);
    }

    static boolean isArray(MessageNodes nodes, TruffleObject object) {
        return ForeignAccess.sendHasSize(nodes.hasSize, object);
    }

    static List<Object> asList(MessageNodes nodes, TruffleObject object) {
        if (!ForeignAccess.sendHasSize(nodes.hasSize, object)) {
            return null;
        }
        return new ObjectList(nodes, object);
    }

    static boolean canExecute(MessageNodes nodes, TruffleObject object) {
        return ForeignAccess.sendIsExecutable(nodes.isExecutable, object);
    }

    private static class ObjectMap extends AbstractMap<Object, Object> {

        private final MessageNodes nodes;
        private final TruffleObject object;
        private final TruffleObject keys;

        ObjectMap(MessageNodes nodes, TruffleObject object, TruffleObject keys) {
            this.nodes = nodes;
            this.object = object;
            this.keys = keys;
        }

        @Override
        public Set<Entry<Object, Object>> entrySet() {
            try {
                Number size = (Number) ForeignAccess.sendGetSize(nodes.getSize, keys);
                return new LazyEntries(keys, size.intValue());
            } catch (UnsupportedMessageException ex) {
                return Collections.emptySet();
            }
        }

        @Override
        public Object get(Object key) {
            try {
                return ForeignAccess.sendRead(nodes.read, object, key);
            } catch (UnknownIdentifierException ex) {
                return null;    // key not present in the map
            } catch (UnsupportedMessageException ex) {
                throw ex.raise();
            }
        }

        @Override
        public Object put(Object key, Object value) {
            Object prev = get(key);
            try {
                ForeignAccess.sendWrite(nodes.write, object, key, value);
            } catch (UnknownIdentifierException | UnsupportedTypeException | UnsupportedMessageException ex) {
                throw ex.raise();
            }
            return prev;
        }

        private final class LazyEntries extends AbstractSet<Entry<Object, Object>> {

            private final TruffleObject props;
            private final int size;

            LazyEntries(TruffleObject props, int size) {
                this.props = props;
                this.size = size;
            }

            @Override
            public Iterator<Entry<Object, Object>> iterator() {
                return new LazyIterator();
            }

            @Override
            public int size() {
                return size;
            }

            private final class LazyIterator implements Iterator<Entry<Object, Object>> {

                private int index;

                LazyIterator() {
                    index = 0;
                }

                @Override
                public boolean hasNext() {
                    return index < size;
                }

                @Override
                public Entry<Object, Object> next() {
                    Object key;
                    try {
                        key = ForeignAccess.sendRead(nodes.read, props, index++);
                    } catch (UnknownIdentifierException | UnsupportedMessageException ex) {
                        throw ex.raise();
                    }
                    return new TruffleEntry(nodes, object, key);
                }

                @Override
                public void remove() {
                    throw new UnsupportedOperationException("remove not supported.");
                }

            }
        }
    }

    static final class TruffleEntry implements Map.Entry<Object, Object> {

        private final MessageNodes nodes;
        private final TruffleObject object;
        private final Object key;

        TruffleEntry(MessageNodes nodes, TruffleObject object, Object key) {
            this.nodes = nodes;
            this.object = object;
            this.key = key;
        }

        @Override
        public Object getKey() {
            return key;
        }

        @Override
        public Object getValue() {
            try {
                return ForeignAccess.sendRead(nodes.read, object, key);
            } catch (UnknownIdentifierException | UnsupportedMessageException ex) {
                throw ex.raise();
            }
        }

        @Override
        public Object setValue(Object value) {
            try {
                ForeignAccess.sendWrite(nodes.write, object, key, value);
            } catch (UnknownIdentifierException | UnsupportedMessageException | UnsupportedTypeException ex) {
                throw ex.raise();
            }
            return null;
        }
    }

    private static class ObjectList extends AbstractList<Object> {

        private final MessageNodes nodes;
        protected final TruffleObject object;

        ObjectList(MessageNodes nodes, TruffleObject object) {
            this.nodes = nodes;
            this.object = object;
        }

        @Override
        public Object get(int index) {
            try {
                return ForeignAccess.sendRead(nodes.read, object, index);
            } catch (UnknownIdentifierException | UnsupportedMessageException ex) {
                throw ex.raise();
            }
        }

        @Override
        public Object set(int index, Object element) {
            Object prev = get(index);
            try {
                ForeignAccess.sendWrite(nodes.write, object, index, element);
            } catch (UnknownIdentifierException | UnsupportedTypeException | UnsupportedMessageException ex) {
                throw ex.raise();
            }
            return prev;
        }

        @Override
        public int size() {
            try {
                Number size = (Number) ForeignAccess.sendGetSize(nodes.getSize, object);
                return size.intValue();
            } catch (UnsupportedMessageException ex) {
                return 0;
            }
        }

    }

    static class MessageNodes {

        final Node keyInfo;
        final Node keys;
        final Node hasSize;
        final Node getSize;
        final Node read;
        final Node write;
        final Node isBoxed;
        final Node unbox;
        final Node isExecutable;
        final Node invoke1;

        MessageNodes() {
            keyInfo = Message.KEY_INFO.createNode();
            keys = Message.KEYS.createNode();
            hasSize = Message.HAS_SIZE.createNode();
            getSize = Message.GET_SIZE.createNode();
            read = Message.READ.createNode();
            write = Message.WRITE.createNode();
            isBoxed = Message.IS_BOXED.createNode();
            unbox = Message.UNBOX.createNode();
            isExecutable = Message.IS_EXECUTABLE.createNode();
            invoke1 = Message.INVOKE.createNode();
        }
    }
}
