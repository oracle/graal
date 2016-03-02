/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.interop.java;

import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import java.util.AbstractList;
import java.util.List;

final class TruffleList<T> extends AbstractList<T> {
    private final TruffleObject array;
    private final Class<T> type;

    private TruffleList(Class<T> elementType, TruffleObject array) {
        this.array = array;
        this.type = elementType;
    }

    public static <T> List<T> create(Class<T> elementType, TruffleObject array) {
        return new TruffleList<>(elementType, array);
    }

    @Override
    public T get(int index) {
        try {
            return type.cast(ToJavaNode.message(Message.READ, array, index));
        } catch (InteropException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public T set(int index, T element) {
        T prev = get(index);
        try {
            ToJavaNode.message(Message.WRITE, array, index, element);
        } catch (InteropException e) {
            throw new IllegalStateException(e);
        }
        return prev;
    }

    @Override
    public int size() {
        try {
            return (Integer) ToJavaNode.message(Message.GET_SIZE, array);
        } catch (InteropException e) {
            throw new IllegalStateException(e);
        }
    }

}
