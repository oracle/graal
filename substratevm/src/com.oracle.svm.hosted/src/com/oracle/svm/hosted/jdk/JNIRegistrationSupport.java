/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.jdk;

import static com.oracle.svm.core.BuildArtifacts.ArtifactType;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.impl.InternalPlatform;

import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.core.BuildArtifacts;
import com.oracle.svm.core.ParsingReason;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.jdk.JNIRegistrationUtil;
import com.oracle.svm.core.jdk.NativeLibrarySupport;
import com.oracle.svm.core.util.InterruptImageBuilding;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.FeatureImpl.AfterAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.AfterImageWriteAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.BeforeImageWriteAccessImpl;
import com.oracle.svm.hosted.c.NativeLibraries;
import com.oracle.svm.hosted.c.codegen.CCompilerInvoker;
import com.oracle.svm.hosted.c.util.FileUtils;
import com.oracle.svm.hosted.image.AbstractImage.NativeImageKind;
import com.oracle.svm.hosted.imagelayer.SnapshotWriters;
import com.oracle.svm.hosted.imagelayer.SVMImageSingletonWriter;
import com.oracle.svm.hosted.imagelayer.SVMImageLayerSingletonLoader;
import com.oracle.svm.hosted.snapshot.util.SnapshotAdapters;
import com.oracle.svm.shared.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.shared.option.HostedOptionKey;
import com.oracle.svm.shared.singletons.ImageSingletonLoader;
import com.oracle.svm.shared.singletons.ImageSingletonWriter;
import com.oracle.svm.shared.singletons.LayeredPersistFlags;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.PartiallyLayerAware;
import com.oracle.svm.shared.singletons.traits.LayeredCallbacksSingletonTrait;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredCallbacksSupplier;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.DebugContext.Activation;
import jdk.graal.compiler.debug.DebugContext.Scope;
import jdk.graal.compiler.debug.Indent;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin.RequiredInvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.phases.util.Providers;
import jdk.internal.loader.BootLoader;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/** Registration of native JDK libraries. */
@Platforms(InternalPlatform.PLATFORM_JNI.class)
@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class, other = PartiallyLayerAware.class)
@AutomaticallyRegisteredFeature
public final class JNIRegistrationSupport extends JNIRegistrationUtil implements InternalFeature {

    public static class Options {
        @Option(help = "Create a `jvm` shim for native libraries that link against that library.")//
        public static final HostedOptionKey<Boolean> CreateJvmShim = new HostedOptionKey<>(false);
    }

    private NativeLibraries nativeLibraries = null;
    private JNIRegistrationSupportSingleton jniRegistrationSupportSingleton = null;
    private boolean isSunMSCAPIProviderReachable = false;
    private final List<Consumer<String>> libraryRegistrationHandlers = new CopyOnWriteArrayList<>();
    private List<String> darwinOtoolCommand;

    public static JNIRegistrationSupport singleton() {
        return ImageSingletons.lookup(JNIRegistrationSupport.class);
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        jniRegistrationSupportSingleton = new JNIRegistrationSupportSingleton();
        ImageSingletons.add(JNIRegistrationSupportSingleton.class, jniRegistrationSupportSingleton);
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        nativeLibraries = ((BeforeAnalysisAccessImpl) access).getNativeLibraries();
        registerLibrary("java");
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        if (isWindows()) {
            AfterAnalysisAccessImpl afterAnalysisAccessImpl = (AfterAnalysisAccessImpl) access;
            var optSunMSCAPIClass = optionalType(access, "sun.security.mscapi.SunMSCAPI").map(AnalysisType.class::cast);
            isSunMSCAPIProviderReachable = optSunMSCAPIClass.isPresent() && afterAnalysisAccessImpl.isReachable(optSunMSCAPIClass.get());
        }
        if (ImageLayerBuildingSupport.buildingExtensionLayer()) {
            for (String library : jniRegistrationSupportSingleton.prevLayerRegisteredLibraries) {
                addLibrary(library);
            }
        }
    }

    @Override
    public void registerGraphBuilderPlugins(Providers providers, Plugins plugins, ParsingReason reason) {
        registerLoadLibraryPlugin(providers, plugins, System.class);
        registerLoadLibraryPlugin(providers, plugins, BootLoader.class);
    }

