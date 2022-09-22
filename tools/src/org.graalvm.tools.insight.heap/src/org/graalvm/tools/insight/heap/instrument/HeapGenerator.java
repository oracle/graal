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
package org.graalvm.tools.insight.heap.instrument;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import org.graalvm.tools.insight.heap.HeapDump.InstanceBuilder;
import org.graalvm.tools.insight.heap.HeapDump.ThreadBuilder;
import org.graalvm.tools.insight.heap.HeapDump;
import org.graalvm.tools.insight.heap.HeapDump.ClassInstance;
import org.graalvm.tools.insight.heap.HeapDump.ObjectInstance;
import java.io.IOException;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import org.graalvm.tools.insight.Insight;
import org.graalvm.tools.insight.heap.HeapDump.ArrayBuilder;

final class HeapGenerator {
    private final HeapDump.Builder generator;
    private final Map<TreeSet<String>, ClassInstance> classes = new HashMap<>();
    private final Map<String, ClassInstance> languages = new HashMap<>();
    private final Map<Object, ObjectInstance> objects = new IdentityHashMap<>();
    private final Map<SourceKey, ObjectInstance> sources = new HashMap<>();
    private final Map<SourceSectionKey, ObjectInstance> sourceSections = new HashMap<>();
    private final LinkedList<Dump> pending = new LinkedList<>();
    private ClassInstance sourceSectionClass;
    private ObjectInstance unreachable;
    private int frames;
    private ClassInstance keyClass;
    private ClassInstance sourceClass;

    HeapGenerator(HeapDump.Builder generator) {
        this.generator = generator;
    }

    void dump(Object[] args) throws UnsupportedTypeException, UnsupportedMessageException {
        DumpData dumpData = getDumpData(args);
        InteropLibrary iop = InteropLibrary.getUncached();
        try {
            Object events = dumpData.getEvents();
            int defaultDepth = dumpData.getDepth();

            long eventCount = iop.getArraySize(events);
            generator.dumpHeap((data) -> {
                try {
                    for (long i = 0; i < eventCount; i++) {
                        Object ithEvent = iop.readArrayElement(events, i);
                        Object stack = readMember(iop, ithEvent, "stack");
                        Integer ithDepthOrNull = asIntOrNull(iop, ithEvent, "depth");
                        int depth = ithDepthOrNull != null ? ithDepthOrNull : defaultDepth;
                        if (iop.hasArrayElements(stack)) {
                            dumpStack(data, iop, stack, depth);
                        } else {
                            throw new HeapException("'stack' shall be an array");
                        }
                    }
                    while (!pending.isEmpty()) {
                        Dump d = pending.removeFirst();
                        d.dump();
                    }
                } catch (IOException | InteropException ex) {
                    throw new HeapException(ex);
                }
            });
        } catch (IOException ex) {
            throw new HeapException(ex);
        }
    }

    private static Object checkDumpParameter(Object[] args, InteropLibrary iop) throws UnsupportedTypeException {
        // argument check
        final String errMessage = "Use as dump({ format: '', events: []})";
        if (args.length != 1) {
            throw UnsupportedTypeException.create(args, errMessage, new HeapException(errMessage));
        }
        Object dump = args[0];
        if (iop.isExecutable(dump)) {
            // we don't want executable objects in format 1.0
            throw UnsupportedTypeException.create(args, errMessage, new HeapException(errMessage));
        }
        return dump;
    }

    static DumpData getDumpData(Object[] args) throws UnsupportedTypeException, UnsupportedMessageException {
        InteropLibrary iop = InteropLibrary.getUncached();
        Object dump = checkDumpParameter(args, iop);

        // format check
        final String errMessage = "Use as dump({ format: '1.0', events: []})";

        Object format = readMember(iop, dump, "format");
        if (!iop.isString(format) || !"1.0".equals(iop.asString(format))) {
            throw UnsupportedTypeException.create(args, errMessage, new HeapException(errMessage));
        }

        Object events = readMember(iop, dump, "events");
        if (!iop.hasArrayElements(events)) {
            throw UnsupportedTypeException.create(args, errMessage, new HeapException(errMessage));
        }

        Integer depthOrNull = asIntOrNull(iop, dump, "depth");
        int defaultDepth = depthOrNull != null ? depthOrNull : Integer.MAX_VALUE;
        return new DumpData(format, events, defaultDepth);
    }

