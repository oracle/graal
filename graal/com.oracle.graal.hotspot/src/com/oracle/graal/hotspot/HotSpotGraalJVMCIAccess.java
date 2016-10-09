/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.compiler.common.util.Util.JAVA_SPECIFICATION_VERSION;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.oracle.graal.debug.GraalError;
import com.oracle.graal.serviceprovider.ServiceProvider;

import jdk.vm.ci.runtime.JVMCICompilerFactory;
import jdk.vm.ci.services.JVMCIAccess;

@ServiceProvider(JVMCIAccess.class)
public final class HotSpotGraalJVMCIAccess extends JVMCIAccess {

    // Use reflection so that this compiles on Java 8
    private static final Method getModule;
    private static final Method addExports;
    static {
        if (JAVA_SPECIFICATION_VERSION >= 9) {
            try {
                Class<?> moduleClass = Class.forName("java.lang.reflect.Module");
                Class<?> modulesClass = Class.forName("jdk.internal.module.Modules");
                addExports = modulesClass.getDeclaredMethod("addExports", moduleClass, String.class, moduleClass);
                getModule = Class.class.getMethod("getModule");
            } catch (NoSuchMethodException | SecurityException | ClassNotFoundException e) {
                throw new GraalError(e);
            }
        } else {
            getModule = null;
            addExports = null;
        }
    }

    private boolean exportsAdded;

    /**
     * Dynamically exports various internal JDK packages to the Graal module. This requires only
     * {@code --add-exports=java.base/jdk.internal.module=com.oracle.graal.graal_core} on the VM
     * command line instead of a {@code --add-exports} instance for each JDK internal package used
     * by Graal.
     */
    private void addExports() {
        if (JAVA_SPECIFICATION_VERSION >= 9 && !exportsAdded) {
            try {
                Object javaBaseModule = getModule.invoke(String.class);
                Object graalModule = getModule.invoke(getClass());
                addExports.invoke(null, javaBaseModule, "jdk.internal.misc", graalModule);
                addExports.invoke(null, javaBaseModule, "jdk.internal.jimage", graalModule);
                addExports.invoke(null, javaBaseModule, "com.sun.crypto.provider", graalModule);
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                throw new GraalError(e);
            }
        }
    }

    @Override
    public <T> T getProvider(Class<T> service) {
        if (service == JVMCICompilerFactory.class) {
            addExports();
            return service.cast(new HotSpotGraalCompilerFactory());
        }
        return null;
    }
}
