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
package com.oracle.svm.core.util;

// Checkstyle: allow reflection

import java.lang.reflect.InvocationTargetException;

import com.oracle.svm.core.annotate.NeverInline;
import com.oracle.svm.core.annotate.StubCallingConvention;
import com.oracle.svm.core.jdk.InternalVMMethod;

/**
 * Helper methods to throw exceptions from manually generated Graal graphs. We do not want these
 * helpers to show up in exception stack traces, therefore the class is annotated with
 * {@link InternalVMMethod}.
 */
@InternalVMMethod
public class ExceptionHelpers {

    @StubCallingConvention
    @NeverInline("Exception slow path")
    private static InvocationTargetException throwInvocationTargetException(Throwable target) throws InvocationTargetException {
        throw new InvocationTargetException(target);
    }

    @StubCallingConvention
    @NeverInline("Exception slow path")
    private static IllegalArgumentException throwIllegalArgumentException(String message) {
        throw new IllegalArgumentException(message, null);
    }

    @StubCallingConvention
    @NeverInline("Exception slow path")
    private static IllegalArgumentException throwFailedCast(Class<?> expected, Object actual) {
        throw new IllegalArgumentException("cannot cast " + actual.getClass().getName() + " to " + expected.getName(), null);
    }
}
