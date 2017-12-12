/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.nfi.impl;

import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.TruffleObject;

final class LibFFILibrary implements TruffleObject {

    protected final long handle;

    static LibFFILibrary createDefault() {
        return new LibFFILibrary(0);
    }

    static LibFFILibrary create(long handle) {
        assert handle != 0;
        LibFFILibrary ret = new LibFFILibrary(handle);
        NativeAllocation.getGlobalQueue().registerNativeAllocation(ret, new Destructor(handle));
        return ret;
    }

    private LibFFILibrary(long handle) {
        this.handle = handle;
    }

    @Override
    public ForeignAccess getForeignAccess() {
        return LibFFILibraryMessageResolutionForeign.ACCESS;
    }

    private static final class Destructor extends NativeAllocation.Destructor {

        private final long handle;

        private Destructor(long handle) {
            this.handle = handle;
        }

        @Override
        protected void destroy() {
            NFIContext.freeLibrary(handle);
        }
    }
}
