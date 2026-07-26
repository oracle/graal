/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted;

import java.lang.reflect.Executable;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.impl.RuntimeReflectionSupport;

import com.oracle.svm.hosted.reflect.ReflectionDataBuilder;

/**
 * Narrow hosted query used to recognize provider registration signals without exposing the
 * concrete reflection builder to provider-policy code.
 */
interface ReflectionRegistrationView {
    boolean hasTypeAccess(Class<?> type);

    boolean hasExecutableAccess(Executable executable);

    static ReflectionRegistrationView singleton() {
        ReflectionDataBuilder builder = (ReflectionDataBuilder) ImageSingletons.lookup(RuntimeReflectionSupport.class);
        return new ReflectionRegistrationView() {
            @Override
            public boolean hasTypeAccess(Class<?> type) {
                return builder.isTypeRegisteredForReflection(type);
            }

            @Override
            public boolean hasExecutableAccess(Executable executable) {
                return builder.isMethodRegisteredForReflection(executable);
            }
        };
    }
}
