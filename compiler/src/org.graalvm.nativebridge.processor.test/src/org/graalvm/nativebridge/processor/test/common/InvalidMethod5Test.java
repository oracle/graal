/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativebridge.processor.test.common;

import org.graalvm.jniutils.HSObject;
import org.graalvm.nativebridge.EndPointHandle;
import org.graalvm.nativebridge.GenerateNativeToHotSpotBridge;
import org.graalvm.nativebridge.ReceiverMethod;
import org.graalvm.nativebridge.processor.test.AbstractService;
import org.graalvm.nativebridge.processor.test.ExpectError;
import org.graalvm.nativebridge.processor.test.TestJNIConfig;

@GenerateNativeToHotSpotBridge(jniConfig = TestJNIConfig.class)
abstract class InvalidMethod5Test extends AbstractService {

    @EndPointHandle final HSObject delegate;

    InvalidMethod5Test(HSObject delegate) {
        this.delegate = delegate;
    }

    @Override
    public final boolean execute() {
        return executeImpl(42L);
    }

    @ReceiverMethod("executePrivate")
    @ExpectError("The receiver method `executePrivate` must be a non-private instance method.")
    abstract boolean executeImpl(long value);
}
