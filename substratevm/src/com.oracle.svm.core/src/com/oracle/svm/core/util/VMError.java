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
package com.oracle.svm.core.util;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.annotate.Uninterruptible;

/**
 * A collection of static methods for error reporting of fatal error. A fatal error leaves the VM in
 * an inconsistent state, so no meaningful recovery is possible.
 */
public final class VMError {

    /**
     * Implementation note: During native image generation, a HostedError is thrown to indicate a
     * fatal error. The methods are substituted so that at run time a fatal error is reported. This
     * means that it is not possible to catch a fatal error at run time, since there is actually no
     * HostedError thrown.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static final class HostedError extends Error {

        private static final long serialVersionUID = 1574347086891451263L;

        HostedError(String msg) {
            super(msg);
        }

        HostedError(Throwable ex) {
            super(ex);
        }

        HostedError(String msg, Throwable cause) {
            super(msg, cause);
        }

    }

    public static RuntimeException shouldNotReachHere() {
        throw new HostedError("should not reach here");
    }

    public static RuntimeException shouldNotReachHere(String msg) {
        throw new HostedError(msg);
    }

    public static RuntimeException shouldNotReachHere(Throwable ex) {
        throw new HostedError(ex);
    }

    public static RuntimeException shouldNotReachHere(String msg, Throwable cause) {
        throw new HostedError(msg, cause);
    }

    @Uninterruptible(reason = "Allow VMError to be used in uninterruptible code.", mayBeInlined = true)
    public static void guarantee(boolean condition) {
        if (!condition) {
            throw shouldNotReachHere("guarantee failed");
        }
    }

    @Uninterruptible(reason = "Allow VMError to be used in uninterruptible code.", mayBeInlined = true)
    public static void guarantee(boolean condition, String msg) {
        if (!condition) {
            throw shouldNotReachHere(msg);
        }
    }

    public static RuntimeException unimplemented() {
        throw new UnsupportedOperationException("unimplemented");
    }

    public static RuntimeException unimplemented(String msg) {
        throw new UnsupportedOperationException(msg);
    }

    public static RuntimeException unsupportedFeature(String msg) {
        throw new HostedError("UNSUPPORTED FEATURE: " + msg);
    }

    public static boolean hostedError(Throwable t) {
        return t instanceof HostedError;
    }
}
