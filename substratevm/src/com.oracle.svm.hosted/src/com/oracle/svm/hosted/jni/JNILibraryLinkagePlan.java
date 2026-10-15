/*
 * Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.jni;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.impl.InternalPlatform;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.hub.RuntimeClassLoading;
import com.oracle.svm.core.jdk.JNIPlatformNativeLibrarySupport;
import com.oracle.svm.core.jdk.NativeLibrarySupport;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.c.NativeLibraries;
import com.oracle.svm.hosted.jdk.JNIRegistrationSupport;
import com.oracle.svm.shared.option.LocatableMultiOptionValue.ValueWithOrigin;
import com.oracle.svm.shared.option.SubstrateOptionsParser;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.PartiallyLayerAware;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.VMError;

/**
 * Records the selected linkage and analysis reachability of JNI libraries and turns that state
 * into static-linker inputs and shim requirements after analysis.
 */
@Platforms(InternalPlatform.PLATFORM_JNI.class)
@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class, other = PartiallyLayerAware.class)
public final class JNILibraryLinkagePlan {

    private final NativeLibraries nativeLibraries;
    private final Map<String, JNILibrary> libraries = new ConcurrentHashMap<>();
    private final Set<String> explicitlyConfiguredLibraries;
    private final SortedMap<String, SortedSet<String>> shimExports = new TreeMap<>();
    private boolean manifestsProcessed;

    JNILibraryLinkagePlan() {
        this.nativeLibraries = NativeLibraries.singleton();
        JNILibraryLinkageConfiguration configuration = getJNILibraryLinkage();
        explicitlyConfiguredLibraries = configuration.explicitlyConfiguredLibraries();
        configuration.linkages().forEach((library, linkage) -> libraries.put(library, createLibrary(library, linkage)));
    }

    public static JNILibraryLinkagePlan singleton() {
        return ImageSingletons.lookup(JNILibraryLinkagePlan.class);
    }

    private record JNILibraryLinkageConfiguration(Map<String, JNILibraryLinkage> linkages, Set<String> explicitlyConfiguredLibraries) {
    }

    private static JNILibraryLinkageConfiguration getJNILibraryLinkage() {
        Map<String, ValueWithOrigin<String>> finalEntries = new HashMap<>();
        JNIRegistrationSupport.Options.JNILibraryLinkage.getValue().getValuesWithOrigins().forEach(entry -> {
            String value = entry.value();
            String[] parts = value.split(":", -1);
            if (parts.length != 2 || parts[0].isEmpty()) {
                throw UserError.abort("Invalid option %s provided by %s. Expected `<library>:{static|dynamic}`.",
                                SubstrateOptionsParser.commandArgument(JNIRegistrationSupport.Options.JNILibraryLinkage, value), entry.origin());
            }
            finalEntries.put(parts[0], entry);
        });

        Map<String, JNILibraryLinkage> result = new HashMap<>();
        JNIPlatformNativeLibrarySupport.defaultBuiltinLibraries.forEach(library -> result.put(library, JNILibraryLinkage.STATIC));
        Set<String> explicitlyConfiguredLibraries = new HashSet<>();
        finalEntries.forEach((library, entry) -> {
            String value = entry.value();
            String[] parts = value.split(":", -1);
            JNILibraryLinkage linkage = switch (parts[1]) {
                case "static" -> JNILibraryLinkage.STATIC;
                case "dynamic" -> JNILibraryLinkage.DYNAMIC;
                default -> throw UserError.abort("Invalid option %s provided by %s. Linkage must be either `static` or `dynamic`.",
                                SubstrateOptionsParser.commandArgument(JNIRegistrationSupport.Options.JNILibraryLinkage, value), entry.origin());
            };
            if (linkage == JNILibraryLinkage.DYNAMIC && SubstrateOptions.StaticExecutable.getValue()) {
                throw UserError.abort("Invalid option %s provided by %s. JNI libraries cannot use dynamic linkage when building a static executable.",
                                SubstrateOptionsParser.commandArgument(JNIRegistrationSupport.Options.JNILibraryLinkage, value), entry.origin());
            }
            if (linkage == JNILibraryLinkage.STATIC && mustRemainDynamic(library)) {
                throw UserError.abort("Invalid option %s provided by %s. JNI library `%s` must remain dynamically linked.",
                                SubstrateOptionsParser.commandArgument(JNIRegistrationSupport.Options.JNILibraryLinkage, value), entry.origin(), library);
            }

            result.put(library, linkage);
            explicitlyConfiguredLibraries.add(library);
        });
        return new JNILibraryLinkageConfiguration(result, explicitlyConfiguredLibraries);
    }

