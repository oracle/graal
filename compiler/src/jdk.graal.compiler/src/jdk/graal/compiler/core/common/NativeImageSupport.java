/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.common;

/**
 * Utility class to retrieve the Native Image context in which code gets executed.
 * <p>
 * This mirrors parts of {@code org.graalvm.nativeimage.ImageInfo} without adding a dependency on
 * the latter.
 */
public final class NativeImageSupport {

    /**
     * Returns {@code ImageInfo.inImageBuildtimeCode()} if {@code org.graalvm.nativeimage.ImageInfo}
     * is available via reflection otherwise false.
     */
    private static boolean inImageBuildtimeCode() {
        try {
            Class<?> c;
            try {
                c = Class.forName("org.graalvm.nativeimage.ImageInfo");
            } catch (ClassNotFoundException e) {
                return false;
            }
            return (boolean) c.getDeclaredMethod("inImageBuildtimeCode").invoke(null);
        } catch (Throwable t) {
            throw new InternalError(t);
        }
    }

    private static final boolean BUILDTIME = inImageBuildtimeCode();

    /**
     * Returns true if (at the time of the call) code is executing in the context of image building.
     */
    public static boolean inBuildtimeCode() {
        return BUILDTIME;
    }

    /**
     * Returns true if (at the time of the call) code is executing at image runtime. This method
     * will be const-folded. It can be used to hide parts of an application that only work when
     * running as native image.
     */
    public static boolean inRuntimeCode() {
        return false;
    }
}
