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
import java.util.Arrays;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion.CCharPointerHolder;
import org.graalvm.nativeimage.impl.CTypeConversionSupport;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.config.ConfigurationValues;

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
        if (javaString == null || bufferSize.equal(0)) {
            return WordFactory.zero();
        }

        byte[] baseString = javaString.toString().getBytes(charset);

        /*
         * The array length is always an int, so the truncation of the buffer size to int can never
         * overflow.
         */
        int len = (int) Math.min(baseString.length, bufferSize.rawValue());

        for (int i = 0; i < len; i++) {
            buffer.write(i, baseString[i]);
        }

        return WordFactory.unsigned(len);
    }

    @Override
    public CCharPointerHolder toCString(CharSequence javaString) {
        if (javaString == null) {
            return NULL_HOLDER;
        }
        return new CCharPointerHolderImpl(javaString);
    }

    @TargetClass(className = "java.nio.DirectByteBuffer")
    @SuppressWarnings("unused")
    static final class Target_java_nio_DirectByteBuffer {
        @Alias
        Target_java_nio_DirectByteBuffer(long addr, int cap) {
        }
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

    @Override
    public CCharPointer get() {
        return cstring.addressOfArrayElement(0);
    }

    @Override
    public void close() {
        cstring.close();
    }
}

@AutomaticFeature
class CTypeConversionFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(CTypeConversionSupport.class, new CTypeConversionSupportImpl());
    }
}
