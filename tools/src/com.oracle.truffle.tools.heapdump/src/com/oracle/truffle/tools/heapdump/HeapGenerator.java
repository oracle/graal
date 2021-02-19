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
package com.oracle.truffle.tools.heapdump;

import com.oracle.truffle.tools.heapdump.HeapUtils.HprofGenerator.ClassInstance;
import com.oracle.truffle.tools.heapdump.HeapUtils.HprofGenerator.HeapDump;
import com.oracle.truffle.tools.heapdump.HeapUtils.HprofGenerator.HeapDump.InstanceBuilder;
import com.oracle.truffle.tools.heapdump.HeapUtils.HprofGenerator.HeapDump.ThreadBuilder;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import java.io.IOException;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import org.graalvm.tools.insight.Insight;

final class HeapGenerator {
    private final HeapUtils.HprofGenerator generator;
    private final Map<TreeSet<String>, ClassInstance> classes = new HashMap<>();
    private final Map<String, ClassInstance> languages = new HashMap<>();
    private final Map<Object, Integer> objects = new IdentityHashMap<>();
    private final Map<SourceKey, Integer> sources = new HashMap<>();
    private final Map<SourceSectionKey, Integer> sourceSections = new HashMap<>();
    private final LinkedList<Dump> pending = new LinkedList<>();
    private ClassInstance sourceSectionClass;
    private Integer unreachable;
    private int frames;
    private ClassInstance keyClass;
    private ClassInstance sourceClass;

    HeapGenerator(HeapUtils.HprofGenerator generator) {
        this.generator = generator;
    }

    void dump(Object[] args) throws UnsupportedTypeException, UnsupportedMessageException {
        try {
            InteropLibrary iop = InteropLibrary.getUncached();
            if (args.length < 1 || !iop.hasArrayElements(args[0]) || (args.length > 1 && !iop.fitsInInt(args[1]))) {
                final String errMessage = "Use as record(obj: [], depth?: number)";
                throw UnsupportedTypeException.create(args, errMessage, new HeapException(errMessage));
            }
            Object events = args[0];
            int depth = args.length == 2 ? iop.asInt(args[1]) : Integer.MAX_VALUE;
            long eventCount = iop.getArraySize(events);
            generator.dumpHeap((data) -> {
                try {
                    for (long i = 0; i < eventCount; i++) {
                        Object ithEvent = iop.readArrayElement(events, i);
                        Object stack = readMember(iop, ithEvent, "stack");
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
                } catch (InteropException ex) {
                    throw new HeapException(ex);
                }
            });
        } catch (IOException ex) {
            throw new HeapException(ex);
        }
    }

    private static String asStringOrNull(InteropLibrary iop, Object from, String key) throws UnsupportedMessageException {
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
            return iop.asString(value);
        } else {
            return null;
        }
    }

