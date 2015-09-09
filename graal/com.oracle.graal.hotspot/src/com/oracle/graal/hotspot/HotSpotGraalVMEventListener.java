/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.TreeMap;

import jdk.internal.jvmci.code.CompilationResult;
import jdk.internal.jvmci.code.InstalledCode;
import jdk.internal.jvmci.hotspot.HotSpotCodeCacheProvider;
import jdk.internal.jvmci.hotspot.HotSpotJVMCIRuntime;
import jdk.internal.jvmci.hotspot.HotSpotVMConfig;
import jdk.internal.jvmci.hotspot.HotSpotVMEventListener;
import jdk.internal.jvmci.service.ServiceProvider;

import com.oracle.graal.debug.Debug;
import com.oracle.graal.debug.TTY;

@ServiceProvider(HotSpotVMEventListener.class)
public class HotSpotGraalVMEventListener implements HotSpotVMEventListener {

    @Override
    public void notifyShutdown() {
        HotSpotGraalRuntime.runtime().shutdown();
    }

    @Override
    public void notifyInstall(HotSpotCodeCacheProvider codeCache, InstalledCode installedCode, CompilationResult compResult) {
        if (Debug.isDumpEnabled()) {
            Debug.dump(new Object[]{compResult, installedCode}, "After code installation");
        }
        if (Debug.isLogEnabled()) {
            Debug.log("%s", codeCache.disassemble(installedCode));
        }
    }

    @Override
    public void completeInitialization(HotSpotJVMCIRuntime runtime) {
        if (Boolean.valueOf(System.getProperty("jvmci.printconfig")) || Boolean.valueOf(System.getProperty("graal.printconfig"))) {
            printConfig(runtime.getConfig());
        }
    }

    private static void printConfig(HotSpotVMConfig config) {
        Field[] fields = config.getClass().getDeclaredFields();
        Map<String, Field> sortedFields = new TreeMap<>();
        for (Field f : fields) {
            if (!f.isSynthetic() && !Modifier.isStatic(f.getModifiers())) {
                f.setAccessible(true);
                sortedFields.put(f.getName(), f);
            }
        }
        for (Field f : sortedFields.values()) {
            try {
                TTY.print("%9s %-40s = %s%n", f.getType().getSimpleName(), f.getName(), pretty(f.get(config)));
            } catch (Exception e) {
            }
        }
    }

    private static String pretty(Object value) {
        if (value == null) {
            return "null";
        }

        Class<?> klass = value.getClass();
        if (value instanceof String) {
            return "\"" + value + "\"";
        } else if (value instanceof Method) {
            return "method \"" + ((Method) value).getName() + "\"";
        } else if (value instanceof Class<?>) {
            return "class \"" + ((Class<?>) value).getSimpleName() + "\"";
        } else if (value instanceof Integer) {
            if ((Integer) value < 10) {
                return value.toString();
            }
            return value + " (0x" + Integer.toHexString((Integer) value) + ")";
        } else if (value instanceof Long) {
            if ((Long) value < 10 && (Long) value > -10) {
                return value + "l";
            }
            return value + "l (0x" + Long.toHexString((Long) value) + "l)";
        } else if (klass.isArray()) {
            StringBuilder str = new StringBuilder();
            int dimensions = 0;
            while (klass.isArray()) {
                dimensions++;
                klass = klass.getComponentType();
            }
            int length = Array.getLength(value);
            str.append(klass.getSimpleName()).append('[').append(length).append(']');
            for (int i = 1; i < dimensions; i++) {
                str.append("[]");
            }
            str.append(" {");
            for (int i = 0; i < length; i++) {
                str.append(pretty(Array.get(value, i)));
                if (i < length - 1) {
                    str.append(", ");
                }
            }
            str.append('}');
            return str.toString();
        }
        return value.toString();
    }
}
