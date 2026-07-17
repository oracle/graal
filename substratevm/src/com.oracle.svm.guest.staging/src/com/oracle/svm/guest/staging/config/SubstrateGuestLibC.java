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

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.shared.c.libc.LibCKind;
import com.oracle.svm.shared.meta.GuestFold;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;

/**
 * Guest-owned target Linux libc identity derived from the authoritative builder selection.
 * <p>
 * Although the query methods are called from runtime-owned code, every query is
 * {@link GuestFold folded} while the image is built. The fold executes in the guest context and
 * replaces the call with a boolean constant, so neither this singleton nor its
 * {@link ImageSingletons#lookup(Class) lookup} is needed at image runtime. Consequently, this
 * singleton deliberately permits build-time access only.
 */
@Platforms(Platform.LINUX.class)
@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class)
public final class SubstrateGuestLibC {

    private final LibCKind kind;

    /**
     * Creates the guest-owned libc metadata from a shared enum name. Passing the name lets the enum
     * value be materialized inside the guest context instead of transferring a builder object.
     *
     * @param kindName the {@link LibCKind} constant name
     */
    public SubstrateGuestLibC(String kindName) {
        this.kind = LibCKind.valueOf(kindName);
    }

    /** Returns whether the target uses musl libc. */
    @GuestFold
    public static boolean isMusl() {
        return singleton().kind == LibCKind.MUSL;
    }

    /** Returns whether the target uses glibc. */
    @GuestFold
    public static boolean isGLibC() {
        return singleton().kind == LibCKind.GLIBC;
    }

    /** Returns whether the target uses Bionic libc. */
    @GuestFold
    public static boolean isBionic() {
        return singleton().kind == LibCKind.BIONIC;
    }

    /** Returns the libc metadata installed in the guest singleton registry. */
    private static SubstrateGuestLibC singleton() {
        return ImageSingletons.lookup(SubstrateGuestLibC.class);
    }
}
