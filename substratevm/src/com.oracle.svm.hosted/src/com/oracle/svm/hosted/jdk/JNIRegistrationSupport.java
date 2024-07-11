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
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;

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
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.impl.InternalPlatform;

import com.oracle.svm.core.BuildArtifacts;
import com.oracle.svm.core.ParsingReason;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.jdk.JNIRegistrationUtil;
import com.oracle.svm.core.jdk.NativeLibrarySupport;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.util.InterruptImageBuilding;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl.AfterImageWriteAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.BeforeImageWriteAccessImpl;
import com.oracle.svm.hosted.c.NativeLibraries;
import com.oracle.svm.hosted.c.codegen.CCompilerInvoker;
import com.oracle.svm.hosted.c.util.FileUtils;

import jdk.internal.loader.BootLoader;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/** Registration of native JDK libraries. */
@Platforms(InternalPlatform.PLATFORM_JNI.class)
@AutomaticallyRegisteredFeature
public final class JNIRegistrationSupport extends JNIRegistrationUtil implements InternalFeature {

    public static class Options {
        @Option(help = "Create a `jvm` shim for native libraries that link against that library.")//
        public static final HostedOptionKey<Boolean> CreateJvmShim = new HostedOptionKey<>(false);
    }

    private final ConcurrentMap<String, Boolean> registeredLibraries = new ConcurrentHashMap<>();
    private NativeLibraries nativeLibraries = null;
    private boolean isSunMSCAPIProviderReachable = false;

    public static JNIRegistrationSupport singleton() {
        return ImageSingletons.lookup(JNIRegistrationSupport.class);
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        nativeLibraries = ((BeforeAnalysisAccessImpl) access).getNativeLibraries();
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        if (isWindows()) {
            var optSunMSCAPIClass = optionalClazz(access, "sun.security.mscapi.SunMSCAPI");
            isSunMSCAPIProviderReachable = optSunMSCAPIClass.isPresent() && access.isReachable(optSunMSCAPIClass.get());
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
        if (libname != null && registeredLibraries.putIfAbsent(libname, Boolean.TRUE) == null) {
            /*
             * If a library is in our list of static standard libraries, add the library to the
             * linker command.
             */
            if (NativeLibrarySupport.singleton().isPreregisteredBuiltinLibrary(libname)) {
                nativeLibraries.addStaticJniLibrary(libname);
            }
        }
    }

    public boolean isRegisteredLibrary(String libname) {
        return registeredLibraries.containsKey(libname);
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
        shimExports.computeIfAbsent(shimName, s -> new TreeSet<>()).addAll(List.of(exports));
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
        if (SubstrateOptions.StaticExecutable.getValue() || isDarwin()) {
            return; /* Not supported. */
        }

        if (shimExports.containsKey("jvm") || Options.CreateJvmShim.getValue()) {
            /* When making a `jvm` shim, also re-export the JNI functions that VM exports. */
            addJvmShimExports("JNI_CreateJavaVM", "JNI_GetCreatedJavaVMs", "JNI_GetDefaultJavaVMInitArgs");
        }

        ((BeforeImageWriteAccessImpl) access).registerLinkerInvocationTransformer(linkerInvocation -> {
            /* Make sure the native image contains all symbols necessary for shim libraries. */
            getShimExports().map(isWindows() ? "/export:"::concat : "-Wl,-u,"::concat)
                            .forEach(linkerInvocation::addNativeLinkerOption);
            return linkerInvocation;
        });

        imageName = ((BeforeImageWriteAccessImpl) access).getImageName();
    }

    private Stream<String> getShimExports() {
        return shimExports.values().stream()
                        .flatMap(Collection::stream)
                        .distinct();
    }

    private AfterImageWriteAccessImpl accessImpl;

    @Override
    @SuppressWarnings("try")
    public void afterImageWrite(AfterImageWriteAccess access) {
        if (SubstrateOptions.StaticExecutable.getValue() || isDarwin()) {
            return; /* Not supported. */
        }

        accessImpl = (AfterImageWriteAccessImpl) access;
        try (Scope s = accessImpl.getDebugContext().scope("JDKLibs")) {
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
    @SuppressWarnings("try")
    private void copyJDKLibraries(Path jdkLibDir) {
        DebugContext debug = accessImpl.getDebugContext();
        try (Scope s = debug.scope("copy");
                        Indent i = debug.logAndIndent("from: %s", jdkLibDir)) {
            for (String libname : new TreeSet<>(registeredLibraries.keySet())) {
                String library = System.mapLibraryName(libname);

                if (NativeLibrarySupport.singleton().isPreregisteredBuiltinLibrary(libname)) {
                    /* Skip statically linked JDK libraries. */
                    debug.log(DebugContext.INFO_LEVEL, "%s: SKIPPED", library);
                    continue;
                }

                if (libname.equals("sunmscapi") && !isSunMSCAPIProviderReachable) {
                    /*
                     * Ignore `sunmscapi` library if `SunMSCAPI` provider is not reachable (it's
                     * always registered as a workaround in `Target_java_security_Provider`).
                     */
                    debug.log(DebugContext.INFO_LEVEL, "%s: IGNORED", library);
                    continue;
                }

                try {
                    Path libraryPath = accessImpl.getImagePath().resolveSibling(library);
                    Files.copy(jdkLibDir.resolve(library), libraryPath, REPLACE_EXISTING);
                    BuildArtifacts.singleton().add(ArtifactType.JDK_LIBRARY, libraryPath);
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

    /** Makes shim libraries that are necessary to satisfy dependencies of JDK libraries. */
    @SuppressWarnings("try")
    private void makeShimLibraries() {
        for (String shimName : shimExports.keySet()) {
            DebugContext debug = accessImpl.getDebugContext();
            try (Scope s = debug.scope(shimName + "Shim")) {
                if (debug.isLogEnabled(DebugContext.INFO_LEVEL)) {
                    debug.log("exports: %s", String.join(", ", shimExports.get(shimName)));
                }
                makeShimLibrary(shimName);
            }
        }
    }

    /** Makes a shim library that re-exports functions from the native image. */
    @SuppressWarnings("try")
    private void makeShimLibrary(String shimName) {
        assert ImageSingletons.contains(CCompilerInvoker.class);

        List<String> linkerCommand;
        Path image = accessImpl.getImagePath();
        Path shimLibrary = image.resolveSibling(System.mapLibraryName(shimName));
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
        } else {
            /*
             * To satisfy the dynamic loader and enable re-export it is enough to have a library
             * with the expected name. So we just create an empty one ...
             */
            linkerCommand = ImageSingletons.lookup(CCompilerInvoker.class)
                            .createCompilerCommand(List.of("-shared", "-x", "c"), shimLibrary, Path.of("/dev/null"));
            /* ... and add an explicit dependency on the native image if it is a shared library. */
            if (!accessImpl.getImageKind().isExecutable) {
                linkerCommand.addAll(List.of("-Wl,-no-as-needed", "-L" + image.getParent(), "-l:" + image.getFileName(),
                                "-Wl,--enable-new-dtags", "-Wl,-rpath,$ORIGIN"));
            }
        }

        DebugContext debug = accessImpl.getDebugContext();
        try (Scope s = debug.scope("link");
                        Activation a = debug.activate()) {
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

    /** Returns the import library of the native image. */
    private Path getImageImportLib() {
        assert isWindows();
        Path importLib = accessImpl.getTempDirectory().resolve(imageName + ".lib");
        assert Files.exists(importLib);
        return importLib;
    }
}
