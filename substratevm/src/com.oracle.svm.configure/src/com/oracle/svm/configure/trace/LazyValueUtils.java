/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.configure.trace;

import java.util.function.Supplier;

import org.graalvm.compiler.phases.common.LazyValue;

public class LazyValueUtils {
    static final LazyValue<?> NULL_VALUE = new LazyValue<>(() -> null);

    public static <T> LazyValue<T> lazyValue(T value) {
        return (value != null) ? new LazyValue<>(() -> value) : lazyNull();
    }

    @SuppressWarnings("unchecked")
    public static <T> LazyValue<T> lazyNull() {
        return (LazyValue<T>) NULL_VALUE;
    }

    public static <T> LazyValue<T> lazyGet(Supplier<T> supplier) {
        return new LazyValue<>(supplier);
    }
}
