/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion.CCharPointerHolder;
import org.graalvm.nativeimage.c.type.CTypeConversion.CCharPointerPointerHolder;
import org.graalvm.nativeimage.impl.CTypeConversionSupport;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.handles.PrimitiveArrayView;
import com.oracle.svm.core.jdk.DirectByteBufferUtil;
import com.oracle.svm.core.traits.BuiltinTraits.RuntimeAccessOnly;
import com.oracle.svm.core.traits.BuiltinTraits.SingleLayer;
import com.oracle.svm.core.traits.SingletonLayeredInstallationKind.InitialLayerOnly;
import com.oracle.svm.core.traits.SingletonTraits;

import jdk.graal.compiler.word.Word;

@AutomaticallyRegisteredImageSingleton(CTypeConversionSupport.class)
@SingletonTraits(access = RuntimeAccessOnly.class, layeredCallbacks = SingleLayer.class, layeredInstallationKind = InitialLayerOnly.class)
class CTypeConversionSupportImpl implements CTypeConversionSupport {

    static final CCharPointerHolder NULL_HOLDER = new CCharPointerHolder() {
        @Override
        public CCharPointer get() {
            return Word.nullPointer();
        }

        @Override
        public void close() {
            /* Nothing to do. */
        }
    };

    static final CCharPointerPointerHolder NULL_POINTER_POINTER_HOLDER = new CCharPointerPointerHolder() {
        @Override
        public CCharPointerPointer get() {
            return Word.nullPointer();
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
            return Word.unsigned(baseString.length);

        } else if (capacity < baseString.length + 1) {
            throw new IllegalArgumentException("Provided buffer is too small to hold 0 terminated java string.");
        }

        for (int i = 0; i < baseString.length; i++) {
            buffer.write(i, baseString[i]);
        }

        // write null terminator at end
        buffer.write(baseString.length, (byte) 0);

        return Word.unsigned(baseString.length);
    }

    @Override
    public CCharPointerHolder toCString(CharSequence javaString) {
        if (javaString == null) {
            return NULL_HOLDER;
        }
        return new CCharPointerHolderImpl(javaString);
    }

    @Override
    public CCharPointerPointerHolder toCStrings(CharSequence[] javaStrings) {
        if (javaStrings == null) {
            return NULL_POINTER_POINTER_HOLDER;
        }
        return new CCharPointerPointerHolderImpl(javaStrings);
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
        ByteBuffer byteBuffer = DirectByteBufferUtil.allocate(address.rawValue(), size);
        return byteBuffer.order(ConfigurationValues.getTarget().arch.getByteOrder());
    }
}

final class CCharPointerHolderImpl implements CCharPointerHolder {

    private final PrimitiveArrayView cstring;

    CCharPointerHolderImpl(CharSequence javaString) {
        byte[] bytes = javaString.toString().getBytes();
        /* Append the terminating 0. */
        bytes = Arrays.copyOf(bytes, bytes.length + 1);
        cstring = PrimitiveArrayView.createForReading(bytes);
    }

    CCharPointerHolderImpl(byte[] bytes) {
        cstring = PrimitiveArrayView.createForReading(bytes);
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

final class CCharPointerPointerHolderImpl extends CCharPointerPointerHolder {

    private final CCharPointerHolder[] ccpHolderArray;
    private final PrimitiveArrayView refCCPArray;

    CCharPointerPointerHolderImpl(CharSequence[] csArray) {
        CTypeConversionSupport cTypeConversion = ImageSingletons.lookup(CTypeConversionSupport.class);
        /* An array to hold the pinned null-terminated C strings. */
        ccpHolderArray = new CCharPointerHolder[csArray.length + 1];
        /* An array to hold the &char[0] behind the corresponding C string. */
        final CCharPointer[] ccpArray = new CCharPointer[csArray.length + 1];
        for (int i = 0; i < csArray.length; i += 1) {
            /* Null-terminate and pin each of the CharSequences. */
            ccpHolderArray[i] = cTypeConversion.toCString(csArray[i]);
            /* Save the CCharPointer of each of the CharSequences. */
            ccpArray[i] = ccpHolderArray[i].get();
        }
        /* Null-terminate the CCharPointer[]. */
        ccpArray[csArray.length] = Word.nullPointer();
        /* Pin the CCharPointer[] so I can get the &ccpArray[0]. */
        refCCPArray = PrimitiveArrayView.createForReading(ccpArray);
    }

    @Override
    public CCharPointerPointer get() {
        return refCCPArray.addressOfArrayElement(0);
    }

    @Override
    public void close() {
        /* Close the pins on each of the pinned C strings. */
        for (int i = 0; i < ccpHolderArray.length - 1; i += 1) {
            ccpHolderArray[i].close();
        }
        /* Close the pin on the pinned CCharPointer[]. */
        refCCPArray.close();
    }
}
