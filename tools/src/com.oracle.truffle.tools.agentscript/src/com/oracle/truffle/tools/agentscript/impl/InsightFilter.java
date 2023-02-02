/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.net.URISyntaxException;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.graalvm.collections.EconomicMap;

final class InsightFilter {

    private static final String CONFIG_EXPRESSIONS = "expressions";
    private static final String CONFIG_STATEMENTS = "statements";
    private static final String CONFIG_ROOTS = "roots";
    private static final String CONFIG_ROOT_FILTER = "rootNameFilter";
    private static final String CONFIG_SOURCE_FILTER = "sourceFilter";
    private static final String CONFIG_AT = "at";
    private static final Set<String> CONFIG_PROPS = new HashSet<>(Arrays.asList(CONFIG_EXPRESSIONS, CONFIG_STATEMENTS, CONFIG_ROOTS, CONFIG_ROOT_FILTER, CONFIG_SOURCE_FILTER, CONFIG_AT));

    private static final String AT_SOURCE_PATH = "sourcePath";
    private static final String AT_SOURCE_URI = "sourceURI";
    private static final String AT_LINE = "line";
    private static final String AT_COLUMN = "column";
    private static final Set<String> AT_PROPS = new HashSet<>(Arrays.asList(AT_SOURCE_PATH, AT_SOURCE_URI, AT_LINE, AT_COLUMN));

    private static final InteropLibrary IOP = InteropLibrary.getFactory().getUncached();

    private final Set<Class<? extends Tag>> allTags;
    private final String rootNameRegExp;
    private final String sourcePathRegExp;
    private final URI sourceURI;
    private final int line;
    private final int column;
    private final Reference<Object> rootNameFn;
    private final int rootNameFnHash;
    private final Reference<Object> sourceFilterFn;
    private final int sourceFilterFnHash;

    private InsightFilter(Set<Class<? extends Tag>> allTags, String rootNameRegExp, URI sourceURI, String sourcePathRegExp, int line, int column, Object rootNameFn, Object sourceFilterFn) {
        this.allTags = allTags;
        this.rootNameRegExp = rootNameRegExp;
        this.sourceURI = sourceURI;
        this.sourcePathRegExp = sourcePathRegExp;
        this.line = line;
        this.column = column;
        this.rootNameFn = new WeakReference<>(rootNameFn);
        this.rootNameFnHash = (rootNameFn != null) ? rootNameFn.hashCode() : 0;
        this.sourceFilterFn = new WeakReference<>(sourceFilterFn);
        this.sourceFilterFnHash = (sourceFilterFn != null) ? sourceFilterFn.hashCode() : 0;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 79 * hash + Objects.hashCode(this.allTags);
        hash = 79 * hash + Objects.hashCode(this.rootNameRegExp);
        hash = 79 * hash + Objects.hashCode(this.sourcePathRegExp);
        hash = 79 * hash + Objects.hashCode(this.sourceURI);
        hash = 79 * hash + line;
        hash = 79 * hash + column;
        hash = 79 * hash + rootNameFnHash;
        hash = 79 * hash + sourceFilterFnHash;
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
        if (this.rootNameFn.get() != other.rootNameFn.get()) {
            return false;
        }
        if (this.sourceFilterFn.get() != other.sourceFilterFn.get()) {
            return false;
        }
        if (!Objects.equals(this.rootNameRegExp, other.rootNameRegExp)) {
            return false;
        }
        if (!Objects.equals(this.allTags, other.allTags)) {
            return false;
        }
        if (!Objects.equals(this.sourcePathRegExp, other.sourcePathRegExp)) {
            return false;
        }
        if (!Objects.equals(this.sourceURI, other.sourceURI)) {
            return false;
        }
        if (this.line != other.line) {
            return false;
        }
        if (this.column != other.column) {
            return false;
        }
        return true;
    }

    Class<?>[] getTags() {
        return allTags.toArray(new Class<?>[0]);
    }

    Set<Class<? extends Tag>> getTagsSet() {
        return allTags;
    }

    String getRootNameRegExp() {
        return rootNameRegExp;
    }

    String getSourcePathRegExp() {
        return sourcePathRegExp;
    }

    URI getSourceURI() {
        return sourceURI;
    }

    int getLine() {
        return line;
    }

    int getColumn() {
        return column;
    }

