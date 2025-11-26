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
package com.oracle.svm.interpreter.ristretto.profile;

import com.oracle.svm.interpreter.metadata.CremaResolvedJavaMethodImpl;
import com.oracle.svm.interpreter.ristretto.RistrettoConstants;

/**
 * Implements the life cycle of compiled code in Ristretto for an interpreter method. Each method
 * can have different states with respect to its compiled code.
 * 
 * <h3>State Machine Overview</h3>
 * </p>
 * The method uses atomic compare-and-swap (CAS) operations on the {@code compilationState} field of
 * {@link CremaResolvedJavaMethodImpl} to manage state transitions. The states are:
 * <ul>
 * <li>{@link RistrettoConstants#COMPILE_STATE_NEVER_COMPILED COMPILE_STATE_NEVER_COMPILED} (0):
 * Method has been profiled but not yet submitted for compilation</li>
 * <li>{@link RistrettoConstants#COMPILE_STATE_SUBMITTED COMPILE_STATE_SUBMITTED} (1): Compilation
 * request has been submitted to the queue</li>
 * <li>{@link RistrettoConstants#COMPILE_STATE_COMPILED COMPILE_STATE_COMPILED} (2): Compilation has
 * completed successfully</li>
 * <li>{@link RistrettoConstants#COMPILE_STATE_INITIALIZING COMPILE_STATE_INITIALIZING} (-1):
 * Profile initialization is in progress</li>
 * <li>{@link RistrettoConstants#COMPILE_STATE_INIT_VAL INIT_VAL}: Initial state before any
 * profiling</li>
 * </ul>
 *
 * <h3>State Transitions</h3>
 * </p>
 * Currently state transitions are sequential and only reset for testing. In the future, with
 * deoptimization support GR-71501 state transitions will become more complex.
 * 
 * <pre>
 * INIT_VAL ----> INITIALIZING --> NEVER_COMPILED ----> SUBMITTED ----> COMPILED
 * </pre>
 */
public class RistrettoCompileStateMachine {
    static boolean shouldEnterProfiling(int state) {
        return state == RistrettoConstants.COMPILE_STATE_INIT_VAL || state == RistrettoConstants.COMPILE_STATE_NEVER_COMPILED ||
                        state == RistrettoConstants.COMPILE_STATE_INITIALIZING;
    }

    static String toString(int state) {
        return switch (state) {
            case RistrettoConstants.COMPILE_STATE_NEVER_COMPILED -> "NEVER_COMPILED";
            case RistrettoConstants.COMPILE_STATE_SUBMITTED -> "SUBMITTED";
            case RistrettoConstants.COMPILE_STATE_COMPILED -> "COMPILED";
            case RistrettoConstants.COMPILE_STATE_INITIALIZING -> "INITIALIZING";
            case RistrettoConstants.COMPILE_STATE_INIT_VAL -> "INIT_VAL";
            default -> throw new IllegalArgumentException("Unknown state: " + state);
        };
    }
}
