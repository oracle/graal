/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.polyglot.proxy;

import java.util.List;
import java.util.Map;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

/**
 * Interface to be implemented to mimic guest language objects that contain members.
 *
 * @see Proxy
 * @since 1.0
 */
public interface ProxyObject extends Proxy {

    /**
     * Returns the value of the member.
     *
     * @throws UnsupportedOperationException if the operation is unsupported
     * @since 1.0
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
     * @since 1.0
     */
    Object getMemberKeys();

    /**
     * Returns <code>true</code> if the proxy object contains a member with the given key, or else
     * <code>false</code>. While not required ever member key which returns <code>true</code> for
     * {@link #hasMember(String)} should be returned by {@link #getMemberKeys()} to allow guest
     * members to list member keys.
     *
     * @see #getMemberKeys()
     * @since 1.0
     */
    boolean hasMember(String key);

    /**
     * Sets the value associated with a member. If the member does not {@link #hasMember(String)
     * exist} then a new member is defined. If the definition of new members is not supported then
     * an {@link UnsupportedOperationException} is thrown.
     *
     * @throws UnsupportedOperationException if the operation is unsupported
     * @since 1.0
     */
    void putMember(String key, Value value);

    /**
     * Removes a member key and its value. If the removal of existing members is not supported then
     * an {@link UnsupportedOperationException} is thrown.
     *
     * @return <code>true</code> when the member was removed, <code>false</code> when the member
     *         didn't exist.
     * @throws UnsupportedOperationException if the operation is unsupported
     * @since 1.0
     */
    @SuppressWarnings("unused")
    default boolean removeMember(String key) {
        throw new UnsupportedOperationException("removeMember() not supported.");
    }

    /**
     * Creates a proxy backed by a {@link Map}. If the set values of the map are host values then
     * the they will be {@link Value#asHostObject() unboxed}.
     *
     * @since 1.0
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
                return values.keySet().toArray();
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
