/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.runtime;

import java.io.*;
import java.net.*;

import com.oracle.max.asm.target.amd64.*;
import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.runtime.logging.*;
import com.oracle.max.graal.runtime.server.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;
import com.sun.cri.xir.*;

/**
 * Singleton class holding the instance of the GraalCompiler.
 */
public final class CompilerImpl implements Compiler, Remote {

    private static Compiler theInstance;

    public static Compiler getInstance() {
        return theInstance;
    }

    public static void initialize() {
        if (theInstance != null) {
            throw new IllegalStateException("Compiler already initialized");
        }

        String remote = System.getProperty("graal.remote");
        if (remote != null) {
            // remote compilation (will not create a local Compiler)
            try {
                System.out.println("Graal compiler started in client/server mode, server: " + remote);
                Socket socket = new Socket(remote, 1199);
                ReplacingStreams streams = new ReplacingStreams(socket.getOutputStream(), socket.getInputStream());
                streams.getInvocation().sendResult(new VMEntriesNative());

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

    public static Compiler initializeServer(VMEntries entries) {
        assert theInstance == null;
        theInstance = new CompilerImpl(entries);
        return theInstance;
    }

    private final VMEntries vmEntries;
    private final VMExits vmExits;
    private GraalCompiler compiler;

    private final HotSpotRuntime runtime;
    private final CiTarget target;
    private final RiXirGenerator generator;
    private final RiRegisterConfig registerConfig;

    private CompilerImpl(VMEntries entries) {

        // initialize VMEntries
        if (entries == null) {
            entries = new VMEntriesNative();
        }

        // initialize VMExits
        VMExits exits = new VMExitsNative(this);

        // logging, etc.
        if (CountingProxy.ENABLED) {
            exits = CountingProxy.getProxy(VMExits.class, exits);
            entries = CountingProxy.getProxy(VMEntries.class, entries);
        }
        if (Logger.ENABLED) {
            exits = LoggingProxy.getProxy(VMExits.class, exits);
            entries = LoggingProxy.getProxy(VMEntries.class, entries);
        }

        // set the final fields
        vmEntries = entries;
        vmExits = exits;

        // initialize compiler and GraalOptions
        HotSpotVMConfig config = vmEntries.getConfiguration();
        config.check();

        // these options are important - graal will not generate correct code without them
        GraalOptions.GenSpecialDivChecks = true;
        GraalOptions.NullCheckUniquePc = true;
        GraalOptions.InvokeSnippetAfterArguments = true;
        GraalOptions.StackShadowPages = config.stackShadowPages;

        runtime = new HotSpotRuntime(config, this);
        registerConfig = runtime.globalStubRegConfig;

        final int wordSize = 8;
        final int stackFrameAlignment = 16;
        target = new HotSpotTarget(new AMD64(), true, wordSize, stackFrameAlignment, config.vmPageSize, wordSize, true);

        RiXirGenerator generator = new HotSpotXirGenerator(config, target, registerConfig, this);
        if (Logger.ENABLED) {
            this.generator = LoggingProxy.getProxy(RiXirGenerator.class, generator);
        } else {
            this.generator = generator;
        }

    }

    @Override
    public GraalCompiler getCompiler() {
        if (compiler == null) {
            compiler = new GraalCompiler(runtime, target, generator, registerConfig);
        }
        return compiler;
    }

    @Override
    public VMEntries getVMEntries() {
        return vmEntries;
    }

    @Override
    public VMExits getVMExits() {
        return vmExits;
    }

}
