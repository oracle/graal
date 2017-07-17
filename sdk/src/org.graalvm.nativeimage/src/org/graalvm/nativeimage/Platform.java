/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativeimage;

/**
 * Root of the interface hierarchy for architectures, OS, and supported combinations of them.
 * <p>
 * A platform group (e.g., an architecture or OS) is an interface extending {@link Platform}. A leaf
 * platform, e.g., a supported architecture-OS-combination, is a class that implements all the
 * groups that it belongs to. It is good practice to make leaf platform classes {@code final}, to
 * prevent accidental subclassing.
 * <p>
 * The annotation {@link Platforms} restricts a type, method, or field to certain platform groups or
 * leaf platforms.
 * <p>
 * This system makes the set of platform groups and leaf platforms extensible. Some standard
 * platforms are defined as inner classes.
 */
public interface Platform {

    /*
     * The standard architectures that we support.
     */

    interface AMD64 extends Platform {
    }

    /*
     * The standard operating systems that we support.
     */

    interface LINUX extends Platform {
    }

    interface DARWIN extends Platform {
    }

    /*
     * Standard leaf platforms, i.e., OS-architecture combinations that we support.
     */

    final class LINUX_AMD64 implements LINUX, AMD64 {
    }

    final class DARWIN_AMD64 implements DARWIN, AMD64 {
    }

    /**
     * Marker for elements (types, methods, or fields) that are only visible during native image
     * generation and cannot be used at run time, regardless of the actual platform.
     */
    final class HOSTED_ONLY implements Platform {
        private HOSTED_ONLY() {
        }
    }

    /**
     * Returns true if the current platform (the platform that the native image is built for) is
     * included in the provided platform group.
     * <p>
     * The platformGroup must be a compile time constant, so that the call to this method can be
     * replaced with the constant boolean result.
     */
    static boolean includedIn(Class<? extends Platform> platformGroup) {
        return platformGroup.isInstance(ImageSingletons.lookup(Platform.class));
    }
}