    static String asStringOrNull(InteropLibrary iop, Object from, String key) {
        Object value;
        if (key != null) {
            try {
                value = iop.readMember(from, key);
            } catch (UnknownIdentifierException | UnsupportedMessageException ex) {
                return null;
            }
        } else {
            value = from;
        }

        if (iop.isString(value)) {
            try {
                return iop.asString(value);
            } catch (UnsupportedMessageException ex) {
                throw CompilerDirectives.shouldNotReachHere(ex);
            }
        } else {
            return null;
        }
    }

    static Integer asIntOrNull(InteropLibrary iop, Object from, String key) {
        Object value;
        if (key != null) {
            try {
                value = iop.readMember(from, key);
            } catch (UnknownIdentifierException | UnsupportedMessageException ex) {
                return null;
            }
        } else {
            value = from;
        }

        if (iop.fitsInInt(value)) {
            try {
                return iop.asInt(value);
            } catch (UnsupportedMessageException ex) {
                throw CompilerDirectives.shouldNotReachHere(ex);
            }
        } else {
            return null;
        }
    }

    static Object readMember(InteropLibrary iop, Object obj, String member) {
        return readMember(iop, obj, member, (id) -> id);
    }

    private interface Convert<T> {
        T convert(Object obj) throws UnsupportedTypeException, UnsupportedMessageException;
    }

    private static <T> T readMember(InteropLibrary iop, Object obj, String member, Convert<T> convert) {
        String errMsg;
        try {
            Object value = iop.readMember(obj, member);
            if (!iop.isNull(value)) {
                return convert.convert(value);
            }
            errMsg = "'" + member + "' should be defined";
        } catch (UnsupportedTypeException | UnsupportedMessageException | UnknownIdentifierException ex) {
            errMsg = "Cannot find '" + member + "'";
        }
        StringBuilder sb = new StringBuilder(errMsg);
        try {
            Object members = iop.getMembers(obj);
            long count = iop.getArraySize(members);
            sb.append(" among [");
            String sep = "";
            for (long i = 0; i < count; i++) {
                sb.append(sep);
                try {
                    sb.append(iop.readArrayElement(members, i));
                } catch (InvalidArrayIndexException ex) {
                    // ignore
                }
                sep = ", ";
            }
            sb.append("]");
        } catch (UnsupportedMessageException cannotDumpMembers) {
            sb.append(" in ").append(iop.toDisplayString(obj));
        }
        throw new HeapException(sb.toString());
    }

    private void dumpStack(HeapDump seg, InteropLibrary iop, Object stack, int depth) throws IOException, UnsupportedMessageException, InvalidArrayIndexException {
        ThreadBuilder threadBuilder = null;
        long frameCount = iop.getArraySize(stack);
        for (long i = 0; i < frameCount; i++) {
            Object stackTraceElement = iop.readArrayElement(stack, i);
            Object at = readMember(iop, stackTraceElement, "at");
            Object frame = readMember(iop, stackTraceElement, "frame");

            String rootName = asStringOrNull(iop, at, "name");
            Object source = readMember(iop, at, "source");
            ClassInstance language = findLanguage(seg, asStringOrNull(iop, source, "language"));
            String srcName = asStringOrNull(iop, source, "name");
            Integer line = asIntOrNull(iop, at, "line");
            Integer charIndex = asIntOrNull(iop, at, "charIndex");
            Integer charLength = asIntOrNull(iop, at, "charLength");

            if (unreachable == null) {
                ClassInstance unreachClass = seg.newClass("unreachable").field("<unreachable>", boolean.class).dumpClass();
                unreachable = seg.newInstance(unreachClass).putBoolean("<unreachable>", true).dumpInstance();
            }

            if (threadBuilder == null) {
                threadBuilder = seg.newThread(rootName + "#" + ++frames);
            }

            ObjectInstance sourceId = dumpSource(iop, seg, source);
            ObjectInstance sectionId = dumpSourceSection(seg, sourceId, charIndex, charLength);
            // The depth is applied to the variables of the frame's object,
            // hence the frame object is dumped with depth + 1
            ObjectInstance localFrame = dumpObject(iop, seg, "frame:" + rootName, frame, depth + 1);
            threadBuilder.addStackFrame(language, rootName, srcName, line == null ? -1 : line, localFrame, sectionId);
        }
        if (threadBuilder != null) {
            threadBuilder.dumpThread();
        }
    }

