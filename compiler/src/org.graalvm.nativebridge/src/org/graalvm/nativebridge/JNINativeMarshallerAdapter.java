/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativebridge;

import org.graalvm.jniutils.JNI.JByteArray;
import org.graalvm.jniutils.JNI.JNIEnv;
import org.graalvm.jniutils.JNI.JObject;
import org.graalvm.jniutils.JNIUtil;
import org.graalvm.nativebridge.BinaryOutput.CCharPointerBinaryOutput;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.WordFactory;

import java.io.IOException;

final class JNINativeMarshallerAdapter<T> implements JNINativeMarshaller<T> {

    private static final int BUFFER_SIZE = 512;

    private final BinaryMarshaller<T> binaryMarshaller;

    JNINativeMarshallerAdapter(BinaryMarshaller<T> binaryMarshaller) {
        this.binaryMarshaller = binaryMarshaller;
    }

    @Override
    public JObject marshall(JNIEnv env, T object) {
        if (object == null) {
            return WordFactory.nullPointer();
        }
        int bufSize = BUFFER_SIZE;
        CCharPointer buffer = StackValue.get(bufSize);
        try (CCharPointerBinaryOutput out = BinaryOutput.create(buffer, bufSize, false)) {
            binaryMarshaller.write(out, object);
            int len = out.getPosition();
            JByteArray binaryData = JNIUtil.NewByteArray(env, len);
            JNIUtil.SetByteArrayRegion(env, binaryData, 0, len, out.getAddress());
            return binaryData;
        } catch (IOException e) {
            throw new AssertionError(e.getMessage(), e);
        }
    }

    @Override
    public T unmarshall(JNIEnv env, JObject jObject) {
        if (jObject.isNull()) {
            return null;
        }
        int bufSize = BUFFER_SIZE;
        CCharPointer staticBuffer = StackValue.get(bufSize);
        int len = JNIUtil.GetArrayLength(env, (JByteArray) jObject);
        CCharPointer useBuffer;
        if (len < bufSize) {
            useBuffer = staticBuffer;
        } else {
            useBuffer = UnmanagedMemory.malloc(len);
        }
        try {
            JNIUtil.GetByteArrayRegion(env, ((JByteArray) jObject), 0, len, useBuffer);
            try (BinaryInput in = BinaryInput.create(useBuffer, len)) {
                return binaryMarshaller.read(in);
            } catch (IOException e) {
                throw new AssertionError(e.getMessage(), e);
            }
        } finally {
            if (useBuffer != staticBuffer) {
                UnmanagedMemory.free(useBuffer);
            }
        }
    }
}
