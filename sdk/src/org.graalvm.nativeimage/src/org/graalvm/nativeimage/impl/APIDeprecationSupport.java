/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.graalvm.nativeimage.impl;

/**
 * A class that is used to emit warnings when calling the APIs that are currently in the migration
 * process, if an appropriate flag is enabled. It is used in conjunction with
 * {@link org.graalvm.nativeimage.ImageSingletons}.
 */
public class APIDeprecationSupport {
    private final boolean flagValue;
    private boolean userEnabledFeaturesStarted = false;

    public APIDeprecationSupport(boolean flagValue) {
        this.flagValue = flagValue;
    }

    public boolean isUserEnabledFeaturesStarted() {
        return userEnabledFeaturesStarted;
    }

    public void setUserEnabledFeaturesStarted(boolean started) {
        userEnabledFeaturesStarted = started;
    }

    public void printDeprecationWarning() {
        if (flagValue && userEnabledFeaturesStarted) {
            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
            String callersClass = stackTrace[2].getClassName();
            String migrationClass;
            switch (callersClass) {
                case "org.graalvm.nativeimage.hosted.RuntimeResourceAccess":
                    migrationClass = "ResourceAccess";
                    break;
                case "org.graalvm.nativeimage.hosted.RuntimeJNIAccess":
                    migrationClass = "JNIAccess";
                    break;
                case "org.graalvm.nativeimage.hosted.RuntimeForeignAccess":
                    migrationClass = "ForeignAccess";
                    break;
                default:
                    migrationClass = "ReflectiveAccess";
                    break;
            }

            System.err.println(
                            String.format("Warning: You are using an outdated metadata registration API. Please migrate to the new API: %s located in the 'dynamicaccess' package.", migrationClass));
            for (int i = 2; i < stackTrace.length; i++) {
                System.err.println("\tat " + stackTrace[i]);
            }
        }
    }
}
