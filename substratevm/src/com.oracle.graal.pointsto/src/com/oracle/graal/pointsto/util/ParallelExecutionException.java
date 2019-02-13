/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.util;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.List;

/**
 * Bundles all exceptions from on parallel execution so they can be reported properly.
 */
public class ParallelExecutionException extends RuntimeException {
    private static final long serialVersionUID = -8477198165297173951L;

    private final List<Throwable> exceptions;

    ParallelExecutionException(List<Throwable> exceptions) {
        this.exceptions = exceptions;
    }

    public List<Throwable> getExceptions() {
        return exceptions;
    }

    @Override
    public void printStackTrace(PrintStream out) {
        super.printStackTrace(out);
        int num = 0;
        for (Throwable cause : exceptions) {
            if (cause != null) {
                out.print("cause " + (num++));
                cause.printStackTrace(out);
            }
        }
    }

    @Override
    public void printStackTrace(PrintWriter out) {
        super.printStackTrace(out);
        int num = 0;
        for (Throwable cause : exceptions) {
            if (cause != null) {
                out.print("cause " + (num++) + ": ");
                cause.printStackTrace(out);
            }
        }
    }
}
