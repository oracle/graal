/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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

public enum ExitStatus {
    OK(0),
    BUILDER_ERROR(1),
    FALLBACK_IMAGE(2),
    BUILDER_INTERRUPT_WITHOUT_REASON(3),
    DRIVER_ERROR(20),
    DRIVER_TO_BUILDER_ERROR(21),
    WATCHDOG_EXIT(30);

    public static ExitStatus of(int status) {
        for (ExitStatus s : values()) {
            if (s.getValue() == status) {
                return s;
            }
        }
        return null;
    }

    private final int code;

    ExitStatus(int code) {
        this.code = code;
    }

    public int getValue() {
        return code;
    }
}
