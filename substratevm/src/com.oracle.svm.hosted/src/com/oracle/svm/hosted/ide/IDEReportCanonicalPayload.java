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
package com.oracle.svm.hosted.ide;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import jdk.graal.compiler.ide.IDEReportSnapshot;
import jdk.graal.compiler.util.json.JsonPrettyWriter;
import jdk.graal.compiler.util.json.JsonWriter;

/** Produces the stable JSON payload shared by export, split, and embedded storage. */
public final class IDEReportCanonicalPayload {
    public static final int SCHEMA_VERSION = 1;

    private static final Set<String> MINIMAL_CATEGORIES = Set.of(
                    "reflection",
                    "inlining",
                    "unreachable",
                    "devirtualization",
                    "return-value");

    private IDEReportCanonicalPayload() {
    }

    public static byte[] create(IDEReportSnapshot snapshot, IDEReportPayloadScope scope) {
        return create(snapshot.reports(), snapshot.usedMethods(), scope);
    }

    public static byte[] create(List<Map<String, Object>> reportRecords, List<Map<String, Object>> methodReferences, IDEReportPayloadScope scope) {
        List<Map<String, Object>> records = new ArrayList<>();
        for (Map<String, Object> record : reportRecords) {
            if (scope == IDEReportPayloadScope.MINIMAL) {
                Object category = record.get("category");
                if (!(category instanceof String)) {
                    throw new IllegalArgumentException("Minimal IDE report payloads require every record to have a semantic category");
                }
                if (!MINIMAL_CATEGORIES.contains(category)) {
                    continue;
                }
            }
            records.add(stableMap(record));
        }
        records.sort(Comparator.comparing(IDEReportCanonicalPayload::compactJson));

        List<Map<String, Object>> usedMethods = new ArrayList<>();
        if (scope == IDEReportPayloadScope.FULL) {
            for (Map<String, Object> method : methodReferences) {
                usedMethods.add(stableMap(method));
            }
            usedMethods.sort(Comparator.comparing(IDEReportCanonicalPayload::compactJson));
        }

        Map<String, Object> payload = new TreeMap<>();
        payload.put("schema_version", SCHEMA_VERSION);
        payload.put("payload_scope", scope.serializedName());
        payload.put("records", records);
        payload.put("used_methods", usedMethods);
        payload.put("extensions", Map.of());
        return (prettyJson(payload) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    public static void write(IDEReportSnapshot snapshot, IDEReportPayloadScope scope, Path path) throws IOException {
        write(create(snapshot, scope), path);
    }

    public static void write(byte[] payload, Path path) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(path, payload);
    }

    private static Map<String, Object> stableMap(Map<String, Object> source) {
        Map<String, Object> result = new TreeMap<>();
        source.forEach((key, value) -> result.put(key, stableValue(value)));
        return result;
    }

    private static Object stableValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new TreeMap<>();
            map.forEach((key, nestedValue) -> result.put(key.toString(), stableValue(nestedValue)));
            return result;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(IDEReportCanonicalPayload::stableValue).toList();
        }
        return value;
    }

    private static String compactJson(Map<String, Object> value) {
        StringWriter output = new StringWriter();
        try (JsonWriter writer = new JsonWriter(output)) {
            writer.print(value);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
        return output.toString();
    }

    private static String prettyJson(Map<String, Object> value) {
        StringWriter output = new StringWriter();
        try (JsonWriter writer = new JsonPrettyWriter(output)) {
            writer.print(new LinkedHashMap<>(value));
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
        return output.toString();
    }
}
