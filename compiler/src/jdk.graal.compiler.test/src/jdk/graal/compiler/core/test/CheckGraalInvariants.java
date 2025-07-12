/*
 * Copyright (c) 2014, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.test;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.graalvm.word.LocationIdentity;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import jdk.graal.compiler.api.replacements.Snippet;
import jdk.graal.compiler.api.replacements.Snippet.ConstantParameter;
import jdk.graal.compiler.api.replacements.Snippet.NonNullParameter;
import jdk.graal.compiler.api.replacements.Snippet.VarargsParameter;
import jdk.graal.compiler.api.test.Graal;
import jdk.graal.compiler.api.test.ModuleSupport;
import jdk.graal.compiler.bytecode.BridgeMethodUtils;
import jdk.graal.compiler.core.CompilerThreadFactory;
import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.DebugContext.Builder;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.graphbuilderconf.ClassInitializationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.memory.MemoryKill;
import jdk.graal.compiler.nodes.memory.MultiMemoryKill;
import jdk.graal.compiler.nodes.memory.SingleMemoryKill;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionDescriptor;
import jdk.graal.compiler.options.OptionDescriptors;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.options.OptionsParser;
import jdk.graal.compiler.phases.OptimisticOptimizations;
import jdk.graal.compiler.phases.PhaseSuite;
import jdk.graal.compiler.phases.VerifyPhase;
import jdk.graal.compiler.phases.VerifyPhase.VerificationError;
import jdk.graal.compiler.phases.contract.VerifyNodeCosts;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.runtime.RuntimeProvider;
import jdk.graal.compiler.serviceprovider.GraalServices;
import jdk.graal.compiler.test.AddExports;
import jdk.graal.compiler.test.SubprocessUtil;
import jdk.graal.compiler.util.EconomicHashMap;
import jdk.graal.compiler.util.EconomicHashSet;
import jdk.internal.misc.Unsafe;
import jdk.vm.ci.code.BailoutException;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.Register.RegisterCategory;
import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.JavaField;
import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.SpeculationLog;
import jdk.vm.ci.meta.Value;

/**
 * Checks that all Graal classes comply with global invariants such as using
 * {@link Object#equals(Object)} to compare certain types instead of identity comparisons.
 */
@AddExports({"java.base/jdk.internal.misc"})
public class CheckGraalInvariants extends GraalCompilerTest {

    /**
     * Magic token to denote the classes in the Java runtime image (i.e. in the {@code jrt:/} file
     * system).
     */
    public static final String JRT_CLASS_PATH_ENTRY = "<jrt>";

    private static boolean shouldVerifyEquals(ResolvedJavaMethod m) {
        if (m.getName().equals("identityEquals")) {
            ResolvedJavaType c = m.getDeclaringClass();
            if (c.getName().equals("Ljdk/vm/ci/meta/AbstractValue;") || c.getName().equals("jdk/vm/ci/meta/Value")) {
                return false;
            }
        }

        return true;
    }

    public static void main(String[] args) {

    }

    public static String relativeFileName(String absolutePath) {
        int lastFileSeparatorIndex = absolutePath.lastIndexOf(File.separator);
        return absolutePath.substring(Math.max(lastFileSeparatorIndex, 0));
    }

    public static class InvariantsTool {

        protected boolean shouldProcess(String classpathEntry) {
            if (classpathEntry.equals(JRT_CLASS_PATH_ENTRY)) {
                return true;
            }
            if (classpathEntry.endsWith(".jar")) {
                String name = new File(classpathEntry).getName();
                return name.contains("graal");
            }
            return false;
        }

        Path getLibgraalJar() {
            assert shouldVerifyLibGraalInvariants();
            String javaClassPath = System.getProperty("java.class.path");
            if (javaClassPath != null) {
                String[] jcp = javaClassPath.split(File.pathSeparator);
                for (String s : jcp) {
                    Path path = Path.of(s);
                    if (s.endsWith(".jar")) {
                        Path libgraal = path.getParent().resolve("libgraal.jar");
                        if (Files.exists(libgraal)) {
                            return libgraal;
                        }
                    }
                }
                throw new AssertionError(String.format("Could not find libgraal.jar as sibling of a jar on java.class.path:%n  %s",
                                Stream.of(jcp).sorted().collect(Collectors.joining("\n  "))));
            }
            throw new AssertionError("The java.class.path system property is missing");
        }

