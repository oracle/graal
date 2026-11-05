/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.test.jni;

import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.Isolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Isolates;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPointLiteral;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.function.InvokeCFunctionPointer;
import org.graalvm.word.impl.Word;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import com.oracle.svm.core.jni.JNIObjectHandles;
import com.oracle.svm.core.jni.headers.JNIObjectHandle;
import com.oracle.svm.core.jni.headers.JNIObjectRefType;
import com.oracle.svm.test.NativeImageBuildArgs;

@NativeImageBuildArgs({
                "--add-exports=org.graalvm.nativeimage.builder/com.oracle.svm.core=ALL-UNNAMED",
                "--add-exports=org.graalvm.nativeimage.builder/com.oracle.svm.core.jni=ALL-UNNAMED",
                "--add-exports=org.graalvm.nativeimage.builder/com.oracle.svm.core.jni.headers=ALL-UNNAMED",
                "--add-exports=org.graalvm.nativeimage.guest.staging/com.oracle.svm.guest.staging.c=ALL-UNNAMED",
                "--initialize-at-build-time=com.oracle.svm.test.jni.JNIGlobalHandlesTest",
                "-da:com.oracle.svm.core.jni..."
})
public class JNIGlobalHandlesTest {
    private static final long HANDLE_BITS_MASK = (1L << 31) - 1;
    private static final long VALIDATION_BITS_SHIFT = 31;
    private static final long VALIDATION_BITS_MASK = ((1L << 32) - 1) << VALIDATION_BITS_SHIFT;
    private static final long MSB = 1L << 63;
    private static final int GLOBAL_HANDLE_REJECTED = 1;

    private static final CEntryPointLiteral<CreateGlobalHandleFunction> CREATE_GLOBAL_HANDLE = CEntryPointLiteral.create(JNIGlobalHandlesTest.class, "createGlobalHandleInIsolate",
                    IsolateThread.class);
    private static final CEntryPointLiteral<ResolveGlobalHandleFunction> RESOLVE_GLOBAL_HANDLE = CEntryPointLiteral.create(JNIGlobalHandlesTest.class, "resolveGlobalHandleInIsolate",
                    IsolateThread.class,
                    JNIObjectHandle.class);

    @Test
    public void getObjectRefTypeRejectsForeignGlobalHandle() {
        assumeNativeImageRuntime();

        TestObject object = new TestObject("ref-type");
        JNIObjectHandle global = newGlobalRef(object);
        try {
            JNIObjectHandle foreignGlobal = withDifferentOwnerTag(global);
            assertValidGlobalHandle(global, object);
            assertSameSlotDifferentOwnerTag(global, foreignGlobal);

            Assert.assertEquals(JNIObjectRefType.Invalid, getObjectRefType(foreignGlobal));
        } finally {
            JNIObjectHandles.deleteGlobalRef(global);
        }
    }

    @Test
    public void globalHandleOwnerTagUsesUniqueIsolateId() {
        assumeNativeImageRuntime();

        TestObject object = new TestObject("owner-tag");
        JNIObjectHandle global = newGlobalRef(object);
        try {
            long isolateId = com.oracle.svm.core.Isolates.getIsolateId();
            Assert.assertEquals(ownerTagFromIsolateId(isolateId), ownerTag(global));
        } finally {
            JNIObjectHandles.deleteGlobalRef(global);
        }
    }

    @Test
    public void isolateIdOwnerTagDoesNotRepeatWhenIsolateAddressIsReused() {
        assumeNativeImageRuntime();

        IsolateSnapshot staleIsolate = createIsolateSnapshot();
        Isolates.tearDownIsolate(staleIsolate.thread);

        for (int i = 0; i < 16; i++) {
            IsolateSnapshot nextIsolate = createIsolateSnapshot();
            try {
                if (staleIsolate.pointerOwnerTag == nextIsolate.pointerOwnerTag) {
                    Assert.assertNotEquals(staleIsolate.idOwnerTag, nextIsolate.idOwnerTag);
                    return;
                }
            } finally {
                Isolates.tearDownIsolate(nextIsolate.thread);
            }
        }

        Assume.assumeTrue("Did not observe isolate address reuse while recreating isolates.", false);
    }

