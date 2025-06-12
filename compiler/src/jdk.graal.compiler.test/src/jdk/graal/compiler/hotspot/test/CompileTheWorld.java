/*
 * Copyright (c) 2013, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.test;

import static java.util.Collections.singletonList;
import static jdk.graal.compiler.core.CompilationWrapper.ExceptionAction.Print;
import static jdk.graal.compiler.core.GraalCompilerOptions.CompilationBailoutAsFailure;
import static jdk.graal.compiler.core.GraalCompilerOptions.CompilationFailureAction;
import static jdk.graal.compiler.core.test.ReflectionOptionDescriptors.extractEntries;
import static jdk.graal.compiler.hotspot.CompilationTask.CompilationTime;
import static jdk.graal.compiler.hotspot.CompilationTask.CompiledAndInstalledBytecodes;
import static jdk.graal.compiler.hotspot.test.CompileTheWorld.Options.DESCRIPTORS;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Formatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.graalvm.collections.EconomicMap;

import com.oracle.truffle.runtime.hotspot.libgraal.LibGraal;

import jdk.graal.compiler.api.replacements.Snippet;
import jdk.graal.compiler.api.test.ModuleSupport;
import jdk.graal.compiler.bytecode.Bytecodes;
import jdk.graal.compiler.core.phases.HighTier;
import jdk.graal.compiler.core.phases.fuzzing.FuzzedSuites;
import jdk.graal.compiler.core.test.ReflectionOptionDescriptors;
import jdk.graal.compiler.debug.GlobalMetrics;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.debug.MethodFilter;
import jdk.graal.compiler.debug.MetricKey;
import jdk.graal.compiler.debug.TTY;
import jdk.graal.compiler.hotspot.CompilationTask;
import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.hotspot.HotSpotBytecodeParser;
import jdk.graal.compiler.hotspot.HotSpotGraalCompiler;
import jdk.graal.compiler.hotspot.HotSpotGraalRuntime;
import jdk.graal.compiler.hotspot.HotSpotGraalRuntimeProvider;
import jdk.graal.compiler.hotspot.meta.HotSpotFuzzedSuitesProvider;
import jdk.graal.compiler.hotspot.meta.HotSpotLoadedSuitesProvider;
import jdk.graal.compiler.hotspot.meta.HotSpotSuitesProvider;
import jdk.graal.compiler.options.OptionDescriptors;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.options.OptionsContainer;
import jdk.graal.compiler.options.OptionsParser;
import jdk.graal.compiler.util.EconomicHashMap;
import jdk.graal.compiler.util.EconomicHashSet;
import jdk.vm.ci.hotspot.HotSpotCodeCacheProvider;
import jdk.vm.ci.hotspot.HotSpotCompilationRequest;
import jdk.vm.ci.hotspot.HotSpotCompilationRequestResult;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.hotspot.HotSpotResolvedObjectType;
import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.runtime.JVMCI;

/**
 * This class implements compile-the-world functionality with JVMCI.
 */
public final class CompileTheWorld extends LibGraalCompilationDriver {

    static {
        ModuleSupport.exportAndOpenAllPackagesToUnnamed("jdk.internal.vm.ci");
        // Truffle may not be on the module-path
        ModuleSupport.exportAndOpenAllPackagesToUnnamed("org.graalvm.truffle", false);
        ModuleSupport.exportAndOpenAllPackagesToUnnamed("org.graalvm.truffle.compiler", false);
        ModuleSupport.exportAndOpenAllPackagesToUnnamed("org.graalvm.truffle.runtime", false);
    }
    /**
     * Magic token to denote that the classes in the Java runtime image are compiled.
     */
    public static final String SUN_BOOT_CLASS_PATH = "sun.boot.class.path";

    /**
     * Magic token to denote the classes in the Java runtime image (i.e. in the {@code jrt:/} file
     * system).
     */
    public static final String JRT_CLASS_PATH_ENTRY = "<jrt>";

    /**
     * @param options a space separated set of option value settings with each option setting in a
     *            {@code -Djdk.graal.<name>=<value>} format but without the leading
     *            {@code -Djdk.graal.}. Ignored if null.
     */
    public static EconomicMap<OptionKey<?>, Object> parseOptions(String options) {
        EconomicMap<OptionKey<?>, Object> values = OptionValues.newOptionMap();
        if (options != null) {
            EconomicMap<String, String> optionSettings = EconomicMap.create();
            for (String optionSetting : options.split("\\s+|#")) {
                OptionsParser.parseOptionSettingTo(optionSetting, optionSettings);
            }
            Iterable<OptionDescriptors> loader = OptionsContainer.getDiscoverableOptions(OptionDescriptors.class.getClassLoader());
            OptionsParser.parseOptions(optionSettings, values, loader);
        }
        if (!values.containsKey(HighTier.Options.Inline)) {
            values.put(HighTier.Options.Inline, false);
        }
        return values;
    }

    /**
     * Class path denoting classes to compile.
     *
     * @see Options#Classpath
     */
    private final String inputClassPath;

    /**
     * Class index to start compilation at.
     *
     * @see Options#StartAt
     */
    private final int startAt;

    /**
     * Class index to stop compilation at.
     *
     * @see Options#StopAt
     */
    private final int stopAt;

    /**
     * Max classes to compile.
     *
     * @see Options#MaxClasses
     */
    private final int maxClasses;

    /** Only compile methods matching this filter if the filter is non-null. */
    private final MethodFilter methodFilter;

    /**
     * Exclude methods matching this filter if the filter is non-null. This is used by mx to exclude
     * some methods, while users are expected to use positive or negative filters in
     * {@link #methodFilter} instead.
     */
    private final MethodFilter excludeMethodFilter;

    private final boolean verbose;

    /**
     * Values for {@link CompileTheWorld.Options}.
     */
    private final OptionValues harnessOptions;

