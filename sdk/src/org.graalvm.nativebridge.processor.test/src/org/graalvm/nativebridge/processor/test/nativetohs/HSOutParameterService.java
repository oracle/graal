/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativebridge.processor.test.nativetohs;

import org.graalvm.jniutils.HSObject;
import org.graalvm.jniutils.JNI.JNIEnv;
import org.graalvm.jniutils.JNI.JObject;
import org.graalvm.nativebridge.GenerateNativeToHotSpotBridge;
import org.graalvm.nativebridge.Out;
import org.graalvm.nativebridge.processor.test.OutParameterService;
import org.graalvm.nativebridge.processor.test.TestJNIConfig;

import java.util.List;
import java.util.Map;

@GenerateNativeToHotSpotBridge(jniConfig = TestJNIConfig.class)
abstract class HSOutParameterService extends HSObject implements OutParameterService {

    HSOutParameterService(JNIEnv env, JObject handle) {
        super(env, handle);
    }

    @Override
    public abstract int singleOutParameterPrimitive(@Out List<String> p1);

    @Override
    public abstract int[] singleOutParameterArray(@Out List<String> p1);

    @Override
    public abstract void singleOutParameterVoid(@Out List<String> p1);

    @Override
    public abstract Map<String, String> singleOutParameterCustom(@Out List<String> p1);

    @Override
    public abstract int multipleOutParametersPrimitive(@Out List<String> p1, @Out List<String> p2);

    @Override
    public abstract int[] multipleOutParametersArray(@Out List<String> p1, @Out List<String> p2);

    @Override
    public abstract void multipleOutParametersVoid(@Out List<String> p1, @Out List<String> p2);

    @Override
    public abstract Map<String, String> multipleOutParametersCustom(@Out List<String> p1, @Out List<String> p2);

    @Override
    public abstract void mixedParametersVoid(List<String> p1, @Out List<String> p2, List<String> p3, @Out List<String> p4);

    @Override
    public abstract int mixedParametersPrimitive(List<String> p1, @Out List<String> p2, List<String> p3, @Out List<String> p4);

    @Override
    public abstract int[] mixedParametersArray(List<String> p1, @Out List<String> p2, List<String> p3, @Out List<String> p4);

    @Override
    public abstract Map<String, String> mixedParametersCustom(List<String> p1, @Out List<String> p2, List<String> p3, @Out List<String> p4);
}
