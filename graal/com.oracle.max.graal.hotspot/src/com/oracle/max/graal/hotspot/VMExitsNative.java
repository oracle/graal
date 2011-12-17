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

package com.oracle.max.graal.hotspot;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

import com.oracle.max.criutils.*;
import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.hotspot.server.*;
import com.sun.cri.ci.CiCompiler.DebugInfoLevel;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

/**
 * Exits from the HotSpot VM into Java code.
 */
public class VMExitsNative implements VMExits, Remote {

    private boolean installedIntrinsics;

    private final Compiler compiler;

    public final HotSpotTypePrimitive typeBoolean;
    public final HotSpotTypePrimitive typeChar;
    public final HotSpotTypePrimitive typeFloat;
    public final HotSpotTypePrimitive typeDouble;
    public final HotSpotTypePrimitive typeByte;
    public final HotSpotTypePrimitive typeShort;
    public final HotSpotTypePrimitive typeInt;
    public final HotSpotTypePrimitive typeLong;
    public final HotSpotTypePrimitive typeVoid;

    ThreadFactory daemonThreadFactory = new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new CompilerThread(r);
            t.setDaemon(true);
            return t;
        }
    };
    private static final class CompilerThread extends Thread {
        public CompilerThread(Runnable r) {
            super(r);
            this.setName("CompilerThread-" + this.getId());
        }
    }
    private ThreadPoolExecutor compileQueue;

    public VMExitsNative(Compiler compiler) {
        this.compiler = compiler;

        typeBoolean = new HotSpotTypePrimitive(compiler, CiKind.Boolean);
        typeChar = new HotSpotTypePrimitive(compiler, CiKind.Char);
        typeFloat = new HotSpotTypePrimitive(compiler, CiKind.Float);
        typeDouble = new HotSpotTypePrimitive(compiler, CiKind.Double);
        typeByte = new HotSpotTypePrimitive(compiler, CiKind.Byte);
        typeShort = new HotSpotTypePrimitive(compiler, CiKind.Short);
        typeInt = new HotSpotTypePrimitive(compiler, CiKind.Int);
        typeLong = new HotSpotTypePrimitive(compiler, CiKind.Long);
        typeVoid = new HotSpotTypePrimitive(compiler, CiKind.Void);
    }

    private static Set<String> compiledMethods = new HashSet<String>();

    public void startCompiler() throws Throwable {
        // Make sure TTY is initialized here such that the correct System.out is used for TTY.
        TTY.initialize();

        // Install intrinsics.
        HotSpotIntrinsic.installIntrinsics((HotSpotRuntime) compiler.getCompiler().runtime);

        // Create compilation queue.
        compileQueue = new ThreadPoolExecutor(GraalOptions.Threads, GraalOptions.Threads, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>(), daemonThreadFactory);

        // Create queue status printing thread.
        if (GraalOptions.PrintQueue) {
            Thread t = new Thread() {
                @Override
                public void run() {
                    while (true) {
                        TTY.println(compileQueue.toString());
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                        }
                    }
                }
            };
            t.setDaemon(true);
            t.start();
        }
    }

    public void bootstrap() throws Throwable {
        TTY.print("Bootstrapping Graal");
        TTY.flush();

        // Initialize compile queue with a selected set of methods.
        Class<Object> objectKlass = Object.class;
        enqueue(objectKlass.getDeclaredMethod("equals", Object.class));
        enqueue(objectKlass.getDeclaredMethod("toString"));

        // Compile until the queue is empty.
        long startTime = System.currentTimeMillis();
        int z = 0;
        while (compileQueue.getCompletedTaskCount() < compileQueue.getTaskCount()) {
            Thread.sleep(100);
            while (z < compileQueue.getCompletedTaskCount() / 100) {
                ++z;
                TTY.print(".");
                TTY.flush();
            }
        }

        TTY.println(" in %d ms", System.currentTimeMillis() - startTime);
        System.gc();
    }

    private void enqueue(Method m) throws Throwable {
        RiMethod riMethod = compiler.getRuntime().getRiMethod(m);
        assert !Modifier.isAbstract(((HotSpotMethodResolved) riMethod).accessFlags()) && !Modifier.isNative(((HotSpotMethodResolved) riMethod).accessFlags()) : riMethod;
        compileMethod((HotSpotMethodResolved) riMethod, 0, true);
    }

    public void shutdownCompiler() throws Throwable {
        compiler.getCompiler().context.print();
        compileQueue.shutdown();
    }

    @Override
    public void compileMethod(final HotSpotMethodResolved method, final int entryBCI, boolean blocking) throws Throwable {
        try {
            if (Thread.currentThread() instanceof CompilerThread && method.holder().name().contains("java/util/concurrent")) {
                return;
            }

            Runnable runnable = new Runnable() {
                public void run() {
                    try {
                        CiTargetMethod result = compiler.getCompiler().compileMethod(method, -1, null, DebugInfoLevel.FULL);
                        HotSpotTargetMethod.installMethod(compiler, method, result, true);
                    } catch (CiBailout bailout) {
                        if (GraalOptions.ExitVMOnBailout) {
                            bailout.printStackTrace(TTY.cachedOut);
                            System.exit(-1);
                        }
                    } catch (Throwable t) {
                        if (GraalOptions.ExitVMOnException) {
                            t.printStackTrace(TTY.cachedOut);
                            System.exit(-1);
                        }
                    }
                }
            };

            if (blocking) {
                runnable.run();
            } else {
                compileQueue.execute(runnable);
            }
        } catch (RejectedExecutionException e) {
            // The compile queue was already shut down.
            return;
        }
    }

    @Override
    public RiMethod createRiMethodUnresolved(String name, String signature, RiType holder) {
        return new HotSpotMethodUnresolved(compiler, name, signature, holder);
    }

    @Override
    public RiSignature createRiSignature(String signature) {
        return new HotSpotSignature(compiler, signature);
    }

    @Override
    public RiField createRiField(RiType holder, String name, RiType type, int offset, int flags) {
        if (offset != -1) {
            HotSpotTypeResolved resolved = (HotSpotTypeResolved) holder;
            return resolved.createRiField(name, type, offset, flags);
        }
        return new BaseUnresolvedField(holder, name, type);
    }

    @Override
    public RiType createRiType(HotSpotConstantPool pool, String name) {
        throw new RuntimeException("not implemented");
    }

    @Override
    public RiType createRiTypePrimitive(int basicType) {
        switch (basicType) {
            case 4:
                return typeBoolean;
            case 5:
                return typeChar;
            case 6:
                return typeFloat;
            case 7:
                return typeDouble;
            case 8:
                return typeByte;
            case 9:
                return typeShort;
            case 10:
                return typeInt;
            case 11:
                return typeLong;
            case 14:
                return typeVoid;
            default:
                throw new IllegalArgumentException("Unknown basic type: " + basicType);
        }
    }

    @Override
    public RiType createRiTypeUnresolved(String name) {
        return new HotSpotTypeUnresolved(compiler, name);
    }

    @Override
    public CiConstant createCiConstant(CiKind kind, long value) {
        if (kind == CiKind.Long) {
            return CiConstant.forLong(value);
        } else if (kind == CiKind.Int) {
            return CiConstant.forInt((int) value);
        } else if (kind == CiKind.Short) {
            return CiConstant.forShort((short) value);
        } else if (kind == CiKind.Char) {
            return CiConstant.forChar((char) value);
        } else if (kind == CiKind.Byte) {
            return CiConstant.forByte((byte) value);
        } else if (kind == CiKind.Boolean) {
            return (value == 0) ? CiConstant.FALSE : CiConstant.TRUE;
        } else {
            throw new IllegalArgumentException();
        }
    }

    @Override
    public CiConstant createCiConstantFloat(float value) {
        return CiConstant.forFloat(value);
    }

    @Override
    public CiConstant createCiConstantDouble(double value) {
        return CiConstant.forDouble(value);
    }

    @Override
    public CiConstant createCiConstantObject(Object object) {
        return CiConstant.forObject(object);
    }

    @Override
    public void pollJavaQueue() {
        ((HotSpotRuntime) compiler.getRuntime()).pollJavaQueue();
    }
}
