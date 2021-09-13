/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.reflect;

import com.oracle.svm.core.jdk.InternalVMMethod;
import com.oracle.svm.core.util.VMError;

/**
 * This class is used as the declaring class for reflection invocation methods. These methods have
 * manually created Graal IR, and are invoked via a function pointer call from
 * {@link SubstrateMethodAccessor} and {@link SubstrateConstructorAccessor}
 */
@InternalVMMethod
@SuppressWarnings("unused")
public final class ReflectionAccessorHolder {

    /**
     * Signature prototype for invoking a method via a {@link SubstrateMethodAccessor}.
     */
    private static Object invokePrototype(Object obj, Object[] args) {
        throw VMError.shouldNotReachHere("Only used as a prototype for generated methods");
    }

    /**
     * Signature prototype for allocating a new instance via a {@link SubstrateConstructorAccessor}.
     */
    private static Object newInstancePrototype(Object[] args) {
        throw VMError.shouldNotReachHere("Only used as a prototype for generated methods");
    }

    /*
     * Methods for throwing exceptions when a method or constructor is used in an illegal way.
     */

    private static Object invokeSpecialError(Object obj, Object[] args) {
        throw new IllegalArgumentException("Static or abstract method cannot be invoked using invokeSpecial");
    }

    private static Object newInstanceError(Object[] args) throws InstantiationException {
        throw new InstantiationException("Only non-abstract instance classes can be instantiated using reflection");
    }
}
