/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.webimage.substitute;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordBase;

import com.oracle.svm.core.annotate.Inject;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.guest.staging.core.threadlocal.FastThreadLocalBoolean;
import com.oracle.svm.guest.staging.core.threadlocal.FastThreadLocalByte;
import com.oracle.svm.guest.staging.core.threadlocal.FastThreadLocalBytes;
import com.oracle.svm.guest.staging.core.threadlocal.FastThreadLocalChar;
import com.oracle.svm.guest.staging.core.threadlocal.FastThreadLocalInt;
import com.oracle.svm.guest.staging.core.threadlocal.FastThreadLocalLong;
import com.oracle.svm.guest.staging.core.threadlocal.FastThreadLocalObject;
import com.oracle.svm.guest.staging.core.threadlocal.FastThreadLocalShort;
import com.oracle.svm.guest.staging.core.threadlocal.FastThreadLocalWord;
import com.oracle.svm.webimage.platform.WebImageJSPlatform;
import com.oracle.svm.webimage.platform.WebImageWasmGCPlatform;

/**
 * Substitutions for all FastThreadLocal classes.
 *
 * Since we only have a single thread, all classes only manage a single value and don't need to do
 * any thread specific work.
 */
public class WebImageFastThreadLocal {
    // dummy
}

@TargetClass(FastThreadLocalObject.class)
@SuppressWarnings({"unused", "static-method"})
@Platforms({WebImageJSPlatform.class, WebImageWasmGCPlatform.class})
final class Target_FastThreadLocalObject_Web<T> {

    @Inject @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) private T obj = null;

    @Substitute
    public T get() {
        return obj;
    }

    @Substitute
    public T get(IsolateThread thread) {
        return get();
    }

    @Substitute
    public void set(T value) {
        obj = value;
    }

    @Substitute
    public void set(IsolateThread thread, T value) {
        set(value);
    }

    @Substitute
    public T getVolatile() {
        return get();
    }

    @Substitute
    public T getVolatile(IsolateThread thread) {
        return get();
    }

    @Substitute
    public void setVolatile(T value) {
        set(value);
    }

    @Substitute
    public void setVolatile(IsolateThread thread, T value) {
        set(value);
    }

    @Substitute
    public boolean compareAndSet(T expect, T update) {
        if (get() == expect) {
            set(update);
            return true;
        }

        return false;
    }

    @Substitute
    public boolean compareAndSet(IsolateThread thread, T expect, T update) {
        return compareAndSet(expect, update);
    }

    @Substitute
    public Pointer getAddress() {
        throw new UnsupportedOperationException("Target_FastThreadLocalObject_Web.getAddress()");
    }

    @Substitute
    public Pointer getAddress(IsolateThread thread) {
        throw new UnsupportedOperationException("Target_FastThreadLocalObject_Web.getAddress(IsolateThread)");
    }
}

@TargetClass(FastThreadLocalWord.class)
@SuppressWarnings({"unused", "static-method"})
@Platforms({WebImageJSPlatform.class, WebImageWasmGCPlatform.class})
final class Target_FastThreadLocalWord_Web<T extends WordBase> {

    @Inject @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) private T obj = null;

    @Substitute
    public T get() {
        return obj;
    }

    @Substitute
    public T get(IsolateThread thread) {
        return get();
    }

    @Substitute
    public void set(T value) {
        obj = value;
    }

    @Substitute
    public void set(IsolateThread thread, T value) {
        set(value);
    }

    @Substitute
    public T getVolatile() {
        return get();
    }

    @Substitute
    public T getVolatile(IsolateThread thread) {
        return get();
    }

    @Substitute
    public void setVolatile(T value) {
        set(value);
    }

    @Substitute
    public void setVolatile(IsolateThread thread, T value) {
        set(value);
    }

    @Substitute
    public boolean compareAndSet(T expect, T update) {
        if (get() == expect) {
            set(update);
            return true;
        }

        return false;
    }

    @Substitute
    public boolean compareAndSet(IsolateThread thread, T expect, T update) {
        return compareAndSet(expect, update);
    }

    @Substitute
    public WordPointer getAddress() {
        throw new UnsupportedOperationException("Target_FastThreadLocalWord_Web.getAddress()");
    }

    @Substitute
    public WordPointer getAddress(IsolateThread thread) {
        throw new UnsupportedOperationException("Target_FastThreadLocalWord_Web.getAddress(IsolateThread)");
    }
}

@TargetClass(FastThreadLocalLong.class)
@SuppressWarnings({"unused", "static-method"})
@Platforms({WebImageJSPlatform.class, WebImageWasmGCPlatform.class})
final class Target_FastThreadLocalLong_Web {

