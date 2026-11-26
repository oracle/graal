/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.pgo;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import org.graalvm.collections.EconomicMap;

import com.oracle.svm.core.pgo.IprofFormat;
import com.oracle.svm.hosted.pgo.phases.PGOApplyProfilesPhase;

import jdk.graal.compiler.util.json.JsonParser;
import jdk.graal.compiler.util.json.JsonParserException;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaUtil;

/**
 * Reader for the iprof files that {@code --pgo-instrument} images write and {@code --pgo} reads
 * (see {@link IprofFormat}). The JSON itself is read by {@link JsonParser}; this class checks the
 * iprof structure and turns it into records. The parser is free of any Native Image singletons or
 * universe state so it can be unit-tested in isolation; method ids are resolved against the
 * {@code HostedUniverse} later (see {@code IprofProfileResolver}).
 * <p>
 * It reads the {@code conditionalProfiles}, {@code callCountProfiles},
 * {@code virtualInvokeProfiles} and {@code samplingProfiles} sections. Other sections, such as
 * {@code monitorProfiles}, are skipped without being validated, because nothing reads them.
 * <p>
 * {@code version}, {@code types} and {@code methods} are required and strictly validated. The
 * profile sections are <em>optional</em> per the iprof schema: an absent section parses as an
 * empty list, and the profile then has no data of that kind. A section that is present must be a
 * JSON array whose entries are structurally valid.
 * <p>
 * Supported schema versions are {@value IprofFormat#VERSION_1_0_0} and
 * {@value IprofFormat#VERSION_1_1_0}; the sections read here are encoded the same way in both. Any
 * other version, a document that is not valid JSON, or any structural violation (missing required
 * section, malformed context string, record array whose length is not a multiple of the record
 * size) is rejected with an {@link IprofFormatException}.
 */
public final class IprofParser {

    /** Longs per {@code virtualInvokeProfiles} record: a receiver type id and its count. */
    public static final int VIRTUAL_INVOKE_RECORD_SIZE = 2;

    /** Signals that an iprof stream is not well-formed or is an unsupported version. */
    public static final class IprofFormatException extends RuntimeException {
        /** The serialization version of this exception class. */
        private static final long serialVersionUID = 1L;

        /**
         * Creates the exception.
         *
         * @param message what is wrong with the stream, naming the section or context when known
         */
        IprofFormatException(String message) {
            super(message);
        }
    }

    /**
     * A single {@code methodId:bci} frame of the calling context of a profile entry.
     *
     * @param methodId the id of the frame's method in the {@code methods} table of the file
     * @param bci the bytecode index in that method: the call site of an outer frame, or the
     *            profiled position of the innermost frame
     */
    public record ContextFrame(int methodId, int bci) {
    }

    /**
     * One parsed {@code conditionalProfiles} entry: the calling context (innermost frame first) and
     * the raw {@code [successorBci, successorIndex, count]} triplets, one per successor of the
     * branch or switch, as {@link PGOApplyProfilesPhase} expects them.
     *
     * @param context the frames of the calling context, innermost first
     * @param records the triplets, {@link PGOApplyProfilesPhase#CONDITIONAL_RECORD_SIZE} longs each
     */
    public record ConditionalEntry(List<ContextFrame> context, long[] records) {
    }

    /**
     * One parsed {@code callCountProfiles} entry: a context-sensitive method-entry count. The
     * innermost frame is the called method at BCI 0.
     *
     * @param context the frames of the calling context, innermost first
     * @param count how often the method was entered in this context
     */
    public record CallCountEntry(List<ContextFrame> context, long count) {
    }

    /**
     * One parsed {@code virtualInvokeProfiles} entry: the calling context of an indirect call and
     * its records as {@code [typeId, count, typeId, count, ...]} receiver-type pairs.
     *
     * @param context the frames of the calling context, innermost first; the innermost BCI is the
     *            call site
     * @param records the pairs, {@link #VIRTUAL_INVOKE_RECORD_SIZE} longs each
     */
    public record VirtualInvokeEntry(List<ContextFrame> context, long[] records) {
    }

