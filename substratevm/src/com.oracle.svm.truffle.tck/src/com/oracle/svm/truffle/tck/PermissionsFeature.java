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

import static com.oracle.graal.pointsto.reports.ReportUtils.report;

import java.io.FileDescriptor;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.Permission;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.java.NewInstanceNode;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

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
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.config.ConfigurationParserUtils;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import org.graalvm.polyglot.io.FileSystem;

/**
 * A Truffle TCK {@code Feature} detecting privileged calls done by Truffle language. The
 * {@code PermissionsFeature} finds calls of privileged methods originating in Truffle language. The
 * calls going through {@code Truffle} library, GraalVM SDK or compiler are treated as safe calls
 * and are not reported.
 * <p>
 * To execute the {@code PermissionsFeature} you need to enable it using
 * {@code --features=com.oracle.svm.truffle.tck.PermissionsFeature} native-image option, specify
 * report file using {@code -H:TruffleTCKPermissionsReportFile} option and specify the language
 * packages by {@code -H:TruffleTCKPermissionsLanguagePackages} option. You also need to disable
 * folding of {@code System.getSecurityManager} using {@code -H:-FoldSecurityManagerGetter} option.
 */
public class PermissionsFeature implements Feature {

    private static final String CONFIG = "truffle-language-permissions-config.json";

    public static class Options {
        @Option(help = "Path to file where to store report of Truffle language privilege access.") public static final HostedOptionKey<String> TruffleTCKPermissionsReportFile = new HostedOptionKey<>(
                        null);

        @Option(help = "Comma separated list of exclude files.") public static final HostedOptionKey<String[]> TruffleTCKPermissionsExcludeFiles = new HostedOptionKey<>(null);

        @Option(help = "Maximal depth of a stack trace.", type = OptionType.Expert) public static final HostedOptionKey<Integer> TruffleTCKPermissionsMaxStackTraceDepth = new HostedOptionKey<>(
                        -1);

        @Option(help = "Maximum number of errounous privileged accesses reported.", type = OptionType.Expert) public static final HostedOptionKey<Integer> TruffleTCKPermissionsMaxErrors = new HostedOptionKey<>(
                        100);
    }

