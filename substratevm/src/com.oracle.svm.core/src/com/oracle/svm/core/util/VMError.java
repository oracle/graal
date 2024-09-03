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

import com.oracle.svm.core.Uninterruptible;

import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * A collection of static methods for error reporting of fatal error. A fatal error leaves the VM in
 * an inconsistent state, so no meaningful recovery is possible.
 */
public final class VMError {

    /**
     * Implementation note: During native image generation, a HostedError is thrown to indicate a
     * fatal error. The methods are substituted (@see VMErrorSubstitutions for implementation) so
     * that at run time a fatal error is reported. This means that it is not possible to catch a
     * fatal error at run time, since there is actually no HostedError thrown.
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

    public static final String msgShouldNotReachHere = "should not reach here";
    public static final String msgShouldNotReachHereSubstitution = msgShouldNotReachHere + ": substitution reached at runtime";
    public static final String msgShouldNotReachHereUnexpectedInput = msgShouldNotReachHere + ": unexpected input could not be handled";
    public static final String msgShouldNotReachHereOverrideInChild = msgShouldNotReachHere + ": method should have been overridden in child";
    public static final String msgShouldNotReachHereAtRuntime = msgShouldNotReachHere + ": this code is expected to be unreachable at runtime";
    public static final String msgShouldNotReachHereUnsupportedPlatform = msgShouldNotReachHere + ": unsupported platform";

    public static final String msgUnimplemented = "unimplemented";
    public static final String msgUnimplementedIntentionally = msgUnimplemented + ": this method has intentionally not been implemented";

    public static RuntimeException shouldNotReachHere(String msg) {
        throw new HostedError(msg);
    }

    public static RuntimeException shouldNotReachHere(Throwable ex) {
        throw new HostedError(ex);
    }

    public static RuntimeException shouldNotReachHere(String msg, Throwable cause) {
        throw new HostedError(msg, cause);
    }

    public static RuntimeException shouldNotReachHereSubstitution() {
        throw new HostedError(msgShouldNotReachHereSubstitution);
    }

    /**
     * A hardcoded list of options (if, switch) did not handle the case actually provided.
     */
    public static RuntimeException shouldNotReachHereUnexpectedInput(Object input) {
        throw new HostedError(msgShouldNotReachHereUnexpectedInput + ": " + input);
    }

    public static RuntimeException shouldNotReachHereOverrideInChild() {
        throw new HostedError(msgShouldNotReachHereOverrideInChild);
    }

    public static RuntimeException shouldNotReachHereAtRuntime() {
        throw new HostedError(msgShouldNotReachHereAtRuntime);
    }

    public static RuntimeException unsupportedPlatform() {
        throw shouldNotReachHere(msgShouldNotReachHereUnsupportedPlatform);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void guarantee(boolean condition) {
        if (!condition) {
            throw shouldNotReachHere("guarantee failed");
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void guarantee(boolean condition, String msg) {
        if (!condition) {
            throw shouldNotReachHere(msg);
        }
    }

    /**
     * Throws a runtime exception with a formatted message.
     *
     * This method uses {@link String#format} which is currently not safe to be used at run time as
     * it pulls in high amounts of JDK code. This might change in the future, e.g., if parse-once is
     * fully supported (GR-39237). Until then, the format string variants of
     * {@link VMError#shouldNotReachHere} and {@link VMError#guarantee} can only be used in
     * hosted-only code.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static RuntimeException shouldNotReachHere(String msg, Object... args) {
        throw shouldNotReachHere(String.format(msg, formatArguments(args)));
    }

    /**
     * @see #shouldNotReachHere(String, Object...)
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static void guarantee(boolean condition, String msg, Object arg1) {
        if (!condition) {
            throw shouldNotReachHere(msg, arg1);
        }
    }

    /**
     * @see #shouldNotReachHere(String, Object...)
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static void guarantee(boolean condition, String msg, Object arg1, Object arg2) {
        if (!condition) {
            throw shouldNotReachHere(msg, arg1, arg2);
        }
    }

    /**
     * @see #shouldNotReachHere(String, Object...)
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static void guarantee(boolean condition, String msg, Object arg1, Object arg2, Object arg3) {
        if (!condition) {
            throw shouldNotReachHere(msg, arg1, arg2, arg3);
        }
    }

    /**
     * @see #shouldNotReachHere(String, Object...)
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static void guarantee(boolean condition, String msg, Object arg1, Object arg2, Object arg3, Object arg4) {
        if (!condition) {
            throw shouldNotReachHere(msg, arg1, arg2, arg3, arg4);
        }
    }

    /**
     * @see #shouldNotReachHere(String, Object...)
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static void guarantee(boolean condition, String msg, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5) {
        if (!condition) {
            throw shouldNotReachHere(msg, arg1, arg2, arg3, arg4, arg5);
        }
    }

    /**
     * A lower-level feature that is not yet supported (but might be implemented later, if
     * relevant).
     */
    public static RuntimeException unimplemented(String msg) {
        throw new UnsupportedOperationException(msg);
    }

    /**
     * A lower-level feature that is not implemented. A conscious decision was made not to implement
     * it.
     */
    public static RuntimeException intentionallyUnimplemented() {
        throw new UnsupportedOperationException(msgUnimplementedIntentionally);
    }

    /**
     * A high-level feature that is not supported, e.g. class loading at runtime.
     */
    public static RuntimeException unsupportedFeature(String msg) {
        throw new HostedError("Unsupported feature: " + msg);
    }

    /**
     * Processes {@code args} to convert selected values to strings.
     * <ul>
     * <li>A {@link ResolvedJavaType} is converted with {@link ResolvedJavaType#toJavaName}
     * {@code (true)}.</li>
     * <li>A {@link ResolvedJavaMethod} is converted with {@link ResolvedJavaMethod#format}
     * {@code ("%H.%n($p)")}.</li>
     * <li>A {@link ResolvedJavaField} is converted with {@link ResolvedJavaField#format}
     * {@code ("%H.%n")}.</li>
     * </ul>
     * All other values are copied to the returned array unmodified.
     *
     * @param args arguments to process
     * @return a copy of {@code args} with certain values converted to strings as described above
     */
    static Object[] formatArguments(Object... args) {
        Object[] newArgs = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            if (arg instanceof ResolvedJavaType) {
                newArgs[i] = ((ResolvedJavaType) arg).toJavaName(true);
            } else if (arg instanceof ResolvedJavaMethod) {
                newArgs[i] = ((ResolvedJavaMethod) arg).format("%H.%n(%p)");
            } else if (arg instanceof ResolvedJavaField) {
                newArgs[i] = ((ResolvedJavaField) arg).format("%H.%n");
            } else {
                newArgs[i] = arg;
            }
        }
        return newArgs;
    }
}
