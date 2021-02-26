/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package com.oracle.truffle.espresso.jvmti;

/*-
 * From jvmti header:
 * 
 * typedef enum {
 *     JVMTI_PHASE_ONLOAD = 1,
 *     JVMTI_PHASE_PRIMORDIAL = 2,
 *     JVMTI_PHASE_START = 6,
 *     JVMTI_PHASE_LIVE = 4,
 *     JVMTI_PHASE_DEAD = 8
 * } jvmtiPhase;
 */
public enum JvmtiPhase {
    ONLOAD(1),
    PRIMORDIAL(2),
    LIVE(4),
    START(6),
    DEAD(8);

    private final int value;

    JvmtiPhase(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }
}
