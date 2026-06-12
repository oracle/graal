/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.svm.util.GuestAccess;

import jdk.graal.compiler.serviceprovider.GraalServices;
import jdk.graal.compiler.vmaccess.VMAccess;

/**
 * Owns standalone {@link VMAccess} initialization and the process-global cache used to work around
 * GR-73534.
 *
 * The cache is only safe while every effective input to the selected
 * {@link jdk.graal.compiler.vmaccess.VMAccess.Builder VMAccess builder} stays unchanged. This
 * helper therefore captures the full builder configuration up front and validates later analyzers
 * against it before reusing the cached access.
 */
final class StandaloneVMAccessSupport {
    /**
     * Name of the {@code VMAccess.Builder} service used by default for standalone analysis.
     */
    private static final String ESPRESSO_VM_ACCESS_NAME = "espresso";
    private static final String DEFAULT_VM_ACCESS_NAME = ESPRESSO_VM_ACCESS_NAME;
    private static final String VM_ACCESS_PROPERTY_PREFIX = "com.oracle.graal.pointsto.standalone.vmaccess.";
    private static final String VM_ACCESS_MODULE_PATH_PROPERTY = VM_ACCESS_PROPERTY_PREFIX + "modulepath";
    private static final String VM_ACCESS_UPGRADE_MODULE_PATH_PROPERTY = VM_ACCESS_PROPERTY_PREFIX + "upgrade.modulepath";
    private static final String VM_ACCESS_JAVA_HOME_PROPERTY = VM_ACCESS_PROPERTY_PREFIX + "java.home";
    private static final String VM_ACCESS_NAME_PROPERTY = VM_ACCESS_PROPERTY_PREFIX + "name";
    private static final String ESPRESSO_LOG_LEVEL_PROPERTY = "espresso.test.log.level";
    private static final List<String> BASE_VM_ACCESS_MODULES = List.of("jdk.graal.compiler", "java.scripting");
    private static final List<String> FULLY_ISOLATED_EXTRA_MODULES = List.of("org.graalvm.word", "org.graalvm.nativeimage.guest.staging");
    private static CachedVMAccess cachedVMAccess;

    private StandaloneVMAccessSupport() {
    }

    /**
     * Returns the process-global {@link VMAccess} for the requested standalone target class path.
     *
     * Reusing the cached access is allowed only when the full effective builder configuration
     * matches the configuration of the first analyzer in this process.
     */
    static synchronized VMAccess getOrCreateVMAccess(String classpath) {
        ResolvedVMAccessBuilder resolvedBuilder = resolveVMAccessBuilder();
        StandaloneVMAccessConfiguration requestedConfiguration = createConfiguration(classpath, resolvedBuilder);
        if (cachedVMAccess == null) {
            VMAccess access = buildVMAccess(resolvedBuilder.builder(), requestedConfiguration);
            GuestAccess.plantConfiguration(access);
            cachedVMAccess = new CachedVMAccess(requestedConfiguration, access);
        } else {
            AnalysisError.guarantee(cachedVMAccess.configuration().equals(requestedConfiguration),
                            "Standalone analysis reuses a process-global VMAccess cache and cannot switch VMAccess configuration within the same process.%nCached: %s%nRequested: %s",
                            cachedVMAccess.configuration().format(),
                            requestedConfiguration.format());
        }
        return cachedVMAccess.access();
    }

    /**
     * Returns whether the currently selected standalone VMAccess backend relies on the Espresso
     * common-pool worker workaround.
     */
    static boolean requiresStandaloneCommonPoolWorkerFactory() {
        return ESPRESSO_VM_ACCESS_NAME.equals(getConfiguredVMAccessName());
    }

    /**
     * Captures every builder input that standalone analysis can vary across runs.
     */
    private static StandaloneVMAccessConfiguration createConfiguration(String classpath, ResolvedVMAccessBuilder resolvedBuilder) {
        List<String> classPathEntries = splitPathEntries(classpath);
        List<String> modulePathEntries = readOptionalPathProperty(VM_ACCESS_MODULE_PATH_PROPERTY);
        List<String> additionalModules = new ArrayList<>(BASE_VM_ACCESS_MODULES);
        Map<String, String> systemProperties = new LinkedHashMap<>();
        List<String> vmOptions = new ArrayList<>();

        List<String> upgradeModulePathEntries = readOptionalPathProperty(VM_ACCESS_UPGRADE_MODULE_PATH_PROPERTY);
        if (!upgradeModulePathEntries.isEmpty()) {
            systemProperties.put("jdk.module.upgrade.path", String.join(File.pathSeparator, upgradeModulePathEntries));
        }

        String logLevel = GraalServices.getSavedProperty(ESPRESSO_LOG_LEVEL_PROPERTY);
        if (logLevel != null) {
            vmOptions.add("--log.level=" + logLevel);
        }

        if (resolvedBuilder.fullyIsolated()) {
            /*
             * Make sure we use the modules prepared for GraalVM.
             */
            String javaHome = GraalServices.getSavedProperty(VM_ACCESS_JAVA_HOME_PROPERTY, GraalServices.getSavedProperty("java.home"));
            AnalysisError.guarantee(javaHome != null, "Missing required property java.home.");
            vmOptions.add("JavaHome=" + javaHome);
            additionalModules.addAll(FULLY_ISOLATED_EXTRA_MODULES);
        }

        return new StandaloneVMAccessConfiguration(
                        resolvedBuilder.vmAccessName(),
                        resolvedBuilder.builderClassName(),
                        resolvedBuilder.fullyIsolated(),
                        List.copyOf(classPathEntries),
                        List.copyOf(modulePathEntries),
                        List.copyOf(additionalModules),
                        true,
                        true,
                        Map.copyOf(systemProperties),
                        List.copyOf(vmOptions));
    }

