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
package com.oracle.svm.test;

import static java.lang.classfile.ClassFile.ACC_FINAL;
import static java.lang.classfile.ClassFile.ACC_PUBLIC;
import static java.lang.classfile.ClassFile.ACC_SUPER;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.lang.classfile.ClassFile;
import java.lang.classfile.attribute.InnerClassInfo;
import java.lang.classfile.attribute.InnerClassesAttribute;
import java.lang.constant.ClassDesc;
import java.lang.reflect.Modifier;
import java.util.Optional;

import org.junit.Test;

@NativeImageBuildArgs({"-H:+UnlockExperimentalVMOptions", "-H:+RuntimeClassLoading"})
public class RuntimeClassLoadingSuperclassModifiersTest {
    private static final ClassDesc OUTER = ClassDesc.of("generated.Outer");
    private static final ClassDesc PARENT = ClassDesc.of("generated.Outer$Parent");
    private static final ClassDesc CHILD = ClassDesc.of("generated.Child");

    private static final class Loader extends ClassLoader {
        Class<?> define(byte[] bytes) {
            return defineClass(null, bytes, 0, bytes.length);
        }
    }

    private static byte[] parent(int classFlags, int innerFlags) {
        return ClassFile.of().build(PARENT, b -> b.withFlags(classFlags)
                        .with(InnerClassesAttribute.of(InnerClassInfo.of(PARENT, Optional.of(OUTER), Optional.of("Parent"), innerFlags))));
    }

    private static byte[] child() {
        return ClassFile.of().build(CHILD, b -> b.withFlags(ACC_PUBLIC | ACC_SUPER).withSuperclass(PARENT));
    }

    @Test
    public void reflectiveFinalDoesNotPreventSubclassing() {
        Loader loader = new Loader();
        Class<?> parent = loader.define(parent(ACC_PUBLIC | ACC_SUPER, ACC_PUBLIC | ACC_FINAL));
        assertTrue("the InnerClasses entry must report final", Modifier.isFinal(parent.getModifiers()));
        assertNotNull("class-file flags permit subclassing", loader.define(child()));
    }

    @Test
    public void classFileFinalPreventsSubclassing() {
        Loader loader = new Loader();
        loader.define(parent(ACC_PUBLIC | ACC_SUPER | ACC_FINAL, ACC_PUBLIC));
        assertThrows(IncompatibleClassChangeError.class, () -> loader.define(child()));
    }

    @Test
    public void ordinaryNonFinalSuperclassRemainsSupported() {
        Loader loader = new Loader();
        loader.define(parent(ACC_PUBLIC | ACC_SUPER, ACC_PUBLIC));
        assertNotNull(loader.define(child()));
    }
}
