/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.jvmci;

import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.INVOKEDYNAMIC;
import static com.oracle.truffle.espresso.jvmci.JVMCIUtils.LOGGER;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.classfile.bytecode.BytecodeStream;
import com.oracle.truffle.espresso.classfile.bytecode.Bytes;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

/**
 * For an EspressoResolvedInstanceType, holds the re-written bytecodes for methods containing
 * invokedynamic bytecodes. The re-written bytecode contains CPIs that can be mapped back to the
 * method and bci where it appears. This is needed in JVMCI APIs that pass the cpi but not the
 * method or bci of the call site. This can then be used to retrieve a {@code CallSiteLink}.
 */
public final class JVMCIIndyData {
    // TODO should be MethodVersion and ERJM should be attached to a MethodVersion
    private final Method[] methods;
    private final char[] bcis;
    private final Map<Method, byte[]> newCode;

    private JVMCIIndyData(Method[] methods, char[] bcis, Map<Method, byte[]> newCode) {
        assert methods.length == bcis.length;
        this.methods = methods;
        this.bcis = bcis;
        this.newCode = newCode;
    }

    public static int indyCpi(int index) {
        return index >>> 16;
    }

    private static int callSiteIndex(int index) {
        return (index & 0xffff) - 1;
    }

    private static int encodeCPI4(int cpi, int callSiteIndex) {
        assert (cpi & 0xffff) == cpi;
        assert ((callSiteIndex + 1) & 0xffff) == (callSiteIndex + 1);
        return (cpi << 16) | (callSiteIndex + 1);
    }

    public static boolean isIndyCPI(int cpi) {
        return indyCpi(cpi) != 0;
    }

    @TruffleBoundary
    public byte[] getCode(Method method) {
        return newCode.get(method);
    }

    public Location getLocation(int index) {
        assert isIndyCPI(index);
        int callSiteIndex = callSiteIndex(index);
        return new Location(methods[callSiteIndex], bcis[callSiteIndex]);
    }

    public static JVMCIIndyData maybeGetExisting(ObjectKlass klass, Meta meta) {
        return (JVMCIIndyData) meta.HIDDEN_JVMCIINDY.getHiddenObject(klass.mirror());
    }

    public static JVMCIIndyData getExisting(ObjectKlass klass, Meta meta) {
        JVMCIIndyData hiddenObject = maybeGetExisting(klass, meta);
        assert hiddenObject != null;
        return hiddenObject;
    }

    public static JVMCIIndyData getOrCreate(ObjectKlass klass, Meta meta) {
        StaticObject mirror = klass.mirror();
        JVMCIIndyData result = (JVMCIIndyData) meta.HIDDEN_JVMCIINDY.getHiddenObject(mirror, true);
        if (result == null) {
            result = create(klass);
            JVMCIIndyData old = (JVMCIIndyData) meta.HIDDEN_JVMCIINDY.compareAndExchangeHiddenObject(mirror, null, result);
            if (old != null) {
                return old;
            }
        }
        return result;
    }

    @TruffleBoundary
    private static JVMCIIndyData create(ObjectKlass klass) {
        List<Location> locations = null;
        Map<Method, byte[]> newCode = null;
        for (Method m : klass.getDeclaredMethods()) {
            if (m.getMethodVersion().usesIndy()) {
                if (locations == null) {
                    locations = new ArrayList<>();
                    newCode = new HashMap<>();
                }
                newCode.put(m, processMethod(m, locations));
            }
        }
        assert locations != null : klass;
        Method[] methods = new Method[locations.size()];
        char[] bcis = new char[locations.size()];
        int i = 0;
        for (Location l : locations) {
            methods[i] = l.method;
            bcis[i] = l.bci;
            i++;
        }
        return new JVMCIIndyData(methods, bcis, Map.copyOf(newCode));
    }

    private static byte[] processMethod(Method method, List<Location> locations) {
        byte[] originalCode = method.getOriginalCode();
        byte[] newCode = Arrays.copyOf(originalCode, originalCode.length);
        BytecodeStream bs = new BytecodeStream(newCode);
        int bci = 0;
        while (bci < bs.endBCI()) {
            int opcode = bs.currentBC(bci);
            if (opcode == INVOKEDYNAMIC) {
                int index = bs.readCPI4(bci);
                assert (index & 0xffff) == 0;
                int cpi = indyCpi(index);
                int callSiteIndex = locations.size();
                int finalBci = bci;
                LOGGER.finer(() -> "Setting up indy index at " + method + ":" + finalBci + " cpi=" + cpi + " callSiteIndex=" + callSiteIndex);
                bs.writeCPI4(bci, encodeCPI4(cpi, callSiteIndex));
                locations.add(new Location(method, (char) bci));
            }
            bci = bs.nextBCI(bci);
        }
        return newCode;
    }

    public int recoverFullCpi(int callSiteIndex) {
        Method method = methods[callSiteIndex];
        int bci = bcis[callSiteIndex];
        int cpi = Bytes.beU2(method.getOriginalCode(), bci + 1);
        return encodeCPI4(cpi, callSiteIndex);
    }

    public int getLocationCount() {
        return methods.length;
    }

    public record Location(Method method, char bci) {
    }
}
