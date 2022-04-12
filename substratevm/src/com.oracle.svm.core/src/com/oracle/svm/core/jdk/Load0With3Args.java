/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

import java.util.Arrays;
import java.util.function.BooleanSupplier;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A predicate that returns {@code true} iff
 * {@code boolean java.lang.ClassLoader.NativeLibrary.load0(String name, boolean isBuiltin, boolean throwExceptionIfFail)}
 * exists. It should only be used in conjunction with {@link JDK11OrEarlier} as
 * {@code NativeLibrary} was moved to a top level class in later JDKs.
 */
// Checkstyle: allow Class.getSimpleName
public class Load0With3Args implements BooleanSupplier {
    @Override
    public boolean getAsBoolean() {
        Optional<Class<?>> nativeLibrary = Stream.of(ClassLoader.class.getDeclaredClasses()).filter(c -> c.getSimpleName().equals("NativeLibrary")).findFirst();
        if (nativeLibrary.isPresent()) {
            Class<?>[] signature = {String.class, boolean.class, boolean.class};
            return Stream.of(nativeLibrary.get().getDeclaredMethods()).filter(m -> m.getName().equals("load0") && Arrays.equals(m.getParameters(), signature)).findFirst().isPresent();
        }
        return false;
    }
}
