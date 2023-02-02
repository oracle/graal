/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2020, Red Hat Inc. All rights reserved.
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
package com.oracle.svm.graal.test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.graalvm.compiler.serviceprovider.GraalServices;
import org.graalvm.compiler.serviceprovider.UnencodedSpeculationReason;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.svm.graal.isolated.IsolatedSpeculationLog;

import jdk.vm.ci.meta.SpeculationLog;

public class IsolatedSpeculationLogEncodingNativeTest {

    @Test
    public void testEncodeAsByteArray() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException, ClassNotFoundException, InstantiationException {
        final int groupId = 1234;
        final Object[] context = {'a', null, "Hello World!", Byte.MAX_VALUE, Short.MAX_VALUE, Long.MAX_VALUE, Integer.MAX_VALUE, Double.MAX_VALUE, Float.MAX_VALUE};

        final Class<?> encodedSpeculationReasonClass = Class.forName("jdk.vm.ci.meta.EncodedSpeculationReason");

        Constructor<?> encodedSpeculationReasonConstructor = encodedSpeculationReasonClass.getDeclaredConstructor(Integer.TYPE, String.class, Object[].class);
        SpeculationLog.SpeculationReason encodedReason = (SpeculationLog.SpeculationReason) encodedSpeculationReasonConstructor.newInstance(groupId, "testGroup", context);

        final Method createSpeculationReason = GraalServices.class.getDeclaredMethod("createSpeculationReason", Integer.TYPE, String.class, Object[].class);
        createSpeculationReason.setAccessible(true);
        SpeculationLog.SpeculationReason encodedReason2 = (SpeculationLog.SpeculationReason) createSpeculationReason.invoke(null, groupId, "testGroup", context);

        Constructor<?> unencodedSpeculationReasonConstructor = UnencodedSpeculationReason.class.getDeclaredConstructor(Integer.TYPE, String.class, Object[].class);
        unencodedSpeculationReasonConstructor.setAccessible(true);
        SpeculationLog.SpeculationReason unencodedReason = (SpeculationLog.SpeculationReason) unencodedSpeculationReasonConstructor.newInstance(groupId, "testGroup", context);

        final Method encodeAsByteArray = IsolatedSpeculationLog.class.getDeclaredMethod("encodeAsByteArray", SpeculationLog.SpeculationReason.class);
        encodeAsByteArray.setAccessible(true);

        byte[] encodedResult = (byte[]) encodeAsByteArray.invoke(null, encodedReason);
        byte[] encodedResult2 = (byte[]) encodeAsByteArray.invoke(null, encodedReason2);
        byte[] unencodedResult = (byte[]) encodeAsByteArray.invoke(null, unencodedReason);
        Assert.assertArrayEquals(encodedResult, encodedResult2);
        Assert.assertArrayEquals(encodedResult, unencodedResult);
    }
}
