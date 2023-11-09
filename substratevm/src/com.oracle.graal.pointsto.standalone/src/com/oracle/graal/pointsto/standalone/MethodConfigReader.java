/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.util.AnalysisError;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.DebugOptions;
import jdk.graal.compiler.debug.MethodFilter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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

    /**
     * Read methods from the specified file. For each parsed method, execute the specified action.
     *
     * @param file the configuration file to read.
     * @param bigbang
     * @param classLoader analysis classloader
     * @param actionForEachMethod the action to take for each resolved method.
     */
    public static void readMethodFromFile(String file, BigBang bigbang, ClassLoader classLoader, Consumer<AnalysisMethod> actionForEachMethod) {
        List<String> methodNameList = new ArrayList<>();
        Path entryFilePath = Paths.get(file);
        File entryFile = entryFilePath.toFile();
        try (FileInputStream fis = new FileInputStream(entryFile);
                        BufferedReader br = new BufferedReader(new InputStreamReader(fis))) {
            String line;
            while ((line = br.readLine()) != null) {
                methodNameList.add(line);
            }
        } catch (IOException e) {
            AnalysisError.shouldNotReachHere(e);
        }
        int totalSize = methodNameList.size();
        int num = forMethodList(bigbang.getDebug(), methodNameList, bigbang, classLoader, actionForEachMethod);
        StringBuilder sb = new StringBuilder();
        sb.append("==Reading analysis entry points status==\n");
        sb.append(num).append(" out of ").append(totalSize).append(" methods are read from ").append(file).append("\n");
        if (num < totalSize) {
            sb.append("To see the details about the missing methods, please append option -H:").append(DebugOptions.Log.getName()).append("=").append(READ_ENTRY_POINTS).append(":3").append("\n");
        }
        System.out.println(sb.toString());
    }

    @SuppressWarnings("try")
    public static int forMethodList(DebugContext debug, List<String> methods, BigBang bigbang, ClassLoader classLoader, Consumer<AnalysisMethod> actionForEachMethod) {
        AtomicInteger validMethodsNum = new AtomicInteger(0);
        try (DebugContext.Scope s = debug.scope(READ_ENTRY_POINTS)) {
            methods.stream().forEach(method -> {
                if (!method.isBlank()) {
                    try {
                        workWithMethod(method, bigbang, classLoader, actionForEachMethod);
                        validMethodsNum.incrementAndGet();
                    } catch (Throwable t) {
                        // Checkstyle: Allow raw info or warning printing - begin
                        debug.log(DebugContext.VERBOSE_LEVEL, "Warning: Can't add method " + method + " as analysis root method. Reason: " + t.getMessage());
                        // Checkstyle: Allow raw info or warning printing - end
                    }
                }
            });
        }
        return validMethodsNum.get();
    }

    private static void workWithMethod(String method, BigBang bigbang, ClassLoader classLoader, Consumer<AnalysisMethod> actionForEachMethod) throws ClassNotFoundException {
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
        Class<?> c = Class.forName(className, false, classLoader);
        // MethodFilter.parse requires ResolvedJavaMethod as input
        MetaAccessProvider originalMetaAccess = bigbang.getUniverse().getOriginalMetaAccess();
        List<ResolvedJavaMethod> methodCandidates = Arrays.stream(c.getDeclaredMethods()).map(m -> originalMetaAccess.lookupJavaMethod(m)).filter(m -> !m.isNative()).collect(Collectors.toList());
        methodCandidates.addAll(Arrays.stream(c.getDeclaredConstructors()).map(m -> originalMetaAccess.lookupJavaMethod(m)).collect(Collectors.toList()));
        ResolvedJavaType t = originalMetaAccess.lookupJavaType(c);
        if (t.getClassInitializer() != null) {
            methodCandidates.add(t.getClassInitializer());
        }
        MethodFilter filter = MethodFilter.parse(method);
        ResolvedJavaMethod found = methodCandidates.stream().filter(filter::matches).findFirst().orElseThrow(
                        () -> new NoSuchMethodError(method));
        actionForEachMethod.accept(bigbang.getUniverse().lookup(found));
    }
}
