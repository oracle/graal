/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2022, Alibaba Group Holding Limited. All rights reserved.
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

package com.oracle.graal.pointsto.standalone;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.util.AnalysisError;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.DebugOptions;
import jdk.graal.compiler.debug.MethodFilter;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * This class reads the configuration file that complies to the following rules:
 * <ol>
 * <li>The configuration file is a plain text file.</li>
 * <li>Each line represents one method.</li>
 * <li>The method is described in the format defined by {@link MethodFilter}</li>
 * </ol>
 */
public class MethodConfigReader {

    private static final String READ_ENTRY_POINTS = "ReadEntryPoints";
    private static final int MAX_REPORTED_MISSING_ENTRY_POINTS = 20;

    /**
     * Read methods from the specified file. For each parsed method, execute the specified action.
     *
     * @param file the configuration file or class-path resource to read.
     * @param bigbang
     * @param classLoaderAccess for loading classes
     * @param actionForEachMethod the action to take for each resolved method.
     */
    public static void readMethodFromFile(String file, BigBang bigbang, PointsToAnalyzer.ClassLoaderAccess classLoaderAccess, Consumer<AnalysisMethod> actionForEachMethod) {
        List<String> methodNameList = new ArrayList<>();
        try (InputStream input = openMethodConfig(file);
                        BufferedReader br = new BufferedReader(new InputStreamReader(input))) {
            String line;
            while ((line = br.readLine()) != null) {
                methodNameList.add(line);
            }
        } catch (IOException e) {
            AnalysisError.shouldNotReachHere(e);
        }
        int totalSize = methodNameList.size();
        EntryPointReadResult readResult = forMethodList(bigbang.getDebug(), methodNameList, bigbang, classLoaderAccess, actionForEachMethod);
        StringBuilder sb = new StringBuilder();
        sb.append("==Reading analysis entry points status==").append(System.lineSeparator());
        sb.append(readResult.resolvedMethods()).append(" out of ").append(totalSize).append(" methods are read from ").append(file).append(System.lineSeparator());
        System.out.println(sb.toString());
        AnalysisError.guarantee(readResult.unresolvedMethods().isEmpty(),
                        "Could not register %d analysis entry point(s) from %s.%nMissing entries:%n%s%nAppend option -H:%s=%s:3 for per-entry diagnostics.",
                        readResult.unresolvedMethods().size(),
                        file,
                        formatMissingEntryPoints(readResult.unresolvedMethods()),
                        DebugOptions.Log.getName(),
                        READ_ENTRY_POINTS);
    }

    /**
     * Opens an entry-points configuration either from the filesystem or, if no regular file exists
     * at the supplied location, from the class path.
     */
    private static InputStream openMethodConfig(String file) throws IOException {
        File entryFile = new File(file);
        if (entryFile.isFile()) {
            return new FileInputStream(entryFile);
        }
        InputStream resourceStream = MethodConfigReader.class.getResourceAsStream(file);
        if (resourceStream != null) {
            return resourceStream;
        }
        throw new IOException("Cannot find analysis entry points file or resource: " + file);
    }

    @SuppressWarnings("try")
    public static EntryPointReadResult forMethodList(DebugContext debug, List<String> methods, BigBang bigbang, PointsToAnalyzer.ClassLoaderAccess classLoaderAccess,
                    Consumer<AnalysisMethod> actionForEachMethod) {
        AtomicInteger validMethodsNum = new AtomicInteger(0);
        List<String> unresolvedMethods = new ArrayList<>();
        try (DebugContext.Scope s = debug.scope(READ_ENTRY_POINTS)) {
            methods.stream().forEach(method -> {
                if (!method.isBlank()) {
                    try {
                        workWithMethod(method, bigbang, classLoaderAccess, actionForEachMethod);
                        validMethodsNum.incrementAndGet();
                    } catch (Throwable t) {
                        unresolvedMethods.add(method);
                        // Checkstyle: Allow raw info or warning printing - begin
                        debug.log(DebugContext.VERBOSE_LEVEL, "Warning: Can't add method %s as analysis root method. Reason: %s", method, t.getMessage());
                        // Checkstyle: Allow raw info or warning printing - end
                    }
                }
            });
        }
        return new EntryPointReadResult(validMethodsNum.get(), List.copyOf(unresolvedMethods));
    }

    private static String formatMissingEntryPoints(List<String> unresolvedMethods) {
        int reportedMethods = Math.min(unresolvedMethods.size(), MAX_REPORTED_MISSING_ENTRY_POINTS);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < reportedMethods; i++) {
            if (i > 0) {
                sb.append(System.lineSeparator());
            }
            sb.append(unresolvedMethods.get(i));
        }
        if (unresolvedMethods.size() > reportedMethods) {
            sb.append(System.lineSeparator()).append("... and ").append(unresolvedMethods.size() - reportedMethods).append(" more");
        }
        return sb.toString();
    }

    public record EntryPointReadResult(int resolvedMethods, List<String> unresolvedMethods) {
    }

    private static void workWithMethod(String method, BigBang bigbang, PointsToAnalyzer.ClassLoaderAccess classLoaderAccess, Consumer<AnalysisMethod> actionForEachMethod)
                    throws ClassNotFoundException {
        int pos = method.indexOf('(');
        int dotAfterClassNamePos;
        if (pos == -1) {
            dotAfterClassNamePos = method.lastIndexOf('.');
        } else {
            dotAfterClassNamePos = method.lastIndexOf('.', pos);
        }
        if (dotAfterClassNamePos == -1) {
            AnalysisError.shouldNotReachHere("The the given method's name " + method + " doesn't contain the declaring class name.");
        }
        String className = method.substring(0, dotAfterClassNamePos);
        ResolvedJavaType t = classLoaderAccess.forName(className);
        if (t == null) {
            throw new ClassNotFoundException(className);
        }
        // MethodFilter.parse requires ResolvedJavaMethod as input
        List<ResolvedJavaMethod> methodCandidates = Arrays.stream(t.getDeclaredMethods()).filter(m -> !m.isNative()).collect(Collectors.toList());
        methodCandidates.addAll(Arrays.stream(t.getDeclaredConstructors()).toList());
        if (t.getClassInitializer() != null) {
            methodCandidates.add(t.getClassInitializer());
        }
        MethodFilter filter = MethodFilter.parse(method);
        ResolvedJavaMethod found = methodCandidates.stream().filter(filter::matches).findFirst().orElseThrow(
                        () -> new NoSuchMethodError(method));
        actionForEachMethod.accept(bigbang.getUniverse().lookup(found));
    }
}
