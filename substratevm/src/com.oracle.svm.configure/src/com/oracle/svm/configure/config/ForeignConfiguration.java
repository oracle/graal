/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;

import com.oracle.svm.configure.ConfigurationBase;
import com.oracle.svm.configure.ConfigurationParser;
import com.oracle.svm.configure.ConfigurationParserOption;
import com.oracle.svm.configure.ForeignConfigurationParser;
import com.oracle.svm.configure.UnresolvedConfigurationCondition;

import jdk.graal.compiler.util.json.JsonPrintable;
import jdk.graal.compiler.util.json.JsonWriter;

public final class ForeignConfiguration extends ConfigurationBase<ForeignConfiguration, ForeignConfiguration.Predicate> {
    public record ConfigurationFunctionDescriptor(String returnType, List<String> parameterTypes) implements JsonPrintable {
        @Override
        public void printJson(JsonWriter writer) throws IOException {
            writer.appendKeyValue("returnType", returnType).appendSeparator()
                            .quote("parameterTypes").appendFieldSeparator().print(parameterTypes);
        }
    }

    private record StubDesc(UnresolvedConfigurationCondition condition, ConfigurationFunctionDescriptor desc, Map<String, Object> linkerOptions) implements JsonPrintable {
        @Override
        public void printJson(JsonWriter writer) throws IOException {
            writer.appendObjectStart();
            desc.printJson(writer);
            if (!linkerOptions.isEmpty()) {
                writer.appendSeparator().quote("options").appendFieldSeparator().print(linkerOptions);
            }
            writer.appendObjectEnd();
        }
    }

    private record DirectStubDesc(UnresolvedConfigurationCondition condition, String clazz, String method, ConfigurationFunctionDescriptor desc,
                    Map<String, Object> linkerOptions) implements JsonPrintable {
        @Override
        public void printJson(JsonWriter writer) throws IOException {
            writer.appendObjectStart()
                            .appendKeyValue("class", clazz).appendSeparator()
                            .appendKeyValue("method", method).appendSeparator();
            if (desc != null) {
                desc.printJson(writer);
            }
            if (!linkerOptions.isEmpty()) {
                writer.appendSeparator().quote("options").appendFieldSeparator().print(linkerOptions);
            }
            writer.appendObjectEnd();
        }

        public DirectStubDesc withoutFD() {
            if (desc == null) {
                return this;
            }
            return new DirectStubDesc(condition, clazz, method, null, linkerOptions);
        }
    }

    private final Set<StubDesc> downcallStubs = ConcurrentHashMap.newKeySet();
    private final Set<StubDesc> upcallStubs = ConcurrentHashMap.newKeySet();
    private final Set<DirectStubDesc> directUpcallStubs = ConcurrentHashMap.newKeySet();

    public ForeignConfiguration() {
    }

    public ForeignConfiguration(ForeignConfiguration other) {
        downcallStubs.addAll(other.downcallStubs);
        upcallStubs.addAll(other.upcallStubs);
        directUpcallStubs.addAll(other.directUpcallStubs);
    }

    @Override
    public ForeignConfiguration copy() {
        return new ForeignConfiguration(this);
    }

