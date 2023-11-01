/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.test;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import jdk.graal.compiler.bytecode.BytecodeDisassembler;
import org.junit.Assert;
import org.junit.Test;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public class BytecodeDisassemblerTest extends GraalCompilerTest {

    /**
     * Tests the disassembler by processing all the classes in {@code java.base} with the possible
     * set of disassembler configurations being cycled through randomly.
     */
    @Test
    public void test() throws Exception {
        disassembleClasses(gatherClasses());
    }

    private static List<Class<?>> gatherClasses() throws IOException {
        List<Class<?>> classes = new ArrayList<>();
        FileSystem fs = FileSystems.newFileSystem(URI.create("jrt:/"), Collections.emptyMap());
        Path top = fs.getPath("/modules/");
        Files.find(top, Integer.MAX_VALUE,
                        (path, attrs) -> attrs.isRegularFile()).forEach(p -> {
                            int nameCount = p.getNameCount();
                            if (nameCount > 2) {
                                String base = p.getName(nameCount - 1).toString();
                                if (base.endsWith(".class") && !base.equals("module-info.class")) {
                                    String module = p.getName(1).toString();
                                    if (module.equals("java.base")) {
                                        String className = p.subpath(2, nameCount).toString().replace('/', '.');
                                        className = className.replace('/', '.').substring(0, className.length() - ".class".length());
                                        Class<?> cl = loadClass(className);
                                        if (cl != null) {
                                            classes.add(cl);
                                        }
                                    }
                                }
                            }
                        });
        return classes;
    }

    private void disassembleClasses(List<Class<?>> classes) {
        Random random = getRandomInstance();
        String[] newLines = {null, "\n", "\r\n"};
        for (Class<?> c : classes) {
            String newLine = newLines[random.nextInt(3)];
            boolean multiline = newLine != null && random.nextBoolean();
            boolean format = random.nextBoolean();
            BytecodeDisassembler.CPIFunction f = random.nextBoolean() ? BytecodeDisassembler.CPIFunction.normalizer() : BytecodeDisassembler.CPIFunction.Identity;
            BytecodeDisassembler disassembler = new BytecodeDisassembler(multiline, newLine, format, f);

            ResolvedJavaType type = getMetaAccess().lookupJavaType(c);
            ResolvedJavaMethod[] methods = type.getDeclaredMethods();
            for (ResolvedJavaMethod m : methods) {
                int codeSize = m.getCodeSize();
                String dis = disassembler.disassemble(m);
                if (codeSize <= 0) {
                    Assert.assertNull(m.toString(), dis);
                } else {
                    Assert.assertNotEquals(m.toString(), 0, dis.length());
                }
            }
        }
    }

    private static Class<?> loadClass(String className) {
        try {
            return Class.forName(className, false, ClassLoader.getPlatformClassLoader());
        } catch (ClassNotFoundException e) {
            throw new AssertionError(e);
        }
    }
}
