/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.test.terminus;

import org.graalvm.nativeimage.hosted.Feature;

/**
 * Checks that exceptions thrown by a guest user feature retain their useful stack trace when they
 * cross the builder/guest boundary.
 */
public class GuestFeatureExceptionStackTraceTest {

    private static final String SENTINEL_MESSAGE = "guest-feature-exception-stack-trace-sentinel";

    public static void main(String[] args) {
    }

    public static final class UserFeatureException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        UserFeatureException(String message) {
            super(message);
        }
    }

    /** A public user feature with the same shape as an application-provided feature. */
    public static final class TestFeature implements Feature {

        @Override
        public void afterRegistration(AfterRegistrationAccess access) {
            throwSentinelException();
        }

        private static void throwSentinelException() {
            throw new UserFeatureException(SENTINEL_MESSAGE);
        }
    }
}
