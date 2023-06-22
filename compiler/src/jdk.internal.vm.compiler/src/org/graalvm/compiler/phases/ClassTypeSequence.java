/*
 * Copyright (c) 2011, 2018, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.compiler.phases;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * A printable representation of the name of class that can serialized as a fully qualified type for
 * dumping. This is to support deobfuscation of dump output. The {@link #toString()} is the
 * unqualified name of the Class.
 */
public final class ClassTypeSequence implements JavaType, CharSequence {
    private final Class<?> clazz;

    public ClassTypeSequence(Class<?> clazz) {
        this.clazz = clazz;
    }

    @Override
    public String getName() {
        return "L" + clazz.getName().replace('.', '/') + ";";
    }

    @Override
    public String toJavaName() {
        return toJavaName(true);
    }

    @Override
    public String toJavaName(boolean qualified) {
        if (qualified) {
            return clazz.getName();
        } else {
            int lastDot = clazz.getName().lastIndexOf('.');
            return clazz.getName().substring(lastDot + 1);
        }
    }

    @Override
    public JavaType getComponentType() {
        return null;
    }

    @Override
    public JavaType getArrayClass() {
        return null;
    }

    @Override
    public JavaKind getJavaKind() {
        return JavaKind.Object;
    }

    @Override
    public ResolvedJavaType resolve(ResolvedJavaType accessingClass) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int length() {
        return clazz.getName().length();
    }

    @Override
    public char charAt(int index) {
        return clazz.getName().charAt(index);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return clazz.getName().subSequence(start, end);
    }

    @Override
    public String toString() {
        return toJavaName(false);
    }
}