    @Inject @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) private long obj = 0;

    @Substitute
    public long get() {
        return obj;
    }

    @Substitute
    public long get(IsolateThread thread) {
        return get();
    }

    @Substitute
    public void set(long value) {
        obj = value;
    }

    @Substitute
    public void set(IsolateThread thread, long value) {
        set(value);
    }

    @Substitute
    public long getVolatile() {
        return get();
    }

    @Substitute
    public long getVolatile(IsolateThread thread) {
        return get();
    }

    @Substitute
    public void setVolatile(long value) {
        set(value);
    }

    @Substitute
    public void setVolatile(IsolateThread thread, long value) {
        set(value);
    }

    @Substitute
    public boolean compareAndSet(long expect, long update) {
        if (get() == expect) {
            set(update);
            return true;
        }

        return false;
    }

    @Substitute
    public boolean compareAndSet(IsolateThread thread, long expect, long update) {
        return compareAndSet(expect, update);
    }

    @Substitute
    public Pointer getAddress() {
        throw new UnsupportedOperationException("Target_FastThreadLocalLong_Web.getAddress()");
    }

    @Substitute
    public Pointer getAddress(IsolateThread thread) {
        throw new UnsupportedOperationException("Target_FastThreadLocalLong_Web.getAddress(IsolateThread)");
    }
}

@TargetClass(FastThreadLocalInt.class)
@SuppressWarnings({"unused", "static-method"})
@Platforms({WebImageJSPlatform.class, WebImageWasmGCPlatform.class})
final class Target_FastThreadLocalInt_Web {

    @Inject @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) private int obj = 0;

    @Substitute
    public int get() {
        return obj;
    }

    @Substitute
    public int get(IsolateThread thread) {
        return get();
    }

    @Substitute
    public void set(int value) {
        obj = value;
    }

    @Substitute
    public void set(IsolateThread thread, int value) {
        set(value);
    }

    @Substitute
    public int getVolatile() {
        return get();
    }

    @Substitute
    public int getVolatile(IsolateThread thread) {
        return get();
    }

    @Substitute
    public void setVolatile(int value) {
        set(value);
    }

    @Substitute
    public void setVolatile(IsolateThread thread, int value) {
        set(value);
    }

    @Substitute
    public boolean compareAndSet(int expect, int update) {
        if (get() == expect) {
            set(update);
            return true;
        }

        return false;
    }

    @Substitute
    public boolean compareAndSet(IsolateThread thread, int expect, int update) {
        return compareAndSet(expect, update);
    }

    @Substitute
    public Pointer getAddress() {
        throw new UnsupportedOperationException("Target_FastThreadLocalInt_Web.getAddress()");
    }

    @Substitute
    public Pointer getAddress(IsolateThread thread) {
        throw new UnsupportedOperationException("Target_FastThreadLocalInt_Web.getAddress(IsolateThread)");
    }
}

@TargetClass(FastThreadLocalBoolean.class)
@SuppressWarnings({"unused", "static-method"})
@Platforms({WebImageJSPlatform.class, WebImageWasmGCPlatform.class})
final class Target_FastThreadLocalBoolean_Web {

    @Inject @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) private boolean obj = false;

    @Substitute
    public boolean get() {
        return obj;
    }

    @Substitute
    public boolean get(IsolateThread thread) {
        return get();
    }

    @Substitute
    public void set(boolean value) {
        obj = value;
    }

    @Substitute
    public void set(IsolateThread thread, boolean value) {
        set(value);
    }

    @Substitute
    public boolean getVolatile() {
        return get();
    }

    @Substitute
    public boolean getVolatile(IsolateThread thread) {
        return get();
    }

    @Substitute
    public void setVolatile(boolean value) {
        set(value);
    }

    @Substitute
    public void setVolatile(IsolateThread thread, boolean value) {
        set(value);
    }

    @Substitute
    public boolean compareAndSet(boolean expect, boolean update) {
        if (get() == expect) {
            set(update);
            return true;
        }

        return false;
    }

    @Substitute
    public boolean compareAndSet(IsolateThread thread, boolean expect, boolean update) {
        return compareAndSet(expect, update);
    }

    @Substitute
    public Pointer getAddress() {
        throw new UnsupportedOperationException("Target_FastThreadLocalBoolean_Web.getAddress()");
    }

    @Substitute
    public Pointer getAddress(IsolateThread thread) {
        throw new UnsupportedOperationException("Target_FastThreadLocalBoolean_Web.getAddress(IsolateThread)");
    }
}

@TargetClass(FastThreadLocalByte.class)
@SuppressWarnings({"unused", "static-method"})
@Platforms({WebImageJSPlatform.class, WebImageWasmGCPlatform.class})
final class Target_FastThreadLocalByte_Web {

