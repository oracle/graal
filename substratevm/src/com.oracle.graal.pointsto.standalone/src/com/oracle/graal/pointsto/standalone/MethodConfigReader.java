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
import com.oracle.graal.pointsto.standalone.util.StandaloneAnalysisException;
import com.oracle.graal.pointsto.util.AnalysisError;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import org.graalvm.collections.Pair;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugOptions;
import org.graalvm.compiler.debug.MethodFilter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.oracle.graal.pointsto.standalone.StandaloneOptions.AnalysisTargetAppCP;

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

    private static class Entry {
        private String declaringClassName;
        private String methodName;
        private String url;
        private ClassLoader classLoader;

        Entry(String declaringClassName, String methodName, String url, ClassLoader classLoader) {
            this.declaringClassName = declaringClassName;
            this.methodName = methodName;
            this.url = url;
            this.classLoader = classLoader;
        }
    }

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
        try (DebugContext.Scope s = debug.scope(READ_ENTRY_POINTS)) {
            Set<Entry> workingList = methods.stream().filter(e -> !e.isBlank()).map(e -> {
                // We use : to separate method and its declaring class location
                String[] contents = e.split(":");
                return new Entry(parseClassName(contents[0]), contents[0], contents.length == 2 ? contents[1] : null, classLoader);
            }).collect(Collectors.toSet());

            boolean lastRound = false;
            int validMethodsNum = 0;
            Set<Entry> exceptionEntries = new HashSet<>();
            int oldHash;
            List<Pair<Entry, Throwable>> maybeIgnoredExceptions = new ArrayList<>();
            List<Pair<Entry, Throwable>> unKnownReasonExceptions = new ArrayList<>();
            List<Pair<Entry, Throwable>> reallyNotExistList = new ArrayList<>();
            while (!workingList.isEmpty()) {
                oldHash = exceptionEntries.hashCode();
                exceptionEntries.clear();
                for (Entry entry : workingList) {
                    try {
                        workWithMethod(entry, bigbang, actionForEachMethod);
                        validMethodsNum++;
                    } catch (NoSuchMethodError e) {
                        /**
                         * When there are multiple versions of the same name class in the analysis
                         * class path, firstly loaded version shadows the others. There are two
                         * possible reasons when a NoSuchMethodError is reported: 1) the method
                         * really doesn't exist: confirmed in the last round and report 2) the
                         * method exists in the shadowed classes: using new classloader to load the
                         * shadowed classes in the next round.
                         */
                        if (lastRound) {
                            reallyNotExistList.add(Pair.create(entry, e));
                        } else {
                            exceptionEntries.add(entry);
                        }
                    } catch (StandaloneAnalysisException e) {
                        maybeIgnoredExceptions.add(Pair.create(entry, e));
                    } catch (Throwable t) {
                        unKnownReasonExceptions.add(Pair.create(entry, t));
                    }
                }
                if (!exceptionEntries.isEmpty()) {
                    /*
                     * Only request one more iteration when the state of exceptionEntries is
                     * changed. In case the requested method or class is really not existed. It can
                     * be repeatedly added into exceptionEntries, but the hashcode won't change.
                     */
                    if (oldHash != exceptionEntries.hashCode()) {
                        URL[] urls = StandaloneAnalysisClassLoader.pathToUrl(exceptionEntries.stream().map(e -> e.url).filter(url -> url != null).distinct().collect(Collectors.toList()));
                        ClassLoader analysisTimeClassLoader = new StandaloneAnalysisClassLoader(urls, null);
                        for (Entry entry : exceptionEntries) {
                            entry.classLoader = analysisTimeClassLoader;
                        }
                    } else {
                        /*
                         * When the state of exceptionEntries is stable, check each item with a
                         * separate classloader
                         */
                        for (Entry exceptionEntry : exceptionEntries) {
                            exceptionEntry.classLoader = new StandaloneAnalysisClassLoader(StandaloneAnalysisClassLoader.pathToUrl(List.of(exceptionEntry.url)), null);
                        }
                        lastRound = true;
                    }
                }

                workingList.clear();
                workingList.addAll(exceptionEntries);
            }

            handleExceptions(debug, maybeIgnoredExceptions, new StringBuilder("The following methods cannot be set as analysis entry points, " +
                            "because of the dependency issues. In general, they can be safely ignored, but please review the details to ensure:\n"));
            handleExceptions(debug, unKnownReasonExceptions, new StringBuilder("The following methods cannot be set as analysis entry points, " +
                            "because of unknown issues. Please review the details:\n"));
            handleExceptions(debug, reallyNotExistList, new StringBuilder("The following methods cannot be set as analysis entry points, " +
                            "because the requested methods do not exist. Please review the details:\n"));
            return validMethodsNum;
        }
    }

    private static void handleExceptions(DebugContext debug, List<Pair<Entry, Throwable>> list, StringBuilder messageBuilder) {
        if (list.isEmpty()) {
            return;
        }
        AtomicInteger num = new AtomicInteger(0);
        list.stream().sorted(Comparator.comparing(p -> p.getRight().getClass().getName())).forEach(
                        pair -> {
                            Entry entry = pair.getLeft();
                            Throwable e = pair.getRight();
                            messageBuilder.append(num.incrementAndGet()).append(".").append(entry.methodName).append("@").append(entry.url).append(" due to ").append(e.toString()).append("\n");
                        });
        // Checkstyle: Allow raw info or warning printing - begin
        debug.log(DebugContext.VERBOSE_LEVEL, messageBuilder.toString());
        // Checkstyle: Allow raw info or warning printing - end
    }

    private static void workWithMethod(Entry entry, BigBang bigbang, Consumer<AnalysisMethod> actionForEachMethod) throws Throwable {
        String className = entry.declaringClassName;
        List<ResolvedJavaMethod> methodCandidates;
        Class<?> c;
        try {
            c = Class.forName(className, false, entry.classLoader);
            // MethodFilter.matches requires ResolvedJavaMethod as input
            MetaAccessProvider originalMetaAccess = bigbang.getUniverse().getOriginalMetaAccess();
            methodCandidates = Arrays.stream(c.getDeclaredMethods()).map(m -> originalMetaAccess.lookupJavaMethod(m)).filter(m -> !m.isNative()).collect(Collectors.toList());
            methodCandidates.addAll(Arrays.stream(c.getDeclaredConstructors()).map(m -> originalMetaAccess.lookupJavaMethod(m)).collect(Collectors.toList()));
            ResolvedJavaType t = originalMetaAccess.lookupJavaType(c);
            if (t.getClassInitializer() != null) {
                methodCandidates.add(t.getClassInitializer());
            }
        } catch (NoClassDefFoundError e) {
            /*
             * Some dependencies can't find in the classpath when getDeclaredMethods or
             * getDeclaredConstructors
             */
            throw StandaloneAnalysisException.notFoundDependency(e,
                            "the dependency class of the requested class cannot be found in classpath, " +
                                            "which suggests it may not be actually required at runtime any more or its classpath is missing in -H:" + AnalysisTargetAppCP.getName());
        } catch (IncompatibleClassChangeError e) {
            /*
             * Incompatible changes in class definition. E.g., class C is implemented interface I
             * which has multiple versions including non-interface ones. If the interface version I
             * is shadowed by the non-interface version in current classloader,
             * IncompatibleClassChangeError is reported at loading C time.
             */
            throw StandaloneAnalysisException.notFoundDependency(e, "the dependency in class definition has been changed, " +
                            "which suggests there may be multiple versions of same name dependency class set in -H:" + AnalysisTargetAppCP.getName());
        }

        MethodFilter filter = MethodFilter.parse(entry.methodName);
        ClassLoader cl = c.getClassLoader();
        ResolvedJavaMethod found = methodCandidates.stream().filter(filter::matches).findFirst().orElseThrow(
                        () -> {
                            Error error = new NoSuchMethodError(entry.methodName);
                            if (cl == null || cl.equals(ClassLoader.getSystemClassLoader())) {
                                return StandaloneAnalysisException.hideJDK(error, "the class hides the same name JDK class where the requested method is not defined.");
                            } else {
                                return error;
                            }
                        });
        actionForEachMethod.accept(bigbang.getUniverse().lookup(found));
    }

    private static String parseClassName(String fullQualifiedMethodName) {
        int pos = fullQualifiedMethodName.indexOf('(');
        int dotAfterClassNamePos;
        if (pos == -1) {
            dotAfterClassNamePos = fullQualifiedMethodName.lastIndexOf('.');
        } else {
            dotAfterClassNamePos = fullQualifiedMethodName.lastIndexOf('.', pos);
        }
        if (dotAfterClassNamePos == -1) {
            AnalysisError.shouldNotReachHere("The the given fullQualifiedMethodName's name " + fullQualifiedMethodName + " doesn't contain the declaring class name.");
        }
        return fullQualifiedMethodName.substring(0, dotAfterClassNamePos);
    }
}