    /**
     * One parsed {@code samplingProfiles} entry: a sampled call stack (innermost frame first, the
     * innermost BCI being the sampled position) and how often it was observed.
     *
     * @param context the frames of the sampled stack, innermost first
     * @param count how many samples saw this stack
     */
    public record SamplingEntry(List<ContextFrame> context, long count) {
    }

    /**
     * One entry of the {@code methods} table: the method's id in the file, its name, and the type
     * ids of its declaring class, return type and parameters.
     *
     * @param methodId the id that contexts use for this method
     * @param name the method's name
     * @param declaringTypeId the {@code types} id of the declaring class
     * @param returnTypeId the {@code types} id of the return type
     * @param parameterTypeIds the {@code types} ids of the parameter types, in order
     */
    public record ProfileMethod(int methodId, String name, int declaringTypeId, int returnTypeId, int[] parameterTypeIds) {
    }

    /**
     * Immutable result of parsing the sections of an iprof file that {@code --pgo} reads.
     * <p>
     * The counts of each section add up to at most {@link Long#MAX_VALUE}, a stack sample's count
     * taken once per frame of its stack; the constructor rejects a section whose counts do not. The
     * parser rejects negative counts and the merger only scales and adds them, so every sum that
     * the consumer forms from the counts of one section fits in a long too: the counts of one
     * branch, the branch counts of the contexts that share a prefix, the calls of a method over its
     * contexts, the receivers of a call site. Samples are counted once per frame because
     * {@code SamplingHotness} adds a stack's count to the tree of every method on the stack. A real
     * profile stays far below the limit (2^63 events at 10^9 per second take 292 years), so a
     * section that reaches it comes from a damaged or hand-made file.
     */
    public static final class ParsedIprofProfile {
        /** The iprof schema version, such as {@code 1.1.0}. */
        private final String version;
        /** The {@code types} table: type name by id. */
        private final Map<Integer, String> typeNamesById;
        /** The {@code methods} table: method by id. */
        private final Map<Integer, ProfileMethod> methodsById;
        /** The {@code conditionalProfiles} entries, in file order. */
        private final List<ConditionalEntry> conditionalEntries;
        /** The {@code callCountProfiles} entries, in file order. */
        private final List<CallCountEntry> callCountEntries;
        /** The {@code virtualInvokeProfiles} entries, in file order. */
        private final List<VirtualInvokeEntry> virtualInvokeEntries;
        /** The {@code samplingProfiles} entries, in file order. */
        private final List<SamplingEntry> samplingEntries;

        /**
         * Creates the profile from parsed or merged sections, which it wraps without copying.
         *
         * @param version the iprof schema version
         * @param typeNamesById the {@code types} table
         * @param methodsById the {@code methods} table
         * @param conditionalEntries the {@code conditionalProfiles} entries
         * @param callCountEntries the {@code callCountProfiles} entries
         * @param virtualInvokeEntries the {@code virtualInvokeProfiles} entries
         * @param samplingEntries the {@code samplingProfiles} entries
         * @throws IprofFormatException if the counts of a section add up to more than
         *             {@link Long#MAX_VALUE}, as the class comment describes
         */
        ParsedIprofProfile(String version, Map<Integer, String> typeNamesById, Map<Integer, ProfileMethod> methodsById,
                        List<ConditionalEntry> conditionalEntries,
                        List<CallCountEntry> callCountEntries, List<VirtualInvokeEntry> virtualInvokeEntries, List<SamplingEntry> samplingEntries) {
            checkOverflow(IprofFormat.CONDITIONAL_PROFILES, "", conditionalEntries.stream().flatMapToLong(
                            entry -> counts(entry.records(), PGOApplyProfilesPhase.CONDITIONAL_RECORD_SIZE, PGOApplyProfilesPhase.CONDITIONAL_RECORD_COUNTER_POSITION)));
            checkOverflow(IprofFormat.CALL_COUNT_PROFILES, "", callCountEntries.stream().mapToLong(CallCountEntry::count));
            checkOverflow(IprofFormat.VIRTUAL_INVOKE_PROFILES, "", virtualInvokeEntries.stream().flatMapToLong(entry -> counts(entry.records(), VIRTUAL_INVOKE_RECORD_SIZE, 1)));
            checkOverflow(IprofFormat.SAMPLING_PROFILES, ", each taken once per frame of its stack,",
                            samplingEntries.stream().mapToLong(entry -> Math.multiplyExact(entry.count(), entry.context().size())));
            this.version = version;
            this.typeNamesById = Collections.unmodifiableMap(typeNamesById);
            this.methodsById = Collections.unmodifiableMap(methodsById);
            this.conditionalEntries = Collections.unmodifiableList(conditionalEntries);
            this.callCountEntries = Collections.unmodifiableList(callCountEntries);
            this.virtualInvokeEntries = Collections.unmodifiableList(virtualInvokeEntries);
            this.samplingEntries = Collections.unmodifiableList(samplingEntries);
        }

