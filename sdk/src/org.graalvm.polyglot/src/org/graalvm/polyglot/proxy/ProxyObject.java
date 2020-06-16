/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;
import java.util.Map;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

/**
 * Interface to be implemented to mimic guest language objects that contain members.
 *
 * @see Proxy
 * @since 19.0
 */
public interface ProxyObject extends Proxy {

    /**
     * Returns the value of the member.
     *
     * @throws UnsupportedOperationException if the operation is unsupported
     * @since 19.0
     */
    Object getMember(String key);

    /**
     * Returns array of member keys. The returned array must be interpreted as having array elements
     * using the semantics of {@link Context#asValue(Object)} otherwise and
     * {@link IllegalStateException} is thrown. If one of the return values of the array is not a
     * {@link String} then a {@link ClassCastException} is thrown. Examples for valid return values
     * are:
     * <ul>
     * <li><code>null</code> for no member keys
     * <li>{@link ProxyArray} that returns {@link String} values for each array element
     * <li>{@link List } with exclusively String elements
     * <li>{@link String String[]}
     * <li>A guest language object representing an array of strings.
     * </ul>
     * Every member key returned by the {@link #getMemberKeys()} method must return
     * <code>true</code> for {@link #hasMember(String)}.
     *
     * @see #hasMember(String)
     * @see Context#asValue(Object)
     * @since 19.0
     */
    Object getMemberKeys();

    /**
     * Returns <code>true</code> if the proxy object contains a member with the given key, or else
     * <code>false</code>. While not required ever member key which returns <code>true</code> for
     * {@link #hasMember(String)} should be returned by {@link #getMemberKeys()} to allow guest
     * members to list member keys.
     *
     * @see #getMemberKeys()
     * @since 19.0
     */
    boolean hasMember(String key);

    /**
     * Sets the value associated with a member. If the member does not {@link #hasMember(String)
     * exist} then a new member is defined. If the definition of new members is not supported then
     * an {@link UnsupportedOperationException} is thrown.
     *
     * @throws UnsupportedOperationException if the operation is unsupported
     * @since 19.0
     */
    void putMember(String key, Value value);

    /**
     * Removes a member key and its value. If the removal of existing members is not supported then
     * an {@link UnsupportedOperationException} is thrown.
     *
     * @return <code>true</code> when the member was removed, <code>false</code> when the member
     *         didn't exist.
     * @throws UnsupportedOperationException if the operation is unsupported
     * @since 19.0
     */
    @SuppressWarnings("unused")
    default boolean removeMember(String key) {
        throw new UnsupportedOperationException("removeMember() not supported.");
    }

    /**
     * Creates a proxy backed by a {@link Map}. If the set values of the map are host values then
     * the they will be {@link Value#asHostObject() unboxed}.
     *
     * @since 19.0
     */
    static ProxyObject fromMap(Map<String, Object> values) {
        return new ProxyObject() {

            public void putMember(String key, Value value) {
                values.put(key, value.isHostObject() ? value.asHostObject() : value);
            }

            public boolean hasMember(String key) {
                return values.containsKey(key);
            }

            public Object getMemberKeys() {
                return new ProxyArray() {
                    private final Object[] keys = values.keySet().toArray();

                    public void set(long index, Value value) {
                        throw new UnsupportedOperationException();
                    }

                    public long getSize() {
                        return keys.length;
                    }

                    public Object get(long index) {
                        if (index < 0 || index > Integer.MAX_VALUE) {
                            throw new ArrayIndexOutOfBoundsException();
                        }
                        return keys[(int) index];
                    }
                };
            }

            public Object getMember(String key) {
                return values.get(key);
            }

            @Override
            public boolean removeMember(String key) {
                if (values.containsKey(key)) {
                    values.remove(key);
                    return true;
                } else {
                    return false;
                }
            }
        };
    }

}