    /**
     * Option values used during compilation.
     */
    private final OptionValues compilerOptions;

    /**
     * Name of scratch directory for extracting nested jars on class path. Empty means do not
     * extract nested jars. {@code null} means use a temporary directory and delete it upon exit.
     */
    private final String scratchDir;

    /**
     * Creates a compile-the-world instance.
     *
     * @param files {@link File#pathSeparator} separated list of Zip/Jar files to compile
     * @param startAt index of the class file to start compilation at
     * @param stopAt index of the class file to stop compilation at
     * @param maxClasses maximum number of classes to process
     * @param methodFilters filters describing the methods to compile
     * @param excludeMethodFilters filters describing the methods not to compile
     * @param scratchDir scratch directory for extracting nested jars
     * @param harnessOptions values for {@link CompileTheWorld.Options}
     * @param compilerOptions option values used by the compiler
     */
    public CompileTheWorld(HotSpotJVMCIRuntime jvmciRuntime,
                    HotSpotGraalCompiler compiler,
                    String files,
                    int startAt,
                    int stopAt,
                    int maxClasses,
                    String methodFilters,
                    String excludeMethodFilters,
                    String scratchDir,
                    boolean verbose,
                    OptionValues harnessOptions,
                    OptionValues compilerOptions) {
        super(jvmciRuntime, compiler,
                        Options.InvalidateInstalledCode.getValue(harnessOptions),
                        false,
                        Options.IgnoreCompilationFailures.getValue(harnessOptions),
                        Options.MultiThreaded.getValue(harnessOptions),
                        Options.Threads.getValue(harnessOptions),
                        Options.StatsInterval.getValue(harnessOptions));

        this.inputClassPath = files;
        this.startAt = Math.max(startAt, 1);
        this.stopAt = Math.max(stopAt, 1);
        this.scratchDir = scratchDir;
        this.maxClasses = Math.max(maxClasses, 1);
        this.methodFilter = methodFilters == null ? null : MethodFilter.parse(methodFilters);
        this.excludeMethodFilter = excludeMethodFilters == null ? null : MethodFilter.parse(excludeMethodFilters);
        this.verbose = verbose;
        this.harnessOptions = harnessOptions;

        // Copy the initial options and add in any extra options
        EconomicMap<OptionKey<?>, Object> compilerOptionsMap = EconomicMap.create(compilerOptions.getMap());

        // We want to see stack traces when a method fails to compile
        CompilationBailoutAsFailure.putIfAbsent(compilerOptionsMap, true);
        CompilationFailureAction.putIfAbsent(compilerOptionsMap, Print);

        this.compilerOptions = new OptionValues(compilerOptionsMap);
    }

    public CompileTheWorld(HotSpotJVMCIRuntime jvmciRuntime,
                    HotSpotGraalCompiler compiler,
                    OptionValues harnessOptions,
                    OptionValues compilerOptions) {
        this(jvmciRuntime, compiler, Options.Classpath.getValue(harnessOptions),
                        Options.StartAt.getValue(harnessOptions),
                        Options.StopAt.getValue(harnessOptions),
                        Options.MaxClasses.getValue(harnessOptions),
                        Options.MethodFilter.getValue(harnessOptions),
                        Options.ExcludeMethodFilter.getValue(harnessOptions),
                        Options.ScratchDir.getValue(harnessOptions),
                        Options.Verbose.hasBeenSet(harnessOptions) ? Options.Verbose.getValue(harnessOptions) : !Options.MultiThreaded.getValue(harnessOptions),
                        harnessOptions,
                        new OptionValues(compilerOptions, parseOptions(Options.Config.getValue(harnessOptions))));
    }

    /**
     * Compiles methods in classes in {@link #inputClassPath}. If {@link #inputClassPath} equals
     * {@link #SUN_BOOT_CLASS_PATH} the boot classes are used.
     */
    public void compile() throws Throwable {
        compile(prepare());
    }

    public void compile(List<Compilation> compilations) throws Throwable {
        compileAll(compilations, compilerOptions);
    }

    public void println() {
        println("");
    }

    public void println(String format, Object... args) {
        println(String.format(format, args));
    }

    public void println(String s) {
        println(verbose, s);
    }

    public static void println(boolean cond, String s) {
        if (cond) {
            TTY.println(s);
        }
    }

    public void printStackTrace(Throwable t) {
        if (verbose) {
            t.printStackTrace(TTY.out);
        }
    }

    @SuppressWarnings("unused")
    private static void dummy() {
    }

    /**
     * Abstraction over different types of class path entries.
     */
    abstract static class ClassPathEntry implements Closeable {
        final String name;

        ClassPathEntry(String name) {
            this.name = name;
        }

        /**
         * Creates a URL for loading classes from this entry.
         */
        public abstract URL createURL() throws IOException;

        /**
         * Gets the list of classes available under this entry.
         */
        public abstract List<String> getClassNames() throws IOException;

        @Override
        public String toString() {
            return name;
        }

        @Override
        public void close() throws IOException {
        }
    }

    /**
     * A class path entry that is a normal file system directory.
     */
    static class DirClassPathEntry extends ClassPathEntry {

        private final File dir;

        DirClassPathEntry(String name) {
            super(name);
            dir = new File(name);
            assert dir.isDirectory();
        }

        @Override
        public URL createURL() throws IOException {
            return dir.toURI().toURL();
        }