        /**
         * The iprof schema version, such as {@code 1.1.0}.
         *
         * @return the version
         */
        public String version() {
            return version;
        }

        /**
         * The {@code types} table.
         *
         * @return type name by type id; unmodifiable
         */
        public Map<Integer, String> typeNamesById() {
            return typeNamesById;
        }

        /**
         * The {@code methods} table.
         *
         * @return method by method id; unmodifiable
         */
        public Map<Integer, ProfileMethod> methodsById() {
            return methodsById;
        }

        /**
         * The {@code conditionalProfiles} entries.
         *
         * @return the entries; unmodifiable
         */
        public List<ConditionalEntry> conditionalEntries() {
            return conditionalEntries;
        }

        /**
         * The {@code callCountProfiles} entries.
         *
         * @return the entries; unmodifiable
         */
        public List<CallCountEntry> callCountEntries() {
            return callCountEntries;
        }

        /**
         * The {@code virtualInvokeProfiles} entries.
         *
         * @return the entries; unmodifiable
         */
        public List<VirtualInvokeEntry> virtualInvokeEntries() {
            return virtualInvokeEntries;
        }

        /**
         * The {@code samplingProfiles} entries.
         *
         * @return the entries; unmodifiable
         */
        public List<SamplingEntry> samplingEntries() {
            return samplingEntries;
        }

        /**
         * The counts of {@code records}, which are {@code recordSize} longs each, with the count at
         * {@code countPosition}.
         */
        private static LongStream counts(long[] records, int recordSize, int countPosition) {
            return IntStream.iterate(countPosition, i -> i < records.length, i -> i + recordSize).mapToLong(i -> records[i]);
        }

        /**
         * Rejects the iprof section named {@code section} if {@code counts} overflow a long when
         * added up. {@code how} says how the counts are taken, for the message; it is empty or
         * starts with a comma.
         */
        private static void checkOverflow(String section, String how, LongStream counts) {
            try {
                counts.reduce(0L, Math::addExact);
            } catch (ArithmeticException exception) {
                throw new IprofFormatException("The counts of section '" + section + "'" + how + " overflow a signed 64-bit integer when added up");
            }
        }
    }

