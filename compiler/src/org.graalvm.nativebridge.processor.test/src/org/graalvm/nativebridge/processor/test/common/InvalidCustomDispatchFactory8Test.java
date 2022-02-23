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
import org.graalvm.nativebridge.CustomDispatchFactory;
import org.graalvm.nativebridge.CustomReceiverAccessor;
import org.graalvm.nativebridge.GenerateNativeToHotSpotBridge;
import org.graalvm.nativebridge.processor.test.CustomReceiverService;
import org.graalvm.nativebridge.processor.test.ExpectError;
import org.graalvm.nativebridge.processor.test.ServiceAPI;
import org.graalvm.nativebridge.processor.test.TestJNIConfig;

@GenerateNativeToHotSpotBridge(jniConfig = TestJNIConfig.class)
abstract class InvalidCustomDispatchFactory8Test extends CustomReceiverService {

    @CustomDispatchAccessor
    static CustomReceiverService getDispatch(ServiceAPI receiver) {
        return receiver.dispatch;
    }

    @CustomReceiverAccessor
    static Object getReceiver(ServiceAPI receiver) {
        return receiver.receiver;
    }

    @CustomDispatchFactory
    @ExpectError("A method annotated by `CustomDispatchFactory` must be a non-private static method with a single object parameter and `ServiceAPI` return type.%n" +
                    "To fix this change the signature to `static ServiceAPI create(Object receiver)`.")
    static Object create(Object receiver) {
        return new ServiceAPI(null, receiver);
    }
}
