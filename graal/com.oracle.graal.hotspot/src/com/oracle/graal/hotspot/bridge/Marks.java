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

    // @formatter:off
    // These constants need to correspond to those of the same name in graalCodeInstaller.hpp
    Integer MARK_VERIFIED_ENTRY            = 0x0001;
    Integer MARK_UNVERIFIED_ENTRY          = 0x0002;
    Integer MARK_OSR_ENTRY                 = 0x0003;
    Integer MARK_UNWIND_ENTRY              = 0x0004;
    Integer MARK_EXCEPTION_HANDLER_ENTRY   = 0x0005;
    Integer MARK_DEOPT_HANDLER_ENTRY       = 0x0006;
    Integer MARK_STATIC_CALL_STUB          = 0x1000;
    Integer MARK_INVOKEINTERFACE           = 0x2001;
    Integer MARK_INVOKESTATIC              = 0x2002;
    Integer MARK_INVOKESPECIAL             = 0x2003;
    Integer MARK_INVOKEVIRTUAL             = 0x2004;
    Integer MARK_INLINE_INVOKEVIRTUAL      = 0x2005;
    Integer MARK_IMPLICIT_NULL             = 0x3000;
    Integer MARK_POLL_NEAR                 = 0x3001;
    Integer MARK_POLL_RETURN_NEAR          = 0x3002;
    Integer MARK_POLL_FAR                  = 0x3003;
    Integer MARK_POLL_RETURN_FAR           = 0x3004;

}