    @Override
    protected void merge(ForeignConfiguration other) {
        downcallStubs.addAll(other.downcallStubs);
        upcallStubs.addAll(other.upcallStubs);

        /*-
         * First, add all direct upcall stubs from 'other'. Second, remove all stub descs that are
         * subsumed by any other stub desc. A stub desc Desc0 is subsumed by a stub desc Desc1 if
         * both denote the same method and linker options but Desc0 has a function descriptor and
         * Desc1 doesn't. In this case, Desc1 already denotes all possible overloads of the method.
         * 
         * Example:
         * directUpcallStubs={
         *     DirectStubDesc(class="A", methodName="foo", desc=null, linkerOptions={}),
         *     DirectStubDesc(class="A", methodName="bar", desc=ConfigurationFunctionDescriptor(returnType="void", parameterTypes=["int"]), linkerOptions={})
         *     DirectStubDesc(class="A", methodName="bar", desc=ConfigurationFunctionDescriptor(returnType="void", parameterTypes=["long"]), linkerOptions={})
         * }
         *
         * other.directUpcallStubs={
         *     DirectStubDesc(class="A", methodName="foo", desc=ConfigurationFunctionDescriptor(returnType="void", parameterTypes=["int"]), linkerOptions={})
         *     DirectStubDesc(class="A", methodName="foo", desc=ConfigurationFunctionDescriptor(returnType="void", parameterTypes=["int"]), linkerOptions={"captureCallState": true})
         *     DirectStubDesc(class="A", methodName="bar", desc=null, linkerOptions={}),
         * }
         * result_step_1={
         *     DirectStubDesc(class="A", methodName="foo", desc=null, linkerOptions={}),
         *     DirectStubDesc(class="A", methodName="foo", desc=ConfigurationFunctionDescriptor(returnType="void", parameterTypes=["int"]), linkerOptions={})
         *     DirectStubDesc(class="A", methodName="foo", desc=ConfigurationFunctionDescriptor(returnType="void", parameterTypes=["int"]), linkerOptions={"captureCallState": true})
         *     DirectStubDesc(class="A", methodName="bar", desc=null, linkerOptions={}),
         *     DirectStubDesc(class="A", methodName="bar", desc=ConfigurationFunctionDescriptor(returnType="void", parameterTypes=["int"]), linkerOptions={})
         *     DirectStubDesc(class="A", methodName="bar", desc=ConfigurationFunctionDescriptor(returnType="void", parameterTypes=["long"]), linkerOptions={})
         * }
         * result_step_2={
         *     DirectStubDesc(class="A", methodName="foo", desc=null, linkerOptions={}),
         *     DirectStubDesc(class="A", methodName="foo", desc=ConfigurationFunctionDescriptor(returnType="void", parameterTypes=["int"]), linkerOptions={"captureCallState": true})
         *     DirectStubDesc(class="A", methodName="bar", desc=null, linkerOptions={}),
         * }
         */
        directUpcallStubs.addAll(other.directUpcallStubs);
        directUpcallStubs.removeIf(e -> e.desc != null && directUpcallStubs.contains(e.withoutFD()));
    }

    @Override
    protected void intersect(ForeignConfiguration other) {
        downcallStubs.retainAll(other.downcallStubs);
        upcallStubs.retainAll(other.upcallStubs);
        Set<DirectStubDesc> tmp = new HashSet<>();
        /*-
         * Example: directUpcallStubs={
         *     DirectStubDesc(class="A", methodName="foo", desc=null, linkerOptions={}),
         *     DirectStubDesc(class="A", methodName="bar", desc=ConfigurationFunctionDescriptor(returnType="void", parameterTypes=["int"]), linkerOptions={})
         *     DirectStubDesc(class="A", methodName="bar", desc=ConfigurationFunctionDescriptor(returnType="void", parameterTypes=["long"]), linkerOptions={})
         *     DirectStubDesc(class="A", methodName="baz", desc=ConfigurationFunctionDescriptor(returnType="void", parameterTypes=["int"]), linkerOptions={})
         * }
         *
         * other.directUpcallStubs={
         *     DirectStubDesc(class="A", methodName="foo", desc=ConfigurationFunctionDescriptor(returnType="void", parameterTypes=["int"]), linkerOptions={})
         *     DirectStubDesc(class="A", methodName="foo", desc=ConfigurationFunctionDescriptor(returnType="void", parameterTypes=["int"]), linkerOptions={"captureCallState": true})
         *     DirectStubDesc(class="A", methodName="bar", desc=null, linkerOptions={}),
         * }
         * tmp_step_1={
         *     DirectStubDesc(class="A", methodName="bar", desc=ConfigurationFunctionDescriptor(returnType="void", parameterTypes=["int"]), linkerOptions={})
         *     DirectStubDesc(class="A", methodName="bar", desc=ConfigurationFunctionDescriptor(returnType="void", parameterTypes=["long"]), linkerOptions={})
         * }
         * tmp_step_2={
         *     DirectStubDesc(class="A", methodName="foo", desc=ConfigurationFunctionDescriptor(returnType="void", parameterTypes=["int"]), linkerOptions={})
         *     DirectStubDesc(class="A", methodName="bar", desc=ConfigurationFunctionDescriptor(returnType="void", parameterTypes=["int"]), linkerOptions={})
         *     DirectStubDesc(class="A", methodName="bar", desc=ConfigurationFunctionDescriptor(returnType="void", parameterTypes=["long"]), linkerOptions={})
         * }
         */
        for (DirectStubDesc e : directUpcallStubs) {
            if (other.directUpcallStubs.contains(e) || other.directUpcallStubs.contains(e.withoutFD())) {
                tmp.add(e);
            }
        }
        for (DirectStubDesc e : other.directUpcallStubs) {
            if (directUpcallStubs.contains(e) || directUpcallStubs.contains(e.withoutFD())) {
                tmp.add(e);
            }
        }
        directUpcallStubs.clear();
        directUpcallStubs.addAll(tmp);
    }

