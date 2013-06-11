/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.graph.UnsafeAccess.*;

import java.lang.reflect.*;
import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.runtime.*;
import com.oracle.graal.compiler.target.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.bridge.*;
import com.oracle.graal.hotspot.logging.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.options.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.tiers.*;

//import static com.oracle.graal.phases.GraalOptions.*;

/**
 * Singleton class holding the instance of the {@link GraalRuntime}.
 * 
 * The platform specific subclass is created by a call from the C++ HotSpot code.
 */
public abstract class HotSpotGraalRuntime implements GraalRuntime {

    private static HotSpotGraalRuntime instance;

    /**
     * Gets the singleton {@link HotSpotGraalRuntime} object.
     */
    public static HotSpotGraalRuntime graalRuntime() {
        return instance;
    }

    /**
     * Called by the platform specific class exactly once to register the singleton instance.
     */
    protected static void setInstance(HotSpotGraalRuntime runtime) {
        assert instance == null : "runtime already registered";
        instance = runtime;

        // Do deferred initialization

        // Proxies for the VM/Compiler interfaces cannot be initialized
        // in the constructor as proxy creation causes static
        // initializers to be executed for all the types involved in the
        // proxied methods. Some of these static initializers (e.g. in
        // HotSpotMethodData) rely on the above instance field being set
        // to retrieve config details.
        VMToCompiler toCompiler = runtime.vmToCompiler;
        CompilerToVM toVM = runtime.compilerToVm;

        if (CountingProxy.ENABLED) {
            toCompiler = CountingProxy.getProxy(VMToCompiler.class, toCompiler);
            toVM = CountingProxy.getProxy(CompilerToVM.class, toVM);
        }
        if (Logger.ENABLED) {
            toCompiler = LoggingProxy.getProxy(VMToCompiler.class, toCompiler);
            toVM = LoggingProxy.getProxy(CompilerToVM.class, toVM);
        }

        runtime.vmToCompiler = toCompiler;
        runtime.compilerToVm = toVM;
    }

    private static final String DEFAULT_GRAAL_RUNTIME = "basic";

    // @formatter:off
    @Option(help = "The runtime configuration to use")
    private static final OptionValue<String> GraalRuntime = new OptionValue<>(DEFAULT_GRAAL_RUNTIME);
    // @formatter:on

    protected static HotSpotGraalRuntimeFactory findFactory(String architecture) {
        for (HotSpotGraalRuntimeFactory factory : ServiceLoader.loadInstalled(HotSpotGraalRuntimeFactory.class)) {
            if (factory.getArchitecture().equals(architecture) && factory.getName().equals(GraalRuntime.getValue())) {
                return factory;
            }
        }
        if (!DEFAULT_GRAAL_RUNTIME.equals(GraalRuntime.getValue())) {
            // Fail fast if a non-default value for GraalRuntime was specified
            // and the corresponding factory is not available
            throw new GraalInternalError("Specified runtime \"%s\" not available for the %s architecture", GraalRuntime.getValue(), architecture);
        }
        return null;
    }

    private static Kind wordKind;

    /**
     * Gets the kind of a word value.
     */
    public static Kind wordKind() {
        assert wordKind != null;
        return wordKind;
    }

    /**
     * Reads a word value from a given address.
     */
    public static long unsafeReadWord(long address) {
        if (wordKind == Kind.Long) {
            return unsafe.getLong(address);
        }
        return unsafe.getInt(address);
    }

    /**
     * Reads a word value from a given object.
     */
    public static long unsafeReadWord(Object object, long offset) {
        if (wordKind == Kind.Long) {
            return unsafe.getLong(object, offset);
        }
        return unsafe.getInt(object, offset);
    }

    protected/* final */CompilerToVM compilerToVm;
    protected/* final */CompilerToGPU compilerToGpu;
    protected/* final */VMToCompiler vmToCompiler;

    protected final HotSpotRuntime runtime;
    protected final TargetDescription target;
    protected final Replacements replacements;

    private HotSpotRuntimeInterpreterInterface runtimeInterpreterInterface;
    private volatile HotSpotGraphCache cache;

    protected final HotSpotVMConfig config;
    private final HotSpotBackend backend;

