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

import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.LanguageInfo;
import java.util.AbstractCollection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Translation of a map of object properties to a collection of debugger values. The implementation
 * is not thread safe.
 */
final class ValuePropertiesCollection extends AbstractCollection<DebugValue> {

    private final Debugger debugger;
    private final LanguageInfo language;
    private final TruffleObject object;
    private final Map<Object, Object> map;
    private final Set<Map.Entry<Object, Object>> entrySet;
    private final DebugScope scope;

    ValuePropertiesCollection(Debugger debugger, LanguageInfo language, TruffleObject object,
                    Map<Object, Object> map, Set<Map.Entry<Object, Object>> entrySet, DebugScope scope) {
        this.debugger = debugger;
        this.language = language;
        this.object = object;
        this.map = map;
        this.entrySet = entrySet;
        this.scope = scope;
    }

    @Override
    public Iterator<DebugValue> iterator() {
        return new PropertiesIterator(object, entrySet.iterator());
    }

    @Override
    public int size() {
        return entrySet.size();
    }

    DebugValue get(String name) {
        Object value = map.get(name);
        if (value == null) {
            return null;
        }
        return new DebugValue.PropertyNamedValue(debugger, language, object, map, name, scope);
    }

    private final class PropertiesIterator implements Iterator<DebugValue> {

        private final TruffleObject object;
        private final Iterator<Map.Entry<Object, Object>> entries;

        PropertiesIterator(TruffleObject object, Iterator<Map.Entry<Object, Object>> entries) {
            this.object = object;
            this.entries = entries;
        }

        @Override
        public boolean hasNext() {
            return entries.hasNext();
        }

        @Override
        public DebugValue next() {
            try {
                return new DebugValue.PropertyValue(debugger, language, object, entries.next(), scope);
            } catch (ThreadDeath td) {
                throw td;
            } catch (Throwable ex) {
                throw new DebugException(debugger, ex, language, null, true, null);
            }
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("remove not supported.");
        }

    }

}
