/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.operation.test;

import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.Supplier;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.operation.OperationConfig;
import com.oracle.truffle.api.operation.OperationNodes;
import com.oracle.truffle.api.operation.OperationParser;
import com.oracle.truffle.api.operation.serialization.OperationDeserializer;
import com.oracle.truffle.api.operation.serialization.OperationSerializer;
import com.oracle.truffle.api.operation.serialization.SerializationUtils;

@RunWith(Parameterized.class)
public class TestOperationsSerializationTest {

    @Parameters(name = "{0}")
    public static List<Class<? extends TestOperations>> getInterpreterClasses() {
        return TestOperationsCommon.allInterpreters();
    }

    @Parameter(0) public Class<? extends TestOperations> interpreterClass;

    @Test
    public void testSerialization() {
        byte[] byteArray = createByteArray();
        TestOperations root = deserialize(byteArray);

        Assert.assertEquals(3L, root.getCallTarget().call());
    }

    @SuppressWarnings("unchecked")
    private <T extends TestOperations> OperationNodes<T> invokeDeserialize(TruffleLanguage<?> language, OperationConfig config, Supplier<DataInput> input, OperationDeserializer callback) {
        try {
            Method deserialize = interpreterClass.getMethod("deserialize", TruffleLanguage.class, OperationConfig.class, Supplier.class, OperationDeserializer.class);
            return (OperationNodes<T>) deserialize.invoke(null, language, config, input, callback);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @SuppressWarnings("unchecked")
    private void invokeSerialize(OperationConfig config, DataOutput buffer, OperationSerializer callback, OperationParser<TestOperationsBuilder> parser) {
        try {
            Method serialize = interpreterClass.getMethod("serialize", OperationConfig.class, DataOutput.class, OperationSerializer.class, OperationParser.class);
            serialize.invoke(null, config, buffer, callback, parser);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private TestOperations deserialize(byte[] byteArray) {
        Supplier<DataInput> input = () -> SerializationUtils.createDataInput(ByteBuffer.wrap(byteArray));
        OperationNodes<TestOperations> nodes = invokeDeserialize(null, OperationConfig.DEFAULT, input,
                        (context, buffer) -> {
                            switch (buffer.readByte()) {
                                case 0:
                                    return buffer.readLong();
                                case 1:
                                    return buffer.readUTF();
                                case 2:
                                    return null;
                                default:
                                    throw new AssertionError();
                            }
                        });

        return nodes.getNodes().get(0);
    }

    private byte[] createByteArray() {

        boolean[] haveConsts = new boolean[2];

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        invokeSerialize(OperationConfig.DEFAULT, new DataOutputStream(output),
                        (context, buffer, object) -> {
                            if (object instanceof Long) {
                                buffer.writeByte(0);
                                haveConsts[(int) (long) object - 1] = true;
                                buffer.writeLong((long) object);
                            } else if (object instanceof String) {
                                buffer.writeByte(1);
                                buffer.writeUTF((String) object);
                            } else if (object == null) {
                                buffer.writeByte(2);
                            } else {
                                assert false;
                            }
                        }, b -> {
                            b.beginRoot(null);

                            b.beginReturn();
                            b.beginAddOperation();
                            b.emitLoadConstant(1L);
                            b.emitLoadConstant(2L);
                            b.endAddOperation();
                            b.endReturn();

                            b.endRoot();
                        });

        Assert.assertArrayEquals(new boolean[]{true, true}, haveConsts);

        byte[] byteArray = output.toByteArray();
        return byteArray;
    }
}
