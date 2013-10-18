/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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

public class Graal {

    private static GraalRuntime runtime;

    private static native GraalRuntime initializeRuntime();

    public static GraalRuntime getRuntime() {
        return runtime;
    }

    static {
        try {
            runtime = initializeRuntime();
        } catch (UnsatisfiedLinkError e) {
            runtime = new InvalidGraalRuntime();
        }
    }

    public static <T> T getRequiredCapability(Class<T> clazz) {
        T t = getRuntime().getCapability(clazz);
        if (t == null) {
            String javaHome = System.getProperty("java.home");
            String vmName = System.getProperty("java.vm.name");
            StringBuilder errorMessage = new StringBuilder();
            if (runtime.getClass() == InvalidGraalRuntime.class) {
                errorMessage.append(String.format("The VM does not support the Graal API.\n"));
            } else {
                errorMessage.append(String.format("The VM does not expose required Graal capability %s.\n", clazz.getName()));
            }
            errorMessage.append(String.format("Currently used Java home directory is %s.\n", javaHome));
            errorMessage.append(String.format("Currently used VM configuration is: %s", vmName));
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