    protected HotSpotGraalRuntime() {
        CompilerToVM toVM = new CompilerToVMImpl();
        CompilerToGPU toGPU = new CompilerToGPUImpl();

        // initialize VmToCompiler
        VMToCompiler toCompiler = new VMToCompilerImpl(this);

        compilerToVm = toVM;
        compilerToGpu = toGPU;
        vmToCompiler = toCompiler;
        config = new HotSpotVMConfig();
        compilerToVm.initializeConfiguration(config);
        config.check();

        // Set some global options:
        if (config.compileTheWorld) {
            GraalOptions.CompileTheWorld.setValue(CompileTheWorld.SUN_BOOT_CLASS_PATH);
        }
        if (config.compileTheWorldStartAt != 1) {
            GraalOptions.CompileTheWorldStartAt.setValue(config.compileTheWorldStartAt);
        }
        if (config.compileTheWorldStopAt != Integer.MAX_VALUE) {
            GraalOptions.CompileTheWorldStopAt.setValue(config.compileTheWorldStopAt);
        }
        GraalOptions.HotSpotPrintCompilation.setValue(config.printCompilation);
        GraalOptions.HotSpotPrintInlining.setValue(config.printInlining);

        if (Boolean.valueOf(System.getProperty("graal.printconfig"))) {
            printConfig(config);
        }

        target = createTarget();
        assert wordKind == null || wordKind.equals(target.wordKind);
        wordKind = target.wordKind;

        runtime = createRuntime();

        // Replacements cannot have speculative optimizations since they have
        // to be valid for the entire run of the VM.
        Assumptions assumptions = new Assumptions(false);
        replacements = new HotSpotReplacementsImpl(runtime, assumptions, runtime.getGraalRuntime().getTarget());

        backend = createBackend();
        GraalOptions.StackShadowPages.setValue(config.stackShadowPages);
        if (GraalOptions.CacheGraphs.getValue()) {
            cache = new HotSpotGraphCache();
        }
    }

    private static void printConfig(HotSpotVMConfig config) {
        Field[] fields = config.getClass().getDeclaredFields();
        Map<String, Field> sortedFields = new TreeMap<>();
        for (Field f : fields) {
            f.setAccessible(true);
            sortedFields.put(f.getName(), f);
        }
        for (Field f : sortedFields.values()) {
            try {
                Logger.info(String.format("%9s %-40s = %s", f.getType().getSimpleName(), f.getName(), Logger.pretty(f.get(config))));
            } catch (Exception e) {
            }
        }
    }

    protected abstract TargetDescription createTarget();

    protected abstract HotSpotBackend createBackend();

    protected abstract HotSpotRuntime createRuntime();

    public HotSpotVMConfig getConfig() {
        return config;
    }

    public TargetDescription getTarget() {
        return target;
    }

    public HotSpotGraphCache getCache() {
        return cache;
    }

    public CompilerToVM getCompilerToVM() {
        return compilerToVm;
    }

    public VMToCompiler getVMToCompiler() {
        return vmToCompiler;
    }

    public CompilerToGPU getCompilerToGPU() {
        return compilerToGpu;
    }

    public JavaType lookupType(String name, HotSpotResolvedObjectType accessingClass, boolean eagerResolve) {
        if (name.length() == 1 && vmToCompiler instanceof VMToCompilerImpl) {
            VMToCompilerImpl impl = (VMToCompilerImpl) vmToCompiler;
            Kind kind = Kind.fromPrimitiveOrVoidTypeChar(name.charAt(0));
            switch (kind) {
                case Boolean:
                    return impl.typeBoolean;
                case Byte:
                    return impl.typeByte;
                case Char:
                    return impl.typeChar;
                case Double:
                    return impl.typeDouble;
                case Float:
                    return impl.typeFloat;
                case Illegal:
                    break;
                case Int:
                    return impl.typeInt;
                case Long:
                    return impl.typeLong;
                case Object:
                    break;
                case Short:
                    return impl.typeShort;
                case Void:
                    return impl.typeVoid;
            }
        }
        return compilerToVm.lookupType(name, accessingClass, eagerResolve);
    }

    public HotSpotRuntimeInterpreterInterface getRuntimeInterpreterInterface() {
        if (runtimeInterpreterInterface == null) {
            runtimeInterpreterInterface = new HotSpotRuntimeInterpreterInterface(getRuntime());
        }
        return runtimeInterpreterInterface;
    }

    public HotSpotRuntime getRuntime() {
        return runtime;
    }

    public Replacements getReplacements() {
        return replacements;
    }

    public void evictDeoptedGraphs() {
        if (cache != null) {
            long[] deoptedGraphs = getCompilerToVM().getDeoptedLeafGraphIds();
            if (deoptedGraphs != null) {
                if (deoptedGraphs.length == 0) {
                    cache.clear();
                } else {
                    cache.removeGraphs(deoptedGraphs);
                }
            }
        }
    }

    @Override
    public String getName() {
        return getClass().getSimpleName();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getCapability(Class<T> clazz) {
        if (clazz == GraalCodeCacheProvider.class || clazz == CodeCacheProvider.class || clazz == MetaAccessProvider.class) {
            return (T) getRuntime();
        }
        if (clazz == DisassemblerProvider.class || clazz == BytecodeDisassemblerProvider.class || clazz == SuitesProvider.class) {
            return (T) getRuntime();
        }
        if (clazz == HotSpotRuntime.class) {
            return (T) runtime;
        }
        if (clazz == Replacements.class) {
            return (T) replacements;
        }
        if (clazz == Backend.class) {
            return (T) getBackend();
        }
        return null;
    }

    public HotSpotBackend getBackend() {
        return backend;
    }
}
