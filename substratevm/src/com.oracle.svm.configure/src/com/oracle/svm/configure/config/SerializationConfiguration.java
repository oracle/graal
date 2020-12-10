/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2020, Alibaba Group Holding Limited. All rights reserved.
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
package com.oracle.svm.configure.config;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.oracle.svm.configure.json.JsonPrintable;
import com.oracle.svm.configure.json.JsonWriter;

public class SerializationConfiguration implements JsonPrintable {

    private final ConcurrentHashMap<String, Set<Long>> serializations = new ConcurrentHashMap<>();

    public void addAll(String serializationTargetClass, Collection<Long> checksums) {
        serializations.computeIfAbsent(serializationTargetClass, key -> new LinkedHashSet<>()).addAll(checksums);
    }

    public void add(String serializationTargetClass, Long checksum) {
        addAll(serializationTargetClass, Collections.singleton(checksum));
    }

    public boolean contains(String serializationTargetClass, Long checksum) {
        return serializations.contains(serializationTargetClass) && serializations.get(serializationTargetClass).contains(checksum);
    }

    @Override
    public void printJson(JsonWriter writer) throws IOException {
        writer.append('[').indent();
        String prefix = "";
        for (Map.Entry<String, Set<Long>> entry : serializations.entrySet()) {
            writer.append(prefix);
            writer.newline().append('{').newline();
            String className = entry.getKey();
            writer.quote("name").append(":").quote(className);
            Set<Long> checksums = entry.getValue();
            if (!checksums.isEmpty()) {
                writer.append(",").newline();
                writer.quote("checksum").append(':');
                if (checksums.size() == 1) {
                    writer.append(checksums.iterator().next().toString());
                } else {
                    writer.append(checksums.stream().map(String::valueOf).collect(Collectors.joining(", ", "[", "]")));
                }
                writer.newline();
            }
            writer.append('}');
            prefix = ",";
        }
        writer.unindent().newline();
        writer.append(']').newline();
    }
}