        protected List<String> getClassPath() {
            List<String> classpath = new ArrayList<>();
            classpath.add(JRT_CLASS_PATH_ENTRY);
            String upgradeModulePath = System.getProperty("jdk.module.upgrade.path");
            if (upgradeModulePath != null) {
                classpath.addAll(List.of(upgradeModulePath.split(File.pathSeparator)));
            }

            if (shouldVerifyLibGraalInvariants()) {
                classpath.add(getLibgraalJar().toString());
            }
            return classpath;
        }

        protected boolean shouldLoadClass(String className) {
            if (className.equals("module-info") || className.startsWith("META-INF.versions.")) {
                return false;
            }
            return true;
        }

        public boolean shouldVerifyLibGraalInvariants() {
            return true;
        }

        public boolean shouldVerifyFoldableMethods() {
            return true;
        }

        public void verifyCurrentTimeMillis(MetaAccessProvider meta, MethodCallTargetNode t, ResolvedJavaType declaringClass) {
            final ResolvedJavaType services = meta.lookupJavaType(GraalServices.class);
            if (!declaringClass.equals(services)) {
                throw new VerificationError(t, "Should use System.nanoTime() for measuring elapsed time or GraalServices.milliTimeStamp() for the time since the epoch");
            }
        }

        /**
         * Makes edits to the list of verifiers to be run.
         */
        @SuppressWarnings("unused")
        protected void updateVerifiers(List<VerifyPhase<CoreProviders>> verifiers) {
        }

        /**
         * Determines if {@code option} should be checked to ensure it has at least one usage.
         */
        public boolean shouldCheckUsage(OptionDescriptor option) {
            if (option.getOptionKey().getClass().isAnonymousClass()) {
                /*
                 * A derived option.
                 */
                return false;
            }
            return true;
        }

        public boolean checkAssertions() {
            return true;
        }

    }

    @Test
    public void test() {
        Assume.assumeFalse("JaCoCo causes failure", SubprocessUtil.isJaCoCoAttached()); // GR-50672
        assumeManagementLibraryIsLoadable();
        runTest(new InvariantsTool());
    }

    public static void runTest(InvariantsTool tool) {
        RuntimeProvider rt = Graal.getRequiredCapability(RuntimeProvider.class);
        Providers providers = rt.getHostBackend().getProviders();
        MetaAccessProvider metaAccess = providers.getMetaAccess();

        PhaseSuite<HighTierContext> graphBuilderSuite = new PhaseSuite<>();
        Plugins plugins = new Plugins(new InvocationPlugins());
        plugins.setClassInitializationPlugin(new DoNotInitializeClassInitializationPlugin());
        GraphBuilderConfiguration config = GraphBuilderConfiguration.getDefault(plugins).withEagerResolving(true).withUnresolvedIsError(true);
        graphBuilderSuite.appendPhase(new TestGraphBuilderPhase(config));
        HighTierContext context = new HighTierContext(providers, graphBuilderSuite, OptimisticOptimizations.NONE);

        Assume.assumeTrue(VerifyPhase.class.desiredAssertionStatus());

        List<String> classPath = tool.getClassPath();
        Assert.assertNotNull("Cannot find class path", classPath);

        final List<String> classNames = new ArrayList<>();
        for (String path : classPath) {
            if (tool.shouldProcess(path)) {
                try {
                    if (path.equals(JRT_CLASS_PATH_ENTRY)) {
                        for (String className : ModuleSupport.getJRTGraalClassNames()) {
                            if (isGSON(className) || isONNX(className)) {
                                continue;
                            }
                            classNames.add(className);
                        }
                    } else {
                        File file = new File(path);
                        if (!file.exists()) {
                            continue;
                        }
                        if (file.isDirectory()) {
                            Path root = file.toPath();
                            Files.walk(root).forEach(p -> {
                                String name = root.relativize(p).toString();
                                if (name.endsWith(".class") && !name.startsWith("META-INF/versions/")) {
                                    String className = name.substring(0, name.length() - ".class".length()).replace('/', '.');
                                    if (!(isInNativeImage(className) || isGSON(className) || isONNX(className))) {
                                        classNames.add(className);
                                    }
                                }
                            });
                        } else {
                            try (ZipFile zipFile = new ZipFile(file)) {
                                for (final Enumeration<? extends ZipEntry> entry = zipFile.entries(); entry.hasMoreElements();) {
                                    final ZipEntry zipEntry = entry.nextElement();
                                    String name = zipEntry.getName();
                                    if (name.endsWith(".class") && !name.startsWith("META-INF/versions/")) {
                                        String className = name.substring(0, name.length() - ".class".length()).replace('/', '.');
                                        if (isInNativeImage(className) || isGSON(className) || isONNX(className)) {
                                            continue;
                                        }
                                        classNames.add(className);
                                    }
                                }
                            }
                        }
                    }
                } catch (IOException ex) {
                    throw new AssertionError(ex);
                }
            }
        }
        Assert.assertFalse("Could not find graal jars on class path: " + classPath, classNames.isEmpty());

        // Allows a subset of methods to be checked through use of a system property
        String property = System.getProperty(CheckGraalInvariants.class.getName() + ".filters");
        String[] filters = property == null ? null : property.split(",");

        OptionValues options = getInitialOptions();
        CompilerThreadFactory factory = new CompilerThreadFactory("CheckInvariantsThread");
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        ThreadPoolExecutor executor = new ThreadPoolExecutor(availableProcessors, availableProcessors, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), factory);

