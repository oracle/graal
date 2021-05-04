/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.staticobject;

final class FieldBasedStaticShape<T> extends StaticShape<T> {
    private static final PrivilegedToken TOKEN = new FieldBasedPrivilegedToken();

    private FieldBasedStaticShape(Class<?> storageClass) {
        super(storageClass, TOKEN);
    }

    static <T> FieldBasedStaticShape<T> create(Class<?> generatedStorageClass, Class<? extends T> generatedFactoryClass) {
        try {
            FieldBasedStaticShape<T> shape = new FieldBasedStaticShape<>(generatedStorageClass);
            T factory = generatedFactoryClass.cast(UNSAFE.allocateInstance(generatedFactoryClass));
            shape.setFactory(factory);
            return shape;
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    Object getStorage(Object obj, boolean primitive) {
        return cast(obj, storageClass);
    }

    private static final class FieldBasedPrivilegedToken extends PrivilegedToken {
    }
}
