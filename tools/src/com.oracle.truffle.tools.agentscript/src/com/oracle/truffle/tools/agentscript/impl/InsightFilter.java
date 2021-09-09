/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.agentscript.impl;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.StopIterationException;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class InsightFilter {
    private final List<Class<? extends Tag>> allTags;
    private final String rootNameRegExp;
    private final boolean rootNameFn;
    private final boolean sourceFilterFn;

    private InsightFilter(List<Class<? extends Tag>> allTags, String rootNameRegExp, boolean rootNameFn, boolean sourceFilterFn) {
        this.allTags = allTags;
        this.rootNameRegExp = rootNameRegExp;
        this.rootNameFn = rootNameFn;
        this.sourceFilterFn = sourceFilterFn;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 79 * hash + Objects.hashCode(this.allTags);
        hash = 79 * hash + Objects.hashCode(this.rootNameRegExp);
        hash = 79 * hash + (this.rootNameFn ? 1 : 0);
        hash = 79 * hash + (this.sourceFilterFn ? 1 : 0);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final InsightFilter other = (InsightFilter) obj;
        if (this.rootNameFn != other.rootNameFn) {
            return false;
        }
        if (this.sourceFilterFn != other.sourceFilterFn) {
            return false;
        }
        if (!Objects.equals(this.rootNameRegExp, other.rootNameRegExp)) {
            return false;
        }
        if (!Objects.equals(this.allTags, other.allTags)) {
            return false;
        }
        return true;
    }

    Class<?>[] getTags() {
        return allTags.toArray(new Class<?>[0]);
    }

    String getRootNameRegExp() {
        return rootNameRegExp;
    }

    static InsightFilter.Data create(AgentType at, Object[] arr)
                    throws IllegalArgumentException, UnsupportedMessageException {
        List<Class<? extends Tag>> allTags = new ArrayList<>();
        String rootNameRegExp = null;
        Object rootNameFn = null;
        Object sourceFilterFn = null;
        if (arr != null && arr.length > 2) {
            Object config = arr[2];
            final InteropLibrary iop = InteropLibrary.getFactory().getUncached();
            Map<String, Object> map = new LinkedHashMap<>();
            if (iop.hasHashEntries(config)) {
                Object it = iop.getHashEntriesIterator(config);
                while (iop.hasIteratorNextElement(it)) {
                    try {
                        Object keyAndValue = iop.getIteratorNextElement(it);
                        Object key;
                        Object value;
                        try {
                            key = iop.readArrayElement(keyAndValue, 0);
                            value = iop.readArrayElement(keyAndValue, 1);
                        } catch (InvalidArrayIndexException ex) {
                            throw CompilerDirectives.shouldNotReachHere(ex);
                        }
                        String type = iop.asString(key);
                        map.put(type, value);
                    } catch (StopIterationException ex) {
                        break;
                    }
                }
            } else {
                Object allMembers = iop.getMembers(config, false);
                long allMembersSize = iop.getArraySize(allMembers);
                for (int i = 0; i < allMembersSize; i++) {
                    Object atI;
                    try {
                        atI = iop.readArrayElement(allMembers, i);
                    } catch (InvalidArrayIndexException ex) {
                        continue;
                    }
                    String type = iop.asString(atI);
                    Object value;
                    try {
                        value = iop.readMember(config, type);
                    } catch (UnknownIdentifierException ex) {
                        continue;
                    }
                    map.put(type, value);
                }
            }
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                String type = entry.getKey();
                switch (type) {
                    case "expressions":
                        if (isSet(iop, map, "expressions")) {
                            allTags.add(StandardTags.ExpressionTag.class);
                        }
                        break;
                    case "statements":
                        if (isSet(iop, map, "statements")) {
                            allTags.add(StandardTags.StatementTag.class);
                        }
                        break;
                    case "roots":
                        if (isSet(iop, map, "roots")) {
                            allTags.add(StandardTags.RootBodyTag.class);
                        }
                        break;
                    case "rootNameFilter": {
                        Object fn = map.get("rootNameFilter");
                        if (fn != null && !iop.isNull(fn)) {
                            if (iop.isString(fn)) {
                                rootNameRegExp = iop.asString(fn);
                            } else {
                                if (!iop.isExecutable(fn)) {
                                    throw new IllegalArgumentException("rootNameFilter should be a string, a regular expression!");
                                }
                                rootNameFn = fn;
                            }
                        }
                        break;
                    }
                    case "sourceFilter": {
                        Object fn = map.get("sourceFilter");
                        if (fn != null && !iop.isNull(fn)) {
                            if (!iop.isExecutable(fn)) {
                                throw new IllegalArgumentException("sourceFilter has to be a function!");
                            }
                            sourceFilterFn = fn;
                        }
                        break;
                    }
                    default:
                        throw InsightException.unknownAttribute(type);
                }
            }
        }
        if (allTags.isEmpty()) {
            throw new IllegalArgumentException("No elements specified to listen to for execution listener. Need to specify at least one element kind: expressions, statements or roots.");
        }

        allTags.sort((c1, c2) -> c1.getName().compareTo(c2.getName()));
        InsightFilter filter = new InsightFilter(allTags, rootNameRegExp, rootNameFn != null, sourceFilterFn != null);
        return new Data(at, filter, arr[1], rootNameFn, sourceFilterFn);
    }

    private static boolean isSet(InteropLibrary iop, Map<String, Object> map, String property) {
        Object value = map.get(property);
        try {
            return iop.isBoolean(value) && iop.asBoolean(value);
        } catch (UnsupportedMessageException ex) {
            return false;
        }
    }

    public static final class Data {
        final AgentType type;
        final InsightFilter filter;
        final Object fn;
        final Object rootNameFn;
        final Object sourceFilterFn;

        private Data(AgentType type, InsightFilter filter, Object fn, Object rootNameFn, Object sourceFilterFn) {
            this.type = type;
            this.filter = filter;
            this.fn = fn;
            this.rootNameFn = rootNameFn;
            this.sourceFilterFn = sourceFilterFn;
        }
    }
}
