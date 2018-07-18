/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.posix.pthread;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.word.WordBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.posix.headers.Pthread;

public final class PthreadThreadLocal<T extends WordBase> {

    private int key;

    @Platforms(Platform.HOSTED_ONLY.class)
    public PthreadThreadLocal() {
    }

    @Uninterruptible(reason = "Called from uninterruptible code. Too early for safepoints.")
    public void initialize() {
        CIntPointer keyPtr = StackValue.get(CIntPointer.class);
        PthreadVMLockSupport.checkResult(Pthread.pthread_key_create(keyPtr, WordFactory.nullPointer()), "pthread_key_create");
        key = keyPtr.read();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.")
    public T get() {
        return Pthread.pthread_getspecific(key);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.")
    public void set(T value) {
        PthreadVMLockSupport.checkResult(Pthread.pthread_setspecific(key, value), "pthread_setspecific");
    }

    @Uninterruptible(reason = "Called from uninterruptible code.")
    public void destroy() {
        PthreadVMLockSupport.checkResult(Pthread.pthread_key_delete(key), "pthread_key_delete");
    }
}