    @Override
    protected void removeIf(Predicate predicate) {
        downcallStubs.removeIf(element -> predicate.testDowncall(element.desc, element.linkerOptions));
        upcallStubs.removeIf(element -> predicate.testUpcall(element.desc, element.linkerOptions));
        directUpcallStubs.removeIf(element -> predicate.testDirectUpcall(element.clazz, element.method, element.desc, element.linkerOptions));
    }

    @Override
    public void subtract(ForeignConfiguration other) {
        downcallStubs.removeAll(other.downcallStubs);
        upcallStubs.removeAll(other.upcallStubs);
        directUpcallStubs.removeAll(other.directUpcallStubs);
    }

    @Override
    public void mergeConditional(UnresolvedConfigurationCondition condition, ForeignConfiguration other) {
        // GR-64144: Not implemented with conditions yet
        merge(other);
    }

    public void addDowncall(String returnType, List<String> parameterTypes, Map<String, Object> linkerOptions) {
        Objects.requireNonNull(returnType);
        Objects.requireNonNull(parameterTypes);
        addDowncall(UnresolvedConfigurationCondition.alwaysTrue(), new ConfigurationFunctionDescriptor(returnType, parameterTypes), Map.copyOf(linkerOptions));
    }

    public void addUpcall(String returnType, List<String> parameterTypes, Map<String, Object> linkerOptions) {
        Objects.requireNonNull(returnType);
        Objects.requireNonNull(parameterTypes);
        addUpcall(UnresolvedConfigurationCondition.alwaysTrue(), new ConfigurationFunctionDescriptor(returnType, parameterTypes), Map.copyOf(linkerOptions));
    }

    public void addDirectUpcall(String returnType, List<String> parameterTypes, Map<String, Object> linkerOptions, String clazz, String method) {
        Objects.requireNonNull(returnType);
        Objects.requireNonNull(parameterTypes);
        Objects.requireNonNull(clazz);
        Objects.requireNonNull(method);
        addDirectUpcall(UnresolvedConfigurationCondition.alwaysTrue(), new ConfigurationFunctionDescriptor(returnType, parameterTypes), Map.copyOf(linkerOptions), clazz, method);
    }

    public void addDowncall(UnresolvedConfigurationCondition configurationCondition, ConfigurationFunctionDescriptor desc, Map<String, Object> linkerOptions) {
        Objects.requireNonNull(desc);
        downcallStubs.add(new StubDesc(configurationCondition, desc, Map.copyOf(linkerOptions)));
    }

    public void addUpcall(UnresolvedConfigurationCondition configurationCondition, ConfigurationFunctionDescriptor desc, Map<String, Object> linkerOptions) {
        Objects.requireNonNull(desc);
        upcallStubs.add(new StubDesc(configurationCondition, desc, Map.copyOf(linkerOptions)));
    }

    public void addDirectUpcall(UnresolvedConfigurationCondition configurationCondition, ConfigurationFunctionDescriptor desc, Map<String, Object> linkerOptions, String clazz, String method) {
        Objects.requireNonNull(desc);
        Objects.requireNonNull(clazz);
        Objects.requireNonNull(method);
        DirectStubDesc candidate = new DirectStubDesc(configurationCondition, clazz, method, desc, Map.copyOf(linkerOptions));
        // only add the new descriptor if it is not subsumed by an existing one
        if (!directUpcallStubs.contains(candidate.withoutFD())) {
            directUpcallStubs.add(candidate);
        }
    }

    public void addDirectUpcall(UnresolvedConfigurationCondition configurationCondition, Map<String, Object> linkerOptions, String clazz, String method) {
        Objects.requireNonNull(clazz);
        Objects.requireNonNull(method);
        DirectStubDesc directStubDesc = new DirectStubDesc(configurationCondition, clazz, method, null, Map.copyOf(linkerOptions));
        // remove all existing descriptors if they are subsumed by the new descriptor
        directUpcallStubs.removeIf(existing -> directStubDesc.equals(existing.withoutFD()));
        directUpcallStubs.add(directStubDesc);
    }

    @Override
    public void printJson(JsonWriter writer) throws IOException {
        Map<String, Collection<? extends JsonPrintable>> stubSets = Map.of(
                        "downcalls", downcallStubs,
                        "upcalls", upcallStubs,
                        "directUpcalls", directUpcallStubs);

        writer.appendObjectStart();
        boolean first = true;
        for (String sectionName : stubSets.keySet()) {
            Collection<? extends JsonPrintable> stubs = stubSets.get(sectionName);
            if (!stubs.isEmpty()) {
                if (!first) {
                    writer.appendSeparator();
                }
                writer.quote(sectionName).appendFieldSeparator().appendArrayStart();
                printStubs(writer, stubs);
                writer.appendArrayEnd();
                first = false;
            }
        }
        writer.appendObjectEnd();
    }

