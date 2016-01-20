/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.hotspot;

import static com.oracle.graal.options.OptionValue.PROFILE_OPTIONVALUE_PROPERTY_NAME;
import static jdk.vm.ci.inittimer.InitTimer.timer;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.inittimer.InitTimer;
import jdk.vm.ci.runtime.JVMCICompilerFactory;
import jdk.vm.ci.runtime.JVMCIRuntime;
import jdk.vm.ci.services.Services;

import com.oracle.graal.options.GraalJarsOptionDescriptorsProvider;
import com.oracle.graal.options.Option;
import com.oracle.graal.options.OptionType;
import com.oracle.graal.options.OptionValue;
import com.oracle.graal.options.OptionsParser;
import com.oracle.graal.phases.tiers.CompilerConfiguration;

public abstract class HotSpotGraalCompilerFactory implements JVMCICompilerFactory {

    /**
     * The name of the system property specifying a file containing extra Graal option settings.
     */
    private static final String GRAAL_OPTIONS_FILE_PROPERTY_NAME = "graal.options.file";

    /**
     * The name of the system property specifying the Graal version.
     */
    private static final String GRAAL_VERSION_PROPERTY_NAME = "graal.version";

    /**
     * The prefix for system properties that correspond to {@link Option} annotated fields. A field
     * named {@code MyOption} will have its value set from a system property with the name
     * {@code GRAAL_OPTION_PROPERTY_PREFIX + "MyOption"}.
     */
    public static final String GRAAL_OPTION_PROPERTY_PREFIX = "graal.";

    /**
     * Gets the system property assignment that would set the current value for a given option.
     */
    public static String asSystemPropertySetting(OptionValue<?> value) {
        return GRAAL_OPTION_PROPERTY_PREFIX + value.getName() + "=" + value.getValue();
    }

    static {
        initializeOptions();
    }

    static class Options {

        // @formatter:off
        @Option(help = "In tiered mode compile the compiler itself using optimized first tier code.", type = OptionType.Expert)
        public static final OptionValue<Boolean> CompileGraalWithC1Only = new OptionValue<>(true);
        // @formatter:on

    }

    @SuppressWarnings("try")
    private static class Lazy {

        static {
            try (InitTimer t = timer("HotSpotBackendFactory.register")) {
                for (HotSpotBackendFactory backend : Services.load(HotSpotBackendFactory.class)) {
                    backend.register();
                }
            }
        }

        static void registerBackends() {
            // force run of static initializer
        }
    }

    /**
     * Parses the options in the file denoted by the {@code VM.getSavedProperty(String) saved}
     * system property named {@value HotSpotGraalCompilerFactory#GRAAL_OPTIONS_FILE_PROPERTY_NAME}
     * if the file exists followed by the options encoded in saved system properties whose names
     * start with {@code "graal.option."}. Key/value pairs are parsed from the file denoted by
     * {@code "graal.options.file"} with {@link Properties#load(java.io.Reader)}.
     */
    @SuppressWarnings("try")
    private static void initializeOptions() {
        try (InitTimer t = timer("InitializeOptions")) {
            boolean jdk8OrEarlier = System.getProperty("java.specification.version").compareTo("1.9") < 0;
            GraalJarsOptionDescriptorsProvider odp = jdk8OrEarlier ? GraalJarsOptionDescriptorsProvider.create() : null;

            String optionsFile = System.getProperty(GRAAL_OPTIONS_FILE_PROPERTY_NAME);

            if (optionsFile != null) {
                File graalOptions = new File(optionsFile);
                if (graalOptions.exists()) {
                    try (FileReader fr = new FileReader(graalOptions)) {
                        Properties props = new Properties();
                        props.load(fr);
                        Map<String, String> optionSettings = new HashMap<>();
                        for (Map.Entry<Object, Object> e : props.entrySet()) {
                            optionSettings.put((String) e.getKey(), (String) e.getValue());
                        }
                        try {
                            OptionsParser.parseOptions(optionSettings, null, odp, null);
                        } catch (Throwable e) {
                            throw new InternalError("Error parsing an option from " + graalOptions, e);
                        }
                    } catch (IOException e) {
                        throw new InternalError("Error reading " + graalOptions, e);
                    }
                }
            }

            Properties savedProps = getSavedProperties(jdk8OrEarlier);

            Map<String, String> optionSettings = new HashMap<>();
            for (Map.Entry<Object, Object> e : savedProps.entrySet()) {
                String name = (String) e.getKey();
                if (name.startsWith(GRAAL_OPTION_PROPERTY_PREFIX)) {
                    if (name.equals(GRAAL_OPTIONS_FILE_PROPERTY_NAME) || name.equals(GRAAL_VERSION_PROPERTY_NAME) || name.equals(PROFILE_OPTIONVALUE_PROPERTY_NAME)) {
                        // Ignore well known properties that do not denote an option
                    } else {
                        String value = (String) e.getValue();
                        optionSettings.put(name.substring(GRAAL_OPTION_PROPERTY_PREFIX.length()), value);
                    }
                }
            }

            OptionsParser.parseOptions(optionSettings, null, odp, null);
        }
    }

    private static Properties getSavedProperties(boolean jdk8OrEarlier) {
        try {
            String vmClassName = jdk8OrEarlier ? "sun.misc.VM" : "jdk.internal.misc.VM";
            Class<?> vmClass = Class.forName(vmClassName);
            Field savedPropsField = vmClass.getDeclaredField("savedProps");
            savedPropsField.setAccessible(true);
            return (Properties) savedPropsField.get(null);
        } catch (Exception e) {
            throw new JVMCIError(e);
        }
    }

    protected abstract HotSpotBackendFactory getBackendFactory(Architecture arch);

    protected abstract CompilerConfiguration createCompilerConfiguration();

    @SuppressWarnings("try")
    @Override
    public HotSpotGraalCompiler createCompiler(JVMCIRuntime runtime) {
        HotSpotJVMCIRuntime jvmciRuntime = (HotSpotJVMCIRuntime) runtime;
        try (InitTimer t = timer("HotSpotGraalRuntime.<init>")) {
            Lazy.registerBackends();
            HotSpotGraalRuntime graalRuntime = new HotSpotGraalRuntime(jvmciRuntime, this);
            HotSpotGraalVMEventListener.addRuntime(graalRuntime);
            return new HotSpotGraalCompiler(jvmciRuntime, graalRuntime);
        }
    }

    @Override
    public String[] getTrivialPrefixes() {
        if (Options.CompileGraalWithC1Only.getValue()) {
            return new String[]{"jdk/vm/ci", "com/oracle/graal"};
        }
        return null;
    }
}
