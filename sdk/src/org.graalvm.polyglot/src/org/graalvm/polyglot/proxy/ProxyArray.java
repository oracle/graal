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

import org.graalvm.polyglot.Value;

public interface ProxyArray extends Proxy {

    Object get(long index);

    void set(long index, Value value);

    long getSize();

    static ProxyArray fromArray(Object... values) {
        return new ProxyArray() {
            public Object get(long index) {
                checkIndex(index);
                return values[(int) index];
            }

            public void set(long index, Value value) {
                checkIndex(index);
                values[(int) index] = value.isHostObject() ? value.asHostObject() : value;
            }

            private void checkIndex(long index) {
                if (index > Integer.MAX_VALUE || index < 0) {
                    throw new ArrayIndexOutOfBoundsException("invalid index.");
                }
            }

            public long getSize() {
                return values.length;
            }
        };
    }

    static ProxyArray fromList(List<Object> values) {
        return new ProxyArray() {

            public Object get(long index) {
                checkIndex(index);
                return values.get((int) index);
            }

            public void set(long index, Value value) {
                checkIndex(index);
                values.set((int) index, value.isHostObject() ? value.asHostObject() : value);
            }

            private void checkIndex(long index) {
                if (index > Integer.MAX_VALUE || index < 0) {
                    throw new ArrayIndexOutOfBoundsException("invalid index.");
                }
            }

            public long getSize() {
                return values.size();
            }

        };
    }

}
