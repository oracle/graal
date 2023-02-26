/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.tools.insight.heap.instrument;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.profiles.BranchProfile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.graalvm.collections.EconomicMap;
import org.graalvm.tools.insight.heap.HeapDump;
import static org.graalvm.tools.insight.heap.instrument.HeapGenerator.asIntOrNull;
import static org.graalvm.tools.insight.heap.instrument.HeapGenerator.asStringOrNull;

/**
 * Dump of heap memory. This object accumulates memory dump events.
 * <p>
 * This class is not thread safe.
 */
@SuppressWarnings({"static-method"})
@ExportLibrary(InteropLibrary.class)
final class MemoryDump implements TruffleObject {

    static final String FORMAT = "format";
    static final String DEPTH = "depth";
    static final String EVENTS = "events";
    static final String STACK = "stack";
    private static final String FORMAT_VERSION = "1.0";

    private static final InteropLibrary iop = InteropLibrary.getUncached();
    private static final MembersArray MEMBERS = new MembersArray(FORMAT, DEPTH, EVENTS);

    private final int limit;
    private final CacheReplacement replacement;
    private final Supplier<HeapDump.Builder> heapDumpBuilder;
    private int maxDepth = 0;
    private final List<Object> events;
    private final WeakIdentityHashMap<Object, ObjectCopy> objectCache = new WeakIdentityHashMap<>();
    private final Map<String, MetaObjectCopy> metaObjectCache = new HashMap<>();

    MemoryDump(int limit, CacheReplacement replacement, Supplier<HeapDump.Builder> heapDumpBuilder) {
        assert replacement != null;
        assert heapDumpBuilder != null;
        this.limit = limit;
        this.replacement = replacement;
        this.heapDumpBuilder = heapDumpBuilder;
        if (replacement == CacheReplacement.LRU) {
            this.events = new LinkedList<>();
        } else {
            this.events = new ArrayList<>();
        }
    }

    @ExportMessage
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    boolean isMemberReadable(String member) {
        switch (member) {
            case FORMAT:
            case DEPTH:
            case EVENTS:
                return true;
            default:
                return false;
        }
    }

    @ExportMessage
    Object readMember(String name) throws UnknownIdentifierException {
        switch (name) {
            case FORMAT:
                return FORMAT_VERSION;
            case DEPTH:
                return maxDepth;
            case EVENTS:
                return new ListArray(events);
            default:
                throw UnknownIdentifierException.create(name);
        }
    }

