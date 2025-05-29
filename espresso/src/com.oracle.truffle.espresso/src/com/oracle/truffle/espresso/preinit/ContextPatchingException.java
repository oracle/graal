/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.preinit;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.espresso.classfile.JavaVersion;

public final class ContextPatchingException extends Exception {

    private static final long serialVersionUID = -762795124477419520L;

    public static ContextPatchingException javaVersionMismatch(JavaVersion languageJavaVersion, JavaVersion contextJavaVersion) throws ContextPatchingException {
        CompilerAsserts.neverPartOfCompilation();
        String errMsg = String.format("Configuration specified a Java version incompatible with the pre-initialized language - expected: %s, got: %s.", languageJavaVersion,
                        contextJavaVersion);
        throw new ContextPatchingException(errMsg);
    }

    private ContextPatchingException(String message) {
        super(message);
    }
}
