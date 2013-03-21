/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.bridge;

/**
 * Constants used to mark special positions in code being installed into the code cache by Graal C++
 * code. These constants need to be kept in sync with those of the same name defined in
 * graalCodeInstaller.hpp.
 */
public interface Marks {

    int MARK_VERIFIED_ENTRY = 1;
    int MARK_UNVERIFIED_ENTRY = 2;
    int MARK_OSR_ENTRY = 3;
    int MARK_EXCEPTION_HANDLER_ENTRY = 4;
    int MARK_DEOPT_HANDLER_ENTRY = 5;
    int MARK_INVOKEINTERFACE = 6;
    int MARK_INVOKEVIRTUAL = 7;
    int MARK_INVOKESTATIC = 8;
    int MARK_INVOKESPECIAL = 9;
    int MARK_INLINE_INVOKE = 10;
    int MARK_POLL_NEAR = 11;
    int MARK_POLL_RETURN_NEAR = 12;
    int MARK_POLL_FAR = 13;
    int MARK_POLL_RETURN_FAR = 14;
}