    /**
     * Parses the sections of an iprof document that {@code --pgo} reads: {@code version},
     * {@code types}, {@code methods} and the profile sections the class comment lists. Other
     * sections are skipped.
     *
     * @param reader a character stream positioned at the start of the JSON document; closed by the
     *            caller
     * @return the parsed, immutable model
     * @throws IprofFormatException if the stream is not valid JSON, is malformed or is an
     *             unsupported version
     * @throws IOException if reading from {@code reader} fails
     */
    @SuppressWarnings({"unchecked", "static-method"})
    public ParsedIprofProfile parse(Reader reader) throws IOException {
        Object root;
        try {
            root = new JsonParser(reader).parse();
        } catch (JsonParserException e) {
            throw new IprofFormatException("Not valid JSON: " + e.getMessage());
        }
        if (!(root instanceof EconomicMap)) {
            throw new IprofFormatException("Iprof root must be a JSON object");
        }
        EconomicMap<String, Object> top = (EconomicMap<String, Object>) root;

        String version = requireString(top, IprofFormat.VERSION);
        if (!IprofFormat.VERSION_1_0_0.equals(version) && !IprofFormat.VERSION_1_1_0.equals(version)) {
            throw new IprofFormatException("Unsupported iprof version '" + version + "'; supported: " + IprofFormat.VERSION_1_0_0 + ", " + IprofFormat.VERSION_1_1_0);
        }

        Map<Integer, String> typeNamesById = parseTypes(requireList(top, IprofFormat.TYPES));
        Map<Integer, ProfileMethod> methodsById = parseMethods(requireList(top, IprofFormat.METHODS));
        /*
         * The profile sections are optional per the iprof schema: an absent section parses as an
         * empty list. PGOProfileUseFeature warns when the profile has no entries at all, or when no
         * entry of a kind matches the image.
         */
        List<ConditionalEntry> conditionalEntries = optionalConditionalProfiles(top);
        List<CallCountEntry> callCountEntries = optionalCallCountProfiles(top);
        List<VirtualInvokeEntry> virtualInvokeEntries = optionalVirtualInvokeProfiles(top, typeNamesById);
        List<SamplingEntry> samplingEntries = optionalSamplingProfiles(top);

        return new ParsedIprofProfile(version, typeNamesById, methodsById, conditionalEntries, callCountEntries, virtualInvokeEntries, samplingEntries);
    }

    /**
     * Parses the {@code types} section into type names by id. Rejects an empty name and a
     * duplicate id.
     */
    private static Map<Integer, String> parseTypes(List<Object> types) {
        Map<Integer, String> result = new HashMap<>(types.size());
        for (Object element : types) {
            EconomicMap<String, Object> type = asObject(element, IprofFormat.TYPES);
            int id = requireInt(type, IprofFormat.ID);
            String name = requireString(type, IprofFormat.NAME);
            if (name.isEmpty()) {
                throw new IprofFormatException("Empty type name for type id " + id);
            }
            if (result.put(id, name) != null) {
                throw new IprofFormatException("Duplicate type id " + id);
            }
        }
        return result;
    }

    /**
     * Parses the {@code methods} section into methods by id. A signature lists the type ids of
     * the declaring class, the return type and then the parameters. The type ids are not checked
     * against the {@code types} section here; {@code IprofProfileMerger} and
     * {@code IprofProfileResolver} reject or skip undeclared ones. Rejects a duplicate method id.
     */
    private static Map<Integer, ProfileMethod> parseMethods(List<Object> methods) {
        Map<Integer, ProfileMethod> result = new HashMap<>(methods.size());
        for (Object element : methods) {
            EconomicMap<String, Object> method = asObject(element, IprofFormat.METHODS);
            int id = requireInt(method, IprofFormat.ID);
            String name = requireString(method, IprofFormat.NAME);
            List<Object> signature = requireList(method, IprofFormat.SIGNATURE);
            if (signature.size() < 2) {
                throw new IprofFormatException("Method id " + id + " signature must contain at least a declaring type and a return type");
            }
            int declaringTypeId = asInt(signature.get(0), IprofFormat.SIGNATURE);
            int returnTypeId = asInt(signature.get(1), IprofFormat.SIGNATURE);
            int[] parameterTypeIds = new int[signature.size() - 2];
            for (int i = 2; i < signature.size(); i++) {
                parameterTypeIds[i - 2] = asInt(signature.get(i), IprofFormat.SIGNATURE);
            }
            if (result.put(id, new ProfileMethod(id, name, declaringTypeId, returnTypeId, parameterTypeIds)) != null) {
                throw new IprofFormatException("Duplicate method id " + id);
            }
        }
        return result;
    }

