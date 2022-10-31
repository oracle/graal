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
import java.util.Arrays;
import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;

import com.oracle.svm.agent.tracing.core.Tracer;
import com.oracle.svm.agent.tracing.core.TracingResultWriter;
import com.oracle.svm.configure.config.ConfigurationSet;
import com.oracle.svm.configure.trace.TraceProcessor;

public class ConfigurationResultWriter extends Tracer implements TracingResultWriter {
    private final TraceProcessor processor;
    private final ConfigurationSet configuration;
    private final ConfigurationSet omittedConfiguration;

    public ConfigurationResultWriter(TraceProcessor processor, ConfigurationSet configuration, ConfigurationSet omittedConfiguration) {
        this.processor = processor;
        this.configuration = configuration;
        this.omittedConfiguration = omittedConfiguration;
    }

    @Override
    protected void traceEntry(EconomicMap<String, Object> entry) {
        processor.processEntry(arraysToLists(entry), configuration);
    }

    /** {@link TraceProcessor} expects {@link List} objects instead of plain arrays. */
    public static EconomicMap<String, Object> arraysToLists(EconomicMap<String, Object> map) {
        MapCursor<String, Object> cursor = map.getEntries();
        while (cursor.advance()) {
            if (cursor.getValue() instanceof Object[]) {
                cursor.setValue(arraysToLists((Object[]) cursor.getValue()));
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
        ConfigurationSet finalConfiguration = configuration.copyAndSubtract(omittedConfiguration);
        return finalConfiguration.writeConfiguration(configFile -> directoryPath.resolve(configFile.getFileName()));
    }
}