    private ClassInstance findLanguage(HeapDump seg, String language) {
        ClassInstance l = this.languages.get(language);
        if (l == null) {
            l = seg.newClass("lang:" + language).dumpClass();
            this.languages.put(language, l);
        }
        return l;
    }

    private ObjectInstance dumpObject(InteropLibrary iop, HeapDump seg, String metaName, Object obj, int depth)
                    throws IOException {
        ObjectInstance id = objects.get(obj);
        if (id != null) {
            return id;
        }
        if (iop.isString(obj)) {
            try {
                return seg.dumpString(iop.asString(obj));
            } catch (UnsupportedMessageException ex) {
                throw new HeapException(ex);
            }
        }
        if (!(obj instanceof TruffleObject)) {
            return seg.dumpPrimitive(obj);
        }
        if (depth <= 0) {
            return unreachable;
        }
        ClassInstance clazz = findClass(iop, seg, metaName, obj);
        if (clazz == null) {
            return unreachable;
        }

        int len = findArrayLength(iop, obj);
        if (len >= 0) {
            ArrayBuilder builder = seg.newArray(len);
            objects.put(obj, builder.id());
            pending.add(() -> {
                for (int i = 0; i < len; i++) {
                    Object v = iop.readArrayElement(obj, i);
                    ObjectInstance vId = dumpObject(iop, seg, null, v, depth - 1);
                    builder.put(i, vId);
                }
                builder.dumpInstance();
            });
            return builder.id();
        } else {
            InstanceBuilder builder = seg.newInstance(clazz);
            objects.put(obj, builder.id());
            pending.add(() -> {
                for (String n : clazz.names()) {
                    final Object v = iop.readMember(obj, n);
                    ObjectInstance vId = dumpObject(iop, seg, null, v, depth - 1);
                    builder.put(n, vId);
                }
                builder.dumpInstance();
            });
            return builder.id();
        }
    }

    private static int findArrayLength(InteropLibrary iop, Object obj) {
        if (!iop.hasArrayElements(obj)) {
            return -1;
        }
        try {
            long len = iop.getArraySize(obj);
            if (len > Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }
            return (int) len;
        } catch (UnsupportedMessageException ex) {
            return -1;
        }
    }

    ClassInstance findClass(InteropLibrary iop, HeapDump seg, String metaHint, Object obj) throws IOException {
        TreeSet<String> sortedNames = new TreeSet<>();
        try {
            Object names = iop.getMembers(obj, true);
            long len = iop.getArraySize(names);
            for (long i = 0; i < len; i++) {
                final String ithName = iop.asString(iop.readArrayElement(names, i));
                if (iop.isMemberReadable(obj, ithName) && !iop.hasMemberReadSideEffects(obj, ithName)) {
                    sortedNames.add(ithName);
                }
            }
        } catch (UnsupportedMessageException ex) {
            // no names
        } catch (InteropException ex) {
            throw new IOException("Object " + obj, ex);
        }

        ClassInstance clazz = classes.get(sortedNames);
        if (clazz == null) {
            String metaName = metaHint;
            if (metaName == null) {
                try {
                    Object meta = iop.getMetaObject(obj);
                    metaName = asStringOrNull(iop, iop.getMetaQualifiedName(meta), null);
                } catch (UnsupportedMessageException unsupportedMessageException) {
                    metaName = "Frame";
                }
            }

            if ("unreachable".equals(metaName) && sortedNames.size() == 1 && "<unreachable>".equals(sortedNames.iterator().next())) {
                return null;
            }

            HeapDump.ClassBuilder builder = seg.newClass(metaName);
            for (String n : sortedNames) {
                builder.field(n, Object.class);
            }
            clazz = builder.dumpClass();
            classes.put(sortedNames, clazz);
        }
        return clazz;
    }

    /**
     * Dumps source section so it looks like {@link SourceSection} in a regular Java Heap Dump.
     *
     * @param seg segment to write heap data to
     * @param sourceId object id of the source the section belongs to
     * @param charIndex 0-based index of initial character of the section in the source
     * @param charLength number of characters in the section
     * @throws IOException when I/O fails
     */
    private ObjectInstance dumpSourceSection(HeapDump seg, ObjectInstance sourceId, Integer charIndex, Integer charLength) throws IOException {
        if (sourceSectionClass == null) {
            sourceSectionClass = seg.newClass("com.oracle.truffle.api.source.SourceSection").field("source", Object.class).field("charIndex", int.class).field("charLength",
                            int.class).dumpClass();
        }
        final int index = charIndex == null ? -1 : charIndex;
        final int length = charLength == null ? -1 : charLength;
        SourceSectionKey key = new SourceSectionKey(sourceId, index, length);
        ObjectInstance id = sourceSections.get(key);
        if (id == null) {
            id = seg.newInstance(sourceSectionClass).put("source", sourceId).putInt("charIndex", index).putInt("charLength", length).dumpInstance();
            sourceSections.put(key, id);
        }
        return id;
    }

