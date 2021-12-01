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
package com.oracle.svm.hosted.jdk;

import static com.oracle.svm.core.BuildArtifacts.ArtifactType;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugContext.Activation;
import org.graalvm.compiler.debug.DebugContext.Scope;
import org.graalvm.compiler.debug.Indent;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.impl.InternalPlatform;

import com.oracle.svm.core.BuildArtifacts;
import com.oracle.svm.core.ParsingReason;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.graal.GraalFeature;
import com.oracle.svm.core.jdk.JNIRegistrationUtil;
import com.oracle.svm.core.jdk.NativeLibrarySupport;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.util.InterruptImageBuilding;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl.AfterImageWriteAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.svm.hosted.c.NativeLibraries;
import com.oracle.svm.hosted.c.codegen.CCompilerInvoker;
import com.oracle.svm.hosted.c.util.FileUtils;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/** Registration of native JDK libraries. */
@Platforms(InternalPlatform.PLATFORM_JNI.class)
@AutomaticFeature
public final class JNIRegistrationSupport extends JNIRegistrationUtil implements GraalFeature {

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
            isSunMSCAPIProviderReachable = access.isReachable(clazz(access, "sun.security.mscapi.SunMSCAPI"));
        }
    }

    @Override
    public void registerGraphBuilderPlugins(Providers providers, Plugins plugins, ParsingReason reason) {
        registerLoadLibraryPlugin(plugins, System.class);
    }

    public void registerLoadLibraryPlugin(Plugins plugins, Class<?> clazz) {
        Registration r = new Registration(plugins.getInvocationPlugins(), clazz);
        r.register1("loadLibrary", String.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode libnameNode) {
                /*
                 * Support for automatic discovery of standard JDK libraries. This works because all
                 * of the JDK uses System.loadLibrary or jdk.internal.loader.BootLoader with literal
                 * String arguments.
                 */
                if (libnameNode.isConstant()) {
                    registerLibrary((String) SubstrateObjectConstant.asObject(libnameNode.asConstant()));
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

    boolean isRegisteredLibrary(String libname) {
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
        shimExports.computeIfAbsent(shimName, s -> new TreeSet<>()).addAll(Arrays.asList(exports));
    }

    private AfterImageWriteAccessImpl accessImpl;

    @Override
    @SuppressWarnings("try")
    public void afterImageWrite(AfterImageWriteAccess access) {
        accessImpl = (AfterImageWriteAccessImpl) access;
        try (Scope s = accessImpl.getDebugContext().scope("JDKLibs")) {
            if (isWindows()) {
                /* On Windows, JDK libraries are in `<java.home>\bin` directory. */
                Path jdkLibDir = Paths.get(System.getProperty("java.home"), "bin");
                /* Copy JDK libraries needed to run the native image. */
                copyJDKLibraries(jdkLibDir);
                /*
                 * JDK libraries can depend on `jvm.dll` and `java.dll`, so to satisfy their
                 * dependencies, we create shim DLLs that re-export the actual functions from the
                 * native image itself.
                 */
                makeShimDLLs();
            }
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
                    BuildArtifacts.singleton().add(ArtifactType.JDK_LIB, libraryPath);
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

    /** Makes shim DLLs to satisfy dependencies of JDK libraries. */
    @SuppressWarnings("try")
    private void makeShimDLLs() {
        for (String shimName : shimExports.keySet()) {
            DebugContext debug = accessImpl.getDebugContext();
            try (Scope s = debug.scope(shimName + "ShimDLL")) {
                if (debug.isLogEnabled(DebugContext.INFO_LEVEL)) {
                    debug.log("exports: %s", String.join(", ", shimExports.get(shimName)));
                }
                makeShimDLL(shimName);
            }
        }
    }

    /** Makes a shim DLL by re-exporting the actual functions from the native image itself. */
    @SuppressWarnings("try")
    private void makeShimDLL(String shimName) {
        Path shimDLL = accessImpl.getImagePath().resolveSibling(shimName + ".dll");
        /* Dependencies are the native image (so we can re-export from it) and C Runtime. */
        Path[] shimDLLDependencies = {getImageImportLib(), Paths.get("msvcrt.lib")};

        assert ImageSingletons.contains(CCompilerInvoker.class);
        List<String> linkerCommand = ImageSingletons.lookup(CCompilerInvoker.class)
                        .createCompilerCommand(Collections.emptyList(), shimDLL, shimDLLDependencies);
        /* First add linker options ... */
        linkerCommand.addAll(Arrays.asList("/link", "/dll", "/implib:" + shimName + ".lib"));
        /* ... and then the exports that were added for re-export. */
        for (String export : shimExports.get(shimName)) {
            linkerCommand.add("/export:" + export);
        }

        DebugContext debug = accessImpl.getDebugContext();
        try (Scope s = debug.scope("link");
                        Activation a = debug.activate()) {
            if (FileUtils.executeCommand(linkerCommand) != 0) {
                VMError.shouldNotReachHere();
            }
            BuildArtifacts.singleton().add(ArtifactType.JDK_LIB_SHIM, shimDLL);
            debug.log("%s.dll: OK", shimName);
        } catch (InterruptedException e) {
            throw new InterruptImageBuilding();
        } catch (IOException e) {
            VMError.shouldNotReachHere(e);
        }
    }

    /** Returns the import library of the native image. */
    private Path getImageImportLib() {
        Path image = accessImpl.getImagePath();
        String imageName = String.valueOf(image.getFileName());
        String importLibName = imageName.substring(0, imageName.lastIndexOf('.')) + ".lib";
        Path importLib = accessImpl.getImageKind().isExecutable
                        ? accessImpl.getTempDirectory().resolve(importLibName)
                        : image.resolveSibling(importLibName);
        assert Files.exists(importLib);
        return importLib;
    }
}
