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
package com.oracle.svm.guest.staging.config;

import java.nio.ByteOrder;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.shared.meta.GuestFold;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;

import jdk.vm.ci.meta.JavaKind;

/**
 * Guest level replacements for {@code ConfigurationValues}. It avoids exposing builder internals
 * such as {@code SubstrateTargetDescription}. Instead, it stores all the values directly in the
 * instance which is installed as an {@linkplain ImageSingletons image singleton} in
 * {@code NativeImageGenerator}.
 */
@SingletonTraits(access = BuiltinTraits.AllAccess.class, layeredCallbacks = BuiltinTraits.NoLayeredCallbacks.class, layeredInstallationKind = SingletonLayeredInstallationKind.Duplicable.class)
public class GuestConfigurationValues {

    private final JavaKind wordKind;
    private final int wordSize;
    private final ByteOrder byteOrder;

    public GuestConfigurationValues(JavaKind wordKind, int wordSize, ByteOrder byteOrder) {
        this.wordKind = wordKind;
        this.wordSize = wordSize;
        this.byteOrder = byteOrder;
    }

    @GuestFold
    private static GuestConfigurationValues singleton() {
        return ImageSingletons.lookup(GuestConfigurationValues.class);
    }

    @GuestFold
    public static JavaKind getWordKind() {
        return singleton().wordKind;
    }

    @GuestFold
    public static int getWordSize() {
        return singleton().wordSize;
    }

    @GuestFold
    public static ByteOrder getByteOrder() {
        return singleton().byteOrder;
    }
}
