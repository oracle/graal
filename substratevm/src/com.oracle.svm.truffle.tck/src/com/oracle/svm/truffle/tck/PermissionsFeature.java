/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.truffle.tck;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.flow.InvokeTypeFlow;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.config.ConfigurationParserUtils;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.Permission;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

public class PermissionsFeature implements Feature {

    private static final String CONFIG = "truffle-language-permissions-config.json";

    public static class Options {
        @Option(help = "Path to file where to store report of Truffle language privilege access.") public static final HostedOptionKey<String> TruffleLanguagePermissionsReportFile = new HostedOptionKey<>(
                        null);

        @Option(help = "Comma separated list of language packages.") public static final HostedOptionKey<String> TruffleLanguagePermissionsLanguagePackages = new HostedOptionKey<>(null);

        @Option(help = "Comma separated list of excluded packages.") public static final HostedOptionKey<String> TruffleLanguagePermissionsExcludedPackages = new HostedOptionKey<>(null);

        @Option(help = "Comma separated list of exclude files.") public static final HostedOptionKey<String[]> TruffleLanguagePermissionsExcludeFiles = new HostedOptionKey<>(null);

        @Option(help = "Maximal depth of a stack trace.", type = OptionType.Expert) public static final HostedOptionKey<Integer> TruffleLanguagePermissionsMaxStackTraceDepth = new HostedOptionKey<>(
                        20);

        @Option(help = "Maximal depth of a stack trace.", type = OptionType.Expert) public static final HostedOptionKey<Integer> TruffleLanguagePermissionsMaxReports = new HostedOptionKey<>(100);
    }

