/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.graalvm.nativeimage.ImageInfo;
import org.junit.Test;

import jdk.graal.compiler.core.GraalCompiler;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.serviceprovider.GraalServices;
import jdk.graal.compiler.test.SubprocessUtil;
import jdk.graal.compiler.test.SubprocessUtil.Subprocess;

/**
 * Tests that {@code jdk.graal.compiler} does not have unwanted dependencies such as
 * {@code org.graalvm.nativeimage}.
 */
public class GraalModuleDependenciesTest extends GraalCompilerTest {

    static Path getJavaExe() {
        Path javaHome = Path.of(System.getProperty("java.home"));
        boolean isWindows = GraalServices.getSavedProperty("os.name").contains("Windows");
        Path javaExe = javaHome.resolve(Path.of("bin", isWindows ? "java.exe" : "java"));
        if (!Files.isExecutable(javaExe)) {
            throw new GraalError("Java launcher %s does not exist or is not executable", javaExe);
        }
        return javaExe;
    }

    private static Subprocess run(String... command) throws InterruptedException, IOException {
        Subprocess proc = SubprocessUtil.java(List.of(command));
        if (proc.exitCode != 0) {
            fail("Non-zero exit code:%n%s", proc.preserveArgfile());
        }
        return proc;
    }

    private static String removeVersionSuffix(String moduleName) {
        int at = moduleName.indexOf('@');
        if (at == -1) {
            return moduleName;
        }
        return moduleName.substring(0, at);
    }

    @Test
    public void test() throws InterruptedException, IOException {
        String javaExe = getJavaExe().toString();
        Subprocess proc = run(javaExe,
                        "--limit-modules=jdk.graal.compiler",
                        "--list-modules");

        String graal = GraalCompiler.class.getModule().getName();
        String nativeImage = ImageInfo.class.getModule().getName();
        Set<String> moduleNames = proc.output.stream().map(GraalModuleDependenciesTest::removeVersionSuffix).collect(Collectors.toSet());
        if (!moduleNames.contains(graal)) {
            fail("Missing Graal (%s):%n%s", graal, proc.preserveArgfile());
        }
        if (moduleNames.contains(nativeImage)) {
            fail("Native Image API (%s) should not be a dependency of Graal (%s):%n%s", nativeImage, graal, proc.preserveArgfile());
        }

        proc = run(javaExe,
                        "--limit-modules=jdk.graal.compiler",
                        "-XX:+EagerJVMCI",
                        "-Djdk.graal.ShowConfiguration=info",
                        "--version");
        Pattern expect = Pattern.compile("^Using .* loaded from class files");
        if (proc.output.stream().noneMatch(line -> expect.matcher(line).find())) {
            fail("Did not find line matching %s%n%s", expect, proc.preserveArgfile());
        }
    }
}
