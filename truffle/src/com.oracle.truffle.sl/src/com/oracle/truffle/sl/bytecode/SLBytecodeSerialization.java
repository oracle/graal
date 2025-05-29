/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.sl.bytecode;

import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.function.Supplier;

import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.serialization.SerializationUtils;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.sl.SLLanguage;
import com.oracle.truffle.sl.runtime.SLBigInteger;
import com.oracle.truffle.sl.runtime.SLNull;

public final class SLBytecodeSerialization {

    private static final byte CODE_SL_NULL = 0;
    private static final byte CODE_STRING = 1;
    private static final byte CODE_LONG = 2;
    private static final byte CODE_SOURCE = 3;
    private static final byte CODE_BIG_INT = 5;
    private static final byte CODE_BOOLEAN_TRUE = 6;
    private static final byte CODE_BOOLEAN_FALSE = 7;

    private SLBytecodeSerialization() {
        // no instances
    }

    public static byte[] serializeNodes(BytecodeParser<SLBytecodeRootNodeGen.Builder> parser) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        DataOutputStream outputStream = new DataOutputStream(byteArrayOutputStream);

        SLBytecodeRootNodeGen.serialize(outputStream, (context, buffer, object) -> {
            if (object instanceof SLNull) {
                buffer.writeByte(CODE_SL_NULL);
            } else if (object instanceof TruffleString) {
                TruffleString str = (TruffleString) object;
                buffer.writeByte(CODE_STRING);
                writeString(buffer, str);
            } else if (object instanceof Long) {
                buffer.writeByte(CODE_LONG);
                buffer.writeLong((long) object);
            } else if (object instanceof Boolean) {
                buffer.writeByte(((boolean) object) ? CODE_BOOLEAN_TRUE : CODE_BOOLEAN_FALSE);
            } else if (object instanceof SLBigInteger) {
                SLBigInteger num = (SLBigInteger) object;
                buffer.writeByte(CODE_BIG_INT);
                writeByteArray(buffer, num.getValue().toByteArray());
            } else if (object instanceof Source) {
                Source s = (Source) object;
                buffer.writeByte(CODE_SOURCE);
                writeByteArray(buffer, s.getName().getBytes());
            } else {
                throw new UnsupportedOperationException("unsupported constant: " + object.getClass().getSimpleName() + " " + object);
            }
        }, parser);

        return byteArrayOutputStream.toByteArray();
    }

    static void writeString(DataOutput buffer, TruffleString str) throws IOException {
        writeByteArray(buffer, str.getInternalByteArrayUncached(SLLanguage.STRING_ENCODING).getArray());
    }

    private static byte[] readByteArray(DataInput buffer) throws IOException {
        int len = buffer.readInt();
        byte[] dest = new byte[len];
        buffer.readFully(dest);
        return dest;
    }

    private static void writeByteArray(DataOutput buffer, byte[] data) throws IOException {
        buffer.writeInt(data.length);
        buffer.write(data);
    }

    public static BytecodeRootNodes<SLBytecodeRootNode> deserializeNodes(SLLanguage language, byte[] inputData) throws IOException {
        Supplier<DataInput> input = () -> SerializationUtils.createDataInput(ByteBuffer.wrap(inputData));
        return SLBytecodeRootNodeGen.deserialize(language, BytecodeConfig.DEFAULT, input, (context, buffer) -> {
            byte tag;
            switch (tag = buffer.readByte()) {
                case CODE_SL_NULL:
                    return SLNull.SINGLETON;
                case CODE_STRING:
                    return readString(buffer);
                case CODE_LONG:
                    return buffer.readLong();
                case CODE_BOOLEAN_TRUE:
                    return Boolean.TRUE;
                case CODE_BOOLEAN_FALSE:
                    return Boolean.FALSE;
                case CODE_BIG_INT:
                    return new SLBigInteger(new BigInteger(readByteArray(buffer)));
                case CODE_SOURCE: {
                    String name = new String(readByteArray(buffer));
                    return Source.newBuilder(SLLanguage.ID, "", name).build();
                }
                default:
                    throw new UnsupportedOperationException("unsupported tag: " + tag);
            }
        });
    }

    static TruffleString readString(DataInput buffer) throws IOException {
        return TruffleString.fromByteArrayUncached(readByteArray(buffer), SLLanguage.STRING_ENCODING);
    }
}
