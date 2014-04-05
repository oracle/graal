/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.compiler.hsail.test;

import com.oracle.graal.compiler.hsail.test.infra.GraalKernelTester;

/**
 *
 * @author ecaspole
 */
public abstract class SingleExceptionTestBase extends GraalKernelTester {

    @Result Class<?> exceptionClass;
    @Result String exceptionString;
    @Result StackTraceElement firstStackTraceElement;

    @Override
    protected boolean supportsRequiredCapabilities() {
        return getHSAILBackend().getRuntime().getConfig().useHSAILDeoptimization;
    }

    void recordException(Exception e) {
        // for now we just test that the class the of the exception
        // matches for the java and gpu side
        exceptionClass = e.getClass();
        // exception = e;
        StackTraceElement[] elems = e.getStackTrace();
        firstStackTraceElement = elems[0];
        // for tests where the exception was in the method parameters
        // ignore the firstStackTraceElement matching
        if (firstStackTraceElement.getClassName().contains("KernelTester")) {
            firstStackTraceElement = null;
        }
        for (StackTraceElement elem : elems) {
            if (elem.toString().contains("KernelTester")) {
                break;
            }
        }
    }
}
