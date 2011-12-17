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
package com.oracle.max.graal.compiler.tests;

import java.lang.reflect.*;
import java.util.*;

import com.oracle.max.graal.cri.*;

/**
 * Utility for getting a {@link GraalRuntime} instance from the current execution environment.
 */
class GraalRuntimeAccess {

    /**
     * The known classes declaring a {@code getGraalRuntime()} method. These class names
     * have aliases that can be used with the {@code "graal.runtime"} system property to
     * specify the VM environment to try first when getting a Graal runtime instance.
     */
    private static Map<String, String> graalRuntimeFactoryClasses = new LinkedHashMap<String, String>();
    static {
        graalRuntimeFactoryClasses.put("HotSpot", "com.oracle.max.graal.hotspot.CompilerImpl");
        graalRuntimeFactoryClasses.put("Maxine", "com.oracle.max.vm.ext.maxri.MaxRuntime");
    }

    /**
     * Gets a Graal runtime instance from the current execution environment.
     */
    static GraalRuntime getGraalRuntime() {
        String vm = System.getProperty("graal.runtime");
        if (vm != null) {
            String cn = graalRuntimeFactoryClasses.get(vm);
            if (cn != null) {
                GraalRuntime graal = getGraalRuntime(cn);
                if (graal != null) {
                    return graal;
                }
            }
        }

        for (String className : graalRuntimeFactoryClasses.values()) {
            GraalRuntime graal = getGraalRuntime(className);
            if (graal != null) {
                return graal;
            }
        }
        throw new InternalError("Could not create a GraalRuntime instance");
    }

    /**
     * Calls {@code getGraalRuntime()} via reflection on a given class.
     *
     * @return {@code null} if there was an error invoking the methodor if the method return {@code null} itself
     */
    private static GraalRuntime getGraalRuntime(String className) {
        try {
            Class<?> c = Class.forName(className);
            Method m = c.getDeclaredMethod("getGraalRuntime");
            return (GraalRuntime) m.invoke(null);
        } catch (Exception e) {
            //e.printStackTrace();
            System.err.println(e);
            return null;
        }
    }
}
