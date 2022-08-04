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
package com.oracle.truffle.sl.operations;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;

import com.oracle.truffle.api.operation.OperationConfig;
import com.oracle.truffle.api.operation.OperationNodes;
import com.oracle.truffle.api.operation.serialization.OperationDeserializationCallback;
import com.oracle.truffle.api.operation.serialization.OperationSerializationCallback;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.sl.SLLanguage;
import com.oracle.truffle.sl.runtime.SLBigNumber;
import com.oracle.truffle.sl.runtime.SLNull;

public class SLOperationSerialization {

    private static final byte CODE_SL_NULL = 0;
    private static final byte CODE_STRING = 1;
    private static final byte CODE_LONG = 2;
    private static final byte CODE_SOURCE = 3;
    private static final byte CODE_CLASS = 4;
    private static final byte CODE_BIG_INT = 5;

    public static byte[] serializeNodes(OperationNodes nodes) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        DataOutputStream outputStream = new DataOutputStream(byteArrayOutputStream);

        nodes.serialize(OperationConfig.COMPLETE, outputStream, new OperationSerializationCallback() {
            public void serialize(OperationSerializationCallback.Context context, DataOutputStream buffer, Object object) throws IOException {
                if (object instanceof SLNull) {
                    buffer.writeByte(CODE_SL_NULL);
                } else if (object instanceof TruffleString) {
                    TruffleString str = (TruffleString) object;
                    buffer.writeByte(CODE_STRING);
                    writeByteArray(buffer, str.getInternalByteArrayUncached(SLLanguage.STRING_ENCODING).getArray());
                } else if (object instanceof Long) {
                    buffer.writeByte(CODE_LONG);
                    buffer.writeLong((long) object);
                } else if (object instanceof SLBigNumber) {
                    SLBigNumber num = (SLBigNumber) object;
                    buffer.writeByte(CODE_BIG_INT);
                    writeByteArray(buffer, num.getValue().toByteArray());
                } else if (object instanceof Source) {
                    Source s = (Source) object;
                    buffer.writeByte(CODE_SOURCE);
                    writeByteArray(buffer, s.getName().getBytes());
                } else if (object instanceof Class<?>) {
                    buffer.writeByte(CODE_CLASS);
                    writeByteArray(buffer, ((Class<?>) object).getName().getBytes());
                } else {
                    throw new UnsupportedOperationException("unsupported constant: " + object.getClass().getSimpleName() + " " + object);
                }
            }
        });

        return byteArrayOutputStream.toByteArray();
    }

    private static byte[] readByteArray(ByteBuffer buffer) {
        int len = buffer.getInt();
        byte[] dest = new byte[len];
        buffer.get(dest);
        return dest;
    }

    private static void writeByteArray(DataOutputStream buffer, byte[] data) throws IOException {
        buffer.writeInt(data.length);
        buffer.write(data);
    }

    public static OperationNodes deserializeNodes(byte[] inputData) throws IOException {
        ByteBuffer buf = ByteBuffer.wrap(inputData);

        return SLOperationsBuilder.deserialize(OperationConfig.DEFAULT, buf, new OperationDeserializationCallback() {
            public Object deserialize(OperationDeserializationCallback.Context context, ByteBuffer buffer) throws IOException {
                byte tag;
                switch (tag = buffer.get()) {
                    case CODE_SL_NULL:
                        return SLNull.SINGLETON;
                    case CODE_STRING: {
                        return TruffleString.fromByteArrayUncached(readByteArray(buffer), SLLanguage.STRING_ENCODING);
                    }
                    case CODE_LONG:
                        return buffer.getLong();
                    case CODE_BIG_INT: {
                        return new SLBigNumber(new BigInteger(readByteArray(buffer)));
                    }
                    case CODE_SOURCE: {
                        String name = new String(readByteArray(buffer));
                        return Source.newBuilder(SLLanguage.ID, "", name).build();
                    }
                    case CODE_CLASS: {
                        String name = new String(readByteArray(buffer));
                        try {
                            return Class.forName(name);
                        } catch (ClassNotFoundException ex) {
                            throw new UnsupportedOperationException("could not load class: " + name);
                        }
                    }
                    default:
                        throw new UnsupportedOperationException("unsupported tag: " + tag);
                }
            }
        });
    }
}
