/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.polyglot.proxy;

import org.graalvm.polyglot.Value;

import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

public interface ProxyHashMap {

    boolean hasEntry(Value key);

    Object getValue(Value key);

    void putEntry(Value key, Value value);

    default boolean removeEntry(@SuppressWarnings("unused") Value key) {
        throw new UnsupportedOperationException("remove() not supported.");
    }

    Object getEntriesIterator();

    static ProxyHashMap fromMap(Map<Object, Object> values) {
        return new ProxyHashMap() {

            @Override
            public boolean hasEntry(Value key) {
                if (key.isHostObject() && values.containsKey(key.asHostObject())) {
                    return true;
                }
                return values.containsKey(key);
            }

            @Override
            public Object getValue(Value key) {
                if (key.isHostObject() && values.containsKey(key.asHostObject())) {
                    return values.get(key.asHostObject());
                } else {
                    return values.get(key);
                }
            }

            @Override
            public void putEntry(Value key, Value value) {
                if (key.isHostObject() && values.containsKey(key)) {
                    values.remove(key);
                }
                values.put(key.isHostObject() ? key.asHostObject() : key,
                                value.isHostObject() ? value.asHostObject() : value);
            }

            @Override
            public boolean removeEntry(Value key) {
                if (key.isHostObject() && values.containsKey(key.asHostObject())) {
                    values.remove(key.asHostObject());
                    return true;
                } else if (values.containsKey(key)) {
                    values.remove(key);
                    return true;
                } else {
                    return false;
                }
            }

            @Override
            public Object getEntriesIterator() {
                Iterator<? extends Map.Entry<?, ?>> entryIterator = values.entrySet().iterator();
                return new ProxyIterator() {
                    @Override
                    public boolean hasNext() {
                        return entryIterator.hasNext();
                    }

                    @Override
                    public Object getNext() throws NoSuchElementException, UnsupportedOperationException {
                        Map.Entry<?, ?> entry = entryIterator.next();
                        return ProxyHashEntry.create(entry.getKey(), entry.getValue());
                    }
                };
            }
        };
    }
}
