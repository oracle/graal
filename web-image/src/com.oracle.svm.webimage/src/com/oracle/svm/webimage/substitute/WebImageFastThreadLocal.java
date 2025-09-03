/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordBase;

import com.oracle.svm.core.annotate.Inject;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.threadlocal.FastThreadLocalBytes;
import com.oracle.svm.core.threadlocal.FastThreadLocalInt;
import com.oracle.svm.core.threadlocal.FastThreadLocalLong;
import com.oracle.svm.core.threadlocal.FastThreadLocalObject;
import com.oracle.svm.core.threadlocal.FastThreadLocalWord;
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
@SuppressWarnings("unused")
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
@SuppressWarnings("unused")
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
}

@TargetClass(FastThreadLocalInt.class)
@SuppressWarnings("unused")
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