    /**
     * Returns the parsed {@code conditionalProfiles} entries, or an empty list when the section is
     * absent. Absence is legal (the category is optional in the iprof schema); presence with a
     * non-array value, or any structurally invalid entry, is still rejected.
     */
    private static List<ConditionalEntry> optionalConditionalProfiles(EconomicMap<String, Object> top) {
        List<Object> conditionalProfiles = optionalList(top, IprofFormat.CONDITIONAL_PROFILES);
        List<ConditionalEntry> result = new ArrayList<>(conditionalProfiles.size());
        for (Object element : conditionalProfiles) {
            EconomicMap<String, Object> entry = asObject(element, IprofFormat.CONDITIONAL_PROFILES);
            String ctx = requireString(entry, IprofFormat.CTX);
            List<Object> rawRecords = requireList(entry, IprofFormat.RECORDS);
            List<ContextFrame> context = parseContext(ctx);
            long[] records = parseConditionalRecords(rawRecords, ctx);
            result.add(new ConditionalEntry(context, records));
        }
        return result;
    }

    /**
     * Returns the parsed {@code callCountProfiles} entries, or an empty list when the section is
     * absent. Each entry has exactly one record, a non-negative count, and a context whose
     * innermost frame is at BCI 0.
     */
    private static List<CallCountEntry> optionalCallCountProfiles(EconomicMap<String, Object> top) {
        List<Object> callCountProfiles = optionalList(top, IprofFormat.CALL_COUNT_PROFILES);
        List<CallCountEntry> result = new ArrayList<>(callCountProfiles.size());
        for (Object element : callCountProfiles) {
            EconomicMap<String, Object> entry = asObject(element, IprofFormat.CALL_COUNT_PROFILES);
            String ctx = requireString(entry, IprofFormat.CTX);
            List<ContextFrame> context = parseContext(ctx);
            List<Object> rawRecords = requireList(entry, IprofFormat.RECORDS);
            if (rawRecords.size() != 1) {
                throw new IprofFormatException("Call count records for context '" + ctx + "' must contain exactly one count");
            }
            long count = asLong(rawRecords.get(0), IprofFormat.RECORDS);
            if (count < 0 || context.getFirst().bci() != 0) {
                throw new IprofFormatException("Call count context '" + ctx + "' must start at method BCI 0 with a non-negative count");
            }
            result.add(new CallCountEntry(context, count));
        }
        return result;
    }

    /**
     * Returns the parsed {@code virtualInvokeProfiles} entries, or an empty list when the section
     * is absent. Records are {@code [typeId, count]} pairs; every type id must be declared in the
     * {@code types} section.
     */
    private static List<VirtualInvokeEntry> optionalVirtualInvokeProfiles(EconomicMap<String, Object> top, Map<Integer, String> typeNamesById) {
        List<Object> virtualInvokeProfiles = optionalList(top, IprofFormat.VIRTUAL_INVOKE_PROFILES);
        List<VirtualInvokeEntry> result = new ArrayList<>(virtualInvokeProfiles.size());
        for (Object element : virtualInvokeProfiles) {
            EconomicMap<String, Object> entry = asObject(element, IprofFormat.VIRTUAL_INVOKE_PROFILES);
            String ctx = requireString(entry, IprofFormat.CTX);
            List<Object> rawRecords = requireList(entry, IprofFormat.RECORDS);
            if (rawRecords.isEmpty() || rawRecords.size() % VIRTUAL_INVOKE_RECORD_SIZE != 0) {
                throw new IprofFormatException("Virtual invoke records length " + rawRecords.size() + " for context '" + ctx + "' is not a positive multiple of " + VIRTUAL_INVOKE_RECORD_SIZE);
            }
            long[] records = new long[rawRecords.size()];
            for (int i = 0; i < rawRecords.size(); i++) {
                records[i] = asLong(rawRecords.get(i), IprofFormat.RECORDS);
            }
            for (int i = 0; i < records.length; i += VIRTUAL_INVOKE_RECORD_SIZE) {
                if (records[i] < 0 || records[i] > Integer.MAX_VALUE || !typeNamesById.containsKey((int) records[i])) {
                    throw new IprofFormatException("Virtual invoke context '" + ctx + "' references undeclared type id " + records[i]);
                }
                int typeId = (int) records[i];
                if (isPrimitiveProfileType(typeNamesById.get(typeId))) {
                    throw new IprofFormatException("Virtual invoke context '" + ctx + "' references primitive receiver type " + typeNamesById.get(typeId));
                }
                if (records[i + 1] < 0) {
                    throw new IprofFormatException("Negative receiver count for virtual invoke context '" + ctx + "'");
                }
            }
            result.add(new VirtualInvokeEntry(parseContext(ctx), records));
        }
        return result;
    }

