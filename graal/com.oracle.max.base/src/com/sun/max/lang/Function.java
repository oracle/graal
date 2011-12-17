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
package com.sun.max.lang;

import java.util.concurrent.*;

/**
 * Creates a function wrapper for a method that returns a value and may throw a checked exception.
 * This interface extends {@link Callable} so that {@code Function}s can be used with an {@link ExecutorService}.
 */
public interface Function<T> extends Callable<T> {

    /**
     * Computes a result, or throws an exception if unable to do so.
     *
     * @return computed result (which will be {@code null} if {@code Result_Type} is {@link Void})
     * @throws Exception if unable to compute a result
     */
    T call() throws Exception;

}