    @Test
    public void staleGlobalHandleRejectedWhenIsolateAddressIsReused() {
        assumeNativeImageRuntime();

        IsolateWithGlobalHandle staleIsolate = createIsolateWithGlobalHandle();
        Isolates.tearDownIsolate(staleIsolate.thread);

        for (int i = 0; i < 16; i++) {
            IsolateWithGlobalHandle nextIsolate = createIsolateWithGlobalHandle();
            try {
                if (staleIsolate.pointerOwnerTag == nextIsolate.pointerOwnerTag) {
                    Assert.assertEquals(slot(staleIsolate.globalHandle), slot(nextIsolate.globalHandle));
                    Assert.assertNotEquals(ownerTag(staleIsolate.globalHandle), ownerTag(nextIsolate.globalHandle));
                    Assert.assertEquals(GLOBAL_HANDLE_REJECTED,
                                    RESOLVE_GLOBAL_HANDLE.getFunctionPointer().invoke(nextIsolate.thread, staleIsolate.globalHandle));
                    return;
                }
            } finally {
                Isolates.tearDownIsolate(nextIsolate.thread);
            }
        }

        Assume.assumeTrue("Did not observe isolate address reuse while recreating isolates.", false);
    }

    @Test
    public void newGlobalRefRejectsForeignGlobalHandle() {
        assumeNativeImageRuntime();

        TestObject object = new TestObject("new-global-ref");
        JNIObjectHandle global = newGlobalRef(object);
        try {
            JNIObjectHandle foreignGlobal = withDifferentOwnerTag(global);
            assertValidGlobalHandle(global, object);
            assertSameSlotDifferentOwnerTag(global, foreignGlobal);

            try {
                JNIObjectHandle duplicate = JNIObjectHandles.newGlobalRef(foreignGlobal);
                if (duplicate.notEqual(JNIObjectHandles.nullHandle())) {
                    JNIObjectHandles.deleteGlobalRef(duplicate);
                }
                Assert.fail("NewGlobalRef must reject a global handle owned by another isolate.");
            } catch (IllegalArgumentException expected) {
                // expected
            }
        } finally {
            JNIObjectHandles.deleteGlobalRef(global);
        }
    }

    @Test
    public void dereferenceRejectsForeignGlobalHandle() {
        assumeNativeImageRuntime();

        TestObject object = new TestObject("dereference");
        JNIObjectHandle global = newGlobalRef(object);
        try {
            JNIObjectHandle foreignGlobal = withDifferentOwnerTag(global);
            assertValidGlobalHandle(global, object);
            assertSameSlotDifferentOwnerTag(global, foreignGlobal);

            try {
                Object resolved = JNIObjectHandles.getObject(foreignGlobal);
                Assert.fail("JNI dereference paths must reject a global handle owned by another isolate: " + resolved);
            } catch (IllegalArgumentException expected) {
                // expected
            }
        } finally {
            JNIObjectHandles.deleteGlobalRef(global);
        }
    }

    private static void assumeNativeImageRuntime() {
        Assume.assumeTrue(ImageInfo.inImageRuntimeCode());
    }

    private static JNIObjectHandle newGlobalRef(Object object) {
        JNIObjectHandle local = JNIObjectHandles.createLocal(object);
        try {
            return JNIObjectHandles.newGlobalRef(local);
        } finally {
            JNIObjectHandles.deleteLocalRef(local);
        }
    }

    private static JNIObjectRefType getObjectRefType(JNIObjectHandle handle) {
        try {
            return JNIObjectHandles.getHandleType(handle);
        } catch (IllegalArgumentException e) {
            return JNIObjectRefType.Invalid;
        }
    }

    private static void assertValidGlobalHandle(JNIObjectHandle handle, Object object) {
        Assert.assertEquals(JNIObjectRefType.Global, getObjectRefType(handle));
        Assert.assertSame(object, JNIObjectHandles.getObject(handle));
    }

