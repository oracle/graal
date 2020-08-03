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
import static com.oracle.truffle.espresso.runtime.Classpath.JAVA_BASE;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.StandardCharsets;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.impl.ContextAccess;
import com.oracle.truffle.espresso.impl.PackageTable.PackageEntry;
import com.oracle.truffle.espresso.jni.NativeEnv;
import com.oracle.truffle.espresso.jni.RawBuffer;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;

@SuppressWarnings("unused")
class JImageLibrary extends NativeEnv implements ContextAccess {
    private static final String VERSION_STRING = "11.0";

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

    private final InteropLibrary uncached;

    // Library pointer
    private final TruffleObject jimageLibrary;

    // Buffers associated with caches are there to prevent GCing of the cached encoded strings.

    // Cache "java.base" native module name
    private final RawBuffer javaBaseBuffer;
    // Cache the version sting.
    private final RawBuffer versionBuffer;
    // Cache the empty string
    private final RawBuffer emptyStringBuffer;

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

            this.javaBaseBuffer = getNativeString(JAVA_BASE);
            this.versionBuffer = getNativeString(VERSION_STRING);
            this.emptyStringBuffer = getNativeString("");

            this.uncached = InteropLibrary.getFactory().getUncached();
        } catch (UnknownIdentifierException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    public TruffleObject open(String name) {
        ByteBuffer error = allocateDirect(1, JavaKind.Int);
        try (RawBuffer nameBuffer = getNativeString(name)) {
            return (TruffleObject) execute(open, nameBuffer.pointer(), byteBufferPointer(error));
        }
    }

    public void close(TruffleObject jimage) {
        execute(close, jimage);
    }

    public byte[] getClassBytes(TruffleObject jimage, String name) {
        // Prepare calls
        ByteBuffer sizeBuffer = allocateDirect(1, JavaKind.Long);
        TruffleObject sizePtr = byteBufferPointer(sizeBuffer);

        long location = findLocation(jimage, sizePtr, name);
        if (location == 0) {
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

    private long findLocation(TruffleObject jimage, TruffleObject sizePtr, String name) {
        try (RawBuffer nameBuffer = getNativeString(name)) {
            TruffleObject namePtr = nameBuffer.pointer();
            long location = (long) execute(findResource, jimage, emptyStringBuffer.pointer(), versionBuffer.pointer(), namePtr, sizePtr);
            if (location != 0) {
                // found.
                return location;
            }

            String pkg = packageFromName(name);
            if (pkg == null) {
                return 0;
            }

            if (!getContext().modulesInitialized()) {
                location = (long) execute(findResource, jimage, javaBaseBuffer.pointer(), versionBuffer.pointer(), namePtr, sizePtr);
                if (location != 0 || !getContext().metaInitialized()) {
                    // During meta initialization, we rely on the fact that we do not succeed in
                    // finding certain classes in java.base (/ex: sun/misc/Unsafe).
                    return location;
                }
                TruffleObject moduleName;
                try (RawBuffer pkgBuffer = getNativeString(pkg)) {
                    moduleName = (TruffleObject) execute(packageToModule, jimage, pkgBuffer.pointer());
                }
                if (uncached.isNull(moduleName)) {
                    return 0;
                }
                return (long) execute(findResource, jimage, moduleName, versionBuffer.pointer(), namePtr, sizePtr);
            } else {
                Symbol<Name> pkgSymbol = getNames().lookup(pkg);
                if (pkgSymbol == null) {
                    return 0;
                }
                PackageEntry pkgEntry = getRegistries().getBootClassRegistry().packages().lookup(pkgSymbol);
                if (pkgEntry == null) {
                    return 0;
                }
                Symbol<Name> moduleName = pkgEntry.module().getName();
                if (moduleName == Name.java_base) {
                    return (long) execute(findResource, jimage, javaBaseBuffer.pointer(), versionBuffer.pointer(), namePtr, sizePtr);
                } else {
                    String nameAsString = moduleName == null ? "" : moduleName.toString();
                    try (RawBuffer moduleNameBuffer = getNativeString(nameAsString)) {
                        return (long) execute(findResource, jimage, moduleNameBuffer.pointer(), versionBuffer.pointer(), namePtr, sizePtr);
                    }
                }
            }
        }
    }

    private String packageToModule(TruffleObject jimage, String pkg) {
        try (RawBuffer pkgBuffer = getNativeString(pkg)) {
            return interopPointerToString((TruffleObject) execute(packageToModule, jimage, pkgBuffer.pointer()));
        }
    }

    private static String packageFromName(String name) {
        int lastSlash = name.lastIndexOf('/');
        if (lastSlash == -1) {
            return null;
        }
        return name.substring(0, lastSlash);
    }

    private Object execute(Object target, Object... args) {
        try {
            return uncached.execute(target, args);
        } catch (UnsupportedTypeException | UnsupportedMessageException | ArityException e) {
            throw EspressoError.shouldNotReachHere();
        }
    }

    private static RawBuffer getNativeString(String name) {
        CharsetEncoder encoder = StandardCharsets.UTF_8.newEncoder();
        int length = ((int) (name.length() * encoder.averageBytesPerChar())) + 1;
        for (;;) {
            if (length <= 0) {
                throw EspressoError.shouldNotReachHere();
            }
            // Be super safe with the size of the buffer.
            ByteBuffer bb = allocateDirect(length);
            encoder.reset();
            CoderResult result = encoder.encode(CharBuffer.wrap(name), bb, true);

            if (result.isOverflow()) {
                // Not enough space in the buffer
                length <<= 1;
            } else if (result.isUnderflow()) {
                result = encoder.flush(bb);
                if (result.isUnderflow() && (bb.position() < bb.capacity())) {
                    // Encoder encoded entire string, and we have one byte of leeway.
                    bb.put((byte) 0);
                    return new RawBuffer(bb, byteBufferPointer(bb));
                }
                if (result.isOverflow() || result.isUnderflow()) {
                    length += 1;
                } else {
                    throw EspressoError.shouldNotReachHere();
                }
            } else {
                throw EspressoError.shouldNotReachHere();
            }
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
