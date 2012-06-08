/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.hotspot.ri;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.hotspot.*;

/**
 * Implementation of {@link CodeInfo} for HotSpot.
 */
public class HotSpotCodeInfo extends CompilerObject implements CodeInfo {

    private static final long serialVersionUID = -6766490427732498354L;

    private long start;
    private byte[] code;
    public final CiTargetMethod targetMethod;
    private HotSpotMethodResolved method;

    public HotSpotCodeInfo(CiTargetMethod targetMethod, HotSpotMethodResolved method) {
        assert targetMethod != null;
        this.method = method;
        this.targetMethod = targetMethod;
    }

    @Override
    public long start() {
        return start;
    }

    @Override
    public byte[] code() {
        return code;
    }

    @Override
    public String toString() {
        int size = code == null ? 0 : code.length;
        return "installed code @[" + Long.toHexString(start) + "-" + Long.toHexString(start + size) + "]";

    }

    @Override
    public RiResolvedMethod method() {
        return method;
    }
}
