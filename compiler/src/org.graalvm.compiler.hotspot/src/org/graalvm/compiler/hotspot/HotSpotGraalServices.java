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
package org.graalvm.compiler.hotspot;

import static org.graalvm.compiler.debug.GraalError.shouldNotReachHere;

import jdk.vm.ci.hotspot.HotSpotMetaData;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.SpeculationLog;

/**
 * Interface to HotSpot specific functionality that abstracts over which JDK version Graal is
 * running on.
 */
public class HotSpotGraalServices {

    /**
     * Get the implicit exceptions section of a {@code HotSpotMetaData} if it exists.
     */
    @SuppressWarnings("unused")
    public static byte[] getImplicitExceptionBytes(HotSpotMetaData metaData) {
        throw shouldNotReachHere();
    }

    /**
     * Enters the global context. This is useful to escape a local context for execution that will
     * create foreign object references that need to outlive the local context.
     *
     * Foreign object references encapsulated by {@link JavaConstant}s created in the global context
     * are only subject to reclamation once the {@link JavaConstant} wrapper dies.
     *
     * @return {@code null} if the current runtime does not support remote object references or if
     *         this thread is currently in the global context
     */
    public static CompilationContext enterGlobalCompilationContext() {
        throw shouldNotReachHere();
    }

    /**
     * Opens a local context that upon closing, will release foreign object references encapsulated
     * by {@link JavaConstant}s created in the context.
     *
     * @param description an non-null object whose {@link Object#toString()} value describes the
     *            context being opened
     * @return {@code null} if the current runtime does not support remote object references
     */
    @SuppressWarnings("unused")
    public static CompilationContext openLocalCompilationContext(Object description) {
        throw shouldNotReachHere();
    }

    /**
     * Exits Graal's runtime. This calls {@link System#exit(int)} in HotSpot's runtime if possible
     * otherwise calls {@link System#exit(int)} in the current runtime.
     */
    @SuppressWarnings("unused")
    public static void exit(int status) {
        throw shouldNotReachHere();
    }

    @SuppressWarnings("unused")
    public static SpeculationLog newHotSpotSpeculationLog(long cachedFailedSpeculationsAddress) {
        throw shouldNotReachHere();
    }
}
