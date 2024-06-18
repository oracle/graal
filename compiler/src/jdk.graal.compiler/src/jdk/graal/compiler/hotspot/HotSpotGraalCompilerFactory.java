/*
 * Copyright (c) 2015, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot;

import static jdk.vm.ci.common.InitTimer.timer;
import static jdk.vm.ci.services.Services.IS_BUILDING_NATIVE_IMAGE;
import static jdk.vm.ci.services.Services.IS_IN_NATIVE_IMAGE;

import java.io.PrintStream;

import jdk.graal.compiler.api.runtime.GraalRuntime;
import jdk.graal.compiler.debug.MethodFilter;
import jdk.graal.compiler.debug.TTY;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.tiers.CompilerConfiguration;
import jdk.vm.ci.common.InitTimer;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.hotspot.HotSpotVMConfigAccess;
import jdk.vm.ci.meta.Signature;
import jdk.vm.ci.runtime.JVMCICompilerFactory;
import jdk.vm.ci.runtime.JVMCIRuntime;
import jdk.vm.ci.services.Services;

public final class HotSpotGraalCompilerFactory implements JVMCICompilerFactory {

    private static MethodFilter graalCompileOnlyFilter;
    private static boolean compileGraalWithC1Only;

    private IsGraalPredicate isGraalPredicate;

    private final HotSpotGraalJVMCIServiceLocator locator;

    HotSpotGraalCompilerFactory(HotSpotGraalJVMCIServiceLocator locator) {
        this.locator = locator;
    }

    @Override
    public String getCompilerName() {
        return "graal";
    }

    /**
     * Initialized when this factory is {@linkplain #onSelection() selected}.
     */
    private OptionValues options;

    /**
     * An exception thrown when {@linkplain HotSpotGraalOptionValues#defaultOptions() option
     * parsing} fails. This will result in a call to {@link HotSpotGraalServices#exit}. Ideally,
     * that would be done here but {@link JVMCICompilerFactory#onSelection()} does not pass in a
     * {@link HotSpotJVMCIRuntime}.
     */
    private IllegalArgumentException optionsFailure;

    private volatile boolean initialized;

    private void ensureInitialized() {
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    initialize();
                    initialized = true;
                }
            }
        }
    }

    @Override
    public void onSelection() {
        if (Services.IS_IN_NATIVE_IMAGE) {
            // When instantiating a JVMCI runtime in the libgraal heap there's no
            // point in delaying HotSpotGraalRuntime initialization as it
            // is very fast (it's compiled and does no class loading) and will
            // usually be done immediately after this call anyway (i.e. in a
            // Graal-as-JIT configuration).
            initialize();
            initialized = true;
        } else {
            // When instantiating a JVMCI runtime in the HotSpot heap, initialization
            // of a HotSpotGraalRuntime is deferred until a compiler instance is
            // requested. This avoids extra class loading when the JMX code in libgraal
            // (i.e. MBeanProxy) calls back into HotSpot. The HotSpot side of this call
            // only needs a few Graal classes (namely LibGraalScope and kin).
        }
    }

    private void initialize() {
        JVMCIVersionCheck.check(Services.getSavedProperties(), false, null);
        assert options == null : "cannot select " + getClass() + " service more than once";
        try {
            options = HotSpotGraalOptionValues.defaultOptions();
        } catch (IllegalArgumentException e) {
            optionsFailure = e;
            return;
        }
        initializeGraalCompilePolicyFields(options);
        isGraalPredicate = compileGraalWithC1Only ? new IsGraalPredicate() : null;
        if (IS_BUILDING_NATIVE_IMAGE) {
            // Triggers initialization of all option descriptors
            Options.CompileGraalWithC1Only.getName();
        }
    }

    private static void initializeGraalCompilePolicyFields(OptionValues options) {
        compileGraalWithC1Only = Options.CompileGraalWithC1Only.getValue(options) && !IS_IN_NATIVE_IMAGE;
        String optionValue = Options.GraalCompileOnly.getValue(options);
        if (optionValue != null) {
            MethodFilter filter = MethodFilter.parse(optionValue);
            if (filter.matchesNothing()) {
                filter = null;
            }
            graalCompileOnlyFilter = filter;
        }
    }

    @Override
    public void printProperties(PrintStream out) {
        ensureInitialized();
        out.println("[Graal properties]");
        if (optionsFailure != null) {
            System.err.printf("Error parsing Graal options: %s%n", optionsFailure.getMessage());
            return;
        }
        HotSpotGraalOptionValues.printProperties(options, out);
    }

    static class Options {

        // @formatter:off
        @Option(help = "If in tiered mode, compiles Graal and JVMCI using optimized first-tier code.", type = OptionType.Expert)
        public static final OptionKey<Boolean> CompileGraalWithC1Only = new OptionKey<>(true);

        @Option(help = "A filter applied to a method the VM has selected for compilation by Graal. " +
                       "A method not matching the filter is redirected to a lower tier compiler. " +
                       "The filter format is the same as for the MethodFilter option.", type = OptionType.Debug)
        public static final OptionKey<String> GraalCompileOnly = new OptionKey<>(null);
        @Option(help = "Make JVMCIPrintProperties show all Graal options, including debug and internal options.", type = OptionType.Debug)
        public static final OptionKey<Boolean> PrintPropertiesAll = new OptionKey<>(false);
        // @formatter:on

    }

    @Override
    public HotSpotGraalCompiler createCompiler(JVMCIRuntime runtime) {
        ensureInitialized();
        HotSpotJVMCIRuntime hsRuntime = (HotSpotJVMCIRuntime) runtime;
        if (optionsFailure != null) {
            System.err.printf("Error parsing Graal options: %s%nError: A fatal exception has occurred. Program will exit.%n", optionsFailure.getMessage());
            HotSpotGraalServices.exit(1, hsRuntime);
        }

        if (Options.PrintPropertiesAll.getValue(options)) {
            HotSpotVMConfigAccess config = new HotSpotVMConfigAccess(hsRuntime.getConfigStore());
            if (!config.getFlag("JVMCIPrintProperties", Boolean.class)) {
                TTY.printf("Warning: Ignoring %s since JVMCIPrintProperties is false%n", Options.PrintPropertiesAll.getName());
            }
        }

        CompilerConfigurationFactory factory = CompilerConfigurationFactory.selectFactory(null, options, hsRuntime);
        if (isGraalPredicate != null) {
            isGraalPredicate.onCompilerConfigurationFactorySelection(hsRuntime, factory);
        }
        HotSpotGraalCompiler compiler = createCompiler("VM", runtime, options, factory);
        // Only the HotSpotGraalRuntime associated with the compiler created via
        // jdk.vm.ci.runtime.JVMCIRuntime.getCompiler() is registered for receiving
        // VM events.
        locator.onCompilerCreation(compiler);
        return compiler;
    }

    /**
     * Creates a new {@link HotSpotGraalRuntime} object and a new {@link HotSpotGraalCompiler} and
     * returns the latter.
     *
     * @param runtimeNameQualifier a qualifier to be added to the {@linkplain GraalRuntime#getName()
     *            name} of the {@linkplain HotSpotGraalCompiler#getGraalRuntime() runtime} created
     *            by this method
     * @param runtime the JVMCI runtime on which the {@link HotSpotGraalRuntime} is built
     * @param compilerConfigurationFactory factory for the {@link CompilerConfiguration}
     */
    @SuppressWarnings("try")
    public static HotSpotGraalCompiler createCompiler(String runtimeNameQualifier, JVMCIRuntime runtime, OptionValues options, CompilerConfigurationFactory compilerConfigurationFactory) {
        HotSpotJVMCIRuntime jvmciRuntime = (HotSpotJVMCIRuntime) runtime;
        try (InitTimer t = timer("HotSpotGraalRuntime.<init>")) {
            HotSpotGraalRuntime graalRuntime = new HotSpotGraalRuntime(runtimeNameQualifier, jvmciRuntime, compilerConfigurationFactory, options);
            return new HotSpotGraalCompiler(jvmciRuntime, graalRuntime, graalRuntime.getOptions());
        }
    }

    static boolean shouldExclude(HotSpotResolvedJavaMethod method) {
        if (graalCompileOnlyFilter != null) {
            String javaClassName = method.getDeclaringClass().toJavaName();
            String name = method.getName();
            Signature signature = method.getSignature();
            if (graalCompileOnlyFilter.matches(javaClassName, name, signature)) {
                return false;
            }
            return true;
        }
        return false;
    }
}
