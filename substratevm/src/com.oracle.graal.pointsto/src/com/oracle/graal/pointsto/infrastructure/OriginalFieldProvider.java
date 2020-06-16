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
package com.oracle.graal.pointsto.infrastructure;

import java.lang.reflect.Field;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;

import jdk.vm.ci.meta.ResolvedJavaField;

public interface OriginalFieldProvider {

    static Field getJavaField(SnippetReflectionProvider reflectionProvider, ResolvedJavaField field) {
        if (field instanceof OriginalFieldProvider) {
            return ((OriginalFieldProvider) field).getJavaField();
        }
        Class<?> declaringClass = OriginalClassProvider.getJavaClass(reflectionProvider, field.getDeclaringClass());
        try {
            return declaringClass.getDeclaredField(field.getName());
        } catch (Throwable e) {
            /*
             * Return null if there is some incomplete classpath issue or the field is either
             * missing or hidden from reflection.
             */
            return null;
        }
    }

    /**
     * Returns the original reflecton field. First the original Java class corresponding to the
     * field's declaring class is retrieved. Then the field is accesed using
     * Class.getDeclaredField(name). This method can return null if the field's declaring class
     * references types missing from the classpath or the field is either missing or hidden from
     * reflection.
     * 
     * @return original reflecton field, or {@code null}
     */
    Field getJavaField();
}