        List<String> errors = Collections.synchronizedList(new ArrayList<>());

        List<VerifyPhase<CoreProviders>> verifiers = new ArrayList<>();

        // If you add a new type to test here, be sure to add appropriate
        // methods to the BadUsageWithEquals class below
        verifiers.add(new VerifyUsageWithEquals(Value.class));
        verifiers.add(new VerifyUsageWithEquals(Register.class));
        verifiers.add(new VerifyUsageWithEquals(RegisterCategory.class));
        verifiers.add(new VerifyUsageWithEquals(JavaType.class));
        verifiers.add(new VerifyUsageWithEquals(JavaMethod.class));
        verifiers.add(new VerifyUsageWithEquals(JavaField.class));
        verifiers.add(new VerifyUsageWithEquals(LocationIdentity.class));
        verifiers.add(new VerifyUsageWithEquals(LIRKind.class));
        verifiers.add(new VerifyUsageWithEquals(ArithmeticOpTable.class));
        verifiers.add(new VerifyUsageWithEquals(ArithmeticOpTable.Op.class));
        verifiers.add(new VerifyUsageWithEquals(SpeculationLog.Speculation.class, SpeculationLog.NO_SPECULATION));

        verifiers.add(new VerifySharedConstantEmptyArray());
        verifiers.add(new VerifyDebugUsage());
        verifiers.add(new VerifyVirtualizableUsage());
        verifiers.add(new VerifyUpdateUsages());
        verifiers.add(new VerifyWordFactoryUsage());
        verifiers.add(new VerifyBailoutUsage());
        verifiers.add(new VerifySystemPropertyUsage());
        verifiers.add(new VerifyInstanceOfUsage());
        verifiers.add(new VerifyGetOptionsUsage());
        verifiers.add(new VerifyUnsafeAccess());
        verifiers.add(new VerifyVariableCasts());
        verifiers.add(new VerifyIterableNodeType());
        verifiers.add(new VerifyArchUsageInPlugins());
        verifiers.add(new VerifyStatelessPhases());
        verifiers.add(new VerifyProfileMethodUsage());
        verifiers.add(new VerifyMemoryKillCheck());
        verifiers.add(new VerifySnippetProbabilities());
        verifiers.add(new VerifyPluginFrameState());
        verifiers.add(new VerifyGraphUniqueUsages());
        verifiers.add(new VerifyEndlessLoops());
        verifiers.add(new VerifyPhaseNoDirectRecursion());
        verifiers.add(new VerifyStringCaseUsage());
        verifiers.add(new VerifyMathAbs());
        verifiers.add(new VerifyLoopInfo());
        verifiers.add(new VerifyGuardsStageUsages());
        verifiers.add(new VerifyAArch64RegisterUsages());
        VerifyAssertionUsage assertionUsages = null;
        boolean checkAssertions = tool.checkAssertions();