    private enum JNILibraryLinkage {
        STATIC,
        DYNAMIC
    }

    private JNILibrary createLibrary(String library, JNILibraryLinkage linkage) {
        JNILibrary result = switch (linkage) {
            case STATIC -> new StaticJNILibrary(library);
            case DYNAMIC -> new DynamicJNILibrary(library);
        };
        if (library.equals("java")) {
            JNIPlatformNativeLibrarySupport.singleton().setLibjavaDynamic(linkage == JNILibraryLinkage.DYNAMIC);
        }
        if (manifestsProcessed) {
            result.processManifests();
        }
        return result;
    }

    void prepareBeforeAnalysis() {
        /*
         * Java code can reference entry points implemented by Native Image's libjvm
         * replacement, which is linked but never loaded explicitly as a JNI library.
         */
        JNIPlatformNativeLibrarySupport.singleton().addBuiltinNatives(getBuiltinSymbols("jvm"));
        libraries.values().forEach(JNILibrary::processManifests);
        manifestsProcessed = true;
    }

    private static boolean mustRemainDynamic(String library) {
        return library.equals("awt");
    }

    public Set<String> getExplicitlyConfiguredLibraries() {
        return explicitlyConfiguredLibraries;
    }

    public void markReachable(String library) {
        JNILibrary jniLibrary = libraries.computeIfAbsent(library, name -> {
            assert !JNIPlatformNativeLibrarySupport.defaultBuiltinLibraries.contains(name);
            return createLibrary(name, JNILibraryLinkage.DYNAMIC);
        });
        jniLibrary.markReachable();
    }

    public List<String> getReachableStaticLibraryNames() {
        return libraries.values().stream()
                        .filter(JNILibrary::isReachable)
                        .filter(JNILibrary::isStatic)
                        .map(JNILibrary::getLibrary)
                        .toList();
    }

    public boolean isReachableDynamicLibrary(String library) {
        JNILibrary candidate = libraries.get(library);
        return candidate != null && candidate.isReachable() && !candidate.isStatic();
    }

    public boolean isReachableStaticLibrary(String library) {
        JNILibrary candidate = libraries.get(library);
        return candidate != null && candidate.isReachable() && candidate.isStatic();
    }

    /**
     * Freezes analysis reachability into linker inputs and shim requirements. This does not copy
     * dynamic libraries or create shims; those artifacts are emitted after the image is written.
     */
    void prepareAfterAnalysis() {
        if (!RuntimeClassLoading.isSupported()) {
            List<String> unreachableExplicitLibraries = libraries.values().stream()
                            .filter(library -> explicitlyConfiguredLibraries.contains(library.getLibrary()) && !library.isReachable())
                            .map(library -> library.getLibrary() + ":" + (library.isStatic() ? "static" : "dynamic"))
                            .sorted()
                            .toList();
            if (!unreachableExplicitLibraries.isEmpty()) {
                throw UserError.abort("JNI library linkage was explicitly configured for libraries that are not reachable: %s. " +
                                "When runtime class loading is disabled, explicitly configured JNI libraries must be reachable during image generation. " +
                                "A JNI library is reachable when analysis encounters a corresponding `System.loadLibrary(\"<library>\")` call.",
                                String.join(", ", unreachableExplicitLibraries));
            }
        }

        libraries.values().stream()
                        .filter(JNILibrary::isReachable)
                        .forEach(JNILibrary::prepareAfterAnalysis);

        if (JNIRegistrationSupport.Options.CreateJvmShim.getValue()) {
            requireShim("jvm");
        }
    }

    public boolean hasRequiredShim(String library) {
        return shimExports.containsKey(library) && !isReachableDynamicLibrary(library);
    }

    public List<String> getRequiredShimNames() {
        return shimExports.keySet().stream().filter(this::hasRequiredShim).toList();
    }

    public SortedSet<String> getShimExports(String library) {
        assert hasRequiredShim(library);
        return shimExports.get(library);
    }

    List<String> getBuiltinSymbols(String library) {
        return nativeLibraries.getStaticLibrarySymbols(library).stream().filter(symbol -> !isShimSymbol(symbol)).toList();
    }

    private List<String> getShimSymbols(String library) {
        return nativeLibraries.getStaticLibrarySymbols(library).stream().filter(symbol -> isShimSymbol(symbol) || isAdditionalWindowsShimSymbol(symbol)).toList();
    }

