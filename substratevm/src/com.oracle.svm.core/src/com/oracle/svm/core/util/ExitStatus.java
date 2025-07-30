/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

/** Exit status codes to be used at build time (in driver and builder). */
public enum ExitStatus {
    OK(0),
    BUILDER_ERROR(1),
    FALLBACK_IMAGE(2),

    // 3 used by `-XX:+ExitOnOutOfMemoryError` (see src/hotspot/share/utilities/debug.cpp)
    OUT_OF_MEMORY(3),
    // Used by OOMKilled in containers
    OUT_OF_MEMORY_KILLED(137),

    BUILDER_INTERRUPT_WITHOUT_REASON(4),
    DRIVER_ERROR(20),
    DRIVER_TO_BUILDER_ERROR(21),
    WATCHDOG_EXIT(30),
    REBUILD_AFTER_ANALYSIS(40),
    // podman can exit 125 if container does not need building
    CONTAINER_REUSE(125),
    MISSING_METADATA(172),
    UNKNOWN(255);

    public static ExitStatus of(int status) {
        for (ExitStatus s : values()) {
            if (s.getValue() == status) {
                return s;
            }
        }
        return UNKNOWN;
    }

    private final int code;

    ExitStatus(int code) {
        this.code = code;
    }

    public int getValue() {
        return code;
    }
}
