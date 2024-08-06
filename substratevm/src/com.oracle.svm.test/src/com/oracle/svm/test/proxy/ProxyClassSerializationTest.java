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

package com.oracle.svm.test.proxy;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ProxyClassSerializationTest {

    private static void serialize(ByteArrayOutputStream byteArrayOutputStream, Object proxyObject) throws IOException {
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
        objectOutputStream.writeObject(proxyObject);
        objectOutputStream.close();
    }

    private static Object deserialize(ByteArrayOutputStream byteArrayOutputStream) throws IOException, ClassNotFoundException {
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(byteArrayOutputStream.toByteArray());
        ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream);
        return objectInputStream.readObject();
    }

    @Test
    public void testProxyClassSerialization() throws Exception {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        Object proxyObject = Proxy.newProxyInstance(Serializable.class.getClassLoader(),
                        new Class<?>[]{Serializable.class, Comparable.class}, new ProxyHandler());
        serialize(byteArrayOutputStream, proxyObject);

        Object deserializedProxy = deserialize(byteArrayOutputStream);

        assert compareArrays(proxyObject.getClass().getInterfaces(), deserializedProxy.getClass().getInterfaces());
    }

    private static boolean compareArrays(Class<?>[] array1, Class<?>[] array2) {
        int n = array1.length;
        int m = array2.length;

        if (n != m) {
            return false;
        }

        Set<Class<?>> classSet1 = new HashSet<>(List.of(array1));
        Set<Class<?>> classSet2 = new HashSet<>(List.of(array2));

        return classSet1.containsAll(classSet2);
    }
}

class ProxyHandler implements InvocationHandler, Serializable {
    private static final long serialVersionUID = 1L;

    ProxyHandler() {
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws InvocationTargetException, IllegalAccessException {
        return method.invoke(proxy, args);
    }
}