    static final class IsEnabled implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return ImageSingletons.contains(PermissionsFeature.class);
        }
    }

    private static final Set<String> compilerPkgs;
    static {
        compilerPkgs = new HashSet<>();
        compilerPkgs.add("org.graalvm.");
        compilerPkgs.add("com.oracle.graalvm.");
        compilerPkgs.add("com.oracle.truffle.api.");
        compilerPkgs.add("com.oracle.truffle.polyglot.");
        compilerPkgs.add("com.oracle.truffle.nfi.");
        compilerPkgs.add("com.oracle.truffle.object.");
        compilerPkgs.add("com.oracle.truffle.tools.");
    }

    private Path reportFilePath;
    private Set<String> languagePackages;
    private Set<String> excludedPackages;
    private Set<AnalysisMethod> whiteList;

    @Override
    public void duringSetup(DuringSetupAccess access) {
        if (SubstrateOptions.FoldSecurityManagerGetter.getValue()) {
            UserError.abort(getClass().getSimpleName() + " requires -H:-FoldSecurityManagerGetter option.");
        }
        String reportFile = Options.TruffleLanguagePermissionsReportFile.getValue();
        if (reportFile == null) {
            UserError.abort("Path to report file must be given by -H:TruffleLanguagePermissionsReportFile option.");
        }
        reportFilePath = Paths.get(reportFile);
        String pkgs = Options.TruffleLanguagePermissionsLanguagePackages.getValue();
        if (pkgs == null) {
            UserError.abort("Language packages must be given by -H:TruffleLanguagePermissionsLanguagePackages option.");
        }
        languagePackages = new HashSet<>();
        for (String pkg : pkgs.split(",")) {
            languagePackages.add(pkg + ".");
        }
        pkgs = Options.TruffleLanguagePermissionsExcludedPackages.getValue();
        excludedPackages = new HashSet<>();
        if (pkgs != null) {
            for (String pkg : pkgs.split(",")) {
                excludedPackages.add(pkg + ".");
            }
        }
    }

    @Override
    @SuppressWarnings("try")
    public void afterAnalysis(AfterAnalysisAccess access) {
        FeatureImpl.AfterAnalysisAccessImpl accessImpl = (FeatureImpl.AfterAnalysisAccessImpl) access;
        DebugContext debugContext = accessImpl.getDebugContext();
        try (DebugContext.Scope s = debugContext.scope(getClass().getSimpleName())) {
            BigBang bigbang = accessImpl.getBigBang();
            WhiteListParser parser = new WhiteListParser(accessImpl.getImageClassLoader(), bigbang);
            ConfigurationParserUtils.parseAndRegisterConfigurations(
                            parser,
                            accessImpl.getImageClassLoader(),
                            getClass().getSimpleName(),
                            Options.TruffleLanguagePermissionsExcludeFiles,
                            new ResourceAsOptionDecorator(getClass().getPackage().getName().replace('.', '/') + "/resources/jre.json"),
                            CONFIG);
            whiteList = parser.getLoadedWhiteList();
            Set<AnalysisMethod> importantMethods = findMethods(bigbang, SecurityManager.class, (m) -> m.getName().startsWith("check"));
            if (!importantMethods.isEmpty()) {
                Map<AnalysisMethod, Set<AnalysisMethod>> cg = callGraph(bigbang, importantMethods, debugContext);
                try (PrintWriter out = new PrintWriter(
                                new OutputStreamWriter(Files.newOutputStream(reportFilePath, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING), "UTF-8"))) {
                    Set<CallGraphFilter> contextFilters = new HashSet<>();
                    Collections.addAll(contextFilters, new SafeInterruptRecognizer(bigbang), new SafePrivilegedRecognizer(bigbang));
                    for (AnalysisMethod importantMethod : importantMethods) {
                        if (cg.containsKey(importantMethod)) {
                            printCallGraphs(out, importantMethod,
                                            Options.TruffleLanguagePermissionsMaxStackTraceDepth.getValue(),
                                            Options.TruffleLanguagePermissionsMaxReports.getValue(),
                                            cg, contextFilters,
                                            new LinkedHashSet<>(), 1, 0);
                        }
                    }
                } catch (IOException ioe) {
                    throw UserError.abort("Cannot write permissions log file due to: " + ioe.getMessage());
                }
            }
        }
    }

    private Map<AnalysisMethod, Set<AnalysisMethod>> callGraph(
                    BigBang bigbang,
                    Set<AnalysisMethod> targets,
                    DebugContext debugContext) {
        Deque<AnalysisMethod> todo = new LinkedList<>();
        Map<AnalysisMethod, Set<AnalysisMethod>> visited = new HashMap<>();
        for (AnalysisMethod m : bigbang.getUniverse().getMethods()) {
            if (m.isEntryPoint()) {
                visited.put(m, new HashSet<>());
                todo.offer(m);
            }
        }
        Deque<AnalysisMethod> path = new LinkedList<>();
        for (AnalysisMethod m : todo) {
            callGraphImpl(m, targets, visited, path, debugContext);
        }
        return visited;
    }

    private boolean callGraphImpl(
                    AnalysisMethod m,
                    Set<AnalysisMethod> targets,
                    Map<AnalysisMethod, Set<AnalysisMethod>> visited,
                    Deque<AnalysisMethod> path,
                    DebugContext debugContext) {
        String mName = getMethodName(m);
        path.addFirst(m);
        try {
            boolean vote = false;
            debugContext.log(DebugContext.VERY_DETAILED_LEVEL, "Entered method: %s.", mName);
            for (InvokeTypeFlow invoke : m.getTypeFlow().getInvokes()) {
                for (AnalysisMethod callee : invoke.getCallees()) {
                    Set<AnalysisMethod> parents = visited.get(callee);
                    String calleeName = getMethodName(callee);
                    debugContext.log(DebugContext.VERY_DETAILED_LEVEL, "Callee: %s, new: %b.", calleeName, parents == null);
                    if (parents == null) {
                        parents = new HashSet<>();
                        visited.put(callee, parents);
                        if (targets.contains(callee)) {
                            parents.add(m);
                            return true;
                        }
                        boolean add = callGraphImpl(callee, targets, visited, path, debugContext);
                        if (add) {
                            parents.add(m);
                            debugContext.log(DebugContext.VERY_DETAILED_LEVEL, "Added callee: %s for %s.", calleeName, mName);
                        }
                        vote |= add;
                    } else if (isBacktraceOverLanguage(callee, path) != Boolean.FALSE) {
                        parents.add(m);
                        debugContext.log(DebugContext.VERY_DETAILED_LEVEL, "Added backtrace callee: %s for %s.", calleeName, mName);
                        vote |= true;
                    } else {
                        vote |= true;
                        if (debugContext.isLogEnabled(DebugContext.VERY_DETAILED_LEVEL)) {
                            debugContext.log(DebugContext.VERY_DETAILED_LEVEL, "Ignoring backtrace callee: %s for %s.", calleeName, mName);
                        }
                    }
                }
            }
            debugContext.log(DebugContext.VERY_DETAILED_LEVEL, "Exited method: %s.", mName);
            return vote;
        } finally {
            path.removeFirst();
        }
    }

    private Boolean isBacktraceOverLanguage(AnalysisMethod method, Deque<AnalysisMethod> path) {
        if (!path.contains(method)) {
            return null;
        }
        for (Iterator<AnalysisMethod> it = path.descendingIterator(); it.hasNext();) {
            AnalysisMethod pe = it.next();
            if (method.equals(pe)) {
                return false;
            }
            if (isLanguageClass(pe)) {
                return true;
            }
        }
        throw new IllegalStateException();
    }

    private int printCallGraphs(
                    PrintWriter out,
                    AnalysisMethod m,
                    int maxDepth,
                    int maxReports,
                    Map<AnalysisMethod, Set<AnalysisMethod>> callGraph,
                    Set<CallGraphFilter> contextFilters,
                    LinkedHashSet<AnalysisMethod> visited,
                    int depth,
                    int noReports) {
        int useNoReports = noReports;
        if (useNoReports >= maxReports) {
            return useNoReports;
        }
        if (isCompilerClass(m)) {
            return useNoReports;
        }
        if (isExcludedClass(m)) {
            return useNoReports;
        }
        if (!visited.contains(m)) {
            visited.add(m);
            try {
                Set<AnalysisMethod> callers = callGraph.get(m);
                if (depth > maxDepth) {
                    if (!callers.isEmpty()) {
                        useNoReports = printCallGraphs(out, callers.iterator().next(), maxDepth, maxReports, callGraph, contextFilters, visited, depth + 1, useNoReports);
                    }
                } else if (isLanguageClass(m)) {
                    StringBuilder builder = new StringBuilder();
                    for (AnalysisMethod call : visited) {
                        builder.append(call.asStackTraceElement(0)).append('\n');
                    }
                    out.println(builder);
                    useNoReports++;
                } else {
                    nextCaller: for (AnalysisMethod caller : callers) {
                        for (CallGraphFilter filter : contextFilters) {
                            if (filter.test(m, caller, visited)) {
                                continue nextCaller;
                            }
                        }
                        useNoReports = printCallGraphs(out, caller, maxDepth, maxReports, callGraph, contextFilters, visited, depth + 1, useNoReports);
                    }
                }
            } finally {
                visited.remove(m);
            }
        }
        return useNoReports;
    }

    private boolean isLanguageClass(AnalysisMethod method) {
        return isClassInPackage(getClassName(method), languagePackages);
    }

    private static boolean isCompilerClass(AnalysisMethod method) {
        return isClassInPackage(getClassName(method), compilerPkgs);
    }

    private boolean isExcludedClass(AnalysisMethod method) {
        if (isClassInPackage(getClassName(method), excludedPackages)) {
            return true;
        }
        return whiteList.contains(method);
    }

    private static boolean isClassInPackage(String javaName, Collection<? extends String> packages) {
        for (String pkg : packages) {
            if (javaName.startsWith(pkg)) {
                return true;
            }
        }
        return false;
    }

    private static Set<AnalysisMethod> findMethods(BigBang bigBang, Class<?> owner, Predicate<ResolvedJavaMethod> filter) {
        AnalysisType clazz = bigBang.forClass(owner);
        if (clazz != null) {
            return findMethods(bigBang, clazz, filter);
        }
        return Collections.emptySet();
    }

    static Set<AnalysisMethod> findMethods(BigBang bigBang, AnalysisType owner, Predicate<ResolvedJavaMethod> filter) {
        Set<AnalysisMethod> result = new HashSet<>();
        for (ResolvedJavaMethod m : owner.getWrappedWithoutResolve().getDeclaredMethods()) {
            if (filter.test(m)) {
                result.add(bigBang.getUniverse().lookup(m));
            }
        }
        return result;
    }

    private static String getMethodName(AnalysisMethod method) {
        return method.format("%H.%n(%p)");
    }

    private static String getClassName(AnalysisMethod method) {
        return method.getDeclaringClass().toJavaName();
    }

    private interface CallGraphFilter {
        boolean test(AnalysisMethod method, AnalysisMethod caller, LinkedHashSet<AnalysisMethod> trace);
    }

    private static final class SafeInterruptRecognizer implements CallGraphFilter {

        private final ResolvedJavaMethod threadInterrupt;
        private final ResolvedJavaMethod threadCurrentThread;

        SafeInterruptRecognizer(BigBang bigBang) {
            Set<AnalysisMethod> methods = findMethods(bigBang, Thread.class, (m) -> m.getName().equals("interrupt"));
            if (methods.size() != 1) {
                throw new IllegalStateException("Failed to lookup Thread.interrupt().");
            }
            threadInterrupt = methods.iterator().next();
            methods = findMethods(bigBang, Thread.class, (m) -> m.getName().equals("currentThread"));
            if (methods.size() != 1) {
                throw new IllegalStateException("Failed to lookup Thread.currentThread().");
            }
            threadCurrentThread = methods.iterator().next();
        }

        @Override
        public boolean test(AnalysisMethod method, AnalysisMethod caller, LinkedHashSet<AnalysisMethod> trace) {
            Boolean res = null;
            if (threadInterrupt.equals(method)) {
                StructuredGraph graph = caller.getTypeFlow().getGraph();
                for (Invoke invoke : graph.getInvokes()) {
                    if (threadInterrupt.equals(invoke.callTarget().targetMethod())) {
                        ValueNode node = invoke.getReceiver();
                        if (node instanceof PiNode) {
                            node = ((PiNode) node).getOriginalNode();
                            if (node instanceof Invoke) {
                                boolean isCurrentThread = threadCurrentThread.equals(((Invoke) node).callTarget().targetMethod());
                                res = res == null ? isCurrentThread : (res && isCurrentThread);
                            }
                        }
                    }
                }
            }
            res = res == null ? false : res;
            return res;
        }
    }

    private final class SafePrivilegedRecognizer implements CallGraphFilter {

        private final Set<AnalysisMethod> dopriviledged;

        SafePrivilegedRecognizer(BigBang bigbang) {
            this.dopriviledged = findMethods(bigbang, java.security.AccessController.class, (m) -> m.getName().equals("doPrivileged") || m.getName().equals("doPrivilegedWithCombiner"));
        }

        @Override
        public boolean test(AnalysisMethod method, AnalysisMethod caller, LinkedHashSet<AnalysisMethod> trace) {
            return this.dopriviledged.contains(method) && !isLanguageClass(caller);
        }
    }

    private static final class ResourceAsOptionDecorator extends HostedOptionKey<String[]> {

        ResourceAsOptionDecorator(String defaultValue) {
            super(new String[]{defaultValue});
        }

        @Override
        public String[] getValue(OptionValues values) {
            return getDefaultValue();
        }
    }
}

