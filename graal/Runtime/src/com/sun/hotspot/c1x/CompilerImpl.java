/*
 * Copyright (c) 2011 Sun Microsystems, Inc.  All rights reserved.
 *
 * Sun Microsystems, Inc. has intellectual property rights relating to technology embodied in the product
 * that is described in this document. In particular, and without limitation, these intellectual property
 * rights may include one or more of the U.S. patents listed at http://www.sun.com/patents and one or
 * more additional patents or pending patent applications in the U.S. and in other countries.
 *
 * U.S. Government Rights - Commercial software. Government users are subject to the Sun
 * Microsystems, Inc. standard license agreement and applicable provisions of the FAR and its
 * supplements.
 *
 * Use is subject to license terms. Sun, Sun Microsystems, the Sun logo, Java and Solaris are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other countries. All SPARC trademarks
 * are used under license and are trademarks or registered trademarks of SPARC International, Inc. in the
 * U.S. and other countries.
 *
 * UNIX is a registered trademark in the U.S. and other countries, exclusively licensed through X/Open
 * Company, Ltd.
 */
package com.sun.hotspot.c1x;

import java.io.*;
import java.lang.management.*;
import java.net.*;

import com.sun.c1x.*;
import com.sun.c1x.target.amd64.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;
import com.sun.cri.xir.*;
import com.sun.hotspot.c1x.logging.*;
import com.sun.hotspot.c1x.server.*;

/**
 * Singleton class holding the instance of the C1XCompiler.
 *
 * @author Thomas Wuerthinger, Lukas Stadler
 */
public final class CompilerImpl implements Compiler, Remote {

    private static Compiler theInstance;
    private static boolean PrintGCStats = false;

    public static Compiler getInstance() {
        return theInstance;
    }

    public static void initialize() {
        if (theInstance != null) {
            throw new IllegalStateException("Compiler already initialized");
        }

        String remote = System.getProperty("c1x.remote");
        if (remote != null) {
            // remote compilation (will not create a local Compiler)
            try {
                System.out.println("C1X compiler started in client/server mode, server: " + remote);
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
            Runtime.getRuntime().addShutdownHook(new ShutdownThread());
        }
    }

    public static Compiler initializeServer(VMEntries entries) {
        assert theInstance == null;
        theInstance = new CompilerImpl(entries);
        Runtime.getRuntime().addShutdownHook(new ShutdownThread());
        return theInstance;
    }

    public static class ShutdownThread extends Thread {

        @Override
        public void run() {
            VMExitsNative.compileMethods = false;
            if (C1XOptions.PrintMetrics) {
                C1XMetrics.print();
            }
            if (C1XOptions.PrintTimers) {
                C1XTimers.print();
            }
            if (PrintGCStats) {
                printGCStats();
            }
        }
    }

    public static void printGCStats() {
        long totalGarbageCollections = 0;
        long garbageCollectionTime = 0;

        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            long count = gc.getCollectionCount();
            if (count >= 0) {
                totalGarbageCollections += count;
            }

            long time = gc.getCollectionTime();
            if (time >= 0) {
                garbageCollectionTime += time;
            }
        }

        System.out.println("Total Garbage Collections: " + totalGarbageCollections);
        System.out.println("Total Garbage Collection Time (ms): " + garbageCollectionTime);
    }

    private final VMEntries vmEntries;
    private final VMExits vmExits;
    private C1XCompiler compiler;

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

        // initialize compiler and C1XOptions
        HotSpotVMConfig config = vmEntries.getConfiguration();
        config.check();

        // these options are important - c1x4hotspot will not generate correct code without them
        C1XOptions.GenSpecialDivChecks = true;
        C1XOptions.NullCheckUniquePc = true;
        C1XOptions.InvokeSnippetAfterArguments = true;
        C1XOptions.StackShadowPages = config.stackShadowPages;

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
    public C1XCompiler getCompiler() {
        if (compiler == null) {
            compiler = new C1XCompiler(runtime, target, generator, registerConfig);
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
