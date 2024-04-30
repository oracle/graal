/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.reflect;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.reports.ReportUtils;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.HostedOptionValues;
import com.oracle.svm.core.util.json.JsonWriter;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.FeatureImpl.DuringSetupAccessImpl;
import com.oracle.svm.hosted.NativeImageGenerator;

import jdk.graal.compiler.options.Option;

/**
 * This feature prints all reflective elements that are in the native image heap. Its goal is to
 * provide appropriate reflection configuration for less precise analysis modes, in which some
 * reflective accesses cannot be folded and hence have to be registered explicitly.
 */
@AutomaticallyRegisteredFeature
public class FoldedReflectionFeature implements InternalFeature {

    public static class Options {
        @Option(help = "Dump folded reflection elements")//
        public static final HostedOptionKey<Boolean> DumpFoldedReflectionElements = new HostedOptionKey<>(false);
    }

    /**
     * Patterns that are skipped because they cause an increase of reachable methods if the
     * generated configuration is used as input for the same build that generated it.
     */
    private static final List<String> SKIPPED_PATTERNS = Arrays.asList("com.oracle.svm.core.snippets.ImplicitExceptions", "sun.misc.Unsafe");

    /**
     * Executables found in the image heap via object replacer.
     */
    private final Set<Executable> executables = ConcurrentHashMap.newKeySet();
    /**
     * Fields found in the image heap via object replacer.
     */
    private final Set<Field> fields = ConcurrentHashMap.newKeySet();

    static class ClassConfiguration {
        public final Class<?> clazz;
        public final Set<Executable> executables = new HashSet<>();
        public final Set<Field> fields = new HashSet<>();

        ClassConfiguration(Class<?> clazz) {
            this.clazz = clazz;
        }
    }

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return Options.DumpFoldedReflectionElements.getValue();
    }

    @Override
    public void duringSetup(DuringSetupAccess a) {
        DuringSetupAccessImpl access = (DuringSetupAccessImpl) a;
        access.registerObjectReachableCallback(Executable.class, (analysisAccess, executable, reason) -> executables.add(executable));
        access.registerObjectReachableCallback(Field.class, (analysisAccess, field, reason) -> fields.add(field));
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        BigBang bb = ((FeatureImpl.AfterAnalysisAccessImpl) access).getBigBang();
        Path reportsPath = NativeImageGenerator.generatedFiles(HostedOptionValues.singleton()).resolve("reports");
        ReportUtils.report("folded reflection elements", reportsPath.resolve("folded_reflection_stats.json"), writer -> printElements(writer, bb));
    }

    private void printElements(PrintWriter printWriter, BigBang bb) {
        Map<Class<?>, ClassConfiguration> typeConfiguration = groupElementsPerClass(bb);
        try (var writer = new JsonWriter(printWriter)) {
            writer.append('[')
                            .newline()
                            .indent();
            Iterator<ClassConfiguration> it = typeConfiguration.values().iterator();
            while (it.hasNext()) {
                printClass(writer, it.next());
                if (it.hasNext()) {
                    writer.append(',');
                }
            }
            writer.unindent()
                            .newline().append(']');
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Map<Class<?>, ClassConfiguration> groupElementsPerClass(BigBang bb) {
        var typeConfiguration = new HashMap<Class<?>, ClassConfiguration>();
        AnalysisMetaAccess metaAccess = bb.getMetaAccess();
        for (Executable executable : executables) {
            AnalysisMethod method = metaAccess.lookupJavaMethod(executable);
            if (!method.isReachable() || shouldSkip(method)) {
                continue;
            }
            typeConfiguration.computeIfAbsent(executable.getDeclaringClass(), ClassConfiguration::new).executables.add(executable);
        }
        for (Field field : fields) {
            AnalysisField analysisField = metaAccess.lookupJavaField(field);
            if (!analysisField.isReachable()) {
                continue;
            }
            typeConfiguration.computeIfAbsent(field.getDeclaringClass(), ClassConfiguration::new).fields.add(field);
        }
        return typeConfiguration;
    }

    private static void printClass(JsonWriter writer, ClassConfiguration classConfig) throws IOException {
        writer.append('{')
                        .newline()
                        .indent();

        writer.quote("name")
                        .append(':')
                        .quote(classConfig.clazz.getName())
                        .append(',')
                        .quote("methods")
                        .append(":")
                        .append("[");

        printMethods(writer, classConfig);

        writer.append("]");

        if (!classConfig.fields.isEmpty()) {
            writer.append(',');
            printFields(writer, classConfig);
        }

        writer.unindent()
                        .newline()
                        .append('}');
    }

    private static boolean shouldSkip(AnalysisMethod method) {
        String qualifiedName = method.getQualifiedName();
        for (String pattern : SKIPPED_PATTERNS) {
            if (qualifiedName.startsWith(pattern)) {
                return true;
            }
        }
        return false;
    }

    private static void printFields(JsonWriter writer, ClassConfiguration classConfig) throws IOException {
        writer.quote("fields")
                        .append(':');
        writer.append("[");
        writer.newline();
        writer.indent();

        Iterator<Field> it = classConfig.fields.iterator();
        while (it.hasNext()) {
            writer.append('{')
                            .quote("name")
                            .append(":")
                            .quote(it.next().getName())
                            .append('}');
            if (it.hasNext()) {
                writer.append(',');
            }
        }

        writer.unindent();
        writer.newline();
        writer.append("]");
    }

    private static void printMethods(JsonWriter writer, ClassConfiguration classConfig) throws IOException {
        Iterator<Executable> it = classConfig.executables.iterator();
        while (it.hasNext()) {
            Executable executable = it.next();

            writer.append("{");
            writer.newline();
            writer.indent();
            writer.quote("name")
                            .append(":")
                            .quote(executable instanceof Constructor ? "<init>" : executable.getName())
                            .append(",")
                            .quote("parameterTypes")
                            .append(":")
                            .append("[");

            Class<?>[] parameterTypes = executable.getParameterTypes();
            for (int i = 0; i < parameterTypes.length; i++) {
                writer.quote(parameterTypes[i].getName());
                if (i + 1 < parameterTypes.length) {
                    writer.append(',');
                }
            }

            writer.append("]");
            writer.newline();
            writer.unindent();
            writer.append("}");

            if (it.hasNext()) {
                writer.append(",");
            }
        }
    }

}
