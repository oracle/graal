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

import org.graalvm.nativebridge.GenerateHotSpotToNativeBridge;
import org.graalvm.nativebridge.processor.test.AbstractService;
import org.graalvm.nativebridge.processor.test.ExpectError;
import org.graalvm.nativebridge.processor.test.Service;
import org.graalvm.nativebridge.processor.test.TestJNIConfig;
import org.graalvm.nativeimage.c.function.CEntryPoint.NotIncludedAutomatically;

@ExpectError("The annotated type must have a non `Object` superclass or implement a single interface.%n" +
                "To fix this introduce a new bridged base class extending `AbstractService` and implementing `Service` and extend it.")
@GenerateHotSpotToNativeBridge(jniConfig = TestJNIConfig.class, include = NotIncludedAutomatically.class)
abstract class InvalidBaseType3Test extends AbstractService implements Service {

    InvalidBaseType3Test() {
    }
}
