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
import static com.oracle.graal.hotspot.HotSpotGraalRuntime.Options.*;

import java.lang.reflect.*;
import java.util.*;

import sun.misc.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.runtime.*;
import com.oracle.graal.compiler.target.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.bridge.*;
import com.oracle.graal.hotspot.logging.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.options.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.runtime.*;

/**
 * Singleton class holding the instance of the {@link GraalRuntime}.
 */
public final class HotSpotGraalRuntime implements GraalRuntime, RuntimeProvider {

    private static final HotSpotGraalRuntime instance = new HotSpotGraalRuntime();
    static {
        instance.completeInitialization();
    }

    /**
     * Gets the singleton {@link HotSpotGraalRuntime} object.
     */
    public static HotSpotGraalRuntime runtime() {
        assert instance != null;
        return instance;
    }

    /**
     * Do deferred initialization.
     */
    public void completeInitialization() {

        // Proxies for the VM/Compiler interfaces cannot be initialized
        // in the constructor as proxy creation causes static
        // initializers to be executed for all the types involved in the
        // proxied methods. Some of these static initializers (e.g. in
        // HotSpotMethodData) rely on the static 'instance' field being set
        // to retrieve configuration details.
        VMToCompiler toCompiler = this.vmToCompiler;
        CompilerToVM toVM = this.compilerToVm;

        if (CountingProxy.ENABLED) {
            toCompiler = CountingProxy.getProxy(VMToCompiler.class, toCompiler);
            toVM = CountingProxy.getProxy(CompilerToVM.class, toVM);
        }
        if (Logger.ENABLED) {
            toCompiler = LoggingProxy.getProxy(VMToCompiler.class, toCompiler);
            toVM = LoggingProxy.getProxy(CompilerToVM.class, toVM);
        }

        this.vmToCompiler = toCompiler;
        this.compilerToVm = toVM;
    }

    // Options must not be directly declared in HotSpotGraalRuntime - see VerifyOptionsPhase
    static class Options {

        // @formatter:off
        @Option(help = "The runtime configuration to use")
        static final OptionValue<String> GraalRuntime = new OptionValue<>("");
        // @formatter:on
    }

    private static HotSpotBackendFactory findFactory(String architecture) {
        HotSpotBackendFactory basic = null;
        HotSpotBackendFactory selected = null;
        HotSpotBackendFactory nonBasic = null;
        int nonBasicCount = 0;

        for (HotSpotBackendFactory factory : ServiceLoader.loadInstalled(HotSpotBackendFactory.class)) {
            if (factory.getArchitecture().equalsIgnoreCase(architecture)) {
                if (factory.getGraalRuntimeName().equals(GraalRuntime.getValue())) {
                    assert selected == null || checkFactoryOverriding(selected, factory);
                    selected = factory;
                }
                if (factory.getGraalRuntimeName().equals("basic")) {
                    assert basic == null || checkFactoryOverriding(basic, factory);
                    basic = factory;
                } else {
                    nonBasic = factory;
                    nonBasicCount++;
                }
            }
        }

        if (selected != null) {
            return selected;
        } else {
            if (!GraalRuntime.getValue().equals("")) {
                // Fail fast if a non-default value for GraalRuntime was specified
                // and the corresponding factory is not available
                throw new GraalInternalError("Specified runtime \"%s\" not available for the %s architecture", GraalRuntime.getValue(), architecture);
            } else if (nonBasicCount == 1) {
                // If there is exactly one non-basic runtime, select this one.
                return nonBasic;
            } else {
                return basic;
            }
        }
    }

    /**
     * Checks that a factory overriding is valid. A factory B can only override/replace a factory A
     * if the B.getClass() is a subclass of A.getClass(). This models the assumption that B is
     * extends the behavior of A and has therefore understood the behavior expected of A.
     * 
     * @param baseFactory
     * @param overridingFactory
     */
    private static boolean checkFactoryOverriding(HotSpotBackendFactory baseFactory, HotSpotBackendFactory overridingFactory) {
        return baseFactory.getClass().isAssignableFrom(overridingFactory.getClass());
    }