    @Inject @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) private byte obj = 0;

    @Substitute
    public byte get() {
        return obj;
    }

    @Substitute
    public byte get(IsolateThread thread) {
        return get();
    }

    @Substitute
    public void set(byte value) {
        obj = value;
    }

    @Substitute
    public void set(IsolateThread thread, byte value) {
        set(value);
    }

    @Substitute
    public byte getVolatile() {
        return get();
    }

    @Substitute
    public byte getVolatile(IsolateThread thread) {
        return get();
    }

    @Substitute
    public void setVolatile(byte value) {
        set(value);
    }

    @Substitute
    public void setVolatile(IsolateThread thread, byte value) {
        set(value);
    }

    @Substitute
    public boolean compareAndSet(byte expect, byte update) {
        if (get() == expect) {
            set(update);
            return true;
        }

        return false;
    }

    @Substitute
    public boolean compareAndSet(IsolateThread thread, byte expect, byte update) {
        return compareAndSet(expect, update);
    }

    @Substitute
    public Pointer getAddress() {
        throw new UnsupportedOperationException("Target_FastThreadLocalByte_Web.getAddress()");
    }

    @Substitute
    public Pointer getAddress(IsolateThread thread) {
        throw new UnsupportedOperationException("Target_FastThreadLocalByte_Web.getAddress(IsolateThread)");
    }
}

@TargetClass(FastThreadLocalShort.class)
@SuppressWarnings({"unused", "static-method"})
@Platforms({WebImageJSPlatform.class, WebImageWasmGCPlatform.class})
final class Target_FastThreadLocalShort_Web {

    @Inject @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) private short obj = 0;

    @Substitute
    public short get() {
        return obj;
    }

    @Substitute
    public short get(IsolateThread thread) {
        return get();
    }

    @Substitute
    public void set(short value) {
        obj = value;
    }

    @Substitute
    public void set(IsolateThread thread, short value) {
        set(value);
    }

    @Substitute
    public short getVolatile() {
        return get();
    }

    @Substitute
    public short getVolatile(IsolateThread thread) {
        return get();
    }

    @Substitute
    public void setVolatile(short value) {
        set(value);
    }

    @Substitute
    public void setVolatile(IsolateThread thread, short value) {
        set(value);
    }

    @Substitute
    public boolean compareAndSet(short expect, short update) {
        if (get() == expect) {
            set(update);
            return true;
        }

        return false;
    }

    @Substitute
    public boolean compareAndSet(IsolateThread thread, short expect, short update) {
        return compareAndSet(expect, update);
    }

    @Substitute
    public Pointer getAddress() {
        throw new UnsupportedOperationException("Target_FastThreadLocalShort_Web.getAddress()");
    }

    @Substitute
    public Pointer getAddress(IsolateThread thread) {
        throw new UnsupportedOperationException("Target_FastThreadLocalShort_Web.getAddress(IsolateThread)");
    }
}

@TargetClass(FastThreadLocalChar.class)
@SuppressWarnings({"unused", "static-method"})
@Platforms({WebImageJSPlatform.class, WebImageWasmGCPlatform.class})
final class Target_FastThreadLocalChar_Web {

    @Inject @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) private char obj = 0;

    @Substitute
    public char get() {
        return obj;
    }

    @Substitute
    public char get(IsolateThread thread) {
        return get();
    }

    @Substitute
    public void set(char value) {
        obj = value;
    }

    @Substitute
    public void set(IsolateThread thread, char value) {
        set(value);
    }

    @Substitute
    public char getVolatile() {
        return get();
    }

    @Substitute
    public char getVolatile(IsolateThread thread) {
        return get();
    }

    @Substitute
    public void setVolatile(char value) {
        set(value);
    }

    @Substitute
    public void setVolatile(IsolateThread thread, char value) {
        set(value);
    }

    @Substitute
    public boolean compareAndSet(char expect, char update) {
        if (get() == expect) {
            set(update);
            return true;
        }

        return false;
    }

    @Substitute
    public boolean compareAndSet(IsolateThread thread, char expect, char update) {
        return compareAndSet(expect, update);
    }

    @Substitute
    public Pointer getAddress() {
        throw new UnsupportedOperationException("Target_FastThreadLocalChar_Web.getAddress()");
    }

    @Substitute
    public Pointer getAddress(IsolateThread thread) {
        throw new UnsupportedOperationException("Target_FastThreadLocalChar_Web.getAddress(IsolateThread)");
    }
}

@TargetClass(FastThreadLocalBytes.class)
@SuppressWarnings("unused")
@Platforms({WebImageJSPlatform.class, WebImageWasmGCPlatform.class})
final class Target_FastThreadLocalBytes_Web<T extends PointerBase> {

    @Substitute
    public T getAddress() {
        throw new UnsupportedOperationException("Target_FastThreadLocalBytes_Web.getAddress()");
    }

    @Substitute
    public T getAddress(IsolateThread thread) {
        throw new UnsupportedOperationException("Target_FastThreadLocalBytes_Web.getAddress(IsolateThread)");
    }
}