    /**
     * Builds the configured standalone {@link VMAccess}.
     */
    private static VMAccess buildVMAccess(VMAccess.Builder builder, StandaloneVMAccessConfiguration configuration) {
        builder.classPath(configuration.classPath());
        if (!configuration.modulePath().isEmpty()) {
            builder.modulePath(configuration.modulePath());
        }
        builder.addModules(configuration.additionalModules());
        builder.enableAssertions(configuration.enableAssertions());
        builder.enableSystemAssertions(configuration.enableSystemAssertions());
        configuration.systemProperties().forEach(builder::systemProperty);
        configuration.vmOptions().forEach(builder::vmOption);
        return builder.build();
    }

    /**
     * Looks up the requested {@code VMAccess.Builder} service and defaults to the espresso-backed
     * builder when no explicit selection was saved.
     */
    private static ResolvedVMAccessBuilder resolveVMAccessBuilder() {
        String requestedAccessName = getConfiguredVMAccessName();
        ServiceLoader<VMAccess.Builder> loader = createVMAccessBuilderLoader();
        VMAccess.Builder selected = null;
        for (VMAccess.Builder builder : loader) {
            if (requestedAccessName.equals(builder.getVMAccessName())) {
                selected = builder;
                break;
            }
        }
        if (selected == null) {
            AnalysisError.shouldNotReachHere("No VMAccess.Builder service found with name " +
                            requestedAccessName + ". Found: " +
                            loader.stream().map(provider -> provider.get().getVMAccessName()).collect(Collectors.joining(", ")));
        }
        return new ResolvedVMAccessBuilder(requestedAccessName, selected.getClass().getName(), selected.isFullyIsolated(), selected);
    }

    /**
     * Mirrors {@link GuestAccess} service discovery so standalone finds VMAccess providers from the
     * VMAccess module layer as well as from the class path.
     */
    private static ServiceLoader<VMAccess.Builder> createVMAccessBuilderLoader() {
        Module vmAccessModule = VMAccess.Builder.class.getModule();
        ModuleLayer vmAccessLayer = vmAccessModule.getLayer();
        if (vmAccessLayer == null) {
            return ServiceLoader.load(VMAccess.Builder.class);
        }
        return ServiceLoader.load(vmAccessLayer, VMAccess.Builder.class);
    }

    private static String getConfiguredVMAccessName() {
        return GraalServices.getSavedProperty(VM_ACCESS_NAME_PROPERTY, DEFAULT_VM_ACCESS_NAME);
    }

    private static List<String> readOptionalPathProperty(String propertyName) {
        String propertyValue = GraalServices.getSavedProperty(propertyName);
        if (propertyValue == null) {
            return List.of();
        }
        return splitPathEntries(propertyValue);
    }

    private static List<String> splitPathEntries(String path) {
        return Arrays.asList(path.split(File.pathSeparator));
    }

    private record CachedVMAccess(StandaloneVMAccessConfiguration configuration, VMAccess access) {
    }

    private record ResolvedVMAccessBuilder(String vmAccessName, String builderClassName, boolean fullyIsolated, VMAccess.Builder builder) {
    }

    private record StandaloneVMAccessConfiguration(
                    String vmAccessName,
                    String builderClassName,
                    boolean fullyIsolated,
                    List<String> classPath,
                    List<String> modulePath,
                    List<String> additionalModules,
                    boolean enableAssertions,
                    boolean enableSystemAssertions,
                    Map<String, String> systemProperties,
                    List<String> vmOptions) {

        private String format() {
            return "StandaloneVMAccessConfiguration[" +
                            "vmAccessName=" + vmAccessName +
                            ", builderClassName=" + builderClassName +
                            ", fullyIsolated=" + fullyIsolated +
                            ", classPath=" + classPath +
                            ", modulePath=" + modulePath +
                            ", additionalModules=" + additionalModules +
                            ", enableAssertions=" + enableAssertions +
                            ", enableSystemAssertions=" + enableSystemAssertions +
                            ", systemProperties=" + systemProperties +
                            ", vmOptions=" + vmOptions +
                            ']';
        }
    }
}
