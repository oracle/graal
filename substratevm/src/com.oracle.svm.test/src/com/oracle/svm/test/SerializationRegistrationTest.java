/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.oracle.svm.core.util.VMError;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeSerialization;
import org.junit.Assert;
import org.junit.Test;

public class SerializationRegistrationTest {

    public static class SerializableTestClass implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String content;

        public SerializableTestClass(String content) {
            this.content = content;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            SerializableTestClass that = (SerializableTestClass) o;
            return Objects.equals(content, that.content);
        }

        @Override
        public int hashCode() {
            return Objects.hash(content);
        }
    }

    public static class AssociatedRegistrationTestClass implements Serializable {
        private static final long serialVersionUID = 8498517244399174914L;
        private int intVal;
        private long[] longVals;

        public AssociatedRegistrationTestClass(int intVal, long[] longVals) {
            this.intVal = intVal;
            this.longVals = longVals;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            AssociatedRegistrationTestClass that = (AssociatedRegistrationTestClass) o;
            if (intVal != that.intVal) {
                return false;
            }
            if (longVals.length != that.longVals.length) {
                return false;
            }
            for (int i = 0; i < longVals.length; i++) {
                if (longVals[i] != that.longVals[i]) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int hashCode() {
            return Objects.hash(intVal, longVals);
        }
    }

    private static final byte[] serializedObject;
    private static final List<SerializableTestClass> list;
    private static AssociatedRegistrationTestClass testClass;
    private static final byte[] serializedAssociatedRegistrationTestClass;

    static {
        list = new ArrayList<>();
        list.add(new SerializableTestClass("Dummy"));
        list.add(new SerializableTestClass("Test"));
        serializedObject = serializeObject(list);

        testClass = new AssociatedRegistrationTestClass(101, new long[]{1L, 2L, 123L});
        serializedAssociatedRegistrationTestClass = serializeObject(testClass);
    }

    private static byte[] serializeObject(Object obj) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try {
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
            objectOutputStream.writeObject(obj);
            objectOutputStream.flush();
        } catch (IOException e) {
            VMError.shouldNotReachHere(e);
        }
        return byteArrayOutputStream.toByteArray();
    }

    private static Object deSerialize(byte[] data) throws IOException, ClassNotFoundException {
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(data);
        ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream);
        Object deserializedObject = objectInputStream.readObject();
        return deserializedObject;
    }

    @Test
    public void testSerializationRegistration() throws IOException, ClassNotFoundException {
        Object deserializedObject = deSerialize(serializedObject);
        Assert.assertEquals(list, deserializedObject);
    }

    @Test
    public void testAssociatedRegistration() throws IOException, ClassNotFoundException {
        Object deserializedObject = deSerialize(serializedAssociatedRegistrationTestClass);
        Assert.assertEquals(testClass, deserializedObject);
    }
}

class SerializationRegistrationTestFeature implements Feature {
    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        RuntimeSerialization.register(ArrayList.class, SerializationRegistrationTest.SerializableTestClass.class);
        RuntimeSerialization.registerIncludingAssociatedClasses(SerializationRegistrationTest.AssociatedRegistrationTestClass.class);
    }
}