    private static void printStubs(JsonWriter writer, Collection<? extends JsonPrintable> stubs) throws IOException {
        boolean first = true;
        for (var stubDesc : stubs) {
            if (first) {
                first = false;
            } else {
                writer.appendSeparator();
            }
            stubDesc.printJson(writer);
        }
    }

    @Override
    public ConfigurationParser createParser(boolean combinedFileSchema, EnumSet<ConfigurationParserOption> parserOptions) {
        if (!combinedFileSchema) {
            throw new IllegalArgumentException("Foreign configuration is only supported with reachability-metadata.json");
        }
        return new UnresolvedForeignConfigurationParser(parserOptions);
    }

    @Override
    public boolean isEmpty() {
        return downcallStubs.isEmpty() && upcallStubs.isEmpty() && directUpcallStubs.isEmpty();
    }

    @Override
    public boolean supportsCombinedFile() {
        return true;
    }

    public interface Predicate {
        boolean testDowncall(ConfigurationFunctionDescriptor desc, Map<String, Object> linkerOptions);

        boolean testUpcall(ConfigurationFunctionDescriptor desc, Map<String, Object> linkerOptions);

        boolean testDirectUpcall(String clazz, String method, ConfigurationFunctionDescriptor desc, Map<String, Object> linkerOptions);
    }

    /**
     * A simple implementation of the {@link ForeignConfigurationParser} which does not
     * resolve/validate any descriptors/handles but just collects the configuration into
     * {@link ForeignConfiguration} in order to be able to do the common operations (e.g. merge,
     * diff, ...) on configuration files.
     */
    private final class UnresolvedForeignConfigurationParser extends ForeignConfigurationParser<ConfigurationFunctionDescriptor, Map<String, Object>> {
        private UnresolvedForeignConfigurationParser(EnumSet<ConfigurationParserOption> parserOptions) {
            super(parserOptions);
        }

        @Override
        protected void registerDowncall(UnresolvedConfigurationCondition configurationCondition, ConfigurationFunctionDescriptor descriptor, Map<String, Object> options) {
            ForeignConfiguration.this.addDowncall(configurationCondition, descriptor, options);

        }

        @Override
        protected void registerUpcall(UnresolvedConfigurationCondition configurationCondition, ConfigurationFunctionDescriptor descriptor, Map<String, Object> options) {
            ForeignConfiguration.this.addUpcall(configurationCondition, descriptor, options);
        }

        @Override
        protected void registerDirectUpcallWithoutDescriptor(UnresolvedConfigurationCondition configurationCondition, String className, String methodName, EconomicMap<String, Object> optionsMap) {
            ForeignConfiguration.this.addDirectUpcall(configurationCondition, economicMapToJavaMap(optionsMap), className, methodName);
        }

        @Override
        protected void registerDirectUpcallWithDescriptor(UnresolvedConfigurationCondition configurationCondition, String className, String methodName, ConfigurationFunctionDescriptor descriptor,
                        Map<String, Object> options) {
            ForeignConfiguration.this.addDirectUpcall(configurationCondition, descriptor, options, className, methodName);
        }

        @Override
        protected void handleRegistrationError(Exception e, EconomicMap<String, Object> map) {
            /*
             * We should never reach here because this handler is only called if one of the
             * register(Donwcall|Upcall|DirectUpcallWithDescriptor|DirectUpcallWithoutDescriptor)
             * methods throws an exception.
             */
            throw new RuntimeException("Should not be reached", e);
        }

        @Override
        protected ConfigurationFunctionDescriptor createFunctionDescriptor(String returnType, List<String> parameterTypes) {
            return new ConfigurationFunctionDescriptor(returnType, parameterTypes);
        }

        @Override
        protected Map<String, Object> createDowncallOptions(EconomicMap<String, Object> map, @SuppressWarnings("unused") ConfigurationFunctionDescriptor desc) {
            return economicMapToJavaMap(map);
        }

        @Override
        protected Map<String, Object> createUpcallOptions(EconomicMap<String, Object> map, @SuppressWarnings("unused") ConfigurationFunctionDescriptor desc) {
            return economicMapToJavaMap(map);
        }

        private static Map<String, Object> economicMapToJavaMap(EconomicMap<String, Object> map) {
            Map<String, Object> result = new HashMap<>();
            MapCursor<String, Object> cursor = map.getEntries();
            while (cursor.advance()) {
                result.put(cursor.getKey(), cursor.getValue());
            }
            return result;
        }
    }
}
