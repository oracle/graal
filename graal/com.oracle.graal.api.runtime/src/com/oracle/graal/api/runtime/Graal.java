/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.api.runtime;

import java.util.*;

import sun.reflect.*;

import com.oracle.jvmci.service.*;

/**
 * Access point for {@linkplain #getRuntime() retrieving} the single {@link GraalRuntime} instance.
 */
public class Graal {

    private static final GraalRuntime runtime = initializeRuntime();

    private static GraalRuntime initializeRuntime() {
        GraalRuntimeAccess access = Services.loadSingle(GraalRuntimeAccess.class, false);
        if (access != null) {
            GraalRuntime rt = access.getRuntime();
            // The constant is patched in-situ by the build system
            System.setProperty("graal.version", "@@@@@@@@@@@@@@@@graal.version@@@@@@@@@@@@@@@@".trim());
            assert !System.getProperty("graal.version").startsWith("@@@@@@@@@@@@@@@@") && !System.getProperty("graal.version").endsWith("@@@@@@@@@@@@@@@@") : "Graal version string constant was not patched by build system";
            return rt;
        }
        return new InvalidGraalRuntime();
    }

    /**
     * Gets the singleton {@link GraalRuntime} instance available to the application.
     */
    public static GraalRuntime getRuntime() {
        return runtime;
    }

    /**
     * Gets a capability provided by the {@link GraalRuntime} instance available to the application.
     *
     * @throws UnsupportedOperationException if the capability is not available
     */
    @CallerSensitive
    public static <T> T getRequiredCapability(Class<T> clazz) {
        T t = getRuntime().getCapability(clazz);
        if (t == null) {
            String javaHome = System.getProperty("java.home");
            String vmName = System.getProperty("java.vm.name");
            Formatter errorMessage = new Formatter();
            if (runtime.getClass() == InvalidGraalRuntime.class) {
                errorMessage.format("The VM does not support the Graal API.%n");
            } else {
                errorMessage.format("The VM does not expose required Graal capability %s.%n", clazz.getName());
            }
            errorMessage.format("Currently used Java home directory is %s.%n", javaHome);
            errorMessage.format("Currently used VM configuration is: %s", vmName);
            throw new UnsupportedOperationException(errorMessage.toString());
        }
        return t;
    }

    private static final class InvalidGraalRuntime implements GraalRuntime {

        @Override
        public String getName() {
            return "";
        }

        @Override
        public <T> T getCapability(Class<T> clazz) {
            return null;
        }
    }
}
