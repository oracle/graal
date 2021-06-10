/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.agent.tracing;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.oracle.svm.agent.tracing.core.Tracer;
import com.oracle.svm.agent.tracing.core.TracingResultWriter;
import com.oracle.svm.configure.json.JsonWriter;
import com.oracle.svm.configure.trace.TraceProcessor;
import com.oracle.svm.core.configure.ConfigurationFile;

public class ConfigurationResultWriter extends Tracer implements TracingResultWriter {
    private final TraceProcessor processor;

    public ConfigurationResultWriter(TraceProcessor processor) {
        this.processor = processor;
    }

    @Override
    protected void traceEntry(Map<String, Object> entry) {
        processor.processEntry(arraysToLists(entry));
    }

    /** {@link TraceProcessor} expects {@link List} objects instead of plain arrays. */
    public static Map<String, Object> arraysToLists(Map<String, Object> map) {
        for (Map.Entry<String, Object> mapEntry : map.entrySet()) {
            if (mapEntry.getValue() instanceof Object[]) {
                mapEntry.setValue(arraysToLists((Object[]) mapEntry.getValue()));
            }
        }
        return map;
    }

    private static List<?> arraysToLists(Object[] array) {
        Object[] newArray = Arrays.copyOf(array, array.length);
        for (int i = 0; i < newArray.length; i++) {
            if (newArray[i] instanceof Object[]) {
                newArray[i] = arraysToLists((Object[]) newArray[i]);
            }
        }
        return Arrays.asList(newArray);
    }

    @Override
    public boolean supportsPeriodicTraceWriting() {
        return true;
    }

    @Override
    public boolean supportsOnUnloadTraceWriting() {
        return true;
    }

    @Override
    public List<Path> writeToDirectory(Path directoryPath) throws IOException {
        List<Path> writtenPaths = new ArrayList<>();
        for (ConfigurationFile configFile : ConfigurationFile.values()) {
            if (configFile.canBeGeneratedByAgent()) {
                Path filePath = directoryPath.resolve(configFile.getFileName());
                try (JsonWriter writer = new JsonWriter(filePath)) {
                    processor.getConfiguration(configFile).printJson(writer);
                    /* Add an extra EOF newline */
                    writer.newline();
                }
                writtenPaths.add(filePath);
            }
        }
        return writtenPaths;
    }
}