@TargetClass(value = java.lang.SecurityManager.class, onlyWith = PermissionsFeature.IsEnabled.class)
final class Target_java_lang_SecurityManager {

    @Substitute
    @SuppressWarnings("unused")
    private void checkSecurityAccess(String target) {
    }

    @Substitute
    private void checkSetFactory() {
    }

    @Substitute
    @SuppressWarnings("unused")
    private void checkPackageDefinition(String pkg) {
    }

    @Substitute
    @SuppressWarnings("unused")
    private void checkPackageAccess(String pkg) {
    }

    @Substitute
    private void checkPrintJobAccess() {
    }

    @Substitute
    @SuppressWarnings("unused")
    private void checkPropertyAccess(String key) {
    }

    @Substitute
    private void checkPropertiesAccess() {
    }

    @Substitute
    @SuppressWarnings("unused")
    private void checkMulticast(InetAddress maddr) {
    }

    @Substitute
    @SuppressWarnings("unused")
    private void checkAccept(String host, int port) {
    }

    @Substitute
    @SuppressWarnings("unused")
    private void checkListen(int port) {
    }

    @Substitute
    @SuppressWarnings("unused")
    private void checkConnect(String host, int port, Object context) {
    }

    @Substitute
    @SuppressWarnings("unused")
    private void checkConnect(String host, int port) {
    }

