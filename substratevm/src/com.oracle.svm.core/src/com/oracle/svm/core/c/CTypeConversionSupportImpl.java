/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.c;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion.CCharPointerHolder;
import org.graalvm.nativeimage.impl.CTypeConversionSupport;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.jdk.Target_java_nio_DirectByteBuffer;

@AutomaticallyRegisteredImageSingleton(CTypeConversionSupport.class)
class CTypeConversionSupportImpl implements CTypeConversionSupport {

    static final CCharPointerHolder NULL_HOLDER = new CCharPointerHolder() {
        @Override
        public CCharPointer get() {
            return WordFactory.nullPointer();
        }

        @Override
        public void close() {
            /* Nothing to do. */
        }
    };

    @Override
    public String toJavaString(CCharPointer cString) {
        if (cString.isNull()) {
            return null;
        } else {
            return toJavaStringUnchecked(cString, SubstrateUtil.strlen(cString));
        }
    }

    @Override
    public String toJavaString(CCharPointer cString, UnsignedWord length) {
        if (cString.isNull()) {
            return null;
        } else {
            return toJavaStringUnchecked(cString, length);
        }
    }

    @Override
    public String toJavaString(CCharPointer cString, UnsignedWord length, Charset charset) {
        if (cString.isNull()) {
            return null;
        } else {
            return toJavaStringWithCharset(cString, length, charset);
        }
    }

    @Override
    public String utf8ToJavaString(CCharPointer utf8String) {
        if (utf8String.isNull()) {
            return null;
        } else {
            // UTF-8 does not break zero-terminated strings.
            return toJavaStringWithCharset(utf8String, SubstrateUtil.strlen(utf8String), StandardCharsets.UTF_8);
        }
    }

    private static String toJavaStringWithCharset(CCharPointer cString, UnsignedWord length, Charset charset) {
        byte[] bytes = new byte[(int) length.rawValue()];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = ((Pointer) cString).readByte(i);
        }
        return new String(bytes, charset);
    }

    private static String toJavaStringUnchecked(CCharPointer cString, UnsignedWord length) {
        byte[] bytes = new byte[(int) length.rawValue()];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = ((Pointer) cString).readByte(i);
        }
        return new String(bytes);
    }

    @Override
    public UnsignedWord toCString(CharSequence javaString, CCharPointer buffer, UnsignedWord bufferSize) {
        return toCString(javaString, Charset.defaultCharset(), buffer, bufferSize);
    }

    @Override
    public UnsignedWord toCString(CharSequence javaString, Charset charset, CCharPointer buffer, UnsignedWord bufferSize) {
        if (javaString == null) {
            throw new IllegalArgumentException("Provided Java string is null");
        }

        byte[] baseString = javaString.toString().getBytes(charset);
        long capacity = bufferSize.rawValue();

        if (buffer.isNull()) {
            if (capacity != 0) {
                throw new IllegalArgumentException("Non zero buffer size passed along with nullptr");
            }
            return WordFactory.unsigned(baseString.length);

        } else if (capacity < baseString.length + 1) {
            throw new IllegalArgumentException("Provided buffer is too small to hold 0 terminated java string.");
        }

        for (int i = 0; i < baseString.length; i++) {
            buffer.write(i, baseString[i]);
        }

        // write null terminator at end
        buffer.write(baseString.length, (byte) 0);

        return WordFactory.unsigned(baseString.length);
    }

    @Override
    public CCharPointerHolder toCString(CharSequence javaString) {
        if (javaString == null) {
            return NULL_HOLDER;
        }
        return new CCharPointerHolderImpl(javaString);
    }

    @Override
    public CCharPointerHolder toCBytes(byte[] bytes) {
        if (bytes == null) {
            return NULL_HOLDER;
        }
        return new CCharPointerHolderImpl(bytes);
    }

    @Override
    public ByteBuffer asByteBuffer(PointerBase address, int size) {
        ByteBuffer byteBuffer = SubstrateUtil.cast(new Target_java_nio_DirectByteBuffer(address.rawValue(), size), ByteBuffer.class);
        return byteBuffer.order(ConfigurationValues.getTarget().arch.getByteOrder());
    }
}

final class CCharPointerHolderImpl implements CCharPointerHolder {

    private final PinnedObject cstring;

    CCharPointerHolderImpl(CharSequence javaString) {
        byte[] bytes = javaString.toString().getBytes();
        /* Append the terminating 0. */
        bytes = Arrays.copyOf(bytes, bytes.length + 1);
        cstring = PinnedObject.create(bytes);
    }

    CCharPointerHolderImpl(byte[] bytes) {
        cstring = PinnedObject.create(bytes);
    }

    @Override
    public CCharPointer get() {
        return cstring.addressOfArrayElement(0);
    }

    @Override
    public void close() {
        cstring.close();
    }
}
