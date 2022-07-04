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

import org.graalvm.nativebridge.CustomDispatchAccessor;
import org.graalvm.nativebridge.GenerateNativeToHotSpotBridge;
import org.graalvm.nativebridge.CustomReceiverAccessor;
import org.graalvm.nativebridge.processor.test.ExpectError;
import org.graalvm.nativebridge.processor.test.TestJNIConfig;

@GenerateNativeToHotSpotBridge(jniConfig = TestJNIConfig.class)
abstract class InvalidMethod2Test extends InvalidExplicitReceiverService {

    @ExpectError("In a class with a custom dispatch, the first method parameter must be the receiver.%n" +
                    "For a class with a custom dispatch, make the method `final` to prevent its generation.%n" +
                    "For a class that has no custom dispatch, remove methods annotated by `CustomDispatchAccessor` and `CustomReceiverAccessor`.")
    @Override
    abstract boolean isValid();

    @CustomDispatchAccessor
    static InvalidExplicitReceiverService getDispatch(Object receiver) {
        return (InvalidExplicitReceiverService) receiver;
    }

    @CustomReceiverAccessor
    static Object getReceiver(Object receiver) {
        return receiver;
    }
}

abstract class InvalidExplicitReceiverService {
    abstract boolean execute(Object receiver);

    abstract boolean isValid();
}
