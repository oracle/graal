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

import java.lang.reflect.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.interpreter.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.runtime.*;
import com.oracle.graal.compiler.*;
import com.oracle.graal.hotspot.bridge.*;
import com.oracle.graal.hotspot.logging.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.phases.*;

/**
 * Singleton class holding the instance of the {@link GraalRuntime}.
 *
 * The platform specific subclass is created by a call from
 * the C++ HotSpot code.
 */
public abstract class HotSpotGraalRuntime implements GraalRuntime {

    private static HotSpotGraalRuntime instance;

    /**
     * Gets the singleton runtime instance object.
     */
    public static HotSpotGraalRuntime getInstance() {
        return instance;
    }

    /**
     * Called by the platform specific class exactly once to register the singleton instance.
     */
    protected static void setInstance(HotSpotGraalRuntime runtime) {
        assert instance == null : "runtime already registered";
        instance = runtime;
    }

    private static Kind wordKind;

    /**
     * Gets the kind of a word value.
     */
    public static Kind wordKind() {
        assert wordKind != null;
        return wordKind;
    }

    protected final CompilerToVM compilerToVm;
    protected final VMToCompiler vmToCompiler;

    protected final HotSpotRuntime runtime;
    protected final GraalCompiler compiler;
    protected final TargetDescription target;

    private HotSpotRuntimeInterpreterInterface runtimeInterpreterInterface;
    private volatile HotSpotGraphCache cache;

    protected final HotSpotVMConfig config;

    protected HotSpotGraalRuntime() {
        CompilerToVM toVM = new CompilerToVMImpl();

        // initialize VmToCompiler
        VMToCompiler toCompiler = new VMToCompilerImpl(this);

        // logging, etc.
        if (CountingProxy.ENABLED) {
            toCompiler = CountingProxy.getProxy(VMToCompiler.class, toCompiler);
            toVM = CountingProxy.getProxy(CompilerToVM.class, toVM);
        }
        if (Logger.ENABLED) {
            toCompiler = LoggingProxy.getProxy(VMToCompiler.class, toCompiler);
            toVM = LoggingProxy.getProxy(CompilerToVM.class, toVM);
        }

        // set the final fields
        compilerToVm = toVM;
        vmToCompiler = toCompiler;
        config = new HotSpotVMConfig();
        compilerToVm.initializeConfiguration(config);
        config.check();

        if (Boolean.valueOf(System.getProperty("graal.printconfig"))) {
            printConfig(config);
        }

        target = createTarget();
        assert wordKind == null || wordKind.equals(target.wordKind);
        wordKind = target.wordKind;

        runtime = createRuntime();

        HotSpotBackend backend = createBackend();
        GraalOptions.StackShadowPages = config.stackShadowPages;
        compiler = new GraalCompiler(getRuntime(), getTarget(), backend);
        if (GraalOptions.CacheGraphs) {
            cache = new HotSpotGraphCache();
        }
    }

    private static void printConfig(HotSpotVMConfig config) {
        Field[] fields = config.getClass().getDeclaredFields();
        for (Field f : fields) {
            f.setAccessible(true);
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

    public GraalCompiler getCompiler() {
        return compiler;
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

    public JavaType lookupType(String name, HotSpotResolvedJavaType accessingClass, boolean eagerResolve) {
        if (name.length() == 1 && vmToCompiler instanceof VMToCompilerImpl) {
            VMToCompilerImpl impl = (VMToCompilerImpl) vmToCompiler;
            Kind kind = Kind.fromPrimitiveOrVoidTypeChar(name.charAt(0));
            switch(kind) {
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
                case Jsr:
                    break;
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
        if (clazz == GraalCodeCacheProvider.class || clazz == MetaAccessProvider.class) {
            return (T) getRuntime();
        }
        if (clazz == GraalCompiler.class) {
            return (T) getCompiler();
        }
        if (clazz == MetaAccessProvider.class) {
            return (T) getRuntime();
        }
        if (clazz == RuntimeInterpreterInterface.class) {
            return (T) getRuntimeInterpreterInterface();
        }
        return null;
    }
}
