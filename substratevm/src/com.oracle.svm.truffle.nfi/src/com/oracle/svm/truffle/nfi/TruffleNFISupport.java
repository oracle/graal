/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.truffle.nfi;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.ObjectHandle;
import org.graalvm.nativeimage.ObjectHandles;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.SignedWord;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.CErrorNumber;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.threadlocal.FastThreadLocalObject;
import com.oracle.svm.truffle.nfi.NativeAPI.TruffleContextHandle;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public abstract class TruffleNFISupport {

    static final Charset UTF8 = Charset.forName("utf8");

    private static final FastThreadLocalObject<LocalNativeScope> currentScope = FastThreadLocalFactory.createObject(LocalNativeScope.class);

    private final ObjectHandles globalHandles;
    private final ObjectHandles closureHandles;
    private final ObjectHandles contextHandles;

    public final String errnoGetterFunctionName;

    protected TruffleNFISupport(String errnoLocation) {
        errnoGetterFunctionName = errnoLocation;
        globalHandles = ObjectHandles.create();
        closureHandles = ObjectHandles.create();
        contextHandles = ObjectHandles.create();
    }

    public static LocalNativeScope createLocalScope(int pinCount) {
        LocalNativeScope parent = currentScope.get();
        LocalNativeScope ret = new LocalNativeScope(parent, pinCount);
        currentScope.set(ret);
        return ret;
    }

    public static void closeLocalScope(LocalNativeScope current, LocalNativeScope parent) {
        assert currentScope.get() == current;
        currentScope.set(parent);
    }

    public static TruffleObjectHandle createLocalHandle(Object obj) {
        return currentScope.get().createLocalHandle(obj);
    }

    public TruffleObjectHandle createGlobalHandle(Object obj) {
        return (TruffleObjectHandle) globalHandles.create(obj);
    }

    public void destroyGlobalHandle(TruffleObjectHandle handle) {
        SignedWord word = (SignedWord) handle;
        if (word.greaterThan(0)) {
            globalHandles.destroy((ObjectHandle) word);
        }
    }

    public Object resolveHandle(TruffleObjectHandle handle) {
        SignedWord word = (SignedWord) handle;
        if (word.equal(0)) {
            return null;
        } else if (word.greaterThan(0)) {
            return globalHandles.get((ObjectHandle) word);
        } else {
            return currentScope.get().resolveLocalHandle(handle);
        }
    }

    public LibFFI.NativeClosureHandle createClosureHandle(NativeClosure closure) {
        return (LibFFI.NativeClosureHandle) closureHandles.create(closure);
    }

    public NativeClosure resolveClosureHandle(LibFFI.NativeClosureHandle handle) {
        return (NativeClosure) closureHandles.get((ObjectHandle) handle);
    }

    public void destroyClosureHandle(LibFFI.NativeClosureHandle handle) {
        closureHandles.destroy((ObjectHandle) handle);
    }

    public TruffleContextHandle createContextHandle(Target_com_oracle_truffle_nfi_impl_NFIContext context) {
        return (TruffleContextHandle) contextHandles.create(context);
    }

    public Target_com_oracle_truffle_nfi_impl_NFIContext resolveContextHandle(TruffleContextHandle handle) {
        return (Target_com_oracle_truffle_nfi_impl_NFIContext) contextHandles.get((ObjectHandle) handle);
    }

    public void destroyContextHandle(TruffleContextHandle handle) {
        contextHandles.destroy((ObjectHandle) handle);
    }

    @TruffleBoundary
    public static String utf8ToJavaString(CCharPointer str) {
        if (str.equal(WordFactory.zero())) {
            return null;
        } else {
            UnsignedWord len = SubstrateUtil.strlen(str);
            ByteBuffer buffer = CTypeConversion.asByteBuffer(str, (int) len.rawValue());
            return UTF8.decode(buffer).toString();
        }
    }

    private static final class ZeroTerminatedCharSequence implements CharSequence {

        private CharSequence seq;

        ZeroTerminatedCharSequence(CharSequence seq) {
            this.seq = seq;
        }

        @Override
        public int length() {
            return seq.length() + 1;
        }

        @Override
        public char charAt(int index) {
            if (index == seq.length()) {
                return '\0';
            } else {
                return seq.charAt(index);
            }
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            if (end == length()) {
                return new ZeroTerminatedCharSequence(seq.subSequence(start, end - 1));
            } else {
                return seq.subSequence(start, end);
            }
        }
    }

    @TruffleBoundary
    public static byte[] javaStringToUtf8(String str) {
        CharBuffer input = CharBuffer.wrap(new ZeroTerminatedCharSequence(str));
        /*
         * No need to trim the result array. The string is zero terminated, and the array is only
         * accessed from native code, which ignores the array length anyway.
         */
        return UTF8.encode(input).array();
    }

    protected abstract CCharPointer strdupImpl(CCharPointer src);

    protected abstract long loadLibraryImpl(long nativeContext, String name, int flags);

    protected abstract void freeLibraryImpl(long library);

    protected abstract long lookupImpl(long nativeContext, long library, String name);

    public static CCharPointer strdup(CCharPointer src) {
        TruffleNFISupport truffleNFISupport = ImageSingletons.lookup(TruffleNFISupport.class);
        return truffleNFISupport.strdupImpl(src);
    }

    public static long loadLibrary(long nativeContext, String name, int flags) {
        TruffleNFISupport truffleNFISupport = ImageSingletons.lookup(TruffleNFISupport.class);
        return truffleNFISupport.loadLibraryImpl(nativeContext, name, flags);
    }

    public static void freeLibrary(long library) {
        TruffleNFISupport truffleNFISupport = ImageSingletons.lookup(TruffleNFISupport.class);
        truffleNFISupport.freeLibraryImpl(library);
    }

    public static long lookup(long nativeContext, long library, String name) {
        TruffleNFISupport truffleNFISupport = ImageSingletons.lookup(TruffleNFISupport.class);
        return truffleNFISupport.lookupImpl(nativeContext, library, name);
    }

    protected static Target_com_oracle_truffle_nfi_impl_NFIContext getContext(long nativeContext) {
        TruffleNFISupport truffleNFISupport = ImageSingletons.lookup(TruffleNFISupport.class);
        NativeAPI.NativeTruffleContext ctx = WordFactory.pointer(nativeContext);
        return truffleNFISupport.resolveContextHandle(ctx.contextHandle());
    }

    /**
     * Context for calling from native code into Java code. On entry, the native {@code errno} value
     * is stored in {@link ErrnoMirror}. When leaving the context, the {@link ErrnoMirror} value is
     * written back to the native {@code errno} location.
     */
    public static class ErrnoMirrorContext implements AutoCloseable {

        private final CIntPointer errnoMirror;

        public ErrnoMirrorContext() {
            errnoMirror = ErrnoMirror.getErrnoMirrorLocation();
            errnoMirror.write(CErrorNumber.getCErrorNumber());
        }

        @Override
        public void close() {
            CErrorNumber.setCErrorNumber(errnoMirror.read());
        }
    }

    /**
     * Context for calling from Java code into native code. On entry, the {@link ErrnoMirror} mirror
     * value is stored to the native {@code errno} location. When leaving the context, the native
     * {@code errno} value is written back to the {@link ErrnoMirror}.
     */
    public static class NativeErrnoContext implements AutoCloseable {

        private final CIntPointer errnoMirror;

        public NativeErrnoContext() {
            errnoMirror = ErrnoMirror.getErrnoMirrorLocation();
            CErrorNumber.setCErrorNumber(errnoMirror.read());
        }

        @Override
        public void close() {
            errnoMirror.write(CErrorNumber.getCErrorNumber());
        }
    }
}
