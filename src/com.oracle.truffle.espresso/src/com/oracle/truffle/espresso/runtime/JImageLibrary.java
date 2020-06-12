/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package com.oracle.truffle.espresso.runtime;

import static com.oracle.truffle.espresso.jni.NativeLibrary.lookupAndBind;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.espresso.impl.ContextAccess;
import com.oracle.truffle.espresso.jni.NativeEnv;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;

@SuppressWarnings("unused")
class JImageLibrary extends NativeEnv implements ContextAccess {
    private static final String LIBJIMAGE_NAME = "jimage";
    private static final String OPEN = "JIMAGE_Open";
    private static final String CLOSE = "JIMAGE_Close";
    private static final String PACKAGE_TO_MOODULE = "JIMAGE_PackageToModule";
    private static final String FIND_RESOURCE = "JIMAGE_FindResource";
    private static final String GET_RESOURCE = "JIMAGE_GetResource";
    private static final String RESOURCE_ITERATOR = "JIMAGE_ResourceIterator";
    private static final String RESOURCE_PATH = "JIMAGE_ResourcePath";

    private static final String OPEN_SIGNATURE = "(pointer, pointer): pointer";
    private static final String CLOSE_SIGNATURE = "(pointer): void";
    private static final String PACKAGE_TO_MODULE_SIGNATURE = "(pointer, pointer): pointer";
    private static final String FIND_RESOURCE_SIGNATURE = "(pointer, pointer, pointer, pointer, pointer): sint64";
    private static final String GET_RESOURCE_SIGNATURE = "(pointer, sint64, pointer, sint64): sint64";
    private static final String RESOURCE_ITERATOR_SIGNATURE = "(pointer, pointer, pointer, pointer, pointer, pointer, pointer, pointer): pointer";
    private static final String RESOURCE_PATH_SIGNATURE = "(pointer, sint64, pointer, sint64): sint8";

    // Library pointer
    private final TruffleObject jimageLibrary;

    private static CharsetEncoder encoder = StandardCharsets.UTF_8.newEncoder();

    // Function pointers

    // JImageFile* JIMAGE_Open(const char *name, jint* error);
    private final TruffleObject open;
    // void JIMAGE_Close(JImageFile* jimage)
    private final TruffleObject close;
    // const char *
    // JIMAGE_PackageToModule(JImageFile* jimage, const char* package_name);
    private final TruffleObject packageToModule;
    // JImageLocationRef JIMAGE_FindResource(JImageFile* jimage,
    // const char* module_name, const char* version, const char* name,
    // jlong* size);
    private final TruffleObject findResource;
    // jlong JIMAGE_GetResource(JImageFile* jimage, JImageLocationRef location,
    // char* buffer, jlong size);
    private final TruffleObject getResource;

    // bool (*JImageResourceVisitor_t)(JImageFile* jimage,
    // const char* module_name, const char* version, const char* package,
    // const char* name, const char* extension, void* arg);
    private final TruffleObject resourceIterator;
    // bool JIMAGE_ResourcePath(JImageFile* image, JImageLocationRef locationRef,
    // char* path, size_t max);
    private final TruffleObject resourcePath;

    private final EspressoContext context;

    JImageLibrary(EspressoContext context) {
        this.context = context;
        try {
            EspressoProperties props = getContext().getVmProperties();

            // Load guest's libjimage.
            jimageLibrary = loadLibraryInternal(props.bootLibraryPath(), LIBJIMAGE_NAME);

            open = lookupAndBind(jimageLibrary, OPEN, OPEN_SIGNATURE);
            close = lookupAndBind(jimageLibrary, CLOSE, CLOSE_SIGNATURE);
            packageToModule = lookupAndBind(jimageLibrary, PACKAGE_TO_MOODULE, PACKAGE_TO_MODULE_SIGNATURE);
            findResource = lookupAndBind(jimageLibrary, FIND_RESOURCE, FIND_RESOURCE_SIGNATURE);
            getResource = lookupAndBind(jimageLibrary, GET_RESOURCE, GET_RESOURCE_SIGNATURE);
            resourceIterator = lookupAndBind(jimageLibrary, RESOURCE_ITERATOR, RESOURCE_ITERATOR_SIGNATURE);
            resourcePath = lookupAndBind(jimageLibrary, RESOURCE_PATH, RESOURCE_PATH_SIGNATURE);
        } catch (UnknownIdentifierException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    public TruffleObject init(String name) {
        ByteBuffer error = allocateDirect(1, JavaKind.Int);
        TruffleObject jimage = (TruffleObject) execute(open, getNativeString(name), byteBufferPointer(error));
        EspressoError.guarantee(!InteropLibrary.getFactory().getUncached().isNull(jimage), "Failed to load modules image");
        return jimage;
    }

    public void close(TruffleObject jimage) {
        execute(close, jimage);
    }

    public byte[] getClassBytes(TruffleObject jimage, String moduleName, String name) {
        // Prepare the call
        ByteBuffer sizeBuffer = allocateDirect(1, JavaKind.Long);
        TruffleObject sizePtr = byteBufferPointer(sizeBuffer);
        TruffleObject moduleNamePtr = getNativeString(moduleName);
        TruffleObject versionPtr = getNativeString("11.0");
        TruffleObject namePtr = getNativeString(name);

        // Do the call
        long location = (long) execute(findResource, jimage, moduleNamePtr, versionPtr, namePtr, sizePtr);
        if (location == 0) {
            // Not found.
            return null;
        }

        // Extract the result
        long capacity = sizeBuffer.getLong(0);
        ByteBuffer bytes = allocateDirect((int) capacity);
        TruffleObject bytesPtr = byteBufferPointer(bytes);
        execute(getResource, jimage, location, bytesPtr, capacity);
        byte[] result = new byte[(int) capacity];
        bytes.get(result);
        return result;
    }

    public String packageToModule(TruffleObject jimage, String packageName) {
        return interopPointerToString((TruffleObject) execute(packageToModule, jimage, getNativeString(packageName)));
    }

    private static Object execute(Object target, Object... args) {
        try {
            return InteropLibrary.getFactory().getUncached().execute(target, args);
        } catch (UnsupportedTypeException | UnsupportedMessageException | ArityException e) {
            throw EspressoError.shouldNotReachHere();
        }
    }

    private static TruffleObject getNativeString(String name) {
        int length = ((int) (name.length() * encoder.averageBytesPerChar())) + 1;
        for (;;) {
            // Be super safe with the size of the buffer.
            ByteBuffer bb = allocateDirect(length);
            encoder.reset();
            encoder.encode(CharBuffer.wrap(name), bb, false);
            if (bb.position() < bb.capacity()) {
                // We have at least one byte of leeway: null-terminate the string.
                bb.put((byte) 0);
                return byteBufferPointer(bb);
            }
            // Buffer was not big enough, retry with a bigger one.
            length = 1;
            // TODO(garcia): introduce some kind of limitation on the size of the encoded string ?
        }
    }

    @Override
    public EspressoContext getContext() {
        return context;
    }

    @CompilerDirectives.TruffleBoundary
    private static ByteBuffer allocateDirect(int capacity, JavaKind kind) {
        return allocateDirect(Math.multiplyExact(capacity, kind.getByteCount()));
    }

    @CompilerDirectives.TruffleBoundary
    private static ByteBuffer allocateDirect(int capacity) {
        return ByteBuffer.allocateDirect(capacity).order(ByteOrder.nativeOrder());
    }
}
