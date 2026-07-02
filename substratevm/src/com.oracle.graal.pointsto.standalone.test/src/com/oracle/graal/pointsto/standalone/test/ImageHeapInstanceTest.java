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
package com.oracle.graal.pointsto.standalone.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;

import com.oracle.graal.pointsto.heap.value.ValueSupplier;
import com.oracle.graal.pointsto.util.AnalysisFuture;

import jdk.vm.ci.meta.JavaConstant;

public class ImageHeapInstanceTest {

    @Test
    public void fieldValueTaskTracksRawValueAvailability() throws ReflectiveOperationException {
        AtomicBoolean available = new AtomicBoolean();
        ValueSupplier<JavaConstant> rawValue = ValueSupplier.lazyValue(() -> JavaConstant.NULL_POINTER, available::get);
        AnalysisFuture<JavaConstant> task = new AnalysisFuture<>(rawValue::get);

        Class<?> fieldValueTaskClass = Class.forName("com.oracle.graal.pointsto.heap.ImageHeapInstance$FieldValueTask");
        Constructor<?> constructor = fieldValueTaskClass.getDeclaredConstructor(ValueSupplier.class, AnalysisFuture.class);
        Method isAvailable = fieldValueTaskClass.getDeclaredMethod("isAvailable");
        Method ensureDone = fieldValueTaskClass.getDeclaredMethod("ensureDone");
        constructor.setAccessible(true);
        isAvailable.setAccessible(true);
        ensureDone.setAccessible(true);
        Object fieldValueTask = constructor.newInstance(rawValue, task);

        assertFalse((boolean) isAvailable.invoke(fieldValueTask));
        available.set(true);
        assertTrue((boolean) isAvailable.invoke(fieldValueTask));
        assertEquals(JavaConstant.NULL_POINTER, ensureDone.invoke(fieldValueTask));
    }
}
