/*
 * Copyright (c) 2014, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.meta;

import jdk.internal.jvmci.meta.*;

import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.hotspot.nodes.type.*;
import com.oracle.graal.nodes.spi.*;

public class HotSpotStampProvider implements StampProvider {

    private final KlassPointerStamp klassStamp;

    private final KlassPointerStamp klassNonNullStamp;

    @SuppressWarnings("unused") private final Kind wordKind;

    public HotSpotStampProvider(Kind wordKind) {
        this.wordKind = wordKind;
        klassStamp = new KlassPointerStamp(false, false, wordKind);
        klassNonNullStamp = new KlassPointerStamp(true, false, wordKind);
    }

    public KlassPointerStamp klass() {
        return klassStamp;
    }

    public KlassPointerStamp klassNonNull() {
        return klassNonNullStamp;
    }

    public Stamp createHubStamp(ObjectStamp object) {
        return klassNonNull();
    }

    public Stamp createMethodStamp() {
        return MethodPointerStamp.methodNonNull();
    }

    public Stamp createHubStamp(boolean nonNull) {
        return nonNull ? klassNonNullStamp : klassStamp;
    }
}