        if (checkAssertions) {
            assertionUsages = new VerifyAssertionUsage(metaAccess);
            verifiers.add(assertionUsages);
        }

        if (tool.shouldVerifyLibGraalInvariants()) {
            verifiers.add(new VerifyLibGraalContextChecks());
        }

        loadVerifiers(verifiers);

        VerifyFoldableMethods foldableMethodsVerifier = new VerifyFoldableMethods();
        if (tool.shouldVerifyFoldableMethods()) {
            verifiers.add(foldableMethodsVerifier);
        }

        verifiers.add(new VerifyCurrentTimeMillisUsage(tool));

        tool.updateVerifiers(verifiers);

        for (Method m : BadUsageWithEquals.class.getDeclaredMethods()) {
            ResolvedJavaMethod method = metaAccess.lookupJavaMethod(m);
            try (DebugContext debug = new Builder(options).build()) {
                StructuredGraph graph = new StructuredGraph.Builder(options, debug, AllowAssumptions.YES).method(method).build();
                try (DebugCloseable _ = debug.disableIntercept(); DebugContext.Scope _ = debug.scope("CheckingGraph", graph, method)) {
                    graphBuilderSuite.apply(graph, context);
                    // update phi stamps
                    graph.getNodes().filter(PhiNode.class).forEach(PhiNode::inferStamp);
                    checkGraph(verifiers, context, graph);
                    errors.add(String.format("Expected error while checking %s", m));
                } catch (VerificationError e) {
                    // expected!
                } catch (Throwable e) {
                    errors.add(String.format("Error while checking %s:%n%s", m, printStackTraceToString(e)));
                }
            }
        }

        Map<ResolvedJavaField, Set<ResolvedJavaMethod>> optionFieldUsages = initOptionFieldUsagesMap(tool, metaAccess, errors);
        ResolvedJavaType optionDescriptorsType = metaAccess.lookupJavaType(OptionDescriptors.class);

        if (errors.isEmpty()) {
            ClassLoader cl = CheckGraalInvariants.class.getClassLoader();
            if (tool.shouldVerifyLibGraalInvariants()) {
                URL[] urls = {toURL(tool.getLibgraalJar())};
                cl = new URLClassLoader(urls, cl);
            }
            // Order outer classes before the inner classes
            classNames.sort(Comparator.naturalOrder());
            List<Class<?>> classes = loadClasses(tool, metaAccess, classNames, cl);
            for (Class<?> c : classes) {
                String className = c.getName();
                executor.execute(() -> {
                    try {
                        checkClass(c, metaAccess, verifiers);
                    } catch (Throwable e) {
                        errors.add(String.format("Error while checking %s:%n%s", className, printStackTraceToString(e)));
                    }
                });

                ResolvedJavaType type = metaAccess.lookupJavaType(c);
                List<ResolvedJavaMethod> methods = new ArrayList<>();
                try {
                    methods.addAll(Arrays.asList(type.getDeclaredMethods(false)));
                    methods.addAll(Arrays.asList(type.getDeclaredConstructors(false)));
                } catch (Throwable e) {
                    errors.add(String.format("Error while checking %s:%n%s", className, printStackTraceToString(e)));
                }
                ResolvedJavaMethod clinit = type.getClassInitializer();
                if (clinit != null) {
                    methods.add(clinit);
                }

                for (ResolvedJavaMethod method : methods) {
                    if (Modifier.isNative(method.getModifiers()) || Modifier.isAbstract(method.getModifiers())) {
                        // ignore
                    } else {
                        String methodName = className + "." + method.getName();
                        if (matches(filters, methodName)) {
                            executor.execute(() -> {
                                try (DebugContext debug = new Builder(options).build()) {
                                    boolean isSubstitution = method.getAnnotation(Snippet.class) != null;
                                    StructuredGraph graph = new StructuredGraph.Builder(options, debug).method(method).setIsSubstitution(isSubstitution).build();
                                    try (DebugCloseable _ = debug.disableIntercept(); DebugContext.Scope _ = debug.scope("CheckingGraph", graph, method)) {
                                        checkMethod(method);
                                        graphBuilderSuite.apply(graph, context);
                                        // update phi stamps
                                        graph.getNodes().filter(PhiNode.class).forEach(PhiNode::inferStamp);
                                        collectOptionFieldUsages(optionFieldUsages, optionDescriptorsType, method, graph);
                                        checkGraph(verifiers, context, graph);
                                    } catch (VerificationError e) {
                                        errors.add(e.getMessage());
                                    } catch (BailoutException e) {
                                        // Graal bail outs on certain patterns in Java bytecode
                                        // (e.g.,
                                        // unbalanced monitors introduced by jacoco).
                                    } catch (Throwable e) {
                                        errors.add(String.format("Error while checking %s:%n%s", methodName, printStackTraceToString(e)));
                                    }
                                }
                            });
                        }
                    }
                }
            }

            executor.shutdown();
            try {
                executor.awaitTermination(1, TimeUnit.HOURS);
            } catch (InterruptedException e1) {
                throw new RuntimeException(e1);
            }

            if (tool.shouldVerifyFoldableMethods()) {
                try {
                    foldableMethodsVerifier.finish();
                } catch (Throwable e) {
                    errors.add(e.getMessage());
                }
            }
        }

