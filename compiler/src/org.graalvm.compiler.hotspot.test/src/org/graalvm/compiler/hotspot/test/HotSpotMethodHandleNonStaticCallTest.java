/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.test;

import java.io.IOException;
import java.util.List;

import org.graalvm.compiler.core.test.SubprocessTest;
import org.junit.Test;

public class HotSpotMethodHandleNonStaticCallTest extends SubprocessTest {

    @Override
    public void configSubprocess(List<String> vmArgs) {
        // This will prevent inlining of the invocation to MethodHandle.invokeBasic at
        // LambdaForm$MH.invoke_MT, and leave a virtual direct polymorphic signature
        // call in the emitted code. We expect the PcDesc of this call to be with 'bits=2', i.e.,
        // with the PCDESC_is_method_handle_invoke bit set.
        vmArgs.add("-XX:CompileCommand=dontinline,java/lang/invoke/LambdaForm$DMH.invokeVirtual_L_V");
    }

    @Test
    public void testInSubprocess() throws InterruptedException, IOException {
        // To manually investigate, pass -DHotSpotMethodHandleNonStaticCallTest.Subprocess=true,
        // -XX:CompileCommand='print,*.invokeDoNothing', and the added command at configSubprocess.
        // You may need a fast debug hotspot build to view the PcDesc flags.
        launchSubprocess(() -> test(getResolvedJavaMethod(HotSpotMethodHandleTest.class, "invokeDoNothing"), new HotSpotMethodHandleTest()));
    }

}
