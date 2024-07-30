/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.serviceprovider;

/**
 * Interface between the compiler and its Native Image based runtime (i.e. libgraal).
 *
 * The methods of this class are substituted by the libgraal implementation.
 */
public final class VMSupport {
    /**
     * @see IsolateUtil#getIsolateAddress
     */
    public static long getIsolateAddress() {
        return 0L;
    }

    /**
     * @see IsolateUtil#getIsolateID
     */
    public static long getIsolateID() {
        return 0L;
    }

    /**
     * Gets a scope that performs setup/cleanup actions around a libgraal compilation.
     */
    public static AutoCloseable getCompilationRequestScope() {
        return null;
    }

    /**
     * Notifies that a fatal error has occurred.
     *
     * @param message description of the error
     * @param delayMS milliseconds to sleep before exiting the VM
     */
    public static void fatalError(String message, int delayMS) {

    }

    /**
     * Notifies libgraal when a Graal runtime is being started.
     */
    public static void startupLibGraal() {
    }

    /**
     * Notifies libgraal when a Graal runtime is being shutdown.
     */
    public static void shutdownLibGraal() {
    }

    /**
     * @param cbClassName name of class declaring the call back method
     * @param cbMethodName name of the call back method
     */
    public static void invokeShutdownCallback(String cbClassName, String cbMethodName) {
    }

    /**
     * @param hintFullGC controls whether the hinted GC should be a full GC.
     * @param forceFullGC controls whether to explicitly perform a full GC
     * @see GraalServices#notifyLowMemoryPoint()
     */
    public static void notifyLowMemoryPoint(boolean hintFullGC, boolean forceFullGC) {
    }
}
