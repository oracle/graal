/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.truffle.nfi.posix;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.InjectAccessors;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.posix.headers.Dlfcn;
import com.oracle.svm.truffle.nfi.TruffleNFIFeature;

@TargetClass(className = "com.oracle.truffle.nfi.impl.NFIContext", onlyWith = TruffleNFIFeature.IsEnabled.class)
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
final class Target_com_oracle_truffle_nfi_impl_NFIContextPosix {

    // Checkstyle: stop
    static class RTLDAccessor {

        static int getRTLD_GLOBAL(@SuppressWarnings("unused") Target_com_oracle_truffle_nfi_impl_NFIContextPosix ctx) {
            return Dlfcn.RTLD_GLOBAL();
        }

        static int getRTLD_LOCAL(@SuppressWarnings("unused") Target_com_oracle_truffle_nfi_impl_NFIContextPosix ctx) {
            return Dlfcn.RTLD_LOCAL();
        }

        static int getRTLD_LAZY(@SuppressWarnings("unused") Target_com_oracle_truffle_nfi_impl_NFIContextPosix ctx) {
            return Dlfcn.RTLD_LAZY();
        }

        static int getRTLD_NOW(@SuppressWarnings("unused") Target_com_oracle_truffle_nfi_impl_NFIContextPosix ctx) {
            return Dlfcn.RTLD_NOW();
        }
    }

    @Alias @InjectAccessors(RTLDAccessor.class) int RTLD_GLOBAL;
    @Alias @InjectAccessors(RTLDAccessor.class) int RTLD_LOCAL;
    @Alias @InjectAccessors(RTLDAccessor.class) int RTLD_LAZY;
    @Alias @InjectAccessors(RTLDAccessor.class) int RTLD_NOW;
    // Checkstyle: resume
}
