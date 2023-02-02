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
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.Supplier;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.svm.graal.isolated.IsolatedSpeculationLog;

import jdk.vm.ci.meta.SpeculationLog;

public class IsolatedSpeculationLogEncodingTest {
    private static final Class<?> SRE_CLASS;
    private static final String SRE_CLASS_NAME = "jdk.vm.ci.meta.SpeculationLog$SpeculationReasonEncoding";
    private static final Constructor<?> ISOLATED_SRE_CONSTRUCTOR;
    private static final Class<?> ISOLATED_SRE_CLASS;
    private static final String ISOLATED_SRE_CLASS_NAME = "com.oracle.svm.graal.isolated.IsolatedSpeculationReasonEncoding";

    static {
        Class<?> sreClass = null;
        Class<?> isolatedSreClass = null;
        try {
            sreClass = Class.forName(SRE_CLASS_NAME);
            isolatedSreClass = Class.forName(ISOLATED_SRE_CLASS_NAME);
        } catch (ClassNotFoundException e) {
            Assert.fail("failed to get classes");
        }
        SRE_CLASS = sreClass;
        Assert.assertNotNull(SRE_CLASS);
        ISOLATED_SRE_CLASS = isolatedSreClass;
        Assert.assertNotNull(ISOLATED_SRE_CLASS);

        Constructor<?> isolatedSreConstructor = null;
        try {
            isolatedSreConstructor = ISOLATED_SRE_CLASS.getDeclaredConstructor();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
            Assert.fail("Failed to get constructors");
        }
        ISOLATED_SRE_CONSTRUCTOR = isolatedSreConstructor;
        ISOLATED_SRE_CONSTRUCTOR.setAccessible(true);
        Assert.assertNotNull(ISOLATED_SRE_CONSTRUCTOR);
    }

    @Test
    public void testEncodeAsByteArray() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException, ClassNotFoundException, InstantiationException {
        final int groupId = 1234;
        final Object[] context = {'a', null, "Hello World!", Byte.MAX_VALUE, Short.MAX_VALUE, Long.MAX_VALUE, Integer.MAX_VALUE, Double.MAX_VALUE, Float.MAX_VALUE};
        final Class<?> encodedSpeculationReasonClass = Class.forName("jdk.vm.ci.meta.EncodedSpeculationReason");
        Constructor<?> encodedSpeculationReasonConstructor = encodedSpeculationReasonClass.getDeclaredConstructor(Integer.TYPE, String.class, Object[].class);
        SpeculationLog.SpeculationReason reason = (SpeculationLog.SpeculationReason) encodedSpeculationReasonConstructor.newInstance(groupId, "testGroup", context);

        final Method encodeAsByteArray = IsolatedSpeculationLog.class.getDeclaredMethod("encodeAsByteArray", SpeculationLog.SpeculationReason.class);
        encodeAsByteArray.setAccessible(true);
        byte[] graalResult = (byte[]) encodeAsByteArray.invoke(null, reason);

        final Method encode = encodedSpeculationReasonClass.getDeclaredMethod("encode", Supplier.class);
        encode.setAccessible(true);
        final SpeculationReasonEncodingSupplier sreSupplier = new SpeculationReasonEncodingSupplier();
        encode.invoke(reason, sreSupplier);
        final Method getByteArray = ISOLATED_SRE_CLASS.getDeclaredMethod("getByteArray");
        getByteArray.setAccessible(true);
        byte[] jdkResult = (byte[]) getByteArray.invoke(sreSupplier.encoding);
        Assert.assertArrayEquals(jdkResult, graalResult);
    }

    static class SpeculationReasonInvocationHandler implements InvocationHandler {
        private final Object receiver;

        SpeculationReasonInvocationHandler(Object receiver) {
            this.receiver = receiver;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            /* This is known to fail when ran with native-unittest */
            final Method declaredMethod = ISOLATED_SRE_CLASS.getDeclaredMethod(method.getName(), method.getParameterTypes());
            declaredMethod.setAccessible(true);
            return declaredMethod.invoke(receiver, args);
        }
    }

    static class SpeculationReasonEncodingSupplier implements Supplier<Object> {
        Object encoding;

        @Override
        public Object get() {
            try {
                encoding = ISOLATED_SRE_CONSTRUCTOR.newInstance();
                return Proxy.newProxyInstance(SRE_CLASS.getClassLoader(), new Class<?>[]{SRE_CLASS},
                                new SpeculationReasonInvocationHandler(encoding));
            } catch (NullPointerException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
                Assert.fail("Failed to instantiate " + SRE_CLASS_NAME + " instance through proxy");
            }
            return null;
        }
    }
}
