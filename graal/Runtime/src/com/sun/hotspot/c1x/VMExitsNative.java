/*
 * Copyright (c) 2010 Sun Microsystems, Inc.  All rights reserved.
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
import java.util.*;

import com.sun.c1x.debug.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;
import com.sun.hotspot.c1x.logging.*;
import com.sun.hotspot.c1x.server.*;

/**
 * Exits from the HotSpot VM into Java code.
 *
 * @author Thomas Wuerthinger, Lukas Stadler
 */
public class VMExitsNative implements VMExits, Remote {

    public static final boolean LogCompiledMethods = false;
    public static boolean compileMethods = true;

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

    @Override
    public void compileMethod(long methodVmId, String name, int entryBCI) throws Throwable {
        if (!compileMethods) {
            return;
        }

        try {
            HotSpotMethodResolved riMethod = new HotSpotMethodResolved(compiler, methodVmId, name);
            CiResult result = compiler.getCompiler().compileMethod(riMethod, -1, null, null);
            if (LogCompiledMethods) {
                String qualifiedName = CiUtil.toJavaName(riMethod.holder()) + "::" + riMethod.name();
                compiledMethods.add(qualifiedName);
            }

            if (result.bailout() != null) {
                StringWriter out = new StringWriter();
                result.bailout().printStackTrace(new PrintWriter(out));
                Throwable cause = result.bailout().getCause();
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
                String s = result.bailout().getMessage();
                if (cause != null) {
                    s = cause.getMessage();
                }
                compiler.getVMEntries().recordBailout(s);
            } else {
                HotSpotTargetMethod.installMethod(compiler, riMethod, result.targetMethod());
            }
        } catch (Throwable t) {
            StringWriter out = new StringWriter();
            t.printStackTrace(new PrintWriter(out));
            TTY.println("Compilation interrupted:\n" + out.toString());
            throw t;
        }
    }

    @Override
    public RiMethod createRiMethodResolved(long vmId, String name) {
        return new HotSpotMethodResolved(compiler, vmId, name);
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
