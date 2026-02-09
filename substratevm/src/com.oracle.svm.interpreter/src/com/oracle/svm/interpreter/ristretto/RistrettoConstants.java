/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.interpreter.ristretto;

import com.oracle.svm.interpreter.ristretto.meta.RistrettoMethod;
import com.oracle.svm.interpreter.ristretto.profile.RistrettoCompilationRequest;

public class RistrettoConstants {
    /**
     * Marker value indicating a {@link RistrettoMethod} was never compiled yet.
     */
    public static final int COMPILE_STATE_NEVER_COMPILED = 0;

    /**
     * Marker value indicating that a {@link RistrettoCompilationRequest} was submitted for the
     * given {@link RistrettoMethod}. This means profiling was stopped on the method.
     */
    public static final int COMPILE_STATE_SUBMITTED = 1;

    /**
     * Marker value indicating that a {@link RistrettoCompilationRequest} was finished for the given
     * {@link RistrettoMethod}. This means profiling was stopped on the method and compilation is no
     * longer on-going.
     */
    public static final int COMPILE_STATE_COMPILED = 2;

    /**
     * Marker value indicating that we are currently initializing profile data for a given
     * {@link RistrettoCompilationRequest}.
     */
    public static final int COMPILE_STATE_INITIALIZING = -1;

    /**
     * Marker value indicating that the compile state machine for a given method has not been
     * initialized yet.
     */
    public static final int COMPILE_STATE_INIT_VAL = Integer.MIN_VALUE;
}
