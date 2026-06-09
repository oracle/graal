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
package com.oracle.svm.test.lambda;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;
import java.util.function.Function;

import org.junit.Test;

import com.oracle.svm.test.NativeImageBuildArgs;

@NativeImageBuildArgs({
                "-H:+UnlockExperimentalVMOptions",
                "-H:ConfigurationResourceRoots=com/oracle/svm/test/lambda/serializablemetadata",
                "-H:-UnlockExperimentalVMOptions"
})
public class SerializableLambdaReachabilityMetadataTest {
    @FunctionalInterface
    interface SerializableFunction extends Function<Integer, String>, Serializable {
    }

    static final class CapturedValue implements Serializable {
        @Serial private static final long serialVersionUID = 1L;

        private final String text;

        CapturedValue(String text) {
            this.text = text;
        }

        String text() {
            return text;
        }
    }

    static final class LambdaFactory {
        static Function<Integer, String> createLambda() {
            return (SerializableFunction) value -> "lambda:" + value;
        }

        static Function<Integer, String> createCapturedLambda(int offset, CapturedValue capturedValue) {
            return (SerializableFunction) value -> capturedValue.text() + ":" + (value + offset);
        }
    }

    @Test
    public void testSerializableLambdaReachabilityMetadata() throws Exception {
        int value = 42;
        Function<Integer, String> lambda = LambdaFactory.createLambda();
        String expected = lambda.apply(value);

        @SuppressWarnings("unchecked")
        Function<Integer, String> deserialized = (Function<Integer, String>) deserialize(serialize((Serializable) lambda));

        assertEquals(expected, deserialized.apply(value));
    }

    @Test
    public void testCapturedSerializableLambdaReachabilityMetadata() throws Exception {
        int value = 42;
        Function<Integer, String> lambda = LambdaFactory.createCapturedLambda(7, new CapturedValue("captured"));
        String expected = lambda.apply(value);

        @SuppressWarnings("unchecked")
        Function<Integer, String> deserialized = (Function<Integer, String>) deserialize(serialize((Serializable) lambda));

        assertEquals("captured:49", expected);
        assertEquals(expected, deserialized.apply(value));
    }

    private static byte[] serialize(Serializable object) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream)) {
            objectOutputStream.writeObject(object);
        }
        return byteArrayOutputStream.toByteArray();
    }

    private static Object deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return objectInputStream.readObject();
        }
    }
}