    /** Whether an iprof type name, such as {@code int}, names a primitive type or {@code void}. */
    private static boolean isPrimitiveProfileType(String typeName) {
        return JavaKind.fromTypeString(MetaUtil.toInternalName(typeName)) != JavaKind.Object;
    }

    /**
     * Returns the parsed {@code samplingProfiles} entries, or an empty list when the section is
     * absent. Each entry has exactly one record, a non-negative sample count.
     */
    private static List<SamplingEntry> optionalSamplingProfiles(EconomicMap<String, Object> top) {
        List<Object> samplingProfiles = optionalList(top, IprofFormat.SAMPLING_PROFILES);
        List<SamplingEntry> result = new ArrayList<>(samplingProfiles.size());
        for (Object element : samplingProfiles) {
            EconomicMap<String, Object> entry = asObject(element, IprofFormat.SAMPLING_PROFILES);
            String ctx = requireString(entry, IprofFormat.CTX);
            List<Object> rawRecords = requireList(entry, IprofFormat.RECORDS);
            if (rawRecords.size() != 1) {
                throw new IprofFormatException("Sampling records for context '" + ctx + "' must contain exactly one count");
            }
            long count = asLong(rawRecords.get(0), IprofFormat.RECORDS);
            if (count < 0) {
                throw new IprofFormatException("Negative sample count for context '" + ctx + "'");
            }
            result.add(new SamplingEntry(parseContext(ctx), count));
        }
        return result;
    }

    /**
     * Splits a context string of the form {@code methodId:bci<methodId:bci<...} into frames,
     * preserving the innermost-first order used by the profile and by
     * {@link PGOApplyProfilesPhase#createPointContext}: {@code 7:12<3:5} gives the frames
     * {@code (7, 12)} and {@code (3, 5)}.
     *
     * @param ctx the context string of a profile entry
     * @return the frames, innermost first
     * @throws IprofFormatException if {@code ctx} is empty or a frame is malformed
     */
    private static List<ContextFrame> parseContext(String ctx) {
        if (ctx.isEmpty()) {
            throw new IprofFormatException("Empty context string");
        }
        List<ContextFrame> frames = new ArrayList<>();
        /* The limit -1 keeps empty frames, such as the one after "7:12<", so that they are rejected. */
        for (String frame : ctx.split(String.valueOf(IprofFormat.FRAME_SEPARATOR), -1)) {
            frames.add(parseFrame(frame, ctx));
        }
        return frames;
    }

    /**
     * Parses one {@code methodId:bci} frame. {@code ctx} is the whole context string, for the
     * error message.
     */
    private static ContextFrame parseFrame(String frame, String ctx) {
        int colon = frame.indexOf(IprofFormat.METHOD_BCI_SEPARATOR);
        if (colon <= 0 || colon == frame.length() - 1) {
            throw new IprofFormatException("Malformed context frame '" + frame + "' in context '" + ctx + "'");
        }
        int methodId = parseNonNegativeInt(frame.substring(0, colon), ctx);
        int bci = parseInt(frame.substring(colon + 1), ctx);
        return new ContextFrame(methodId, bci);
    }