        @Override
        public List<String> getClassNames() throws IOException {
            List<String> classNames = new ArrayList<>();
            String root = dir.getPath();
            SimpleFileVisitor<Path> visitor = new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (attrs.isRegularFile()) {
                        File path = file.toFile();
                        if (path.getName().endsWith(".class")) {
                            String pathString = path.getPath();
                            assert pathString.startsWith(root);
                            String classFile = pathString.substring(root.length() + 1);
                            String className = classFile.replace(File.separatorChar, '.');
                            classNames.add(className.replace('/', '.').substring(0, className.length() - ".class".length()));
                        }
                    }
                    return super.visitFile(file, attrs);
                }
            };
            Files.walkFileTree(dir.toPath(), visitor);
            return classNames;
        }
    }

    /**
     * A class path entry that is a jar or zip file.
     */
    static class JarClassPathEntry extends ClassPathEntry {

        private final JarFile jarFile;

        JarClassPathEntry(String name) throws IOException {
            super(name);
            jarFile = new JarFile(name);
        }

        @Override
        public URL createURL() throws IOException {
            try {
                return new URI("jar:file:" + name + "!/").toURL();
            } catch (URISyntaxException e) {
                throw new IOException(e);
            }
        }

        /**
         * Computes the SHA-1 digest for {@code s} and returns it as a hex string.
         */
        private static String sha1(String s) {
            try {
                byte[] absPathBytes = s.getBytes();
                MessageDigest md = MessageDigest.getInstance("SHA-1");
                md.update(absPathBytes, 0, absPathBytes.length);
                byte[] result = md.digest();
                Formatter res = new Formatter();
                for (byte b : result) {
                    res.format("%02x", b);
                }
                return res.toString();
            } catch (NoSuchAlgorithmException e) {
                throw new GraalError(e);
            }
        }

        /**
         * Extracts each jar file in this jar file to a directory under {@code scratchDir}.
         *
         * @param entryCollector the path of each extracted jar is appended to this list
         */
        void extractNestedJars(Path scratchDir, List<String> entryCollector) {
            Path absPath = Paths.get(name).toFile().getAbsoluteFile().toPath();
            Path myScratchDir = scratchDir.resolve(sha1(absPath.toString()));
            jarFile.stream().forEach(je -> {
                if (!je.isDirectory() && je.getName().endsWith(".jar")) {
                    Path extractedPath = myScratchDir.resolve(je.getName());
                    Path originLink = myScratchDir.resolve("origin");
                    try {
                        if (!Files.exists(extractedPath.getParent())) {
                            Files.createDirectories(extractedPath.getParent());
                        }
                        if (!Files.exists(originLink)) {
                            Files.createSymbolicLink(originLink, absPath);
                        }
                        InputStream in = jarFile.getInputStream(je);
                        Files.deleteIfExists(extractedPath);
                        Files.copy(in, extractedPath);
                        entryCollector.add(extractedPath.toString());
                    } catch (IOException e) {
                        throw new GraalError(e, "Error extracting %s from %s to %s", je.getName(), name, myScratchDir);
                    }
                }
            });
        }

        /**
         * @see "https://docs.oracle.com/javase/9/docs/specs/jar/jar.html#Multi-release"
         */
        static Pattern MultiReleaseJarVersionedClassRE = Pattern.compile("META-INF/versions/[1-9][0-9]*/(.+)");

        @Override
        public List<String> getClassNames() {
            Enumeration<JarEntry> e = jarFile.entries();
            List<String> classNames = new ArrayList<>(jarFile.size());
            while (e.hasMoreElements()) {
                JarEntry je = e.nextElement();
                if (je.isDirectory() || !je.getName().endsWith(".class")) {
                    continue;
                }
                String className = je.getName().substring(0, je.getName().length() - ".class".length());
                if (className.equals("module-info")) {
                    continue;
                }
                if (className.startsWith("META-INF/versions/")) {
                    Matcher m = MultiReleaseJarVersionedClassRE.matcher(className);
                    if (m.matches()) {
                        className = m.group(1);
                    } else {
                        continue;
                    }
                }
                classNames.add(className.replace('/', '.'));
            }
            return classNames;
        }

        @Override
        public void close() throws IOException {
            jarFile.close();
        }
    }

    /**
     * A class path entry representing the {@code jrt:/} file system.
     */
    static class JRTClassPathEntry extends ClassPathEntry {

        private final String limitModules;

        JRTClassPathEntry(String name, String limitModules) {
            super(name);
            this.limitModules = limitModules;
        }

        @Override
        public URL createURL() throws IOException {
            return URI.create("jrt:/").toURL();
        }

        @Override
        public List<String> getClassNames() throws IOException {
            Set<String> negative = new EconomicHashSet<>();
            Set<String> positive = new EconomicHashSet<>();
            if (limitModules != null && !limitModules.isEmpty()) {
                for (String s : limitModules.split(",")) {
                    if (s.startsWith("~")) {
                        negative.add(s.substring(1));
                    } else {
                        positive.add(s);
                    }
                }
            }
            List<String> classNames = new ArrayList<>();
            FileSystem fs = FileSystems.newFileSystem(URI.create("jrt:/"), Collections.emptyMap());
            Path top = fs.getPath("/modules/");
            Files.find(top, Integer.MAX_VALUE,
                            (_, attrs) -> attrs.isRegularFile()).forEach(p -> {
                                int nameCount = p.getNameCount();
                                if (nameCount > 2) {
                                    String base = p.getName(nameCount - 1).toString();
                                    if (base.endsWith(".class") && !base.equals("module-info.class")) {
                                        String module = p.getName(1).toString();
                                        if (positive.isEmpty() || positive.contains(module)) {
                                            if (negative.isEmpty() || !negative.contains(module)) {
                                                // Strip module prefix and convert to dotted form
                                                String className = p.subpath(2, nameCount).toString().replace('/', '.');
                                                // Strip ".class" suffix
                                                className = className.replace('/', '.').substring(0, className.length() - ".class".length());
                                                classNames.add(className);
                                            }
                                        }
                                    }
                                }
                            });
            return classNames;
        }
    }

    private boolean isClassIncluded(String className) {
        if (methodFilter != null && !methodFilter.matchesClassName(className)) {
            return false;
        }
        if (excludeMethodFilter != null && excludeMethodFilter.matchesClassName(className)) {
            return false;
        }
        return true;
    }

    private ClassPathEntry openClassPathEntry(String entry) throws IOException {
        if (entry.endsWith(".zip") || entry.endsWith(".jar")) {
            return new JarClassPathEntry(entry);
        } else if (entry.equals(JRT_CLASS_PATH_ENTRY)) {
            return new JRTClassPathEntry(entry, Options.LimitModules.getValue(harnessOptions));
        } else {
            if (!new File(entry).isDirectory()) {
                return null;
            }
            return new DirClassPathEntry(entry);
        }
    }

    public static class Compilation extends LibGraalCompilationDriver.Compilation {
        /**
         * Position of the class declaring {@link #getMethod} in the CTW class path traversal.
         */
        final int classPos;

        Compilation(HotSpotResolvedJavaMethod method, int num) {
            super(method);
            this.classPos = num;
        }

        @Override
        public String testName() {
            return String.format("CompileTheWorld (%d)", classPos);
        }
    }

    int compiledClasses;

    final Map<ResolvedJavaMethod, Integer> hugeMethods = new EconomicHashMap<>();

    private List<Compilation> prepareCompilations(String[] classPath) throws IOException {
        int startAtClass = startAt;
        int stopAtClass = stopAt;
        int startAtCompile = Options.StartAtCompile.getValue(harnessOptions);
        int stopAtCompile = Options.StopAtCompile.getValue(harnessOptions);

        if (startAtClass >= stopAtClass) {
            throw new IllegalArgumentException(String.format("StartAt (%d) must be less than StopAt (%d)", startAtClass, stopAtClass));
        }
        if (startAtCompile >= stopAtCompile) {
            throw new IllegalArgumentException(String.format("StartAtCompile (%d) must be less than StopAtCompile (%d)", startAtCompile, stopAtCompile));
        }

        int classStep = 1;
        if (maxClasses != Integer.MAX_VALUE) {
            int totalClassFileCount = 0;
            for (String entry : classPath) {
                try (ClassPathEntry cpe = openClassPathEntry(entry)) {
                    if (cpe != null) {
                        totalClassFileCount += cpe.getClassNames().size();
                    }
                }
            }

            int lastClassFile = totalClassFileCount - 1;
            startAtClass = Math.min(startAt, lastClassFile);
            stopAtClass = Math.min(stopAt, lastClassFile);
            int range = stopAtClass - startAtClass + 1;
            if (maxClasses < range) {
                classStep = range / maxClasses;
            }
        }

        TTY.println("CompileTheWorld : Gathering compilations ...");
        Map<HotSpotResolvedJavaMethod, Integer> toBeCompiled = new LinkedHashMap<>();
        compiledClasses = gatherCompilations(toBeCompiled, classPath, startAtClass, stopAtClass, classStep);

        int compilationNum = 0;
        int allCompiles = Math.min(toBeCompiled.size(), stopAtCompile) - Math.max(0, startAtCompile);
        int maxCompiles = Options.MaxCompiles.getValue(harnessOptions);
        float selector = Math.max(0, startAtCompile);
        float selectorStep = maxCompiles < allCompiles ? (float) allCompiles / maxCompiles : 1.0f;
        int repeat = Options.Repeat.getValue(harnessOptions);

        List<Compilation> compilations = new ArrayList<>();
        for (Map.Entry<HotSpotResolvedJavaMethod, Integer> e : toBeCompiled.entrySet()) {
            if (compilationNum >= startAtCompile && compilationNum < stopAtCompile) {
                if (Math.round(selector) == compilationNum) {
                    for (int i = 0; i < repeat; i++) {
                        compilations.add(new Compilation(e.getKey(), e.getValue()));
                    }
                    selector += selectorStep;
                }
            }
            compilationNum++;
        }
        return compilations;
    }

    /**
     * Gets the methods to be compiled into {@code toBeCompiled}.
     *
     * @param toBeCompiled a linked map from methods to the class id declaring the method
     * @return number of classes to be compiled
     */
    private int gatherCompilations(Map<HotSpotResolvedJavaMethod, Integer> toBeCompiled,
                    final String[] entries,
                    int compileStartAt,
                    int compileStopAt,
                    int compileStep) throws IOException {

        List<URL> classPathList = new ArrayList<>();
        classPathList.add(null);
        for (int i = 0; i < entries.length; i++) {
            final String entry = entries[i];
            try (ClassPathEntry cpe = openClassPathEntry(entry)) {
                if (cpe != null) {
                    classPathList.add(cpe.createURL());
                }
            }
        }

        // First element is set to the URL for the current class path entry being compiled
        URL[] classPath = classPathList.toArray(new URL[classPathList.size()]);
        assert classPath[0] == null;

        int classPos = 0;
        for (int i = 0; i < entries.length; i++) {
            final String entry = entries[i];
            try (ClassPathEntry cpe = openClassPathEntry(entry)) {
                if (cpe == null) {
                    println("CompileTheWorld : Skipped classes in " + entry);
                    println();
                    continue;
                }

                if (methodFilter == null || methodFilter.matchesNothing()) {
                    println("CompileTheWorld : Compiling all classes in " + entry);
                } else {
                    String include = methodFilter.toString();
                    println("CompileTheWorld : Compiling all methods in " + entry + " matching one of the following filters: " + include);
                }
                if (excludeMethodFilter != null && !excludeMethodFilter.matchesNothing()) {
                    String exclude = excludeMethodFilter.toString();
                    println("CompileTheWorld : Excluding all methods matching one of the following filters: " + exclude);
                }
                println();

                classPath[0] = cpe.createURL();
                ClassLoader loader = new URLClassLoader(classPath);

                for (String className : cpe.getClassNames()) {

                    // Are we done?
                    if (classPos >= compileStopAt) {
                        break;
                    }

                    classPos++;

                    if (compileStep > 1 && ((classPos - compileStartAt) % compileStep) != 0) {
                        continue;
                    }

                    if (className.startsWith("jdk.management.") ||
                                    className.startsWith("jdk.internal.cmm.*") ||
                                    // GR-5881: The class initializer for
                                    // sun.tools.jconsole.OutputViewer
                                    // spawns non-daemon threads for redirecting sysout and
                                    // syserr.
                                    // These threads tend to cause deadlock at VM exit
                                    className.startsWith("sun.tools.jconsole.")) {
                        continue;
                    }

                    if (!isClassIncluded(className)) {
                        continue;
                    }

                    try {
                        // Load and initialize class
                        Class<?> javaClass = Class.forName(className, true, loader);
                        MetaAccessProvider metaAccess = JVMCI.getRuntime().getHostJVMCIBackend().getMetaAccess();

                        // Pre-load all classes in the constant pool.
                        try {
                            HotSpotResolvedObjectType objectType = (HotSpotResolvedObjectType) metaAccess.lookupJavaType(javaClass);
                            ConstantPool constantPool = objectType.getConstantPool();
                            for (int cpi = 1; cpi < constantPool.length(); cpi++) {
                                constantPool.loadReferencedType(cpi, Bytecodes.LDC);
                            }
                        } catch (Throwable t) {
                            // If something went wrong during pre-loading we just ignore it.
                            if (isClassIncluded(className)) {
                                println("Preloading failed for (%d) %s: %s", classPos, className, t);
                            }
                            continue;
                        }

                        // Are we compiling this class?
                        if (classPos >= compileStartAt) {

                            long start0 = System.nanoTime();
                            // Compile each constructor/method in the class.
                            for (Constructor<?> constructor : javaClass.getDeclaredConstructors()) {
                                HotSpotResolvedJavaMethod javaMethod = (HotSpotResolvedJavaMethod) metaAccess.lookupJavaMethod(constructor);
                                addCompilation(javaMethod, toBeCompiled, classPos);
                            }
                            for (Method method : javaClass.getDeclaredMethods()) {
                                HotSpotResolvedJavaMethod javaMethod = (HotSpotResolvedJavaMethod) metaAccess.lookupJavaMethod(method);
                                addCompilation(javaMethod, toBeCompiled, classPos);
                            }

                            // Also compile the class initializer if it exists
                            HotSpotResolvedJavaMethod clinit = (HotSpotResolvedJavaMethod) metaAccess.lookupJavaType(javaClass).getClassInitializer();
                            if (clinit != null) {
                                addCompilation(clinit, toBeCompiled, classPos);
                            }
                            println("CompileTheWorld (%d) : %s (%d us)", classPos, className, (System.nanoTime() - start0) / 1000);
                        }
                    } catch (Throwable t) {
                        if (isClassIncluded(className)) {
                            println("CompileTheWorld (%d) : Skipping %s %s", classPos, className, t.toString());
                            printStackTrace(t);
                        }
                    }
                }
            }
        }
        return classPos - compileStartAt;
    }

    @Override
    protected CompilationTask createCompilationTask(HotSpotJVMCIRuntime runtime, HotSpotGraalCompiler compiler, HotSpotCompilationRequest request, boolean useProfilingInfo, boolean installAsDefault) {
        CompilationTask task;
        if (Options.FuzzPhasePlan.getValue(harnessOptions)) {
            task = new CompileTheWorldFuzzedSuitesCompilationTask(runtime, compiler, request, useProfilingInfo, installAsDefault);
        } else {
            task = new CompilationTask(runtime, compiler, request, useProfilingInfo, installAsDefault);
        }
        return task;
    }

    private void addCompilation(HotSpotResolvedJavaMethod method, Map<HotSpotResolvedJavaMethod, Integer> toBeCompiled, int classPos) {
        if (!canBeCompiled(method, method.getModifiers(), classPos)) {
            return;
        }
        if (methodFilter != null && !methodFilter.matches(method)) {
            return;
        }
        if (excludeMethodFilter != null && excludeMethodFilter.matches(method)) {
            return;
        }
        toBeCompiled.put(method, classPos);
    }

    /**
     * Determines if a method should be compiled (Cf. CompilationPolicy::can_be_compiled).
     *
     * @return true if it can be compiled, false otherwise
     */
    private boolean canBeCompiled(HotSpotResolvedJavaMethod javaMethod, int modifiers, int classPos) {
        if (Modifier.isAbstract(modifiers) || Modifier.isNative(modifiers)) {
            return false;
        }
        GraalHotSpotVMConfig c = getGraalRuntime().getVMConfig();
        int hugeMethodLimit = getHugeMethodLimit(c);
        if (c.dontCompileHugeMethods && javaMethod.getCodeSize() > hugeMethodLimit) {
            println(verbose || methodFilter != null,
                            String.format("CompileTheWorld (%d) : Skipping huge method %s (use -XX:-DontCompileHugeMethods or -DCompileTheWorld.HugeMethodLimit=%d to include it)",
                                            classPos,
                                            javaMethod.format("%H.%n(%p):%r"),
                                            javaMethod.getCodeSize()));
            hugeMethods.put(javaMethod, javaMethod.getCodeSize());
            return false;
        }
        // Allow use of -XX:CompileCommand=dontinline to exclude problematic methods
        if (!javaMethod.canBeInlined()) {
            return false;
        }
        // Skip @Snippets for now
        for (Annotation annotation : javaMethod.getAnnotations()) {
            if (annotation.annotationType().equals(Snippet.class)) {
                return false;
            }
        }
        return true;
    }

    private int getHugeMethodLimit(GraalHotSpotVMConfig c) {
        if (Options.HugeMethodLimit.hasBeenSet(harnessOptions)) {
            return Options.HugeMethodLimit.getValue(harnessOptions);
        } else {
            return c.hugeMethodLimit;
        }
    }

    @Override
    protected void printResults(OptionValues options, Map<ResolvedJavaMethod, CompilationResult> compileTimes, long compileTime, long memoryUsed, long codeSize, long elapsedTime) {
        println();
        int compiledBytecodes = compileTimes.keySet().stream().mapToInt(ResolvedJavaMethod::getCodeSize).sum();
        int compiledMethods = compileTimes.size();
        long elapsedTimeSeconds = nanoToMillis(compileTime) / 1_000;
        double rateInMethods = (double) compiledMethods / elapsedTimeSeconds;
        double rateInBytecodes = (double) compiledBytecodes / elapsedTimeSeconds;
        TTY.println("CompileTheWorld : ======================== Done ======================");
        TTY.println("CompileTheWorld :         Compiled classes: %,d", compiledClasses);
        TTY.println("CompileTheWorld :         Compiled methods: %,d [%,d bytecodes]", compiledMethods, compiledBytecodes);
        TTY.println("CompileTheWorld :             Elapsed time: %,d ms", nanoToMillis(elapsedTime));
        TTY.println("CompileTheWorld :             Compile time: %,d ms", nanoToMillis(compileTime));
        TTY.println("CompileTheWorld :  Compilation rate/thread: %,.1f methods/sec, %,.0f bytecodes/sec", rateInMethods, rateInBytecodes);
        TTY.println("CompileTheWorld : HotSpot heap memory used: %,.3f MB", (double) memoryUsed / 1_000_000);
        TTY.println("CompileTheWorld :     Huge methods skipped: %,d", hugeMethods.size());
        int limit = Options.MetricsReportLimit.getValue(harnessOptions);
        if (limit > 0) {
            TTY.println("Longest compile times:");
            final Comparator<Map.Entry<ResolvedJavaMethod, CompilationResult>> compileTimeComparator = Comparator.comparing(entry -> entry.getValue().compileTime());
            compileTimes.entrySet().stream().sorted(compileTimeComparator.reversed()).limit(limit).forEach(e -> {
                long time = nanoToMillis(e.getValue().compileTime());
                ResolvedJavaMethod method = e.getKey();
                TTY.println("  %,10d ms   %s [bytecodes: %d]", time, method.format("%H.%n(%p)"), method.getCodeSize());
            });
            TTY.println("Largest methods skipped due to bytecode size exceeding HugeMethodLimit (%d):", getHugeMethodLimit(getGraalRuntime().getVMConfig()));
            final Comparator<Map.Entry<ResolvedJavaMethod, Integer>> codeSizeComparator = Map.Entry.comparingByValue();
            hugeMethods.entrySet().stream().sorted(codeSizeComparator.reversed()).limit(limit).forEach(e -> {
                ResolvedJavaMethod method = e.getKey();
                TTY.println("  %,10d      %s", e.getValue(), method.format("%H.%n(%p)"), method.getCodeSize());
            });
        }

        GlobalMetrics metricValues = ((HotSpotGraalRuntime) getGraalRuntime()).getMetricValues();
        EconomicMap<MetricKey, Long> map = metricValues.asKeyValueMap();
        Long compiledAndInstalledBytecodes = map.get(CompiledAndInstalledBytecodes);
        Long compilationTime = map.get(CompilationTime);
        if (compiledAndInstalledBytecodes != null && compilationTime != null) {
            TTY.println("CompileTheWorld : Aggregate compile speed %d bytecodes per second (%d / %d)", (int) (compiledAndInstalledBytecodes / (compilationTime / 1000000000.0)),
                            compiledAndInstalledBytecodes, compilationTime);
        }

        metricValues.print(options);
        metricValues.clear();
        printSuspiciousThreads();
    }

    /**
     * Snapshot of thread state prior to loading classes for compilation.
     */
    private final Map<Thread, StackTraceElement[]> initialThreads = Thread.getAllStackTraces();

    private void printSuspiciousThreads() {
        /*
         * Apart from the main thread, there should be only be daemon threads alive now. If not,
         * then a class initializer has probably started a thread that could cause a deadlock while
         * trying to exit the VM. One known example of this is sun.tools.jconsole.OutputViewer which
         * spawns threads to redirect sysout and syserr. To help debug such scenarios, the stacks of
         * potentially problematic threads are dumped.
         */
        Map<Thread, StackTraceElement[]> suspiciousThreads = new EconomicHashMap<>();
        for (Map.Entry<Thread, StackTraceElement[]> e : Thread.getAllStackTraces().entrySet()) {
            Thread thread = e.getKey();
            if (thread != Thread.currentThread() && !initialThreads.containsKey(thread) && !thread.isDaemon() && thread.isAlive()) {
                suspiciousThreads.put(thread, e.getValue());
            }
        }
        if (!suspiciousThreads.isEmpty()) {
            TTY.println("--- Non-daemon threads started during CTW ---");
            for (Map.Entry<Thread, StackTraceElement[]> e : suspiciousThreads.entrySet()) {
                Thread thread = e.getKey();
                if (thread.isAlive()) {
                    TTY.println(thread + " " + thread.getState());
                    for (StackTraceElement ste : e.getValue()) {
                        TTY.println("\tat " + ste);
                    }
                }
            }
            TTY.println("---------------------------------------------");
        }
    }

    /**
     * Determine if the message describes a failure we should ignore in the context of CTW. Since in
     * CTW we try to compile all methods as compilation roots indiscriminately, we may hit upon a
     * helper method that expects to only be inlined into a snippet. Such compilations may fail with
     * a well-known message complaining about node intrinsics used outside a snippet. We can ignore
     * these failures.
     */
    public static boolean shouldIgnoreFailure(String failureMessage) {
        return failureMessage.startsWith(HotSpotBytecodeParser.BAD_NODE_INTRINSIC_PLUGIN_CONTEXT);
    }

    @Override
    protected void handleFailure(HotSpotCompilationRequestResult result) {
        if (!shouldIgnoreFailure(result.getFailureMessage())) {

            if (Options.FuzzPhasePlan.getValue(harnessOptions)) {
                HotSpotJVMCIRuntime jvmciRuntime = HotSpotJVMCIRuntime.runtime();
                HotSpotGraalCompiler compiler = (HotSpotGraalCompiler) jvmciRuntime.getCompiler();
                HotSpotGraalRuntimeProvider graalRuntime = compiler.getGraalRuntime();

                HotSpotFuzzedSuitesProvider hp = (HotSpotFuzzedSuitesProvider) graalRuntime.getHostBackend().getProviders().getSuites();
                TTY.printf("Fuzzing seed was %s%n", hp.getLastSeed().get());
            }

            super.handleFailure(result);
        }
    }

    /**
     * Prepares a compilation of the methods in the classes in {@link #inputClassPath}. If
     * {@link #inputClassPath} equals {@link #SUN_BOOT_CLASS_PATH} the boot classes are used.
     */
    public List<Compilation> prepare() throws IOException {
        String classPath = SUN_BOOT_CLASS_PATH.equals(inputClassPath) ? JRT_CLASS_PATH_ENTRY : inputClassPath;
        final String[] entries = getEntries(classPath);

        if (!LibGraal.isAvailable()) {
            try {
                // compile dummy method to get compiler initialized outside of the
                // config debug override.
                HotSpotResolvedJavaMethod dummyMethod = (HotSpotResolvedJavaMethod) JVMCI.getRuntime().getHostJVMCIBackend().getMetaAccess().lookupJavaMethod(
                                CompileTheWorld.class.getDeclaredMethod("dummy"));
                compileWithJarGraal(new LibGraalCompilationDriver.Compilation(dummyMethod), compilerOptions, null, false);
            } catch (NoSuchMethodException | SecurityException e1) {
                printStackTrace(e1);
            }
        }

        return prepareCompilations(entries);
    }

    private Path resolveScratchDir() throws IOException {
        if (scratchDir == null) {
            Path scratch = Files.createTempDirectory(Paths.get(".").toAbsolutePath(), "CompileTheWorld.scratchDir");
            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    removeDirectory(scratch);
                }

                private void removeDirectory(Path directory) {
                    try {
                        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
                            @Override
                            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                                Files.delete(file);
                                return FileVisitResult.CONTINUE;
                            }

                            @Override
                            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                                Files.delete(dir);
                                return FileVisitResult.CONTINUE;
                            }
                        });
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
            return scratch;
        } else {
            return Paths.get(scratchDir).toAbsolutePath();
        }
    }

    /**
     * Expands {@code classPath} to a list of entries.
     */
    private String[] getEntries(String classPath) throws IOException {
        final String[] initialEntries = classPath.split(File.pathSeparator);
        if (!"".equals(scratchDir)) {
            List<String> entries = new ArrayList<>(initialEntries.length * 2);
            Path scratch = resolveScratchDir();
            for (String entry : initialEntries) {
                entries.add(entry);
                if (entry.endsWith(".zip") || entry.endsWith(".jar")) {
                    try (JarClassPathEntry cpe = new JarClassPathEntry(entry)) {
                        cpe.extractNestedJars(scratch, entries);
                    }
                }
            }
            if (entries.size() == initialEntries.length) {
                return initialEntries;
            }
            return entries.toArray(new String[entries.size()]);
        }
        return initialEntries;
    }

    private static long nanoToMillis(long ns) {
        return ns / 1_000_000;
    }

    @Override
    public String testName() {
        return "CompileTheWorld";
    }

    static class Options {
        public static final OptionKey<Boolean> Help = new OptionKey<>(false);
        public static final OptionKey<String> Classpath = new OptionKey<>(CompileTheWorld.SUN_BOOT_CLASS_PATH);
        public static final OptionKey<Boolean> Verbose = new OptionKey<>(true);
        public static final OptionKey<Boolean> IgnoreCompilationFailures = new OptionKey<>(false);
        /**
         * Ignore Graal classes by default to avoid problems associated with compiling snippets and
         * method substitutions.
         */
        public static final OptionKey<String> LimitModules = new OptionKey<>("~jdk.graal.compiler");
        public static final OptionKey<Integer> Iterations = new OptionKey<>(1);
        public static final OptionKey<Integer> Repeat = new OptionKey<>(1);
        public static final OptionKey<String> MethodFilter = new OptionKey<>(null);
        public static final OptionKey<Integer> HugeMethodLimit = new OptionKey<>(8000);
        public static final OptionKey<String> ExcludeMethodFilter = new OptionKey<>(null);
        public static final OptionKey<Integer> StartAt = new OptionKey<>(1);
        public static final OptionKey<Integer> StopAt = new OptionKey<>(Integer.MAX_VALUE);
        public static final OptionKey<Integer> MaxClasses = new OptionKey<>(Integer.MAX_VALUE);
        public static final OptionKey<Integer> MaxCompiles = new OptionKey<>(Integer.MAX_VALUE);
        public static final OptionKey<Integer> StartAtCompile = new OptionKey<>(0);
        public static final OptionKey<Integer> StopAtCompile = new OptionKey<>(Integer.MAX_VALUE);
        public static final OptionKey<String> ScratchDir = new OptionKey<>(null);
        public static final OptionKey<String> Config = new OptionKey<>(null);
        public static final OptionKey<Boolean> MultiThreaded = new OptionKey<>(false);
        public static final OptionKey<Integer> StatsInterval = new OptionKey<>(15);
        public static final OptionKey<Integer> MetricsReportLimit = new OptionKey<>(10);
        public static final OptionKey<Integer> Threads = new OptionKey<>(0);
        public static final OptionKey<Boolean> InvalidateInstalledCode = new OptionKey<>(true);
        public static final OptionKey<Boolean> FuzzPhasePlan = new OptionKey<>(false);
        public static final OptionKey<String> LoadPhasePlan = new OptionKey<>(null);

        // @formatter:off
        static final ReflectionOptionDescriptors DESCRIPTORS = new ReflectionOptionDescriptors(Options.class,
                           "Help", "List options and their help messages and then exit.",
                      "Classpath", "Class path denoting methods to compile. Default is to compile boot classes.",
      "IgnoreCompilationFailures", "Do not exit with an error if any errors in compilation are detected. Defaults to false.",
                        "Verbose", "Verbose operation. Default is !MultiThreaded.",
                   "LimitModules", "Comma separated list of module names to which compilation should be limited. " +
                                   "Module names can be prefixed with \"~\" to exclude the named module.",
                     "Iterations", "The number of iterations to perform.",
                         "Repeat", "The number of times to compile each method.",
                   "MethodFilter", "Only compile methods matching this filter.",
                "HugeMethodLimit", "Don't compile methods larger than this (default: value of -XX:HugeMethodLimit).",
            "ExcludeMethodFilter", "Exclude methods matching this filter from compilation.",
                        "StartAt", "First class to consider for compilation (default = 1).",
                         "StopAt", "Last class to consider for compilation (default = <number of classes>).",
                     "MaxClasses", "Maximum number of classes to process (default = <number of classes>). " +
                                   "Ignored if less than (StopAt - StartAt + 1).",
                    "MaxCompiles", "Maximum number of compilations to perform.",
                 "StartAtCompile", "Skip all compilations before this value.",
                  "StopAtCompile", "Skip all compilations as of this value.",
                         "Config", "Option values to use during compile the world compilations. For example, " +
                                   "to disable partial escape analysis and print compilations specify " +
                                   "'PartialEscapeAnalysis=false PrintCompilation=true'. " +
                                   "Unless explicitly enabled with 'Inline=true' here, inlining is disabled.",
                     "ScratchDir", "Scratch directory for extracting nested jars. Specify empty string to disable " +
                                   "nested jar file extraction. Omit to use a temporary directory deleted before exiting.",
                  "MultiThreaded", "Run using multiple threads for compilation.",
                  "StatsInterval", "Report progress stats every N seconds.",
             "MetricsReportLimit", "Max number of entries to show in per-metric reports.",
                        "Threads", "Number of threads to use for multithreaded execution. Defaults to Runtime.getRuntime().availableProcessors().",
        "InvalidateInstalledCode", "Invalidate the generated code so the code cache doesn't fill up.",
                  "FuzzPhasePlan", "Use a different fuzzed phase plan for each compilation.",
                  "LoadPhasePlan", "Load the phase plan from the given file." +
                                   "This requires MethodFilter to have a defined value.");
        // @formatter:on
    }

    public static OptionValues loadHarnessOptions() {
        EconomicMap<OptionKey<?>, Object> values = OptionValues.newOptionMap();
        List<OptionDescriptors> loader = singletonList(DESCRIPTORS);
        OptionsParser.parseOptions(extractEntries(System.getProperties(), "CompileTheWorld.", true), values, loader);
        OptionValues options = new OptionValues(values);
        if (Options.Help.getValue(options)) {
            options.printHelp(loader, System.out, "CompileTheWorld.", true);
            System.exit(0);
        }
        return options;
    }

    public static CompileTheWorld create() {
        HotSpotJVMCIRuntime jvmciRuntime = HotSpotJVMCIRuntime.runtime();
        HotSpotGraalCompiler compiler = (HotSpotGraalCompiler) jvmciRuntime.getCompiler();
        HotSpotGraalRuntimeProvider graalRuntime = compiler.getGraalRuntime();
        OptionValues harnessOptions = loadHarnessOptions();
        OptionValues compilerOptions = graalRuntime.getOptions();
        if (Options.FuzzPhasePlan.getValue(harnessOptions)) {
            compilerOptions = FuzzedSuites.fuzzingOptions(compilerOptions);
        }
        return new CompileTheWorld(jvmciRuntime, compiler, harnessOptions, compilerOptions);
    }

    public static void main(String[] args) throws Throwable {
        HotSpotJVMCIRuntime jvmciRuntime = HotSpotJVMCIRuntime.runtime();
        HotSpotGraalCompiler compiler = (HotSpotGraalCompiler) jvmciRuntime.getCompiler();
        HotSpotGraalRuntimeProvider graalRuntime = compiler.getGraalRuntime();
        HotSpotCodeCacheProvider codeCache = (HotSpotCodeCacheProvider) jvmciRuntime.getHostJVMCIBackend().getCodeCache();
        OptionValues harnessOptions = loadHarnessOptions();

        String phasePlanFile = Options.LoadPhasePlan.getValue(harnessOptions);
        if (phasePlanFile != null) {
            assert Options.MethodFilter.getValue(harnessOptions) != null : "A MethodFilter should be provided.";
            graalRuntime.getHostBackend().getProviders().setSuites(
                            new HotSpotLoadedSuitesProvider((HotSpotSuitesProvider) graalRuntime.getHostBackend().getProviders().getSuites(), phasePlanFile));
        } else if (Options.FuzzPhasePlan.getValue(harnessOptions)) {
            graalRuntime.getHostBackend().getProviders().setSuites(new HotSpotFuzzedSuitesProvider((HotSpotSuitesProvider) graalRuntime.getHostBackend().getProviders().getSuites()));
        }

        int iterations = Options.Iterations.getValue(harnessOptions);
        for (int i = 0; i < iterations; i++) {
            codeCache.resetCompilationStatistics();
            TTY.println("CompileTheWorld : iteration " + i);

            CompileTheWorld ctw = create();
            ctw.compile();
            if (iterations > 1) {
                // Force a GC to encourage reclamation of nmethods when their InstalledCode
                // reference has been dropped.
                System.gc();
            }
        }
        // This is required as non-daemon threads can be started by class initializers
        System.exit(0);
    }
}