    /**
     * Dumps source so it looks like {@link Source} in a regular Java Heap Dump.
     *
     * @param iop interop library to use
     * @param seg segment to write heap data to
     * @param source object representing the {@code SourceInfo} object defined by {@link Insight}
     *            specification
     * @return instance of the dumped object representing the source
     * @throws IOException when I/O fails
     * @throws UnsupportedMessageException for example if the source object isn't properly
     *             represented
     */
    private ObjectInstance dumpSource(InteropLibrary iop, HeapDump seg, Object source) throws IOException, UnsupportedMessageException {
        String srcName = readMember(iop, source, "name", iop::asString);
        String mimeType = asStringOrNull(iop, source, "mimeType");
        String uri = asStringOrNull(iop, source, "uri");
        String characters = asStringOrNull(iop, source, "characters");

        SourceKey key = new SourceKey(srcName, uri, mimeType, characters);
        ObjectInstance prevId = sources.get(key);
        if (prevId != null) {
            return prevId;
        }

        if (sourceClass == null) {
            keyClass = seg.newClass("com.oracle.truffle.api.source.SourceImpl$Key").field("uri", String.class).field("content", String.class).field("mimeType", String.class).field("name",
                            String.class).dumpClass();
            sourceClass = seg.newClass("com.oracle.truffle.api.source.Source").field("key", Object.class).dumpClass();
        }

        ObjectInstance keyId = seg.newInstance(keyClass).put("uri", seg.dumpString(uri)).put("mimeType", seg.dumpString(mimeType)).put("content", seg.dumpString(characters)).put("name",
                        seg.dumpString(srcName)).dumpInstance();
        ObjectInstance srcId = seg.newInstance(sourceClass).put("key", keyId).dumpInstance();
        sources.put(key, srcId);
        return srcId;
    }

    private interface Dump {
        void dump() throws UnknownIdentifierException, IOException, UnsupportedMessageException, InvalidArrayIndexException;
    }

    static final class DumpData {

        private final Object format;
        private final Object events;
        private final int depth;

        DumpData(Object format, Object events, int depth) {
            this.format = format;
            this.events = events;
            this.depth = depth;
        }

        Object getFormat() {
            return format;
        }

        Object getEvents() {
            return events;
        }

        int getDepth() {
            return depth;
        }

    }

    private static final class SourceKey {
        private final String srcName;
        private final String uri;
        private final String mimeType;
        private final String characters;

        SourceKey(String srcName, String uri, String mimeType, String characters) {
            this.srcName = srcName;
            this.uri = uri;
            this.mimeType = mimeType;
            this.characters = characters;
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 79 * hash + Objects.hashCode(this.srcName);
            hash = 79 * hash + Objects.hashCode(this.uri);
            hash = 79 * hash + Objects.hashCode(this.mimeType);
            hash = 79 * hash + Objects.hashCode(this.characters);
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
            final SourceKey other = (SourceKey) obj;
            if (!Objects.equals(this.srcName, other.srcName)) {
                return false;
            }
            if (!Objects.equals(this.uri, other.uri)) {
                return false;
            }
            if (!Objects.equals(this.mimeType, other.mimeType)) {
                return false;
            }
            return Objects.equals(this.characters, other.characters);
        }
    }

    private static final class SourceSectionKey {
        private final ObjectInstance sourceId;
        private final int charIndex;
        private final int charLength;

        private SourceSectionKey(ObjectInstance sourceId, int charIndex, int charLength) {
            this.sourceId = sourceId;
            this.charIndex = charIndex;
            this.charLength = charLength;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 41 * hash + Objects.hashCode(this.sourceId);
            hash = 41 * hash + this.charIndex;
            hash = 41 * hash + this.charLength;
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
            final SourceSectionKey other = (SourceSectionKey) obj;
            if (this.charIndex != other.charIndex) {
                return false;
            }
            if (this.charLength != other.charLength) {
                return false;
            }
            if (!Objects.equals(this.sourceId, other.sourceId)) {
                return false;
            }
            return true;
        }

    }
}