    /**
     * Predicate to enable substitutions needed by the {@link PermissionsFeature}.
     */
    static final class IsEnabled implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return ImageSingletons.contains(PermissionsFeature.class);
        }
    }

    /**
     * List of safe packages.
     */
    private static final Set<String> compilerPackages;
    static {
        compilerPackages = new HashSet<>();
        compilerPackages.add("org.graalvm.");
        compilerPackages.add("com.oracle.graalvm.");
        compilerPackages.add("com.oracle.truffle.api.");
        compilerPackages.add("com.oracle.truffle.polyglot.");
        compilerPackages.add("com.oracle.truffle.nfi.");
        compilerPackages.add("com.oracle.truffle.object.");
    }

    private static final Set<ClassLoader> systemClassLoaders;
    static {
        systemClassLoaders = new HashSet<>();
        for (ClassLoader cl = ClassLoader.getSystemClassLoader(); cl != null; cl = cl.getParent()) {
            systemClassLoaders.add(cl);
        }
    }

    /**
     * Path to store report into.
     */
    private Path reportFilePath;
    /**
     * Methods which are allowed to do privileged calls without being reported.
     */
    private Set<AnalysisMethod> whiteList;

    /**
     * Marker interface for SVM generated accessor classes which are opaque for permission analysis.
     */
    private AnalysisType reflectionProxy;

    @Override
    public void duringSetup(DuringSetupAccess access) {
        if (SubstrateOptions.FoldSecurityManagerGetter.getValue()) {
            UserError.abort(getClass().getSimpleName() + " requires -H:-FoldSecurityManagerGetter option.");
        }
        String reportFile = Options.TruffleTCKPermissionsReportFile.getValue();
        if (reportFile == null) {
            UserError.abort("Path to report file must be given by -H:TruffleTCKPermissionsReportFile option.");
        }
        reportFilePath = Paths.get(reportFile);

        FeatureImpl.DuringSetupAccessImpl accessImpl = (FeatureImpl.DuringSetupAccessImpl) access;
        accessImpl.getHostVM().keepAnalysisGraphs();
    }

    @Override
    @SuppressWarnings("try")
    public void afterAnalysis(AfterAnalysisAccess access) {
        try {
            if (Files.exists(reportFilePath) && Files.size(reportFilePath) > 0) {
                Files.newOutputStream(reportFilePath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }
        } catch (IOException ioe) {
            throw UserError.abort("Cannot delete existing report file " + reportFilePath + ".");
        }
        FeatureImpl.AfterAnalysisAccessImpl accessImpl = (FeatureImpl.AfterAnalysisAccessImpl) access;
        DebugContext debugContext = accessImpl.getDebugContext();
        try (DebugContext.Scope s = debugContext.scope(getClass().getSimpleName())) {
            BigBang bigbang = accessImpl.getBigBang();
            WhiteListParser parser = new WhiteListParser(accessImpl.getImageClassLoader(), bigbang);
            ConfigurationParserUtils.parseAndRegisterConfigurations(parser,
                            accessImpl.getImageClassLoader(),
                            getClass().getSimpleName(),
                            Options.TruffleTCKPermissionsExcludeFiles,
                            new ResourceAsOptionDecorator(getClass().getPackage().getName().replace('.', '/') + "/resources/jre.json"),
                            CONFIG);
            reflectionProxy = bigbang.forClass("com.oracle.svm.reflect.helpers.ReflectionProxy");
            if (reflectionProxy == null) {
                UserError.abort("Cannot load ReflectionProxy type");
            }
            whiteList = parser.getLoadedWhiteList();
            Set<AnalysisMethod> deniedMethods = new HashSet<>();
            deniedMethods.addAll(findMethods(bigbang, SecurityManager.class, (m) -> m.getName().startsWith("check")));
            deniedMethods.addAll(findMethods(bigbang, sun.misc.Unsafe.class, (m) -> m.isPublic()));
            // The type of the host Java NIO FileSystem.
            // The FileSystem obtained from the FileSystem.newDefaultFileSystem() is in the Truffle
            // package but
            // can be directly used by a language. We need to include it into deniedMethods.
            deniedMethods.addAll(findMethods(bigbang, FileSystem.newDefaultFileSystem().getClass(), (m) -> m.isPublic()));
            if (!deniedMethods.isEmpty()) {
                Map<AnalysisMethod, Set<AnalysisMethod>> cg = callGraph(bigbang, deniedMethods, debugContext);
                List<List<AnalysisMethod>> report = new ArrayList<>();
                Set<CallGraphFilter> contextFilters = new HashSet<>();
                Collections.addAll(contextFilters, new SafeInterruptRecognizer(bigbang), new SafePrivilegedRecognizer(bigbang),
                                new SafeServiceLoaderRecognizer(bigbang, accessImpl.getImageClassLoader()));
                int maxStackDepth = Options.TruffleTCKPermissionsMaxStackTraceDepth.getValue();
                maxStackDepth = maxStackDepth == -1 ? Integer.MAX_VALUE : maxStackDepth;
                for (AnalysisMethod deniedMethod : deniedMethods) {
                    if (cg.containsKey(deniedMethod)) {
                        collectViolations(report, deniedMethod,
                                        maxStackDepth,
                                        Options.TruffleTCKPermissionsMaxErrors.getValue(),
                                        cg, contextFilters,
                                        new LinkedHashSet<>(), 1, 0);
                    }
                }
                if (!report.isEmpty()) {
                    report(
                                    "detected privileged calls originated in language packages ",
                                    reportFilePath,
                                    (pw) -> {
                                        StringBuilder builder = new StringBuilder();
                                        for (List<AnalysisMethod> callPath : report) {
                                            for (AnalysisMethod call : callPath) {
                                                builder.append(call.asStackTraceElement(0)).append('\n');
                                            }
                                            builder.append('\n');
                                        }
                                        pw.print(builder);
                                    });
                }
            }
        }
    }

    /**
     * Creates an inverted call graph for methods given by {@code targets} parameter. For each
     * called method in {@code targets} or transitive caller of {@code targets} the resulting
     * {@code Map} contains an entry holding all direct callers of the method in the entry value.
     *
     * @param bigbang the {@link BigBang}
     * @param targets the target methods to build call graph for
     * @param debugContext the {@link DebugContext}
     */
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
            boolean callPathContainsTarget = false;
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
                            callPathContainsTarget = true;
                            continue;
                        }
                        boolean add = callGraphImpl(callee, targets, visited, path, debugContext);
                        if (add) {
                            parents.add(m);
                            debugContext.log(DebugContext.VERY_DETAILED_LEVEL, "Added callee: %s for %s.", calleeName, mName);
                        }
                        callPathContainsTarget |= add;
                    } else if (!isBacktrace(callee, path) || isBackTraceOverLanguageMethod(callee, path)) {
                        parents.add(m);
                        debugContext.log(DebugContext.VERY_DETAILED_LEVEL, "Added backtrace callee: %s for %s.", calleeName, mName);
                        callPathContainsTarget = true;
                    } else {
                        if (debugContext.isLogEnabled(DebugContext.VERY_DETAILED_LEVEL)) {
                            debugContext.log(DebugContext.VERY_DETAILED_LEVEL, "Ignoring backtrace callee: %s for %s.", calleeName, mName);
                        }
                    }
                }
            }
            debugContext.log(DebugContext.VERY_DETAILED_LEVEL, "Exited method: %s.", mName);
            return callPathContainsTarget;
        } finally {
            path.removeFirst();
        }
    }

    /**
     * Checks if the method is already included on call path, in other words it's a recursive call.
     *
     * @param method the {@link AnalysisMethod} to check
     * @param path the current call path
     */
    private static boolean isBacktrace(AnalysisMethod method, Deque<AnalysisMethod> path) {
        return path.contains(method);
    }

    /**
     * Checks if the back call of given method crosses some language method on given call path. If
     * the back call crosses a language method the call has to be included into the call graph, the
     * crossed language method is the start method of a violation. Example: P privileged method, L
     * language method.
     *
     * <pre>
     * G((A,L),(A,P),(L,C),(C,A),(C,D))
     * </pre>
     *
     * The violation is L->C->A->P
     *
     * @param method the method being invoked
     * @param path the current call path
     * @return {@code true} if the call of given method crosses some language method.
     */
    private static boolean isBackTraceOverLanguageMethod(AnalysisMethod method, Deque<AnalysisMethod> path) {
        if (!isCompilerClass(method) && !isSystemClass(method)) {
            return false;
        }
        boolean found = false;
        for (Iterator<AnalysisMethod> it = path.descendingIterator(); it.hasNext();) {
            AnalysisMethod pe = it.next();
            if (method.equals(pe)) {
                found = true;
            } else if (found && !isCompilerClass(pe) && !isSystemClass(pe)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Collects the calls of privileged methods originated in Truffle language.
     *
     * @param report the list to collect violations into
     * @param m currently processed method
     * @param maxDepth maximal call trace depth
     * @param maxReports maximal number of reports
     * @param callGraph call graph obtained from
     *            {@link PermissionsFeature#callGraph(com.oracle.graal.pointsto.BigBang, java.util.Set, org.graalvm.compiler.debug.DebugContext)}
     * @param contextFilters filters removing known valid calls
     * @param visited visited methods
     * @param depth current depth
     */
    private int collectViolations(
                    List<? super List<AnalysisMethod>> report,
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
        if (depth > 1) {
            // The denied method can be a compiler method
            if (isCompilerClass(m)) {
                return useNoReports;
            }
            // The denied method cannot be excluded by a white list
            if (isExcludedClass(m)) {
                return useNoReports;
            }
        }
        if (!visited.contains(m)) {
            visited.add(m);
            try {
                Set<AnalysisMethod> callers = callGraph.get(m);
                if (depth > maxDepth) {
                    if (!callers.isEmpty()) {
                        useNoReports = collectViolations(report, callers.iterator().next(), maxDepth, maxReports, callGraph, contextFilters, visited, depth + 1, useNoReports);
                    }
                } else if (!isSystemClass(m) && !isReflectionProxy(m)) {
                    List<AnalysisMethod> callPath = new ArrayList<>(visited);
                    report.add(callPath);
                    useNoReports++;
                } else {
                    nextCaller: for (AnalysisMethod caller : callers) {
                        for (CallGraphFilter filter : contextFilters) {
                            if (filter.test(m, caller, visited)) {
                                continue nextCaller;
                            }
                        }
                        useNoReports = collectViolations(report, caller, maxDepth, maxReports, callGraph, contextFilters, visited, depth + 1, useNoReports);
                    }
                }
            } finally {
                visited.remove(m);
            }
        }
        return useNoReports;
    }

    /**
     * Tests if the given {@link AnalysisMethod} comes from {@code ReflectionProxy} implementation.
     *
     * @param method the {@link AnalysisMethod} to check
     */
    private boolean isReflectionProxy(AnalysisMethod method) {
        for (AnalysisType iface : method.getDeclaringClass().getInterfaces()) {
            if (iface.equals(reflectionProxy)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Tests if the given {@link AnalysisMethod} is from system {@link ClassLoader}.
     *
     * @param method the {@link AnalysisMethod} to check
     */
    private static boolean isSystemClass(AnalysisMethod method) {
        Class<?> clz = method.getDeclaringClass().getJavaClass();
        if (clz == null) {
            return false;
        }
        return clz.getClassLoader() == null || systemClassLoaders.contains(clz.getClassLoader());
    }

    /**
     * Tests if the given {@link AnalysisMethod} is from Truffle library, GraalVM SDK or compiler
     * package.
     *
     * @param method the {@link AnalysisMethod} to check
     */
    private static boolean isCompilerClass(AnalysisMethod method) {
        return isClassInPackage(getClassName(method), compilerPackages);
    }

    /**
     * Tests if the given {@link AnalysisMethod} is excluded by white list.
     *
     * @param method the {@link AnalysisMethod} to check
     */
    private boolean isExcludedClass(AnalysisMethod method) {
        return whiteList.contains(method);
    }

    /**
     * Tests if a class of given name transitively belongs to some package given by {@code packages}
     * parameter.
     *
     * @param javaName the {@link AnalysisMethod} to check
     * @param packages the list of packages
     */
    private static boolean isClassInPackage(String javaName, Collection<? extends String> packages) {
        for (String pkg : packages) {
            if (javaName.startsWith(pkg)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Finds methods declared in {@code owner} class using {@code filter} predicate.
     *
     * @param bigBang the {@link BigBang}
     * @param owner the class which methods should be listed
     * @param filter the predicate filtering methods declared in {@code owner}
     * @return the methods accepted by {@code filter}
     * @throws IllegalStateException if owner cannot be resolved
     */
    private static Set<AnalysisMethod> findMethods(BigBang bigBang, Class<?> owner, Predicate<ResolvedJavaMethod> filter) {
        AnalysisType clazz = bigBang.forClass(owner);
        if (clazz == null) {
            throw new IllegalStateException("Cannot resolve " + owner.getName() + ".");
        }
        return findMethods(bigBang, clazz, filter);
    }

    /**
     * Finds methods declared in {@code owner} {@link AnalysisType} using {@code filter} predicate.
     *
     * @param bigBang the {@link BigBang}
     * @param owner the {@link AnalysisType} which methods should be listed
     * @param filter the predicate filtering methods declared in {@code owner}
     * @return the methods accepted by {@code filter}
     */
    static Set<AnalysisMethod> findMethods(BigBang bigBang, AnalysisType owner, Predicate<ResolvedJavaMethod> filter) {
        return findImpl(bigBang, owner.getWrappedWithoutResolve().getDeclaredMethods(), filter);
    }

    /**
     * Finds constructors declared in {@code owner} {@link AnalysisType} using {@code filter}
     * predicate.
     *
     * @param bigBang the {@link BigBang}
     * @param owner the {@link AnalysisType} which constructors should be listed
     * @param filter the predicate filtering constructors declared in {@code owner}
     * @return the constructors accepted by {@code filter}
     */
    static Set<AnalysisMethod> findConstructors(BigBang bigBang, AnalysisType owner, Predicate<ResolvedJavaMethod> filter) {
        return findImpl(bigBang, owner.getWrappedWithoutResolve().getDeclaredConstructors(), filter);
    }

    private static Set<AnalysisMethod> findImpl(BigBang bigBang, ResolvedJavaMethod[] methods, Predicate<ResolvedJavaMethod> filter) {
        Set<AnalysisMethod> result = new HashSet<>();
        for (ResolvedJavaMethod m : methods) {
            if (filter.test(m)) {
                result.add(bigBang.getUniverse().lookup(m));
            }
        }
        return result;
    }

    /**
     * Returns a method name in the format: {@code ownerFQN.name(parameters)}.
     *
     * @param method to create a name for
     */
    private static String getMethodName(AnalysisMethod method) {
        return method.format("%H.%n(%p)");
    }

    /**
     * Returns a fully qualified name of the {@code method} owner.
     *
     * @param method to obtain an owner name for
     */
    private static String getClassName(AnalysisMethod method) {
        return method.getDeclaringClass().toJavaName();
    }

    /**
     * Filter to filter out known valid calls, included by points to analysis, from the report.
     */
    private interface CallGraphFilter {
        boolean test(AnalysisMethod method, AnalysisMethod caller, LinkedHashSet<AnalysisMethod> trace);
    }

    /**
     * Filters out {@link Thread#interrupt()} calls done on {@link Thread#currentThread()}.
     */
    private static final class SafeInterruptRecognizer implements CallGraphFilter {

        private final SVMHost hostVM;
        private final ResolvedJavaMethod threadInterrupt;
        private final ResolvedJavaMethod threadCurrentThread;

        SafeInterruptRecognizer(BigBang bigBang) {
            this.hostVM = (SVMHost) bigBang.getHostVM();

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
                StructuredGraph graph = hostVM.getAnalysisGraph(caller);
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

    /**
     * Filters out {@code AccessController#doPrivileged} done by JRE.
     */
    private final class SafePrivilegedRecognizer implements CallGraphFilter {

        private final SVMHost hostVM;
        private final Set<AnalysisMethod> dopriviledged;

        SafePrivilegedRecognizer(BigBang bigbang) {
            this.hostVM = (SVMHost) bigbang.getHostVM();
            this.dopriviledged = findMethods(bigbang, java.security.AccessController.class, (m) -> m.getName().equals("doPrivileged") || m.getName().equals("doPrivilegedWithCombiner"));
        }

        @Override
        public boolean test(AnalysisMethod method, AnalysisMethod caller, LinkedHashSet<AnalysisMethod> trace) {
            if (!dopriviledged.contains(method)) {
                return false;
            }
            boolean safeClass = isCompilerClass(caller) || isSystemClass(caller);
            if (safeClass) {
                return true;
            }
            StructuredGraph graph = hostVM.getAnalysisGraph(caller);
            for (Invoke invoke : graph.getInvokes()) {
                if (method.equals(invoke.callTarget().targetMethod())) {
                    NodeInputList<ValueNode> args = invoke.callTarget().arguments();
                    if (args.isEmpty()) {
                        return false;
                    }
                    ValueNode arg0 = args.get(0);
                    if (!(arg0 instanceof NewInstanceNode)) {
                        return false;
                    }
                    ResolvedJavaType newType = ((NewInstanceNode) arg0).instanceClass();
                    AnalysisMethod methodCalledByAccessController = findPrivilegedEntryPoint(method, trace);
                    if (newType == null || methodCalledByAccessController == null) {
                        return false;
                    }
                    if (newType.equals(methodCalledByAccessController.getDeclaringClass())) {
                        return false;
                    }
                }
            }
            return true;
        }

        /**
         * Finds an entry point to {@code PrivilegedAction} called by {@code dopriviledgedMethod}.
         */
        private AnalysisMethod findPrivilegedEntryPoint(AnalysisMethod dopriviledgedMethod, LinkedHashSet<AnalysisMethod> trace) {
            AnalysisMethod ep = null;
            for (AnalysisMethod m : trace) {
                if (dopriviledgedMethod.equals(m)) {
                    return ep;
                }
                ep = m;
            }
            return null;
        }
    }

    private final class SafeServiceLoaderRecognizer implements CallGraphFilter {

        private final ResolvedJavaMethod nextService;
        private final ImageClassLoader imageClassLoader;

        SafeServiceLoaderRecognizer(BigBang bigBang, ImageClassLoader imageClassLoader) {
            AnalysisType serviceLoaderIterator = bigBang.forClass("java.util.ServiceLoader$LazyIterator");
            Set<AnalysisMethod> methods = findMethods(bigBang, serviceLoaderIterator, (m) -> m.getName().equals("nextService"));
            if (methods.size() != 1) {
                throw new IllegalStateException("Failed to lookup ServiceLoader$LazyIterator.nextService().");
            }
            this.nextService = methods.iterator().next();
            this.imageClassLoader = imageClassLoader;
        }

        @Override
        public boolean test(AnalysisMethod method, AnalysisMethod caller, LinkedHashSet<AnalysisMethod> trace) {
            if (nextService.equals(method)) {
                AnalysisType instantiatedType = findInstantiatedType(trace);
                if (instantiatedType != null) {
                    if (!isRegiseredInServiceLoader(instantiatedType)) {
                        return true;
                    }
                }
            }
            return false;
        }

        /**
         * Finds last constructor invocation.
         */
        private AnalysisType findInstantiatedType(LinkedHashSet<AnalysisMethod> trace) {
            AnalysisType res = null;
            for (AnalysisMethod m : trace) {
                if ("<init>".equals(m.getName())) {
                    res = m.getDeclaringClass();
                }
            }
            return res;
        }

        /**
         * Finds if the given type may be instantiated by ServiceLoader.
         */
        private boolean isRegiseredInServiceLoader(AnalysisType type) {
            String resource = String.format("META-INF/services/%s", type.toClassName());
            if (imageClassLoader.getClassLoader().getResource(resource) != null) {
                return true;
            }
            for (AnalysisType ifc : type.getInterfaces()) {
                if (isRegiseredInServiceLoader(ifc)) {
                    return true;
                }
            }
            AnalysisType superClz = type.getSuperclass();
            if (superClz != null) {
                return isRegiseredInServiceLoader(superClz);
            }
            return false;
        }
    }

    /**
     * Options facade for a resource containing the JRE white list.
     */
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
