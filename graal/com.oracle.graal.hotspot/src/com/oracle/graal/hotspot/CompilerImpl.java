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

import java.io.*;
import java.lang.reflect.*;
import java.net.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.*;
import com.oracle.graal.compiler.target.*;
import com.oracle.graal.cri.*;
import com.oracle.graal.hotspot.bridge.*;
import com.oracle.graal.hotspot.logging.*;
import com.oracle.graal.hotspot.ri.*;
import com.oracle.graal.hotspot.server.*;
import com.oracle.max.asm.target.amd64.*;
import com.oracle.max.cri.ci.*;
import com.oracle.max.cri.xir.*;

/**
 * Singleton class holding the instance of the GraalCompiler.
 */
public final class CompilerImpl implements Compiler, Remote {

    private static Compiler theInstance;

    public static Compiler getInstance() {
        if (theInstance == null) {
            initialize();
        }
        return theInstance;
    }

    public static synchronized void initialize() {
        if (theInstance != null) {
            return;
        }

        String remote = System.getProperty("graal.remote");
        if (remote != null) {
            // remote compilation (will not create a local Compiler)
            try {
                System.out.println("Graal compiler started in client/server mode, server: " + remote);
                Socket socket = new Socket(remote, 1199);
                ReplacingStreams streams = new ReplacingStreams(socket.getOutputStream(), socket.getInputStream());
                streams.getInvocation().sendResult(new CompilerToVMImpl());

                theInstance = (Compiler) streams.getInvocation().waitForResult(false);
            } catch (IOException e1) {
                System.out.println("Connection to compilation server FAILED.");
                throw new RuntimeException(e1);
            } catch (ClassNotFoundException e2) {
                System.out.println("Connection to compilation server FAILED.");
                throw new RuntimeException(e2);
            }
        } else {
            // ordinary local compilation
            theInstance = new CompilerImpl(null);
        }
    }

    public static Compiler initializeServer(CompilerToVM entries) {
        assert theInstance == null;
        theInstance = new CompilerImpl(entries);
        return theInstance;
    }

    private final CompilerToVM compilerToVm;
    private final VMToCompiler vmToCompiler;

    private HotSpotRuntime runtime;
    private GraalCompiler compiler;
    private CiTarget target;
    private volatile HotSpotGraphCache cache;

    private final HotSpotVMConfig config;

    public HotSpotVMConfig getConfig() {
        return config;
    }

    private CompilerImpl(CompilerToVM initialEntries) {

        CompilerToVM toVM = initialEntries;
        // initialize CompilerToVM
        if (toVM == null) {
            toVM = new CompilerToVMImpl();
        }

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

        // initialize compiler
        config = compilerToVm.getConfiguration();
        config.check();

        if (Boolean.valueOf(System.getProperty("graal.printconfig"))) {
            printConfig(config);
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

    @Override
    public CiTarget getTarget() {
        if (target == null) {
            final int wordSize = 8;
            final int stackFrameAlignment = 16;
            target = new CiTarget(new AMD64(), true, stackFrameAlignment, config.vmPageSize, wordSize, true, true, true);
        }

        return target;
    }

    /**
     * Factory method for getting a {@link ExtendedRiRuntime} instance. This method is called via reflection.
     */
    public static ExtendedRiRuntime getGraalRuntime() {
        return getInstance().getRuntime();
    }

    /**
     * Factory method for getting a {@link GraalCompiler} instance. This method is called via reflection.
     */
    public static GraalCompiler getGraalCompiler() {
        return getInstance().getCompiler();
    }

    @Override
    public GraalCompiler getCompiler() {
        if (compiler == null) {
            // these options are important - graal will not generate correct code without them
            GraalOptions.StackShadowPages = config.stackShadowPages;

            RiXirGenerator generator = new HotSpotXirGenerator(config, getTarget(), getRuntime().getGlobalStubRegisterConfig(), this);
            if (Logger.ENABLED) {
                generator = LoggingProxy.getProxy(RiXirGenerator.class, generator);
            }

            Backend backend = Backend.create(runtime, target);
            generator.initialize(backend.newXirAssembler());

            compiler = new GraalCompiler(getRuntime(), getTarget(), backend, generator);
            if (GraalOptions.CacheGraphs) {
                cache = new HotSpotGraphCache();
            }
        }
        return compiler;
    }

    @Override
    public HotSpotGraphCache getCache() {
        return cache;
    }

    @Override
    public CompilerToVM getCompilerToVM() {
        return compilerToVm;
    }

    @Override
    public VMToCompiler getVMToCompiler() {
        return vmToCompiler;
    }

    @Override
    public RiType lookupType(String returnType, HotSpotTypeResolved accessingClass, boolean eagerResolve) {
        if (returnType.length() == 1 && vmToCompiler instanceof VMToCompilerImpl) {
            VMToCompilerImpl exitsNative = (VMToCompilerImpl) vmToCompiler;
            RiKind kind = RiKind.fromPrimitiveOrVoidTypeChar(returnType.charAt(0));
            switch(kind) {
                case Boolean:
                    return exitsNative.typeBoolean;
                case Byte:
                    return exitsNative.typeByte;
                case Char:
                    return exitsNative.typeChar;
                case Double:
                    return exitsNative.typeDouble;
                case Float:
                    return exitsNative.typeFloat;
                case Illegal:
                    break;
                case Int:
                    return exitsNative.typeInt;
                case Jsr:
                    break;
                case Long:
                    return exitsNative.typeLong;
                case Object:
                    break;
                case Short:
                    return exitsNative.typeShort;
                case Void:
                    return exitsNative.typeVoid;
            }
        }
        return compilerToVm.RiSignature_lookupType(returnType, accessingClass, eagerResolve);
    }

    @Override
    public HotSpotRuntime getRuntime() {
        if (runtime == null) {
            runtime = new HotSpotRuntime(config, this);
        }
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
}
