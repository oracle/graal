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
import java.lang.management.*;
import java.util.*;

import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.debug.*;
import com.oracle.max.graal.runtime.logging.*;
import com.oracle.max.graal.runtime.server.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

/**
 * Exits from the HotSpot VM into Java code.
 */
public class VMExitsNative implements VMExits, Remote {

    public static final boolean LogCompiledMethods = false;
    public static boolean compileMethods = true;
    private static boolean PrintGCStats = false;

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

    private static PrintStream originalOut;
    private static PrintStream originalErr;

    public void startCompiler() {
        originalOut = System.out;
        originalErr = System.err;
    }

    public void shutdownCompiler() throws Throwable {
        compileMethods = false;

        new Sandbox() {

            @Override
            public void run() {
                if (GraalOptions.Meter) {

                    GraalMetrics.print();
                }
                if (GraalOptions.Time) {
                    GraalTimers.print();
                }
                if (PrintGCStats) {
                    printGCStats();
                }
            }
        }.start();
    }

    public abstract class Sandbox {

        public void start() throws Throwable {
            // (ls) removed output and error stream rewiring, this influences applications and, for example, makes dacapo tests fail.
//            PrintStream oldOut = System.out;
//            PrintStream oldErr = System.err;
            run();
//            System.setOut(oldOut);
//            System.setErr(oldErr);
        }

        protected abstract void run() throws Throwable;
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

    @Override
    public void compileMethod(final HotSpotMethodResolved method, final int entryBCI) throws Throwable {
        if (!compileMethods) {
            return;
        }

        new Sandbox() {
            @Override
            public void run() throws Throwable {
                try {
                    CiResult result = compiler.getCompiler().compileMethod(method, -1, null, null);
                    if (LogCompiledMethods) {
                        String qualifiedName = CiUtil.toJavaName(method.holder()) + "::" + method.name();
                        compiledMethods.add(qualifiedName);
                    }

                    if (result.bailout() != null) {
                        Throwable cause = result.bailout().getCause();
                        if (!GraalOptions.QuietBailout) {
                            StringWriter out = new StringWriter();
                            result.bailout().printStackTrace(new PrintWriter(out));
                            TTY.println("Bailout:\n" + out.toString());
                            if (cause != null) {
                                Logger.info("Trace for cause: ");
                                for (StackTraceElement e : cause.getStackTrace()) {
                                    String current = e.getClassName() + "::" + e.getMethodName();
                                    String type = "";
                                    if (compiledMethods.contains(current)) {
                                        type = "compiled";
                                    }
                                    Logger.info(String.format("%-10s %3d %s", type, e.getLineNumber(), current));
                                }
                            }
                        }
                        String s = result.bailout().getMessage();
                        if (cause != null) {
                            s = cause.getMessage();
                        }
                        compiler.getVMEntries().recordBailout(s);
                    } else {
                        HotSpotTargetMethod.installMethod(compiler, method, result.targetMethod());
                    }
                } catch (Throwable t) {
                    StringWriter out = new StringWriter();
                    t.printStackTrace(new PrintWriter(out));
                    TTY.println("Compilation interrupted: (" + method.name() + ")\n" + out.toString());
                    throw t;
                }
            }
        }.start();
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
        return new HotSpotField(compiler, holder, name, type, offset, flags);
    }

    @Override
    public RiType createRiType(long vmId, String name) {
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
    public RiConstantPool createRiConstantPool(long vmId) {
        return new HotSpotConstantPool(compiler, vmId);
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
}
