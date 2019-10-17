/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.graalvm.locator;

/**
 * JDK version independent interface to JDK services used by GraalVMLocator.
 */
final class JDKServices {

    private JDKServices() {
    }

    private static InternalError shouldNotReachHere() {
        throw new InternalError("JDK specific overlay for " + JDKServices.class.getName() + " missing");
    }

    /**
     * Returns the base class loader to use for the GraalVMLocator. On JDK 8 that is the JVMCI class
     * loader. In JDK 11 it is the platform class loader. On JDK 11 it needs to be the platform
     * class loader as otherwise classpath or module path languages/instruments maybe loaded.
     */
    static ClassLoader getLocatorBaseClassLoader(@SuppressWarnings("unused") Class<?> c) {
        throw shouldNotReachHere();
    }

}