    private static boolean isShimSymbol(String symbol) {
        return !symbol.startsWith("JNI_OnLoad") && !symbol.startsWith("JNI_OnUnload") && !symbol.startsWith("Java_");
    }

    private static boolean isAdditionalWindowsShimSymbol(String symbol) {
        /* Windows nio.dll imports this JNI entry point from java.dll. */
        return Platform.includedIn(InternalPlatform.WINDOWS_BASE.class) && symbol.equals("Java_sun_security_provider_NativeSeedGenerator_nativeGenerateSeed");
    }

    /** Returns all image symbols that must be preserved for planned shim libraries. */
    public static Stream<String> getShimLibrarySymbols() {
        if (ImageSingletons.contains(JNILibraryLinkagePlan.class)) {
            JNILibraryLinkagePlan plan = singleton();
            return plan.getRequiredShimNames().stream().map(plan.shimExports::get).flatMap(Collection::stream).distinct();
        }
        return Stream.empty();
    }

    private void requireShim(String library) {
        List<String> symbols = getShimSymbols(library);
        VMError.guarantee(!symbols.isEmpty(), "Missing symbol manifest for shim library '%s'", library);
        addShimExports(library, symbols);
    }

    private void addShimExports(String library, Collection<String> exports) {
        shimExports.computeIfAbsent(library, _ -> new TreeSet<>()).addAll(exports);
    }

    private abstract class JNILibrary {
        private final String library;
        private List<String> dependencies;
        private boolean manifestsProcessed;
        private boolean reachable;

        JNILibrary(String library) {
            this.library = library;
        }

        String getLibrary() {
            return library;
        }

        List<String> getDependencies() {
            VMError.guarantee(dependencies != null);
            return dependencies;
        }

        void processManifests() {
            assert !manifestsProcessed;
            dependencies = Optional.ofNullable(nativeLibraries.getStaticLibraryDependencies(library)).orElseGet(List::of);
            registerNativeSymbols();
            manifestsProcessed = true;
        }

        abstract void registerNativeSymbols();

        abstract void prepareAfterAnalysis();

        abstract boolean isStatic();

        boolean isReachable() {
            return reachable;
        }

        void markReachable() {
            reachable = true;
        }
    }

    /** A JNI library that is linked statically into the image. */
    private final class StaticJNILibrary extends JNILibrary {

        StaticJNILibrary(String library) {
            super(library);
        }

        @Override
        void registerNativeSymbols() {
            JNIPlatformNativeLibrarySupport.singleton().addBuiltinNatives(getBuiltinSymbols(getLibrary()));
        }

        @Override
        void prepareAfterAnalysis() {
            VMError.guarantee(nativeLibraries.hasStaticLibrary(getLibrary()),
                            "Missing static library for built-in JNI library: %s", getLibrary());
            for (String dependency : nativeLibraries.getTransitiveStaticLibraryDependencies(getLibrary())) {
                JNILibrary dependencyLibrary = libraries.get(dependency);
                if (mustRemainDynamic(dependency) || (dependencyLibrary != null && !dependencyLibrary.isStatic())) {
                    throw UserError.abort("JNI library '%s' cannot be statically linked because it depends on dynamically linked JNI library '%s'. " +
                                    "Configure both libraries for static linkage or link '%s' dynamically.", getLibrary(), dependency, getLibrary());
                }
            }
            NativeLibrarySupport.singleton().registerUninitializedBuiltinLibrary(getLibrary());
            nativeLibraries.addStaticLibraryAndDependencies(getLibrary());
        }

        @Override
        boolean isStatic() {
            return true;
        }
    }

    /** A JNI library that is copied next to the image and loaded dynamically at run time. */
    private final class DynamicJNILibrary extends JNILibrary {

        DynamicJNILibrary(String library) {
            super(library);
        }

        @Override
        void registerNativeSymbols() {
        }

        @Override
        void prepareAfterAnalysis() {
            for (String dependency : getDependencies()) {
                if (dependency.equals("jvm")) {
                    requireShim(dependency);
                    continue;
                }

                JNILibrary dependencyLibrary = libraries.get(dependency);
                if (dependencyLibrary != null && dependencyLibrary.isStatic()) {
                    VMError.guarantee(dependency.equals("java"),
                                    "JNI library '%s' cannot be dynamically linked because it depends on statically linked JNI library '%s', for which no shim is available.",
                                    getLibrary(), dependency);
                    requireShim(dependency);
                }
            }
        }

        @Override
        boolean isStatic() {
            return false;
        }
    }
}