    private static void assertSameSlotDifferentOwnerTag(JNIObjectHandle global, JNIObjectHandle foreignGlobal) {
        Assert.assertNotEquals(global.rawValue(), foreignGlobal.rawValue());
        Assert.assertTrue(foreignGlobal.rawValue() < 0);
        Assert.assertEquals(slot(global), slot(foreignGlobal));
        Assert.assertNotEquals(ownerTag(global), ownerTag(foreignGlobal));
    }

    private static JNIObjectHandle withDifferentOwnerTag(JNIObjectHandle handle) {
        long slot = slot(handle);
        long validationBits = ownerTag(handle);
        long differentValidationBits = validationBits ^ (1L << VALIDATION_BITS_SHIFT);
        return (JNIObjectHandle) Word.pointer(MSB | differentValidationBits | slot);
    }

    private static long slot(JNIObjectHandle handle) {
        return handle.rawValue() & HANDLE_BITS_MASK;
    }

    private static long ownerTag(JNIObjectHandle handle) {
        return handle.rawValue() & VALIDATION_BITS_MASK;
    }

    private static long ownerTagFromIsolateId(long isolateId) {
        return (Long.hashCode(isolateId) & 0xffffffffL) << VALIDATION_BITS_SHIFT;
    }

    private static IsolateSnapshot createIsolateSnapshot() {
        long isolateId = nextIsolateId();
        IsolateThread thread = Isolates.createIsolate(Isolates.CreateIsolateParameters.getDefault());
        Isolate isolate = Isolates.getIsolate(thread);
        return new IsolateSnapshot(thread, ownerTagFromIsolatePointer(isolate), ownerTagFromIsolateId(isolateId));
    }

    private static IsolateWithGlobalHandle createIsolateWithGlobalHandle() {
        IsolateSnapshot snapshot = createIsolateSnapshot();
        JNIObjectHandle globalHandle = CREATE_GLOBAL_HANDLE.getFunctionPointer().invoke(snapshot.thread);
        return new IsolateWithGlobalHandle(snapshot.thread, globalHandle, snapshot.pointerOwnerTag);
    }

    private static long nextIsolateId() {
        return com.oracle.svm.core.Isolates.ISOLATE_COUNTER.get().readLong(0);
    }

    private static long ownerTagFromIsolatePointer(Isolate isolate) {
        return (Long.hashCode(isolate.rawValue()) & 0xffffffffL) << VALIDATION_BITS_SHIFT;
    }

    private record TestObject(String name) {
    }

    private interface CreateGlobalHandleFunction extends CFunctionPointer {
        @InvokeCFunctionPointer
        JNIObjectHandle invoke(IsolateThread thread);
    }

    private interface ResolveGlobalHandleFunction extends CFunctionPointer {
        @InvokeCFunctionPointer
        int invoke(IsolateThread thread, JNIObjectHandle handle);
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    private static JNIObjectHandle createGlobalHandleInIsolate(@SuppressWarnings("unused") IsolateThread thread) {
        return newGlobalRef(new TestObject("stale"));
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    private static int resolveGlobalHandleInIsolate(@SuppressWarnings("unused") IsolateThread thread, JNIObjectHandle handle) {
        try {
            JNIObjectHandles.getObject(handle);
            return 0;
        } catch (IllegalArgumentException expected) {
            return GLOBAL_HANDLE_REJECTED;
        }
    }

    private static final class IsolateSnapshot {
        private final IsolateThread thread;
        private final long pointerOwnerTag;
        private final long idOwnerTag;

        private IsolateSnapshot(IsolateThread thread, long pointerOwnerTag, long idOwnerTag) {
            this.thread = thread;
            this.pointerOwnerTag = pointerOwnerTag;
            this.idOwnerTag = idOwnerTag;
        }
    }

    private static final class IsolateWithGlobalHandle {
        private final IsolateThread thread;
        private final JNIObjectHandle globalHandle;
        private final long pointerOwnerTag;

        private IsolateWithGlobalHandle(IsolateThread thread, JNIObjectHandle globalHandle, long pointerOwnerTag) {
            this.thread = thread;
            this.globalHandle = globalHandle;
            this.pointerOwnerTag = pointerOwnerTag;
        }
    }
}