    /**
     * Gets the kind of a word value on the {@linkplain #getHostBackend() host} backend.
     */
    public static Kind getHostWordKind() {
        return instance.getHostBackend().getTarget().wordKind;
    }

    /**
     * Reads a word value from a given address.
     */
    public static long unsafeReadWord(long address) {
        return unsafe.getAddress(address);
    }

    /**
     * Reads a klass pointer from a constant object.
     */
    public static long unsafeReadKlassPointer(Object object) {
        return instance.getCompilerToVM().readUnsafeKlassPointer(object);
    }

    /**
     * Reads a word value from a given object.
     */
    public static long unsafeReadWord(Object object, long offset) {
        if (getHostWordKind() == Kind.Long) {
            return unsafe.getLong(object, offset);
        }
        return unsafe.getInt(object, offset) & 0xFFFFFFFFL;
    }

    protected/* final */CompilerToVM compilerToVm;
    protected/* final */CompilerToGPU compilerToGpu;
    protected/* final */VMToCompiler vmToCompiler;

    private HotSpotRuntimeInterpreterInterface runtimeInterpreterInterface;
    private volatile HotSpotGraphCache cache;

    protected final HotSpotVMConfig config;
    private final HotSpotBackend hostBackend;

    private final Map<Class<? extends Architecture>, HotSpotBackend> backends = new HashMap<>();

    private HotSpotGraalRuntime() {
        CompilerToVM toVM = new CompilerToVMImpl();
        CompilerToGPU toGPU = new CompilerToGPUImpl();
        VMToCompiler toCompiler = new VMToCompilerImpl(this);

        compilerToVm = toVM;
        compilerToGpu = toGPU;
        vmToCompiler = toCompiler;
        config = new HotSpotVMConfig(compilerToVm);

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

        // Only set HotSpotPrintCompilation and HotSpotPrintInlining if they still have their
        // default value (false).
        if (GraalOptions.HotSpotPrintCompilation.getValue() == false) {
            GraalOptions.HotSpotPrintCompilation.setValue(config.printCompilation);
        }
        if (GraalOptions.HotSpotPrintInlining.getValue() == false) {
            GraalOptions.HotSpotPrintInlining.setValue(config.printInlining);
        }

        if (Boolean.valueOf(System.getProperty("graal.printconfig"))) {
            printConfig(config);
        }

        String hostArchitecture = getHostArchitectureName();
        hostBackend = registerBackend(findFactory(hostArchitecture).createBackend(this, null));

        String[] gpuArchitectures = getGPUArchitectureNames();
        for (String arch : gpuArchitectures) {
            HotSpotBackendFactory factory = findFactory(arch);
            if (factory == null) {
                throw new GraalInternalError("No backend available for specified GPU architecture \"%s\"", arch);
            }
            registerBackend(factory.createBackend(this, hostBackend));
        }

        GraalOptions.StackShadowPages.setValue(config.stackShadowPages);
        if (GraalOptions.CacheGraphs.getValue()) {
            cache = new HotSpotGraphCache(compilerToVm);
        }
    }

    private HotSpotBackend registerBackend(HotSpotBackend backend) {
        Class<? extends Architecture> arch = backend.getTarget().arch.getClass();
        HotSpotBackend oldValue = backends.put(arch, backend);
        assert oldValue == null : "cannot overwrite existing backend for architecture " + arch.getSimpleName();
        return backend;
    }

    /**
     * Gets the host architecture name for the purpose of finding the corresponding
     * {@linkplain HotSpotBackendFactory backend}.
     */
    private static String getHostArchitectureName() {
        String arch = System.getProperty("os.arch");
        switch (arch) {
            case "x86_64":
                // This is what Mac OS X reports;
                arch = "amd64";
                break;
        }
        return arch;
    }

