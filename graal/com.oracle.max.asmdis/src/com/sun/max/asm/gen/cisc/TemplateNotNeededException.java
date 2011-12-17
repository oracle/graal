/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.asm.gen.cisc;

import com.sun.max.asm.dis.*;

/**
 * Thrown to abruptly stop template creation in some corner cases
 * that would otherwise be hard to describe.
 *
 * The {@link Disassembler disassembler} works by matching instruction patterns
 * against a set of templates. Creation of these templates can be noticeably
 * sped up by reusing a shared instance of this exception.
 * A client using the disassembler should {@linkplain #enableSharedInstance() enable}
 * before creating the first Disassembler object.
 */
public class TemplateNotNeededException extends Exception {

    public TemplateNotNeededException() {
        super();
    }

    public static synchronized void enableSharedInstance() {
        if (sharedInstance == null) {
            sharedInstance = new TemplateNotNeededException();
        }
    }

    public static synchronized void disableSharedInstance() {
        sharedInstance = null;
    }

    public static TemplateNotNeededException raise() throws TemplateNotNeededException {
        final TemplateNotNeededException instance = sharedInstance;
        if (instance != null) {
            throw instance;
        }
        throw new TemplateNotNeededException();
    }

    // Checkstyle: stop
    private static TemplateNotNeededException sharedInstance;
}