    private static Integer asIntOrNull(InteropLibrary iop, Object from, String key) throws UnsupportedMessageException {
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
            return iop.asInt(value);
        } else {
            return null;
        }
    }

    private static Object readMember(InteropLibrary iop, Object obj, String member) {
        String errMsg;
        try {
            Object value = iop.readMember(obj, member);
            if (!iop.isNull(value)) {
                return value;
            }
            errMsg = "'" + member + "' should be defined";
        } catch (UnsupportedMessageException | UnknownIdentifierException ex) {
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
                ClassInstance unreachClass = seg.newClass("unreachable").addField("<unreachable>", boolean.class).dumpClass();
                unreachable = seg.newInstance(unreachClass).put("<unreachable>", true).dumpInstance();
            }

            if (threadBuilder == null) {
                threadBuilder = seg.newThread(rootName + "#" + ++frames);
            }

            int sourceId = dumpSource(iop, seg, source);
            int sectionId = dumpSourceSection(seg, sourceId, charIndex, charLength);
            int localFrame = dumpObject(iop, seg, "frame:" + rootName, frame, depth);
            threadBuilder.addStackFrame(language, rootName, srcName, line == null ? -1 : line, localFrame, sectionId);
        }
        if (threadBuilder != null) {
            threadBuilder.dumpThread();
        }
    }

    private ClassInstance findLanguage(HeapDump seg, String language) throws IOException {
        ClassInstance l = this.languages.get(language);
        if (l == null) {
            l = seg.newClass("lang:" + language).dumpClass();
            this.languages.put(language, l);
        }
        return l;
    }

    private int dumpObject(InteropLibrary iop, HeapDump seg, String metaName, Object obj, int depth)
                    throws IOException {
        Integer id = objects.get(obj);
        if (id != null) {
            return id;
        }
        if (depth <= 0) {
            return unreachable;
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
        ClassInstance clazz = findClass(iop, seg, metaName, obj);
        if (clazz == null) {
            return unreachable;
        }
        InstanceBuilder builder = seg.newInstance(clazz);
        objects.put(obj, builder.id());
        pending.add(() -> {
            for (String n : clazz.names()) {
                final Object v = iop.readMember(obj, n);
                int vId = dumpObject(iop, seg, null, v, depth - 1);
                builder.put(n, vId);
            }
            builder.dumpInstance();
        });
        return builder.id();
    }

    ClassInstance findClass(InteropLibrary iop, HeapDump seg, String metaHint, Object obj) throws IOException {
        TreeSet<String> sortedNames = new TreeSet<>();
        try {
            Object names = iop.getMembers(obj);
            long len = iop.getArraySize(names);
            for (long i = 0; i < len; i++) {
                sortedNames.add(iop.readArrayElement(names, i).toString());
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
                builder.addField(n, Object.class);
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
    private int dumpSourceSection(HeapDump seg, int sourceId, Integer charIndex, Integer charLength) throws IOException {
        if (sourceSectionClass == null) {
            sourceSectionClass = seg.newClass("com.oracle.truffle.api.source.SourceSection").addField("source", Object.class).addField("charIndex", int.class).addField("charLength",
                            int.class).dumpClass();
        }
        SourceSectionKey key = new SourceSectionKey(sourceId, charIndex == null ? -1 : charIndex, charLength == null ? -1 : charLength);
        Integer id = sourceSections.get(key);
        if (id == null) {
            id = seg.newInstance(sourceSectionClass).put("source", sourceId).put("charIndex", charIndex).put("charLength", charLength).dumpInstance();
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
     * @return instance id of the dumped object representing the source
     * @throws IOException when I/O fails
     * @throws UnsupportedMessageException for example if the source object isn't properly
     *             represented
     */
    private int dumpSource(InteropLibrary iop, HeapDump seg, Object source) throws IOException, UnsupportedMessageException {
        String srcName = asStringOrNull(iop, source, "name");
        String mimeType = asStringOrNull(iop, source, "mimeType");
        String uri = asStringOrNull(iop, source, "uri");
        String characters = asStringOrNull(iop, source, "characters");

        SourceKey key = new SourceKey(srcName, uri, mimeType, characters);
        Integer prevId = sources.get(key);
        if (prevId != null) {
            return prevId;
        }

        if (sourceClass == null) {
            keyClass = seg.newClass("com.oracle.truffle.api.source.SourceImpl$Key").addField("uri", String.class).addField("content", String.class).addField("mimeType", String.class).addField("name",
                            String.class).dumpClass();
            sourceClass = seg.newClass("com.oracle.truffle.api.source.Source").addField("key", Object.class).dumpClass();
        }

        int keyId = seg.newInstance(keyClass).put("uri", seg.dumpString(uri)).put("mimeType", seg.dumpString(mimeType)).put("content", seg.dumpString(characters)).put("name",
                        seg.dumpString(srcName)).dumpInstance();
        int srcId = seg.newInstance(sourceClass).put("key", keyId).dumpInstance();
        sources.put(key, srcId);
        return srcId;
    }

    private interface Dump {
        void dump() throws UnknownIdentifierException, IOException, UnsupportedMessageException;
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
        private final int sourceId;
        private final int charIndex;
        private final int charLength;

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 53 * hash + this.sourceId;
            hash = 53 * hash + this.charIndex;
            hash = 53 * hash + this.charLength;
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
            if (this.sourceId != other.sourceId) {
                return false;
            }
            if (this.charIndex != other.charIndex) {
                return false;
            }
            return this.charLength == other.charLength;
        }

        private SourceSectionKey(int sourceId, int charIndex, int charLength) {
            this.sourceId = sourceId;
            this.charIndex = charIndex;
            this.charLength = charLength;
        }

    }
}
