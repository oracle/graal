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

package com.oracle.svm.webimage;

/**
 * Mimic {@link System#exit(int)} using an error.
 *
 * Since most JS runtimes do not have a good way to immediately terminate the entire process, Web
 * Image throws this error whenever System.exit is called.
 *
 * Client code could in theory still catch and ignore these errors. However, the {@link Error} class
 * was chosen as the super class because code should not try to catch it.
 */
public class ExitError extends Error {
    private static final long serialVersionUID = -5549257403831920004L;

    public final int exitCode;

    public ExitError(int exitCode) {
        super("Process exited with code: " + exitCode + ". DO NOT CATCH THIS ERROR!");
        this.exitCode = exitCode;
    }

}