    @Substitute
    @SuppressWarnings("unused")
    private void checkDelete(String file) {
    }

    @Substitute
    @SuppressWarnings("unused")
    private void checkWrite(String file) {
    }

    @Substitute
    @SuppressWarnings("unused")
    private void checkWrite(FileDescriptor fd) {
    }

    @Substitute
    @SuppressWarnings("unused")
    private void checkRead(String file, Object context) {
    }

    @Substitute
    @SuppressWarnings("unused")
    private void checkRead(String file) {
    }

    @Substitute
    @SuppressWarnings("unused")
    private void checkRead(FileDescriptor fd) {
    }

    @Substitute
    @SuppressWarnings("unused")
    private void checkLink(String lib) {
    }

    @Substitute
    @SuppressWarnings("unused")
    private void checkExec(String cmd) {
    }

    @Substitute
    @SuppressWarnings("unused")
    private void checkExit(int status) {
    }

    @Substitute
    @SuppressWarnings("unused")
    private void checkAccess(ThreadGroup g) {
    }

    @Substitute
    @SuppressWarnings("unused")
    private void checkAccess(Thread t) {
    }

    @Substitute
    private void checkCreateClassLoader() {
    }

    @Substitute
    @SuppressWarnings("unused")
    private void checkPermission(Permission perm, Object context) {
    }

    @Substitute
    @SuppressWarnings("unused")
    private void checkPermission(Permission perm) {
    }
}

final class SecurityManagerHolder {
    static final SecurityManager SECURITY_MANAGER = new SecurityManager();
}

@TargetClass(value = java.lang.System.class, onlyWith = PermissionsFeature.IsEnabled.class)
final class Target_java_lang_System {
    @Substitute
    private static SecurityManager getSecurityManager() {
        return SecurityManagerHolder.SECURITY_MANAGER;
    }
}
