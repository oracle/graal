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

import static com.oracle.truffle.espresso.runtime.Classpath.JAVA_BASE;

import java.nio.ByteBuffer;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.espresso.descriptors.ByteSequence;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.ffi.NativeSignature;
import com.oracle.truffle.espresso.ffi.NativeType;
import com.oracle.truffle.espresso.ffi.nfi.NativeUtils;
import com.oracle.truffle.espresso.impl.ContextAccessImpl;
import com.oracle.truffle.espresso.impl.PackageTable.PackageEntry;
import com.oracle.truffle.espresso.jni.RawBuffer;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;

@SuppressWarnings("unused")
final class JImageLibrary extends ContextAccessImpl {
    private static final String VERSION_STRING = "11.0";

    private static final String LIBJIMAGE_NAME = "jimage";
    private static final String OPEN = "JIMAGE_Open";
    private static final String CLOSE = "JIMAGE_Close";
    private static final String PACKAGE_TO_MOODULE = "JIMAGE_PackageToModule";
    private static final String FIND_RESOURCE = "JIMAGE_FindResource";
    private static final String GET_RESOURCE = "JIMAGE_GetResource";

    private static final NativeSignature OPEN_SIGNATURE = NativeSignature.create(NativeType.POINTER, NativeType.POINTER, NativeType.POINTER);
    private static final NativeSignature CLOSE_SIGNATURE = NativeSignature.create(NativeType.VOID, NativeType.POINTER);
    private static final NativeSignature PACKAGE_TO_MODULE_SIGNATURE = NativeSignature.create(NativeType.POINTER, NativeType.POINTER, NativeType.POINTER);
    private static final NativeSignature FIND_RESOURCE_SIGNATURE = NativeSignature.create(NativeType.LONG, NativeType.POINTER, NativeType.POINTER, NativeType.POINTER, NativeType.POINTER,
                    NativeType.POINTER);
    private static final NativeSignature GET_RESOURCE_SIGNATURE = NativeSignature.create(NativeType.LONG, NativeType.POINTER, NativeType.LONG, NativeType.POINTER, NativeType.LONG);
    private static final NativeSignature RESOURCE_ITERATOR_SIGNATURE = NativeSignature.create(NativeType.POINTER, NativeType.POINTER, NativeType.POINTER, NativeType.POINTER, NativeType.POINTER,
                    NativeType.POINTER, NativeType.POINTER, NativeType.POINTER, NativeType.POINTER);
    private static final NativeSignature RESOURCE_PATH_SIGNATURE = NativeSignature.create(NativeType.BOOLEAN, NativeType.POINTER, NativeType.LONG, NativeType.POINTER, NativeType.LONG);

    private final InteropLibrary uncached;

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

    JImageLibrary(EspressoContext context) {
        super(context);
        EspressoProperties props = getContext().getVmProperties();

        // Load guest's libjimage.
        // Library pointer
        TruffleObject jimageLibrary = getNativeAccess().loadLibrary(props.bootLibraryPath(), LIBJIMAGE_NAME, true);

        open = getNativeAccess().lookupAndBindSymbol(jimageLibrary, OPEN, OPEN_SIGNATURE);
        close = getNativeAccess().lookupAndBindSymbol(jimageLibrary, CLOSE, CLOSE_SIGNATURE);
        packageToModule = getNativeAccess().lookupAndBindSymbol(jimageLibrary, PACKAGE_TO_MOODULE, PACKAGE_TO_MODULE_SIGNATURE);
        findResource = getNativeAccess().lookupAndBindSymbol(jimageLibrary, FIND_RESOURCE, FIND_RESOURCE_SIGNATURE);
        getResource = getNativeAccess().lookupAndBindSymbol(jimageLibrary, GET_RESOURCE, GET_RESOURCE_SIGNATURE);

        this.javaBaseBuffer = RawBuffer.getNativeString(JAVA_BASE);
        this.versionBuffer = RawBuffer.getNativeString(VERSION_STRING);
        this.emptyStringBuffer = RawBuffer.getNativeString("");

        this.uncached = InteropLibrary.getFactory().getUncached();
    }

    public TruffleObject open(String name) {
        ByteBuffer error = NativeUtils.allocateDirect(JavaKind.Int.getByteCount());
        try (RawBuffer nameBuffer = RawBuffer.getNativeString(name)) {
            return (TruffleObject) execute(open, nameBuffer.pointer(), NativeUtils.byteBufferPointer(error));
        }
    }

    public void close(TruffleObject jimage) {
        execute(close, jimage);
    }

    public byte[] getClassBytes(TruffleObject jimage, ByteSequence name) {
        // Prepare calls
        ByteBuffer sizeBuffer = NativeUtils.allocateDirect(JavaKind.Long.getByteCount());
        TruffleObject sizePtr = NativeUtils.byteBufferPointer(sizeBuffer);

        long location = findLocation(jimage, sizePtr, name);
        if (location == 0) {
            return null;
        }

        // Extract the result
        long capacity = sizeBuffer.getLong(0);
        ByteBuffer bytes = NativeUtils.allocateDirect((int) capacity);
        TruffleObject bytesPtr = NativeUtils.byteBufferPointer(bytes);
        execute(getResource, jimage, location, bytesPtr, capacity);
        byte[] result = new byte[(int) capacity];
        bytes.get(result);
        return result;
    }

    private long findLocation(TruffleObject jimage, TruffleObject sizePtr, ByteSequence name) {
        try (RawBuffer nameBuffer = RawBuffer.getNativeString(name)) {
            TruffleObject namePtr = nameBuffer.pointer();
            long location = (long) execute(findResource, jimage, emptyStringBuffer.pointer(), versionBuffer.pointer(), namePtr, sizePtr);
            if (location != 0) {
                // found.
                return location;
            }

            ByteSequence pkg = packageFromName(name);
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
                try (RawBuffer pkgBuffer = RawBuffer.getNativeString(pkg)) {
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
                    try (RawBuffer moduleNameBuffer = RawBuffer.getNativeString(nameAsString)) {
                        return (long) execute(findResource, jimage, moduleNameBuffer.pointer(), versionBuffer.pointer(), namePtr, sizePtr);
                    }
                }
            }
        }
    }

    private static ByteSequence packageFromName(ByteSequence name) {
        int lastSlash = name.lastIndexOf((byte) '/');
        if (lastSlash == -1) {
            return null;
        }
        return name.subSequence(0, lastSlash);
    }

    private Object execute(Object target, Object... args) {
        try {
            return uncached.execute(target, args);
        } catch (UnsupportedTypeException | UnsupportedMessageException | ArityException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere(e);
        }
    }
}