    @ExportMessage
    Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        return MEMBERS;
    }

    /**
     * Add a dump to this one. Events contained in the provided dump are appended to this dump.
     *
     * @param dump an array with the memory dump. Format 1.0 expects a dump object as the first
     *            item.
     */
    void addDump(Object[] dump) throws UnsupportedTypeException, UnsupportedMessageException {
        HeapGenerator.DumpData dumpData = HeapGenerator.getDumpData(dump);
        this.maxDepth = Math.max(this.maxDepth, dumpData.getDepth());
        long eventCount = iop.getArraySize(dumpData.getEvents());
        for (int i = 0; i < eventCount; i++) {
            Object event = InteropUtils.readArrayElement(dumpData.getEvents(), i, "event");
            // We need to copy the event data as a dump to this MemoryDump object
            Object eventStack = HeapGenerator.readMember(iop, event, STACK);
            Integer depthOrNull = asIntOrNull(iop, event, DEPTH);
            int eventDepth = depthOrNull != null ? depthOrNull : dumpData.getDepth();
            StackCopier copier = new StackCopier();
            Object stack = copier.copyStack(eventStack, eventDepth);
            this.events.add(new Event(stack));
            if (limit >= 0 && events.size() > limit) {
                if (replacement == CacheReplacement.FLUSH) {
                    flush();
                    maxDepth = dumpData.getDepth();
                } else {
                    assert replacement == CacheReplacement.LRU : replacement;
                    events.remove(0);
                }
            }
        }
    }

    int eventCount() {
        return events.size();
    }

    void clear() {
        events.clear();
    }

    void flush() throws UnsupportedTypeException, UnsupportedMessageException {
        HeapGenerator heap = new HeapGenerator(heapDumpBuilder.get());
        heap.dump(new Object[]{this});
        events.clear();
    }

    private final class StackCopier {

        private final Map<Object, Object> duplicates = new IdentityHashMap<>();

        Object copyStack(Object eventStack, int eventDepth) {
            if (!iop.hasArrayElements(eventStack)) {
                throw new HeapException("The `" + STACK + "` must be an array");
            }
            long count;
            try {
                count = iop.getArraySize(eventStack);
            } catch (UnsupportedMessageException ex) {
                throw CompilerDirectives.shouldNotReachHere("Can not read size of a stack array");
            }
            StackTraceElement[] elements = new StackTraceElement[(int) count];
            int j = 0;
            for (int i = 0; i < count; i++) {
                Object item = InteropUtils.readArrayElement(eventStack, i, "event stack");
                Location at = new Location(HeapGenerator.readMember(iop, item, "at"));
                Object frame = HeapGenerator.readMember(iop, item, "frame");
                // `frame` contains variables as members,
                // variable values are copied with depth = `eventDepth`,
                // hence total depth is `eventDepth + 1`.
                Object frameCopy = copyObject(frame, eventDepth + 1);
                if (frameCopy == null) { // skipped
                    continue;
                }
                elements[j++] = new StackTraceElement(at, frameCopy);
            }
            if (j != elements.length) {
                elements = Arrays.copyOf(elements, j);
            }
            return new ArrayObject(elements);
        }

        private Object copyObject(Object obj, int depth) {
            Object preferredValue = preferredValueOf(obj);
            if (preferredValue != null) {
                return preferredValue;
            }
            if (iop.isExecutable(obj)) {
                // Executables not supported in format 1.0
                return null;
            }
            if (iop.hasMembers(obj)) {
                if (depth <= 0) {
                    return Unreachable.INSTANCE;
                }
                Object dupl = duplicates.get(obj);
                if (dupl != null) {
                    return dupl;
                }
                ObjectCopy newObj = new ObjectCopy();
                duplicates.put(obj, newObj);
                ObjectCopy prevObj = objectCache.get(obj);
                boolean same = prevObj != null;
                Object members;
                try {
                    members = iop.getMembers(obj);
                } catch (UnsupportedMessageException ex) {
                    throw CompilerDirectives.shouldNotReachHere(obj.getClass().getName());
                }
                long count;
                try {
                    count = iop.getArraySize(members);
                } catch (UnsupportedMessageException ex) {
                    throw CompilerDirectives.shouldNotReachHere(members.getClass().getName());
                }
                for (int i = 0; i < count; i++) {
                    Object member = InteropUtils.readArrayElement(members, i, "member");
                    String name;
                    try {
                        name = iop.asString(member);
                    } catch (UnsupportedMessageException ex) {
                        throw new HeapException("Member must be a string.");
                    }
                    if (iop.isMemberReadable(obj, name) && !iop.hasMemberReadSideEffects(obj, name)) {
                        Object value;
                        try {
                            value = iop.readMember(obj, name);
                        } catch (UnsupportedMessageException | UnknownIdentifierException ex) {
                            throw new HeapException("Can not read member " + name);
                        }
                        Object newValue = copyObject(value, depth - 1);
                        if (newValue == null) { // skipped
                            continue;
                        }
                        if (!(newValue instanceof ObjectCopy) || ((ObjectCopy) newValue).isFinished()) {
                            // If the copy of the value is unfinished,
                            // then the same object will be copied to the same value.
                            // Only otherwise we need to check:
                            if (same && prevObj.members.get(name) != newValue) {
                                same = false;
                            }
                        }
                        newObj.addMember(name, newValue);
                    }
                }
                if (same && prevObj.members.size() == newObj.members.size()) {
                    duplicates.put(obj, prevObj);
                    return prevObj;
                } else {
                    newObj.setMetaObject(copyMetaObject(obj));
                    newObj.setFinished();
                    objectCache.put(obj, newObj);
                    return newObj;
                }
            } else {
                return obj;
            }
        }

        MetaObjectCopy copyMetaObject(Object obj) {
            if (!iop.hasMetaObject(obj)) {
                return null;
            }
            try {
                Object metaObj = iop.getMetaObject(obj);
                String qualifiedName = iop.asString(iop.getMetaQualifiedName(metaObj));
                MetaObjectCopy metaCopy = metaObjectCache.get(qualifiedName);
                if (metaCopy == null) {
                    String simpleName = iop.asString(iop.getMetaSimpleName(metaObj));
                    metaCopy = new MetaObjectCopy(simpleName, qualifiedName);
                }
                return metaCopy;
            } catch (UnsupportedMessageException ex) {
                return null;
            }
        }

        private Object preferredValueOf(Object obj) {
            if (iop.isNull(obj)) {
                return obj;
            }
            if (obj instanceof Boolean ||
                            obj instanceof Byte ||
                            obj instanceof Character ||
                            obj instanceof Short ||
                            obj instanceof Integer ||
                            obj instanceof Long ||
                            obj instanceof Float ||
                            obj instanceof Double ||
                            obj instanceof CharSequence) {
                return obj;
            }
            return null;
        }

    }

    @ExportLibrary(InteropLibrary.class)
    static final class ListArray implements TruffleObject {

        private final List<?> list;

        private ListArray(List<?> list) {
            this.list = list;
        }

        @ExportMessage
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        @TruffleBoundary
        long getArraySize() {
            return list.size();
        }

        @ExportMessage
        @TruffleBoundary
        boolean isArrayElementReadable(long index) {
            return 0 <= index && index < list.size();
        }

        @ExportMessage
        @TruffleBoundary
        Object readArrayElement(long index) throws InvalidArrayIndexException {
            if (!isArrayElementReadable(index)) {
                throw InvalidArrayIndexException.create(index);
            }
            return list.get((int) index);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class MembersArray implements TruffleObject {

        @CompilationFinal(dimensions = 1) private final String[] members;

        private MembersArray(String... members) {
            this.members = members;
        }

        @ExportMessage
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        long getArraySize() {
            return members.length;
        }

        @ExportMessage
        boolean isArrayElementReadable(long index) {
            return 0 <= index && index < members.length;
        }

        @ExportMessage
        Object readArrayElement(long index, @Cached BranchProfile exception) throws InvalidArrayIndexException {
            if (!isArrayElementReadable(index)) {
                exception.enter();
                throw InvalidArrayIndexException.create(index);
            }
            return members[(int) index];
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class Event implements TruffleObject {

        private static final MembersArray MEMBERS = new MembersArray(STACK);

        private final Object stack;

        Event(Object stack) {
            this.stack = stack;
        }

        @ExportMessage
        boolean hasMembers() {
            return true;
        }

        @ExportMessage
        Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
            return MEMBERS;
        }

        @ExportMessage
        boolean isMemberReadable(String member) {
            return member.equals(STACK);
        }

        @ExportMessage
        Object readMember(String name) throws UnknownIdentifierException {
            if (name.equals(STACK)) {
                return stack;
            }
            throw UnknownIdentifierException.create(name);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class Location implements TruffleObject {

        private static final String PROP_NAME = "name";
        private static final String PROP_SOURCE = "source";
        private static final String PROP_LINE = "line";
        private static final String PROP_COLUMN = "column";
        private static final String PROP_CHAR_INDEX = "charIndex";
        private static final String PROP_CHAR_LENGTH = "charLength";

        private static final MembersArray MEMBERS = new MembersArray(PROP_NAME, PROP_SOURCE, PROP_LINE, PROP_COLUMN, PROP_CHAR_INDEX, PROP_CHAR_LENGTH);

        private final String name;
        private final Object source;
        private final Integer line;
        private final Integer column;
        private final Integer charIndex;
        private final Integer charLength;

        private Location(Object at) {
            name = asStringOrNull(iop, at, PROP_NAME);
            source = HeapGenerator.readMember(iop, at, PROP_SOURCE);
            line = asIntOrNull(iop, at, PROP_LINE);
            column = asIntOrNull(iop, at, PROP_COLUMN);
            charIndex = asIntOrNull(iop, at, PROP_CHAR_INDEX);
            charLength = asIntOrNull(iop, at, PROP_CHAR_LENGTH);
        }

        @ExportMessage
        boolean hasMembers() {
            return true;
        }

        @ExportMessage
        Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
            return MEMBERS;
        }

        @ExportMessage
        boolean isMemberReadable(String member) {
            switch (member) {
                case PROP_NAME:
                    return name != null;
                case PROP_SOURCE:
                    return true;
                case PROP_LINE:
                    return line != null;
                case PROP_COLUMN:
                    return column != null;
                case PROP_CHAR_INDEX:
                    return charIndex != null;
                case PROP_CHAR_LENGTH:
                    return charLength != null;
                default:
                    return false;
            }
        }

        @ExportMessage
        Object readMember(String member) throws UnknownIdentifierException {
            switch (member) {
                case PROP_NAME:
                    if (name != null) {
                        return name;
                    }
                    break;
                case PROP_SOURCE:
                    return source;
                case PROP_LINE:
                    if (line != null) {
                        return line;
                    }
                    break;
                case PROP_COLUMN:
                    if (column != null) {
                        return column;
                    }
                    break;
                case PROP_CHAR_INDEX:
                    if (charIndex != null) {
                        return charIndex;
                    }
                    break;
                case PROP_CHAR_LENGTH:
                    if (charLength != null) {
                        return charLength;
                    }
                    break;
            }
            throw UnknownIdentifierException.create(member);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class StackTraceElement implements TruffleObject {

        private static final String PROP_AT = "at";
        private static final String PROP_FRAME = "frame";

        private static final MembersArray MEMBERS = new MembersArray(PROP_AT, PROP_FRAME);

        private final Location at;
        private final Object frame;

        private StackTraceElement(Location at, Object frame) {
            this.at = at;
            this.frame = frame;
        }

        @ExportMessage
        boolean hasMembers() {
            return true;
        }

        @ExportMessage
        Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
            return MEMBERS;
        }

        @ExportMessage
        boolean isMemberReadable(String member) {
            switch (member) {
                case PROP_AT:
                case PROP_FRAME:
                    return true;
                default:
                    return false;
            }
        }

        @ExportMessage
        Object readMember(String name) throws UnknownIdentifierException {
            switch (name) {
                case PROP_AT:
                    return at;
                case PROP_FRAME:
                    return frame;
                default:
                    throw UnknownIdentifierException.create(name);
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class ArrayObject implements TruffleObject {

        @CompilationFinal(dimensions = 1) private final Object[] array;

        private ArrayObject(Object[] array) {
            this.array = array;
        }

        @ExportMessage
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        long getArraySize() {
            return array.length;
        }

        @ExportMessage
        boolean isArrayElementReadable(long index) {
            return 0 <= index && index < array.length;
        }

        @ExportMessage
        Object readArrayElement(long index) throws InvalidArrayIndexException {
            if (!isArrayElementReadable(index)) {
                throw InvalidArrayIndexException.create(index);
            }
            return array[(int) index];
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class ObjectCopy implements TruffleObject {

        private final EconomicMap<String, Object> members = EconomicMap.create();
        private MetaObjectCopy metaObject;
        private boolean finished = false;

        void addMember(String name, Object value) {
            assert !finished;
            members.put(name, value);
        }

        void setMetaObject(MetaObjectCopy metaObject) {
            this.metaObject = metaObject;
        }

        boolean isFinished() {
            return finished;
        }

        void setFinished() {
            this.finished = true;
        }

        @ExportMessage
        boolean hasMembers() {
            return true;
        }

        @TruffleBoundary
        @ExportMessage
        Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
            assert isFinished();
            String[] names = new String[members.size()];
            int i = 0;
            for (String name : members.getKeys()) {
                names[i++] = name;
            }
            return new MembersArray(names);
        }

        @TruffleBoundary
        @ExportMessage
        boolean isMemberReadable(String member) {
            return members.containsKey(member);
        }

        @TruffleBoundary
        @ExportMessage
        Object readMember(String name) throws UnknownIdentifierException {
            Object value = members.get(name);
            if (value != null) {
                return value;
            } else {
                throw UnknownIdentifierException.create(name);
            }
        }

        @ExportMessage
        boolean hasMetaObject() {
            return metaObject != null;
        }

        @ExportMessage
        Object getMetaObject() throws UnsupportedMessageException {
            if (metaObject != null) {
                return metaObject;
            }
            throw UnsupportedMessageException.create();
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class MetaObjectCopy implements TruffleObject {

        private final String metaSimpleName;
        private final String metaQualifiedName;

        MetaObjectCopy(String metaSimpleName, String metaQualifiedName) {
            this.metaSimpleName = metaSimpleName;
            this.metaQualifiedName = metaQualifiedName;
        }

        @ExportMessage
        boolean isMetaObject() {
            return true;
        }

        @ExportMessage
        Object getMetaSimpleName() {
            return metaSimpleName;
        }

        @ExportMessage
        Object getMetaQualifiedName() {
            return metaQualifiedName;
        }

        @ExportMessage
        boolean isMetaInstance(Object instance) {
            return instance instanceof ObjectCopy && ((ObjectCopy) instance).metaObject == this;
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class Unreachable implements TruffleObject {

        static final Unreachable INSTANCE = new Unreachable();
        private static final String UNREACHABLE = "<unreachable>";
        private static final MembersArray MEMBERS = new MembersArray(UNREACHABLE);

        @ExportMessage
        boolean hasMembers() {
            return true;
        }

        @ExportMessage
        Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
            return MEMBERS;
        }

        @ExportMessage
        boolean isMemberReadable(String member) {
            return UNREACHABLE.equals(member);
        }

        @ExportMessage
        Object readMember(String name) throws UnknownIdentifierException {
            if (UNREACHABLE.equals(name)) {
                return Boolean.TRUE;
            } else {
                throw UnknownIdentifierException.create(name);
            }
        }

    }
}