        if (assertionUsages != null) {
            assert checkAssertions;
            try {
                assertionUsages.postProcess();
            } catch (Throwable e) {
                errors.add(e.getMessage());
            }
        }

        checkOptionFieldUsages(errors, optionFieldUsages);

        if (!errors.isEmpty()) {
            StringBuilder msg = new StringBuilder();
            String nl = String.format("%n");
            for (String e : errors) {
                if (!msg.isEmpty()) {
                    msg.append(nl);
                }
                msg.append(e);
            }
            Assert.fail(msg.toString());
        }
    }

    private static URL toURL(Path path) {
        try {
            return path.toUri().toURL();
        } catch (MalformedURLException e) {
            throw new GraalError(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static void loadVerifiers(List<VerifyPhase<CoreProviders>> verifiers) {
        for (VerifyPhase<CoreProviders> verifier : ServiceLoader.load(VerifyPhase.class)) {
            verifiers.add(verifier);
        }
    }

    /**
     * Initializes a map from fields annotated with {@link Option} whose usages should be checked to
     * empty sets that will collect the methods accessing each field.
     * <p>
     * The sets are synchronized to support parallel processing of methods.
     */
    private static Map<ResolvedJavaField, Set<ResolvedJavaMethod>> initOptionFieldUsagesMap(InvariantsTool tool, MetaAccessProvider metaAccess, List<String> errors) {
        Map<ResolvedJavaField, Set<ResolvedJavaMethod>> optionFields = new EconomicHashMap<>();
        for (OptionDescriptors set : OptionsParser.getOptionsLoader()) {
            for (OptionDescriptor option : set) {
                if (tool.shouldCheckUsage(option)) {
                    Class<?> declaringClass = option.getDeclaringClass();
                    try {
                        Field javaField = declaringClass.getDeclaredField(option.getFieldName());
                        optionFields.put(metaAccess.lookupJavaField(javaField), Collections.synchronizedSet(new EconomicHashSet<>()));
                    } catch (NoSuchFieldException e) {
                        errors.add(e.toString());
                    }
                }
            }
        }
        return optionFields;
    }

    private static void collectOptionFieldUsages(Map<ResolvedJavaField, Set<ResolvedJavaMethod>> optionFields, ResolvedJavaType optionDescriptorsType, ResolvedJavaMethod method,
                    StructuredGraph graph) {
        if (!optionDescriptorsType.isAssignableFrom(method.getDeclaringClass())) {
            for (LoadFieldNode lfn : graph.getNodes().filter(LoadFieldNode.class)) {

                ResolvedJavaField field = lfn.field();
                Set<ResolvedJavaMethod> loads = optionFields.get(field);
                if (loads != null) {
                    loads.add(graph.method());
                }
            }
        }
    }

    private static void checkOptionFieldUsages(List<String> errors, Map<ResolvedJavaField, Set<ResolvedJavaMethod>> optionFieldUsages) {
        for (Map.Entry<ResolvedJavaField, Set<ResolvedJavaMethod>> e : optionFieldUsages.entrySet()) {
            if (e.getValue().isEmpty()) {
                if (e.getKey().format("%H.%n").equals(GraalOptions.VerifyPhases.getDescriptor().getLocation())) {
                    // Special case: This option may only have downstream uses
                } else {
                    errors.add("No uses found for " + e.getKey().format("%H.%n"));
                }
            }
        }
    }

    /**
     * Native Image is an external tool and does not need to follow the Graal invariants.
     */
    private static boolean isInNativeImage(String className) {
        return className.startsWith("org.graalvm.nativeimage");
    }

    /**
     * GSON classes are compiled with old JDK.
     */
    private static boolean isGSON(String className) {
        return className.contains("com.google.gson");
    }

    /**
     * ONNXRuntime: do not check for the svm invariants.
     */
    private static boolean isONNX(String className) {
        return className.contains("ai.onnxruntime");
    }

    private static List<Class<?>> loadClasses(InvariantsTool tool, MetaAccessProvider metaAccess, List<String> classNames, ClassLoader cl) {
        List<Class<?>> classes = new ArrayList<>(classNames.size());
        for (String className : classNames) {
            if (!tool.shouldLoadClass(className)) {
                continue;
            }
            try {
                Class<?> c = Class.forName(className, false, cl);

                /*
                 * Ensure all types are linked eagerly, so that we can access the bytecode of all
                 * methods.
                 */
                ResolvedJavaType type = metaAccess.lookupJavaType(c);
                type.link();

                if (Node.class.isAssignableFrom(c)) {
                    /*
                     * Eagerly initialize Node classes because the VerifyNodeCosts checker will
                     * initialize them anyway, and doing it here eagerly while being single-threaded
                     * avoids race conditions.
                     */
                    Unsafe.getUnsafe().ensureClassInitialized(c);
                }
                classes.add(c);
            } catch (UnsupportedClassVersionError e) {
                // graal-test.jar can contain classes compiled for different Java versions
            } catch (Throwable t) {
                GraalError.shouldNotReachHere(t); // ExcludeFromJacocoGeneratedReport
            }
        }
        return classes;
    }

    /**
     * @param metaAccess
     * @param verifiers
     */
    private static void checkClass(Class<?> c, MetaAccessProvider metaAccess, List<VerifyPhase<CoreProviders>> verifiers) {
        if (Node.class.isAssignableFrom(c)) {
            if (c.getAnnotation(NodeInfo.class) == null) {
                throw new AssertionError(String.format("Node subclass %s requires %s annotation", c.getName(), NodeClass.class.getSimpleName()));
            }
            VerifyNodeCosts.verifyNodeClass(c);
            // Any concrete class which implements MemoryKill must actually implement either
            // SingleMemoryKill or MultiMemoryKill.
            assert !MemoryKill.class.isAssignableFrom(c) || Modifier.isAbstract(c.getModifiers()) || SingleMemoryKill.class.isAssignableFrom(c) || MultiMemoryKill.class.isAssignableFrom(c) : c +
                            " must inherit from either SingleMemoryKill or MultiMemoryKill";
        }
        if (c.equals(DebugContext.class)) {
            try {
                // there are many log/logIndent methods, check the 2 most basic versions
                c.getDeclaredMethod("log", String.class);
                c.getDeclaredMethod("logAndIndent", String.class);
            } catch (NoSuchMethodException | SecurityException e) {
                throw new VerificationError("DebugContext misses basic log/logAndIndent methods", e);
            }
        }
        for (VerifyPhase<CoreProviders> verifier : verifiers) {
            verifier.verifyClass(c, metaAccess);
        }
    }

    private static void checkMethod(ResolvedJavaMethod method) {
        if (method.getAnnotation(Snippet.class) == null) {
            Annotation[][] parameterAnnotations = method.getParameterAnnotations();
            for (int i = 0; i < parameterAnnotations.length; i++) {
                for (Annotation a : parameterAnnotations[i]) {
                    Class<? extends Annotation> annotationType = a.annotationType();
                    if (annotationType == ConstantParameter.class || annotationType == VarargsParameter.class || annotationType == NonNullParameter.class) {
                        VerificationError verificationError = new VerificationError("Parameter %d of %s is annotated with %s but the method is not annotated with %s", i, method,
                                        annotationType.getSimpleName(),
                                        Snippet.class.getSimpleName());
                        throw verificationError;
                    }
                }
            }
        }
    }

    /**
     * Checks the invariants for a single graph.
     */
    private static void checkGraph(List<VerifyPhase<CoreProviders>> verifiers, HighTierContext context, StructuredGraph graph) {
        for (VerifyPhase<CoreProviders> verifier : verifiers) {
            if (!(verifier instanceof VerifyUsageWithEquals) || shouldVerifyEquals(graph.method())) {
                verifier.apply(graph, context);
            } else {
                verifier.apply(graph, context);
            }
        }
        if (graph.method().isBridge()) {
            BridgeMethodUtils.getBridgedMethod(graph.method());
        }
    }

    private static boolean matches(String[] filters, String s) {
        if (filters == null || filters.length == 0) {
            return true;
        }
        for (String filter : filters) {
            if (s.contains(filter)) {
                return true;
            }
        }
        return false;
    }

    private static String printStackTraceToString(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    static class BadUsageWithEquals {
        Value aValue;
        Register aRegister;
        RegisterCategory aRegisterCategory;
        JavaType aJavaType;
        JavaField aJavaField;
        JavaMethod aJavaMethod;
        LocationIdentity aLocationIdentity;
        LIRKind aLIRKind;
        ArithmeticOpTable anArithmeticOpTable;
        ArithmeticOpTable.Op anArithmeticOpTableOp;

        static Value aStaticValue;
        static Register aStaticRegister;
        static RegisterCategory aStaticRegisterCategory;
        static JavaType aStaticJavaType;
        static JavaField aStaticJavaField;
        static JavaMethod aStaticJavaMethod;
        static LocationIdentity aStaticLocationIdentity;
        static LIRKind aStaticLIRKind;
        static ArithmeticOpTable aStaticArithmeticOpTable;
        static ArithmeticOpTable.Op aStaticArithmeticOpTableOp;

        boolean test01(Value f) {
            return aValue == f;
        }

        boolean test02(Register f) {
            return aRegister == f;
        }

        boolean test03(RegisterCategory f) {
            return aRegisterCategory == f;
        }

        boolean test04(JavaType f) {
            return aJavaType == f;
        }

        boolean test05(JavaField f) {
            return aJavaField == f;
        }

        boolean test06(JavaMethod f) {
            return aJavaMethod == f;
        }

        boolean test07(LocationIdentity f) {
            return aLocationIdentity == f;
        }

        boolean test08(LIRKind f) {
            return aLIRKind == f;
        }

        boolean test09(ArithmeticOpTable f) {
            return anArithmeticOpTable == f;
        }

        boolean test10(ArithmeticOpTable.Op f) {
            return anArithmeticOpTableOp == f;
        }

        boolean test12(Value f) {
            return aStaticValue == f;
        }

        boolean test13(Register f) {
            return aStaticRegister == f;
        }

        boolean test14(RegisterCategory f) {
            return aStaticRegisterCategory == f;
        }

        boolean test15(JavaType f) {
            return aStaticJavaType == f;
        }

        boolean test16(JavaField f) {
            return aStaticJavaField == f;
        }

        boolean test17(JavaMethod f) {
            return aStaticJavaMethod == f;
        }

        boolean test18(LocationIdentity f) {
            return aStaticLocationIdentity == f;
        }

        boolean test19(LIRKind f) {
            return aStaticLIRKind == f;
        }

        boolean test20(ArithmeticOpTable f) {
            return aStaticArithmeticOpTable == f;
        }

        boolean test21(ArithmeticOpTable.Op f) {
            return aStaticArithmeticOpTableOp == f;
        }
    }
}

class DoNotInitializeClassInitializationPlugin implements ClassInitializationPlugin {

    @Override
    public boolean supportsLazyInitialization(ConstantPool cp) {
        return true;
    }

    @Override
    public void loadReferencedType(GraphBuilderContext builder, ConstantPool cp, int cpi, int bytecode) {
        cp.loadReferencedType(cpi, bytecode, false);
    }

    @Override
    public boolean apply(GraphBuilderContext builder, ResolvedJavaType type, Supplier<FrameState> frameState) {
        return false;
    }
}