    public static final String GRAAL_GPU_ISALIST_PROPERTY_NAME = "graal.gpu.isalist";

    /**
     * Gets the names of the supported GPU architectures for the purpose of finding the
     * corresponding {@linkplain HotSpotBackendFactory backend} objects. This method first looks for
     * a comma or {@link java.io.File#pathSeparatorChar} separated list of names in the
     * {@value #GRAAL_GPU_ISALIST_PROPERTY_NAME} system property. If this property is not set, then
     * the GPU native support code is queried.
     */
    private static String[] getGPUArchitectureNames() {
        String gpuList = System.getProperty(GRAAL_GPU_ISALIST_PROPERTY_NAME);
        if (gpuList != null && !gpuList.isEmpty()) {
            String[] gpus = gpuList.split("[,:]");
            return gpus;
        }
        return new String[0];
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

    public HotSpotVMConfig getConfig() {
        return config;
    }

    public TargetDescription getTarget() {
        return hostBackend.getTarget();
    }

    public HotSpotGraphCache getGraphCache() {
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
            runtimeInterpreterInterface = new HotSpotRuntimeInterpreterInterface(getHostProviders().getMetaAccess());
        }
        return runtimeInterpreterInterface;
    }

    public HotSpotProviders getHostProviders() {
        return getHostBackend().getProviders();
    }

    @Override
    public String getName() {
        return getClass().getSimpleName();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getCapability(Class<T> clazz) {
        if (clazz == RuntimeProvider.class) {
            return (T) this;
        }
        return null;
    }

    public HotSpotBackend getHostBackend() {
        return hostBackend;
    }

    public <T extends Architecture> Backend getBackend(Class<T> arch) {
        assert arch != Architecture.class;
        return backends.get(arch);
    }

    public Map<Class<? extends Architecture>, HotSpotBackend> getBackends() {
        return Collections.unmodifiableMap(backends);
    }

    /**
     * The offset from the origin of an array to the first element.
     * 
     * @return the offset in bytes
     */
    public static int getArrayBaseOffset(Kind kind) {
        switch (kind) {
            case Boolean:
                return Unsafe.ARRAY_BOOLEAN_BASE_OFFSET;
            case Byte:
                return Unsafe.ARRAY_BYTE_BASE_OFFSET;
            case Char:
                return Unsafe.ARRAY_CHAR_BASE_OFFSET;
            case Short:
                return Unsafe.ARRAY_SHORT_BASE_OFFSET;
            case Int:
                return Unsafe.ARRAY_INT_BASE_OFFSET;
            case Long:
                return Unsafe.ARRAY_LONG_BASE_OFFSET;
            case Float:
                return Unsafe.ARRAY_FLOAT_BASE_OFFSET;
            case Double:
                return Unsafe.ARRAY_DOUBLE_BASE_OFFSET;
            case Object:
                return Unsafe.ARRAY_OBJECT_BASE_OFFSET;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    /**
     * The scale used for the index when accessing elements of an array of this kind.
     * 
     * @return the scale in order to convert the index into a byte offset
     */
    public static int getArrayIndexScale(Kind kind) {
        switch (kind) {
            case Boolean:
                return Unsafe.ARRAY_BOOLEAN_INDEX_SCALE;
            case Byte:
                return Unsafe.ARRAY_BYTE_INDEX_SCALE;
            case Char:
                return Unsafe.ARRAY_CHAR_INDEX_SCALE;
            case Short:
                return Unsafe.ARRAY_SHORT_INDEX_SCALE;
            case Int:
                return Unsafe.ARRAY_INT_INDEX_SCALE;
            case Long:
                return Unsafe.ARRAY_LONG_INDEX_SCALE;
            case Float:
                return Unsafe.ARRAY_FLOAT_INDEX_SCALE;
            case Double:
                return Unsafe.ARRAY_DOUBLE_INDEX_SCALE;
            case Object:
                return Unsafe.ARRAY_OBJECT_INDEX_SCALE;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }
}
