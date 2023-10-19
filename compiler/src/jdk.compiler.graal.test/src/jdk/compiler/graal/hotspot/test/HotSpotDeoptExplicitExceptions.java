/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.compiler.graal.hotspot.test;

import static jdk.compiler.graal.nodes.graphbuilderconf.GraphBuilderConfiguration.BytecodeExceptionMode.CheckAll;

import java.io.IOException;

import jdk.compiler.graal.core.test.SubprocessTest;
import jdk.compiler.graal.hotspot.HotSpotBackend;
import jdk.compiler.graal.hotspot.stubs.CreateExceptionStub;
import jdk.compiler.graal.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.compiler.graal.nodes.graphbuilderconf.GraphBuilderConfiguration.BytecodeExceptionMode;
import org.junit.Assume;
import org.junit.Test;

/**
 * This test exercises the deoptimization in the {@link BytecodeExceptionMode} foreign call path.
 */
public class HotSpotDeoptExplicitExceptions extends SubprocessTest {

    @Override
    protected GraphBuilderConfiguration editGraphBuilderConfiguration(GraphBuilderConfiguration conf) {
        return super.editGraphBuilderConfiguration(conf).withBytecodeExceptionMode(CheckAll);
    }

    static String nullCheckSnippet(Object o) {
        return o.toString();
    }

    static int divByZeroSnippet(int x, int y) {
        return x / y;
    }

    static String classCastSnippet(Object o) {
        return (String) o;
    }

    void testBody() {
        test("nullCheckSnippet", (Object) null);
        test("divByZeroSnippet", 1, 0);
        test("classCastSnippet", Boolean.TRUE);
    }

    @Test
    public void explicitExceptions() throws IOException, InterruptedException {
        Assume.assumeTrue("required entry point is missing", ((HotSpotBackend) getBackend()).getRuntime().getVMConfig().deoptBlobUnpackWithExceptionInTLS != 0);
        if (!CreateExceptionStub.Options.HotSpotDeoptExplicitExceptions.getValue(getInitialOptions())) {
            launchSubprocess(this::testBody, "-Dgraal.HotSpotDeoptExplicitExceptions=true");
        } else {
            testBody();
        }
    }

}
