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
package com.oracle.svm.core.threadlocal;

//Checkstyle: allow reflection

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;

import java.util.function.IntSupplier;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.WordBase;

import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.annotate.UnknownObjectField;
import com.oracle.svm.core.annotate.UnknownPrimitiveField;

import jdk.vm.ci.meta.JavaKind;

/**
 * Stores additional information about a {@link FastThreadLocal} that is not part of the public API,
 * but needed for compilation.
 */
public class VMThreadLocalInfo {

    @SuppressWarnings("unchecked")    //
    public static final Class<? extends FastThreadLocal>[] THREAD_LOCAL_CLASSES = (Class<? extends FastThreadLocal>[]) new Class<?>[]{FastThreadLocalInt.class, FastThreadLocalLong.class,
                    FastThreadLocalWord.class, FastThreadLocalObject.class};

    @Platforms(Platform.HOSTED_ONLY.class)
    public static Class<?> getValueClass(Class<? extends FastThreadLocal> threadLocalClass) {
        if (threadLocalClass == FastThreadLocalInt.class) {
            return int.class;
        } else if (threadLocalClass == FastThreadLocalLong.class) {
            return long.class;
        } else if (threadLocalClass == FastThreadLocalWord.class) {
            return WordBase.class;
        } else if (threadLocalClass == FastThreadLocalObject.class) {
            return Object.class;
        } else {
            throw shouldNotReachHere();
        }
    }

    public final Class<? extends FastThreadLocal> threadLocalClass;
    public final IntSupplier sizeSupplier;
    public final LocationIdentity locationIdentity;
    public final boolean isObject;
    public final JavaKind storageKind;
    public final Class<?> valueClass;
    @UnknownObjectField(types = {String.class}) public String name;
    @UnknownPrimitiveField public int offset;
    @UnknownPrimitiveField public int sizeInBytes;

    @Platforms(Platform.HOSTED_ONLY.class)
    public VMThreadLocalInfo(FastThreadLocal threadLocal) {
        this.threadLocalClass = threadLocal.getClass();
        this.locationIdentity = threadLocal.getLocationIdentity();

        if (threadLocalClass == FastThreadLocalBytes.class) {
            sizeSupplier = ((FastThreadLocalBytes<?>) threadLocal).getSizeSupplier();
        } else {
            sizeSupplier = null;
        }

        if (threadLocalClass == FastThreadLocalObject.class) {
            isObject = true;
            valueClass = ((FastThreadLocalObject<?>) threadLocal).getValueClass();
        } else {
            isObject = false;
            valueClass = null;
        }

        if (threadLocalClass == FastThreadLocalInt.class) {
            storageKind = JavaKind.Int;
        } else if (threadLocalClass == FastThreadLocalLong.class) {
            storageKind = JavaKind.Long;
        } else if (threadLocalClass == FastThreadLocalWord.class) {
            storageKind = FrameAccess.getWordKind();
        } else if (threadLocalClass == FastThreadLocalObject.class) {
            storageKind = JavaKind.Object;
        } else if (threadLocalClass == FastThreadLocalBytes.class) {
            storageKind = null;
        } else {
            throw shouldNotReachHere();
        }

        /* Initialize with illegal value for assertion checking. */
        offset = -1;
        sizeInBytes = -1;
    }

    @Override
    public String toString() {
        return name + "@" + offset;
    }
}