    static InsightFilter.Data create(AgentType aType, Object[] arr)
                    throws IllegalArgumentException, UnsupportedMessageException {
        Set<Class<? extends Tag>> allTags = new HashSet<>();
        String rootNameRegExp = null;
        Object rootNameFn = null;
        Object sourceFilterFn = null;
        String sourcePathRegExp = null;
        URI sourceURI = null;
        int lineIs = 0;
        int columnIs = 0;
        if (arr != null && arr.length > 2) {
            Object config = arr[2];
            ObjectReader configReader = ObjectReader.get(config, CONFIG_PROPS);
            if (configReader.is(CONFIG_EXPRESSIONS)) {
                allTags.add(StandardTags.ExpressionTag.class);
            }
            if (configReader.is(CONFIG_STATEMENTS)) {
                allTags.add(StandardTags.StatementTag.class);
            }
            if (configReader.is(CONFIG_ROOTS)) {
                allTags.add(StandardTags.RootBodyTag.class);
            }
            Object rootNameFilter = configReader.get(CONFIG_ROOT_FILTER);
            if (rootNameFilter != null) {
                if (IOP.isString(rootNameFilter)) {
                    rootNameRegExp = IOP.asString(rootNameFilter);
                } else {
                    if (!IOP.isExecutable(rootNameFilter)) {
                        throw new IllegalArgumentException("rootNameFilter should be a string, a regular expression!");
                    }
                    rootNameFn = rootNameFilter;
                }
            }
            Object sourceFilter = configReader.get(CONFIG_SOURCE_FILTER);
            if (sourceFilter != null) {
                if (!IOP.isExecutable(sourceFilter)) {
                    throw new IllegalArgumentException("sourceFilter has to be a function!");
                }
                sourceFilterFn = sourceFilter;
            }
            Object at = configReader.get(CONFIG_AT);
            if (at != null) {
                ObjectReader atReader = ObjectReader.get(at, AT_PROPS);
                Object sourcePath = atReader.get(AT_SOURCE_PATH);
                Object sourceURIValue = atReader.get(AT_SOURCE_URI);
                if (sourcePath != null && sourceURIValue != null) {
                    throw new IllegalArgumentException("Both sourceURI and sourcePath is defined. Only one source specification is expected.");
                }
                if (sourcePath == null && sourceURIValue == null) {
                    throw new IllegalArgumentException("Neither sourceURI nor sourcePath is defined. A source specification is expected.");
                }
                if (sourceURIValue != null) {
                    if (!IOP.isString(sourceURIValue)) {
                        throw new IllegalArgumentException("sourceURI is not a string.");
                    }
                    try {
                        sourceURI = new URI(IOP.asString(sourceURIValue));
                    } catch (URISyntaxException uex) {
                        throw new IllegalArgumentException("sourceURI is not a valid URI: " + uex.getLocalizedMessage());
                    }
                }
                if (sourcePath != null) {
                    if (!IOP.isString(sourcePath)) {
                        throw new IllegalArgumentException("sourcePath is not a string!");
                    }
                    sourcePathRegExp = IOP.asString(sourcePath);
                }
                Object lineValue = atReader.get(AT_LINE);
                if (lineValue != null) {
                    lineIs = getPositiveNumber(lineValue, "Source line");
                }
                Object columnValue = atReader.get(AT_COLUMN);
                if (columnValue != null) {
                    columnIs = getPositiveNumber(columnValue, "Source column");
                }
            }
        }
        if (allTags.isEmpty()) {
            throw new IllegalArgumentException("No elements specified to listen to for execution listener. Need to specify at least one element kind: expressions, statements or roots.");
        }

        InsightFilter filter = new InsightFilter(allTags, rootNameRegExp, sourceURI, sourcePathRegExp, lineIs, columnIs, rootNameFn, sourceFilterFn);
        return new Data(aType, filter, arr[1], rootNameFn, sourceFilterFn);
    }

    private static int getPositiveNumber(Object value, String errMsg) throws UnsupportedMessageException {
        if (value == null) {
            return 0;
        }
        if (IOP.fitsInInt(value)) {
            int num = IOP.asInt(value);
            if (num > 0) {
                return num;
            }
        }
        throw new IllegalArgumentException(errMsg + " is not a positive number or does not fit in integer type: " + IOP.asString(IOP.toDisplayString(value)));
    }

    private abstract static class ObjectReader {

        static ObjectReader get(Object object, Set<String> properties) {
            if (IOP.hasHashEntries(object)) {
                return new HashObjectReader(object, properties);
            } else {
                return new MembersObjectReader(object);
            }
        }

        boolean is(String propertyName) {
            Object value = get(propertyName);
            try {
                return value != null && IOP.isBoolean(value) && IOP.asBoolean(value);
            } catch (UnsupportedMessageException ex) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }

        abstract Object get(String propertyName);

    }

    private static class HashObjectReader extends ObjectReader {

        private final EconomicMap<String, Object> map = EconomicMap.create();

        HashObjectReader(Object object, Set<String> properties) {
            try {
                Object it = IOP.getHashEntriesIterator(object);
                while (IOP.hasIteratorNextElement(it)) {
                    try {
                        Object keyAndValue = IOP.getIteratorNextElement(it);
                        String name;
                        Object value;
                        try {
                            Object key = IOP.readArrayElement(keyAndValue, 0);
                            name = IOP.asString(key);
                            if (!properties.contains(name)) {
                                throw InsightException.unknownAttribute(name);
                            }
                            value = IOP.readArrayElement(keyAndValue, 1);
                        } catch (InvalidArrayIndexException ex) {
                            throw CompilerDirectives.shouldNotReachHere(ex);
                        }
                        if (!IOP.isNull(value)) {
                            map.put(name, value);
                        }
                    } catch (StopIterationException ex) {
                        break;
                    }
                }
            } catch (UnsupportedMessageException ex) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }

        @Override
        Object get(String propertyName) {
            return map.get(propertyName);
        }
    }

    private static class MembersObjectReader extends ObjectReader {

        private final Object object;

        MembersObjectReader(Object object) {
            if (!IOP.hasMembers(object)) {
                try {
                    throw new IllegalArgumentException("Config object " + IOP.asString(IOP.toDisplayString(object)) + " does not have members.");
                } catch (UnsupportedMessageException ex) {
                    throw CompilerDirectives.shouldNotReachHere();
                }
            }
            this.object = object;
        }

        @Override
        Object get(String propertyName) {
            Object value;
            try {
                value = IOP.readMember(object, propertyName);
                if (value != null && IOP.isNull(value)) {
                    value = null;
                }
            } catch (UnknownIdentifierException ex) {
                value = null;
            } catch (UnsupportedMessageException ex) {
                throw CompilerDirectives.shouldNotReachHere();
            }
            return value;
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
