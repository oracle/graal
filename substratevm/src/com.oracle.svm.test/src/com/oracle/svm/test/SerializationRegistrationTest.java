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

    private static final byte[] serializedObject;
    private static final List<SerializableTestClass> list;

    static {
        list = new ArrayList<>();
        list.add(new SerializableTestClass("Dummy"));
        list.add(new SerializableTestClass("Test"));

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try {
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
            objectOutputStream.writeObject(list);
            objectOutputStream.flush();
        } catch (IOException e) {
            VMError.shouldNotReachHere(e);
        }

        serializedObject = byteArrayOutputStream.toByteArray();
    }

    @Test
    public void testSerializationRegistration() throws IOException, ClassNotFoundException {
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(serializedObject);
        ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream);
        Object deserializedObject = objectInputStream.readObject();
        Assert.assertEquals(list, deserializedObject);
    }
}

class SerializationRegistrationTestFeature implements Feature {
    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        RuntimeSerialization.register(ArrayList.class, SerializationRegistrationTest.SerializableTestClass.class);
    }
}
