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
package org.graalvm.compiler.nodes;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.nodes.loop.LoopEx;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.serviceprovider.GraalServices;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class OptimizationLog {

    public static class Options {
        @Option(help = "Dump optimization log for each compilation " +
                "to optimization_log/<execution-id>/<compilation-id>.json.", type = OptionType.Debug)
        public static final OptionKey<Boolean> OptimizationLog = new OptionKey<>(false);
    }

    interface JSONValue {
        void appendTo(Appendable appendable) throws IOException;
    }

    static class JSONMap implements JSONValue {
        private final Map<String, JSONValue> map = new HashMap<>();

        public void addEntry(String name, JSONValue value) {
            map.put(name, value);
        }

        @Override
        public void appendTo(Appendable appendable) throws IOException {
            appendable.append("{\n");
            boolean isFirst = true;
            for (Map.Entry<String, JSONValue> entry : map.entrySet()) {
                if (!isFirst) {
                    appendable.append(",\n");
                }
                isFirst = false;
                appendable
                        .append('"')
                        .append(entry.getKey())
                        .append("\": ");
                entry.getValue().appendTo(appendable);

            }
            appendable.append("\n}");
        }
    }

    static class JSONArray implements JSONValue {
        private final ArrayList<JSONValue> elements = new ArrayList<>();

        public void add(JSONValue value) {
            elements.add(value);
        }

        @Override
        public void appendTo(Appendable appendable) throws IOException {
            appendable.append("[\n");
            boolean isFirst = true;
            for (JSONValue value : elements) {
                if (!isFirst) {
                    appendable.append(",\n");
                }
                isFirst = false;
                value.appendTo(appendable);
            }
            appendable.append("\n]");
        }
    }

    static class JSONString implements JSONValue {
        private final String string;

        JSONString(String string) {
            this.string = string;
        }

        @Override
        public void appendTo(Appendable appendable) throws IOException {
            if (string == null) {
                appendable.append("null");
            } else {
                appendable.append('"').append(string).append('"');
            }
        }
    }

    static class JSONInteger implements JSONValue {
        private final Integer integer;

        JSONInteger(Integer integer) {
            this.integer = integer;
        }

        @Override
        public void appendTo(Appendable appendable) throws IOException {
            if (integer == null) {
                appendable.append("null");
            } else {
                appendable.append(integer.toString());
            }
        }
    }

    static class OptimizationEntry {
        private final String description;
        private final ResolvedJavaMethod method;
        private final Integer bci;

        OptimizationEntry(String description, ResolvedJavaMethod method, Integer bci) {
            this.description = description;
            this.method = method;
            this.bci = bci;
        }

        public JSONMap asJSONMap() {
            JSONMap map = new JSONMap();
            map.addEntry("description", new JSONString(description));
            map.addEntry("method", new JSONString(method.format("%h")));
            map.addEntry("bci", new JSONInteger(bci));
            return map;
        }
    }

    private final ArrayList<OptimizationEntry> optimizationEntries = new ArrayList<>();
    private final CompilationIdentifier compilationIdentifier;
    private final ResolvedJavaMethod method;
    private final String compilationId;

    public OptimizationLog(ResolvedJavaMethod method, CompilationIdentifier compilationIdentifier) {
        this.method = method;
        this.compilationIdentifier = compilationIdentifier;
        this.compilationId = parseCompilationId();
    }

    public void logLoopPartialUnroll(LoopEx loop) {
        LoopBeginNode loopBegin = loop.loopBegin();
        Integer bci = null;
        if (loopBegin.stateAfter != null) {
            bci = loopBegin.stateAfter.bci;
        } else if (loopBegin.getNodeSourcePosition() != null) {
            bci = loopBegin.getNodeSourcePosition().getBCI();
        }
        optimizationEntries.add(new OptimizationEntry("Loop Partial Unroll", loopBegin.graph().method(), bci));
    }

    public void printToFile() throws IOException {
        String filename = compilationId + ".json";
        Path path = Path.of("optimization_log", GraalServices.getExecutionID(), filename);
        Files.createDirectories(path.getParent());
        PrintStream stream = new PrintStream(Files.newOutputStream(path));
        appendTo(stream);
    }

    private void appendTo(Appendable appendable) throws IOException {
        JSONMap map = new JSONMap();
        map.addEntry("executionId", new JSONString(GraalServices.getExecutionID()));
        map.addEntry("compilationMethodName", new JSONString(compilationIdentifier.toString(CompilationIdentifier.Verbosity.NAME)));
        map.addEntry("compilationId", new JSONString(compilationId));
        // TODO do we need this?
        map.addEntry("resolvedMethodName", new JSONString(method.format("%n")));
        map.addEntry("optimizations", entriesAsJSONArray());
        map.appendTo(appendable);
    }

    private String parseCompilationId() {
        String compilationId = compilationIdentifier.toString(CompilationIdentifier.Verbosity.ID);
        int dash = compilationId.indexOf('-');
        if (dash == -1) {
            return compilationId;
        }
        return compilationId.substring(dash + 1);
    }

    private JSONArray entriesAsJSONArray() {
        JSONArray array = new JSONArray();
        for (OptimizationEntry entry : optimizationEntries) {
            array.add(entry.asJSONMap());
        }
        return array;
    }
}
