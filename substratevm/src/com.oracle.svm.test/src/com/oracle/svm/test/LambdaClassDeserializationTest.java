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

import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.function.Function;

// By declaring and serializing lambda in one class and deserializing it in another, we can simulate situation where program only
// deserializes lambda class
public class LambdaClassDeserializationTest {

    private static final class SerializeLambda {
        public static Function<Integer, String> createLambda() {
            @SuppressWarnings("unchecked")
            Function<Integer, String> lambda = (Function<Integer, String> & Serializable) (x) -> "Value of parameter is " + x;
            return lambda;
        }

        public static void serialize(ByteArrayOutputStream byteArrayOutputStream, Serializable serializableObject) throws IOException {
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
            objectOutputStream.writeObject(serializableObject);
            objectOutputStream.close();
        }
    }

    private static final class DeserializeLambda {
        public static Object deserialize(ByteArrayOutputStream byteArrayOutputStream) throws IOException, ClassNotFoundException {
            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(byteArrayOutputStream.toByteArray());
            ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream);
            return objectInputStream.readObject();
        }
    }

    @Test
    public void testLambdaLambdaDeserialization() throws Exception {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        int n = 10;

        Function<Integer, String> lambda = SerializeLambda.createLambda();
        String originalLambdaString = lambda.apply(n);

        SerializeLambda.serialize(byteArrayOutputStream, (Serializable) lambda);

        @SuppressWarnings("unchecked")
        Function<Integer, String> deserializedFn = (Function<Integer, String>) DeserializeLambda.deserialize(byteArrayOutputStream);

        String deserializedLambdaString = deserializedFn.apply(n);

        Assert.assertEquals(originalLambdaString, deserializedLambdaString);
    }
}
