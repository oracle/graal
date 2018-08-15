/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.debug;

import java.util.AbstractList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.oracle.truffle.api.interop.KeyInfo;
import com.oracle.truffle.api.nodes.LanguageInfo;

/**
 * Translation of a list of array elements to list of debugger values. The implementation is not
 * thread safe.
 */
final class ValueInteropList extends AbstractList<DebugValue> {

    private final Debugger debugger;
    private final LanguageInfo language;
    private final List<Object> list;

    ValueInteropList(Debugger debugger, LanguageInfo language, List<Object> list) {
        this.debugger = debugger;
        this.language = language;
        this.list = list;
    }

    @Override
    public DebugValue get(int index) {
        AtomicReference<Object> objRef;
        try {
            objRef = new AtomicReference<>(list.get(index));
        } catch (ThreadDeath td) {
            throw td;
        } catch (Throwable ex) {
            throw new DebugException(debugger, ex, language, null, true, null);
        }
        String name = Integer.toString(index);
        Map.Entry<Object, Object> elementEntry = new Map.Entry<Object, Object>() {
            @Override
            public String getKey() {
                return name;
            }

            @Override
            public Object getValue() {
                return objRef.get();
            }

            @Override
            public Object setValue(Object value) {
                list.set(index, value);
                return objRef.getAndSet(value);
            }
        };
        DebugValue dv = new DebugValue.PropertyValue(debugger, language, KeyInfo.READABLE | KeyInfo.MODIFIABLE, elementEntry, null);
        return dv;
    }

    @Override
    public DebugValue set(int index, DebugValue value) {
        DebugValue old = get(index);
        try {
            list.set(index, value.get());
        } catch (ThreadDeath td) {
            throw td;
        } catch (Throwable ex) {
            throw new DebugException(debugger, ex, language, null, true, null);
        }
        return old;
    }

    @Override
    public int size() {
        return list.size();
    }

}
