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

import com.oracle.truffle.api.nodes.RootNode;
import java.util.AbstractList;
import java.util.List;

/**
 * Translation of a list of array elements to list of debugger values. The implementation is not
 * thread safe.
 */
final class ValueInteropList extends AbstractList<DebugValue> {

    private final Debugger debugger;
    private final RootNode rootNode;
    private final List<Object> list;

    ValueInteropList(Debugger debugger, RootNode rootNode, List<Object> list) {
        this.debugger = debugger;
        this.rootNode = rootNode;
        this.list = list;
    }

    @Override
    public DebugValue get(int index) {
        Object obj = list.get(index);
        DebugValue dv = new DebugValue.HeapValue(debugger, rootNode, obj);
        return dv;
    }

    @Override
    public DebugValue set(int index, DebugValue value) {
        DebugValue old = get(index);
        list.set(index, value.get());
        return old;
    }

    @Override
    public int size() {
        return list.size();
    }

}
