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
package com.oracle.max.graal.hotspot;

import java.io.*;
import java.net.*;

import com.oracle.max.asm.target.amd64.*;
import com.oracle.max.cri.ci.*;
import com.oracle.max.cri.ri.*;
import com.oracle.max.cri.xir.*;
import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.observer.*;
import com.oracle.max.graal.cri.*;
import com.oracle.max.graal.hotspot.bridge.*;
import com.oracle.max.graal.hotspot.logging.*;
import com.oracle.max.graal.hotspot.ri.*;
import com.oracle.max.graal.hotspot.server.*;
import com.oracle.max.graal.printer.*;

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

    private final CompilerToVM vmEntries;
    private final VMToCompiler vmExits;

    private GraalContext context;
    private HotSpotRuntime runtime;
    private GraalCompiler compiler;
    private CiTarget target;

    private final HotSpotVMConfig config;

    public HotSpotVMConfig getConfig() {
        return config;
    }

    private CompilerImpl(CompilerToVM initialEntries) {

        CompilerToVM entries = initialEntries;
        // initialize VMEntries
        if (entries == null) {
            entries = new CompilerToVMImpl();
        }

        // initialize VMExits
        VMToCompiler exits = new VMToCompilerImpl(this);

        // logging, etc.
        if (CountingProxy.ENABLED) {
            exits = CountingProxy.getProxy(VMToCompiler.class, exits);
            entries = CountingProxy.getProxy(CompilerToVM.class, entries);
        }
        if (Logger.ENABLED) {
            exits = LoggingProxy.getProxy(VMToCompiler.class, exits);
            entries = LoggingProxy.getProxy(CompilerToVM.class, entries);
        }

        // set the final fields
        vmEntries = entries;
        vmExits = exits;

        // initialize compiler
        config = vmEntries.getConfiguration();
        config.check();
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
     * Factory method for getting a Graal compiler instance. This method is called via reflection.
     */
    public static GraalRuntime getGraalRuntime() {
        return getInstance().getRuntime();
    }

    @Override
    public GraalCompiler getCompiler() {
        if (compiler == null) {
            RiRegisterConfig registerConfig;

            // these options are important - graal will not generate correct code without them
            GraalOptions.StackShadowPages = config.stackShadowPages;

            registerConfig = getRuntime().globalStubRegConfig;
            RiXirGenerator generator = new HotSpotXirGenerator(config, getTarget(), registerConfig, this);
            if (Logger.ENABLED) {
                generator = LoggingProxy.getProxy(RiXirGenerator.class, generator);
            }
            compiler = new GraalCompiler(context, getRuntime(), getTarget(), generator, registerConfig);
        }
        return compiler;
    }

    @Override
    public CompilerToVM getVMEntries() {
        return vmEntries;
    }

    @Override
    public VMToCompiler getVMExits() {
        return vmExits;
    }

    @Override
    public RiType lookupType(String returnType, HotSpotTypeResolved accessingClass) {
        if (returnType.length() == 1 && vmExits instanceof VMToCompilerImpl) {
            VMToCompilerImpl exitsNative = (VMToCompilerImpl) vmExits;
            CiKind kind = CiKind.fromPrimitiveOrVoidTypeChar(returnType.charAt(0));
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
        return vmEntries.RiSignature_lookupType(returnType, accessingClass);
    }

    @Override
    public HotSpotRuntime getRuntime() {
        if (runtime == null) {
            context = new GraalContext("Virtual Machine Compiler");
            if (GraalOptions.PrintCFGToFile) {
                context.addCompilationObserver(new CFGPrinterObserver());
            }
            if (GraalOptions.PrintIdealGraphLevel != 0 || GraalOptions.Plot || GraalOptions.PlotOnError) {
                CompilationObserver observer;
                if (GraalOptions.PrintIdealGraphFile) {
                    observer = new IdealGraphPrinterObserver();
                } else {
                    observer = new IdealGraphPrinterObserver(GraalOptions.PrintIdealGraphAddress, GraalOptions.PrintIdealGraphPort);
                }
                context.addCompilationObserver(observer);
            }
            runtime = new HotSpotRuntime(context, config, this);
        }
        return runtime;
    }
}