    public void registerLoadLibraryPlugin(Providers providers, Plugins plugins, Class<?> clazz) {
        Registration r = new Registration(plugins.getInvocationPlugins(), clazz);
        r.register(new RequiredInvocationPlugin("loadLibrary", String.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode libnameNode) {
                /*
                 * Support for automatic discovery of standard JDK libraries. This works because all
                 * of the JDK uses System.loadLibrary or jdk.internal.loader.BootLoader with literal
                 * String arguments.
                 */
                if (libnameNode.isConstant()) {
                    registerLibrary(providers.getSnippetReflection().asObject(String.class, libnameNode.asJavaConstant()));
                }
                /* We never want to do any actual intrinsification, process the original invoke. */
                return false;
            }
        });
    }

    void registerLibrary(String libname) {
        if (libname != null && !jniRegistrationSupportSingleton.currentLayerRegisteredLibraries.contains(libname)) {
            jniRegistrationSupportSingleton.currentLayerRegisteredLibraries.add(libname);
            for (Consumer<String> handler : libraryRegistrationHandlers) {
                handler.accept(libname);
            }
            addLibrary(libname);
        }
    }

    void addLibraryRegistrationHandler(Consumer<String> handler) {
        libraryRegistrationHandlers.add(handler);
    }

    private void addLibrary(String libname) {
        /*
         * If a library is in our list of static standard libraries, add the library to the linker
         * command.
         */
        if (NativeLibrarySupport.singleton().isPreregisteredBuiltinLibrary(libname)) {
            nativeLibraries.addStaticJniLibrary(libname);
        }
    }

    public boolean isCurrentLayerRegisteredLibrary(String libname) {
        return jniRegistrationSupportSingleton.currentLayerRegisteredLibraries.contains(libname);
    }

    boolean isPreviousLayerRegisteredLibrary(String libname) {
        return jniRegistrationSupportSingleton.prevLayerRegisteredLibraries.contains(libname);
    }

    public boolean isAnyLayerRegisteredLibrary(String libname) {
        return isCurrentLayerRegisteredLibrary(libname) || isPreviousLayerRegisteredLibrary(libname);
    }

    boolean isRegisteredLibrary(String libname) {
        return isAnyLayerRegisteredLibrary(libname);
    }

    /** Adds exports that `jvm` shim should re-export. */
    void addJvmShimExports(String... exports) {
        addShimExports("jvm", exports);
    }

    /** Adds exports that `java` shim should re-export. */
    void addJavaShimExports(String... exports) {
        addShimExports("java", exports);
    }

    private final SortedMap<String, SortedSet<String>> shimExports = new TreeMap<>();

    private void addShimExports(String shimName, String... exports) {
        assert exports != null && exports.length > 0;
        shimExports.computeIfAbsent(shimName, _ -> new TreeSet<>()).addAll(List.of(exports));
    }

    /** Returns symbols that are re-exported by shim libraries. */
    public static Stream<String> getShimLibrarySymbols() {
        if (ImageSingletons.contains(JNIRegistrationSupport.class)) {
            return singleton().getShimExports();
        }
        return Stream.empty();
    }

    private String imageName;

    @Override
    public void beforeImageWrite(BeforeImageWriteAccess access) {
        if (SubstrateOptions.StaticExecutable.getValue()) {
            return; /* Not supported. */
        }

        if (shimExports.containsKey("jvm") || Options.CreateJvmShim.getValue()) {
            /* When making a `jvm` shim, also re-export the JNI functions that VM exports. */
            addJvmShimExports("JNI_CreateJavaVM", "JNI_GetCreatedJavaVMs", "JNI_GetDefaultJavaVMInitArgs");
        }

        ((BeforeImageWriteAccessImpl) access).registerLinkerInvocationTransformer(linkerInvocation -> {
            if (isDarwin()) {
                linkerInvocation.addRPath("@loader_path");
            }
            /* Make sure the native image contains all symbols necessary for shim libraries. */
            getShimExports().map(this::preserveShimExport)
                            .forEach(linkerInvocation::addNativeLinkerOption);
            return linkerInvocation;
        });

        imageName = ((BeforeImageWriteAccessImpl) access).getImageName();
    }

    private String preserveShimExport(String symbol) {
        if (isWindows()) {
            return "/export:" + symbol;
        } else if (isDarwin()) {
            return "-Wl,-u,_" + symbol;
        } else {
            return "-Wl,-u," + symbol;
        }
    }

    private Stream<String> getShimExports() {
        return shimExports.values().stream()
                        .flatMap(Collection::stream)
                        .distinct();
    }

    private AfterImageWriteAccessImpl accessImpl;

    @Override
    public void afterImageWrite(AfterImageWriteAccess access) {
        if (SubstrateOptions.StaticExecutable.getValue()) {
            return; /* Not supported. */
        }

        accessImpl = (AfterImageWriteAccessImpl) access;
        try (Scope _ = accessImpl.getDebugContext().scope("JDKLibs")) {
            Path jdkLibDir = JDKLibDirectoryProvider.singleton().getJDKLibDirectory();
            /* Copy JDK libraries needed to run the native image. */
            copyJDKLibraries(jdkLibDir);
            /*
             * JDK libraries can depend on `libjvm` and `libjava`, so to satisfy their dependencies
             * we use shim libraries to re-export the actual functions from the native image itself.
             */
            makeShimLibraries();
        } finally {
            accessImpl = null;
        }
    }

    /** Copies registered dynamic libraries from the JDK next to the image. */
    private void copyJDKLibraries(Path jdkLibDir) {
        DebugContext debug = accessImpl.getDebugContext();
        try (Scope _ = debug.scope("copy");
                        Indent _ = debug.logAndIndent("from: %s", jdkLibDir)) {
            for (String libname : getJDKLibrariesToCopy(jdkLibDir)) {
                String library = System.mapLibraryName(libname);
                try {
                    copyJDKLibrary(jdkLibDir, library);
                    debug.log("%s: OK", library);
                } catch (NoSuchFileException e) {
                    /* Ignore libraries that are not present in the JDK. */
                    debug.log(DebugContext.INFO_LEVEL, "%s: IGNORED", library);
                } catch (IOException e) {
                    VMError.shouldNotReachHere(e);
                }
            }
        }
    }

    private SortedSet<String> getJDKLibrariesToCopy(Path jdkLibDir) {
        SortedSet<String> result = new TreeSet<>();
        Deque<String> worklist = new ArrayDeque<>(new TreeSet<>(jniRegistrationSupportSingleton.currentLayerRegisteredLibraries));
        List<String> otoolCommand = isDarwin() ? getDarwinOtoolCommand() : List.of();
        while (!worklist.isEmpty()) {
            String libname = worklist.removeFirst();
            if (!shouldCopyJDKLibrary(libname) || !result.add(libname)) {
                continue;
            }
            if (isDarwin()) {
                worklist.addAll(getDarwinJDKLibraryDependencies(jdkLibDir, libname, otoolCommand));
            }
        }
        return result;
    }

    private List<String> getDarwinOtoolCommand() {
        if (darwinOtoolCommand == null) {
            darwinOtoolCommand = findDarwinOtoolCommand();
        }
        return darwinOtoolCommand;
    }

    private static List<String> findDarwinOtoolCommand() {
        Path xcrun = Path.of("/usr/bin/xcrun");
        if (Files.isExecutable(xcrun)) {
            try {
                Process process = FileUtils.prepareCommand(List.of(xcrun.toString(), "--find", "otool"), null).redirectErrorStream(true).start();
                List<String> output = FileUtils.readAllLines(process.getInputStream());
                int status = process.waitFor();
                if (status == 0 && !output.isEmpty() && !output.getFirst().isBlank()) {
                    return List.of(output.getFirst().trim());
                }
            } catch (InterruptedException e) {
                throw new InterruptImageBuilding();
            } catch (IOException e) {
                /* Fall back to PATH lookup below and report an actionable error if that also fails. */
            }
        }
        return List.of("otool");
    }

    private boolean shouldCopyJDKLibrary(String libname) {
        if (jniRegistrationSupportSingleton.prevLayerRegisteredLibraries.contains(libname)) {
            return false;
        }
        if (NativeLibrarySupport.singleton().isPreregisteredBuiltinLibrary(libname)) {
            return false;
        }
        if (shimExports.containsKey(libname)) {
            return false;
        }
        return !libname.equals("sunmscapi") || isSunMSCAPIProviderReachable;
    }

    private void copyJDKLibrary(Path jdkLibDir, String library) throws IOException {
        Path libraryPath = accessImpl.getImagePath().resolveSibling(library);
        Files.copy(jdkLibDir.resolve(library), libraryPath, REPLACE_EXISTING);
        BuildArtifacts.singleton().add(ArtifactType.JDK_LIBRARY, libraryPath);
    }

    private static List<String> getDarwinJDKLibraryDependencies(Path jdkLibDir, String libname, List<String> otoolCommand) {
        Path libraryPath = jdkLibDir.resolve(System.mapLibraryName(libname));
        if (!Files.exists(libraryPath)) {
            return List.of();
        }

        List<String> dependencies = new ArrayList<>();
        List<String> command = new ArrayList<>(otoolCommand);
        command.add("-L");
        command.add(libraryPath.toString());
        try {
            Process process = FileUtils.prepareCommand(command, null).redirectErrorStream(true).start();
            List<String> output = FileUtils.readAllLines(process.getInputStream());
            int status = process.waitFor();
            if (status != 0) {
                throw UserError.abort("Could not inspect Darwin JDK library dependencies with '%s' (exit status %d):%n%s",
                                String.join(" ", command), status, String.join(System.lineSeparator(), output));
            }
            for (String line : output) {
                String dependency = line.trim().split("\\s+", 2)[0];
                String dependencyLibName = asJDKLibraryName(jdkLibDir, dependency);
                if (dependencyLibName != null) {
                    dependencies.add(dependencyLibName);
                }
            }
        } catch (InterruptedException e) {
            throw new InterruptImageBuilding();
        } catch (IOException e) {
            throw UserError.abort(e, "Could not inspect Darwin JDK library dependencies with '%s'. Install Xcode Command Line Tools or make 'otool' available on PATH.",
                            String.join(" ", command));
        }
        return dependencies;
    }

    private static String asJDKLibraryName(Path jdkLibDir, String dependency) {
        if (dependency.startsWith("@rpath/") || dependency.startsWith("@loader_path/")) {
            return asJDKLibraryName(jdkLibDir.resolve(Path.of(dependency).getFileName()));
        }

        Path dependencyPath = Path.of(dependency);
        if (dependencyPath.isAbsolute() && dependencyPath.startsWith(jdkLibDir)) {
            return asJDKLibraryName(dependencyPath);
        }

        return null;
    }

    private static String asJDKLibraryName(Path libraryPath) {
        String fileName = libraryPath.getFileName().toString();
        if (fileName.startsWith("lib") && fileName.endsWith(".dylib")) {
            return fileName.substring("lib".length(), fileName.length() - ".dylib".length());
        }
        return null;
    }

    /** Makes shim libraries that are necessary to satisfy dependencies of JDK libraries. */
    private void makeShimLibraries() {
        for (String shimName : shimExports.keySet()) {
            DebugContext debug = accessImpl.getDebugContext();
            try (Scope _ = debug.scope(shimName + "Shim")) {
                if (debug.isLogEnabled(DebugContext.INFO_LEVEL)) {
                    debug.log("exports: %s", String.join(", ", shimExports.get(shimName)));
                }
                makeShimLibrary(shimName);
            }
        }
    }

    /** Makes a shim library that re-exports functions from the native image. */
    private void makeShimLibrary(String shimName) {
        assert ImageSingletons.contains(CCompilerInvoker.class);

        List<String> linkerCommand;
        Path image = accessImpl.getImagePath();
        Path shimLibrary = image.resolveSibling(System.mapLibraryName(shimName));
        if (accessImpl.getImageKind() == NativeImageKind.SHARED_LIBRARY && shimLibrary.equals(image)) {
            /*
             * A shared library image gets built with the same name this shim-library would have.
             * This is an advanced use-case, and we assume the user knows what they are doing. Thus,
             * we will suppress producing a shim library in this case.
             */
            return;
        }

        if (isWindows()) {
            /* Dependencies are the native image (so we can re-export from it) and C Runtime. */
            linkerCommand = ImageSingletons.lookup(CCompilerInvoker.class)
                            .createCompilerCommand(List.of(), shimLibrary, getImageImportLib(), Path.of("msvcrt.lib"));
            /* First add linker options ... */
            linkerCommand.addAll(List.of("/link", "/dll", "/implib:" + shimName + ".lib"));
            /* ... and then the exports that were added for re-export. */
            for (String export : shimExports.get(shimName)) {
                linkerCommand.add("/export:" + export);
            }
        } else if (isDarwin()) {
            Path shimSource = writeDarwinShimSource(shimName);
            linkerCommand = ImageSingletons.lookup(CCompilerInvoker.class)
                            .createCompilerCommand(List.of("-shared", "-Wl,-install_name,@rpath/" + shimLibrary.getFileName()), shimLibrary, shimSource);
        } else {
            /*
             * To satisfy the dynamic loader and enable re-export it is enough to have a library
             * with the expected name. So we just create an empty one ...
             */
            linkerCommand = ImageSingletons.lookup(CCompilerInvoker.class)
                            .createCompilerCommand(List.of("-shared", "-x", "c", "-nostdlib"), shimLibrary, Path.of("/dev/null"));
            /* ... and add an explicit dependency on the native image if it is a shared library. */
            if (!accessImpl.getImageKind().isExecutable) {
                linkerCommand.addAll(List.of("-Wl,-no-as-needed", "-L" + image.getParent(), "-l:" + image.getFileName(),
                                "-Wl,--enable-new-dtags", "-Wl,-rpath,$ORIGIN"));
            }
        }

        DebugContext debug = accessImpl.getDebugContext();
        try (Scope _ = debug.scope("link");
                        Activation _ = debug.activate()) {
            int cmdResult = FileUtils.executeCommand(linkerCommand);
            if (cmdResult != 0) {
                VMError.shouldNotReachHereUnexpectedInput(cmdResult); // ExcludeFromJacocoGeneratedReport
            }
            BuildArtifacts.singleton().add(ArtifactType.JDK_LIBRARY_SHIM, shimLibrary);
            debug.log("%s: OK", shimLibrary.getFileName());
        } catch (InterruptedException e) {
            throw new InterruptImageBuilding();
        } catch (IOException e) {
            VMError.shouldNotReachHere(e);
        }
    }

    private Path writeDarwinShimSource(String shimName) {
        Path shimSource = accessImpl.getTempDirectory().resolve(shimName + "_shim.c");
        StringBuilder source = new StringBuilder();
        source.append("#include <dlfcn.h>\n");
        source.append("#include <stdio.h>\n");
        source.append("#include <stdlib.h>\n\n");
        source.append("static void *image_handle;\n");
        for (String export : shimExports.get(shimName)) {
            source.append("static void *").append(targetPointerName(export)).append(";\n");
        }
        source.append("\n");
        source.append("static void *resolve_symbol(const char *name) {\n");
        source.append("    void *main_handle = dlopen(NULL, RTLD_LAZY);\n");
        source.append("    dlerror();\n");
        source.append("    void *result = main_handle == NULL ? NULL : dlsym(main_handle, name);\n");
        source.append("    if (dlerror() != NULL) {\n");
        source.append("        result = NULL;\n");
        source.append("    }\n");
        source.append("    if (result == NULL) {\n");
        source.append("        if (image_handle == NULL) {\n");
        source.append("            image_handle = dlopen(").append(cStringLiteral("@loader_path/" + accessImpl.getImagePath().getFileName())).append(", RTLD_LAZY);\n");
        source.append("        }\n");
        source.append("        dlerror();\n");
        source.append("        result = image_handle == NULL ? NULL : dlsym(image_handle, name);\n");
        source.append("        if (dlerror() != NULL) {\n");
        source.append("            result = NULL;\n");
        source.append("        }\n");
        source.append("    }\n");
        source.append("    if (result == NULL) {\n");
        source.append("        fprintf(stderr, \"Could not resolve Native Image shim symbol %s\\n\", name);\n");
        source.append("        abort();\n");
        source.append("    }\n");
        source.append("    return result;\n");
        source.append("}\n\n");
        source.append("__attribute__((constructor)) static void initialize_shim(void) {\n");
        for (String export : shimExports.get(shimName)) {
            source.append("    ").append(targetPointerName(export)).append(" = resolve_symbol(").append(cStringLiteral(export)).append(");\n");
        }
        source.append("}\n\n");
        for (String export : shimExports.get(shimName)) {
            String targetPointer = targetPointerName(export);
            source.append("__attribute__((naked)) void ").append(export).append("(void) {\n");
            source.append("#if defined(__aarch64__)\n");
            source.append("    __asm__(\"adrp x16, _").append(targetPointer).append("@PAGE\\n\"\n");
            source.append("            \"ldr x16, [x16, _").append(targetPointer).append("@PAGEOFF]\\n\"\n");
            source.append("            \"br x16\");\n");
            source.append("#elif defined(__x86_64__)\n");
            source.append("    __asm__(\"jmpq *_").append(targetPointer).append("(%rip)\");\n");
            source.append("#else\n");
            source.append("#error Unsupported Darwin architecture\n");
            source.append("#endif\n");
            source.append("}\n\n");
        }
        try {
            Files.writeString(shimSource, source, StandardCharsets.UTF_8);
        } catch (IOException e) {
            VMError.shouldNotReachHere(e);
        }
        return shimSource;
    }

    private static String cStringLiteral(String value) {
        StringBuilder result = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> result.append("\\\\");
                case '"' -> result.append("\\\"");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if (c < 0x20 || c == 0x7f) {
                        result.append('\\');
                        result.append((char) ('0' + ((c >> 6) & 7)));
                        result.append((char) ('0' + ((c >> 3) & 7)));
                        result.append((char) ('0' + (c & 7)));
                    } else {
                        result.append(c);
                    }
                }
            }
        }
        return result.append('"').toString();
    }

    private static String targetPointerName(String symbol) {
        StringBuilder result = new StringBuilder("target_");
        for (int i = 0; i < symbol.length(); i++) {
            char c = symbol.charAt(i);
            result.append(Character.isLetterOrDigit(c) || c == '_' ? c : '_');
        }
        return result.toString();
    }

    /** Returns the import library of the native image. */
    private Path getImageImportLib() {
        assert isWindows();
        Path importLib = accessImpl.getTempDirectory().resolve(imageName + ".lib");
        assert Files.exists(importLib);
        return importLib;
    }

    @SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = JNIRegistrationSupportSingleton.LayeredCallbacks.class)
    private static final class JNIRegistrationSupportSingleton {
        private final List<String> currentLayerRegisteredLibraries = new CopyOnWriteArrayList<>();
        private final List<String> prevLayerRegisteredLibraries = new ArrayList<>();

        static class LayeredCallbacks extends SingletonLayeredCallbacksSupplier {
            @Override
            public LayeredCallbacksSingletonTrait getLayeredCallbacksTrait() {
                return new LayeredCallbacksSingletonTrait(new SingletonLayeredCallbacks<JNIRegistrationSupportSingleton>() {
                    @Override
                    public LayeredPersistFlags doPersist(ImageSingletonWriter writer, JNIRegistrationSupportSingleton singleton) {
                        var snapshotWriter = ((SVMImageSingletonWriter) writer).getSnapshotWriter();
                        SnapshotWriters.initStringList(snapshotWriter::initRegisteredJNILibraries, singleton.currentLayerRegisteredLibraries.stream());
                        return LayeredPersistFlags.CALLBACK_ON_REGISTRATION;
                    }

                    @Override
                    public void onSingletonRegistration(ImageSingletonLoader loader, JNIRegistrationSupportSingleton singleton) {
                        var snapshotLoader = ((SVMImageLayerSingletonLoader.ImageSingletonLoaderImpl) loader).getSnapshotLoader();
                        SnapshotAdapters.forEach(snapshotLoader.getRegisteredJNILibraries(), singleton.prevLayerRegisteredLibraries::add);
                    }
                });
            }
        }
    }
}