    /**
     * Parses the records of a {@code conditionalProfiles} entry and checks each
     * {@code [successorBci, successorIndex, count]} triplet: the successor BCI fits in an int, the
     * successor index is below the number of triplets, and the count is non-negative. The
     * {@link ParsedIprofProfile} constructor checks that the counts of the whole section fit in a
     * long when added up. {@code ctx} is the entry's context, for the error message.
     */
    private static long[] parseConditionalRecords(List<Object> rawRecords, String ctx) {
        if (rawRecords.isEmpty() || rawRecords.size() % PGOApplyProfilesPhase.CONDITIONAL_RECORD_SIZE != 0) {
            throw new IprofFormatException("Conditional records length " + rawRecords.size() + " for context '" + ctx +
                            "' is not a positive multiple of " + PGOApplyProfilesPhase.CONDITIONAL_RECORD_SIZE);
        }
        int recordCount = rawRecords.size() / PGOApplyProfilesPhase.CONDITIONAL_RECORD_SIZE;
        long[] records = new long[rawRecords.size()];
        for (int i = 0; i < rawRecords.size(); i++) {
            records[i] = asLong(rawRecords.get(i), IprofFormat.RECORDS);
        }
        for (int i = 0; i < records.length; i += PGOApplyProfilesPhase.CONDITIONAL_RECORD_SIZE) {
            long successorBci = records[i];
            long successorIndex = records[i + 1];
            long count = records[i + 2];
            if (successorBci < Integer.MIN_VALUE || successorBci > Integer.MAX_VALUE) {
                throw new IprofFormatException("Conditional context '" + ctx + "' has successor BCI outside the integer range: " + successorBci);
            }
            if (successorIndex < 0 || successorIndex >= recordCount) {
                throw new IprofFormatException("Conditional context '" + ctx + "' has successor index " + successorIndex + " outside [0," + recordCount + ")");
            }
            if (count < 0) {
                throw new IprofFormatException("Conditional context '" + ctx + "' has negative count " + count);
            }
        }
        return records;
    }

    /** Parses an int, ignoring surrounding whitespace; {@code ctx} is for the error message. */
    private static int parseInt(String value, String ctx) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IprofFormatException("Expected integer but found '" + value + "' in context '" + ctx + "'");
        }
    }

    /** Parses a method id, which must not be negative; {@code ctx} is for the error message. */
    private static int parseNonNegativeInt(String value, String ctx) {
        int parsed = parseInt(value, ctx);
        if (parsed < 0) {
            throw new IprofFormatException("Expected non-negative method id but found " + parsed + " in context '" + ctx + "'");
        }
        return parsed;
    }

    @SuppressWarnings("unchecked")
    private static EconomicMap<String, Object> asObject(Object value, String owner) {
        if (!(value instanceof EconomicMap)) {
            throw new IprofFormatException("Expected JSON object in '" + owner + "'");
        }
        return (EconomicMap<String, Object>) value;
    }

    /** The array under {@code key}, or an empty list when {@code map} has no value for it. */
    private static List<Object> optionalList(EconomicMap<String, Object> map, String key) {
        return map.get(key) == null ? List.of() : requireList(map, key);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> requireList(EconomicMap<String, Object> map, String key) {
        Object value = require(map, key);
        if (!(value instanceof List)) {
            throw new IprofFormatException("Key '" + key + "' must be a JSON array");
        }
        return (List<Object>) value;
    }

    private static String requireString(EconomicMap<String, Object> map, String key) {
        Object value = require(map, key);
        if (!(value instanceof String)) {
            throw new IprofFormatException("Key '" + key + "' must be a string");
        }
        return (String) value;
    }

    private static int requireInt(EconomicMap<String, Object> map, String key) {
        return asInt(require(map, key), key);
    }

    private static Object require(EconomicMap<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            throw new IprofFormatException("Missing required key '" + key + "'");
        }
        return value;
    }

    private static int asInt(Object value, String owner) {
        long longValue = asLong(value, owner);
        if (longValue < Integer.MIN_VALUE || longValue > Integer.MAX_VALUE) {
            throw new IprofFormatException("Value " + longValue + " in '" + owner + "' does not fit in an int");
        }
        return (int) longValue;
    }

    private static long asLong(Object value, String owner) {
        if (value instanceof Integer || value instanceof Long) {
            return ((Number) value).longValue();
        }
        throw new IprofFormatException("Expected integral number in '" + owner + "' but found " + typeName(value));
    }

    /** The class of a JSON value, or {@code null}, for an error message. */
    private static String typeName(Object value) {
        return value == null ? "null" : value.getClass().getName();
    }
}
