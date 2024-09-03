/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Field;
import java.nio.file.Path;

import org.junit.Assume;
import org.junit.Test;

import jdk.graal.compiler.debug.PathUtilities;
import jdk.graal.compiler.test.AddExports;

@AddExports("jdk.graal.compiler/jdk.graal.compiler.printer")
public class GraalDebugHandlersFactoryTest extends GraalCompilerTest {

    @Test
    public void createUniqueTest() throws Exception {
        Field maxFileNameLengthField = PathUtilities.class.getDeclaredField("MAX_FILE_NAME_LENGTH");
        try {
            maxFileNameLengthField.setAccessible(true);
        } catch (RuntimeException ex) {
            Assume.assumeFalse("If InaccessibleObjectException is thrown, skip the test, we are on JDK9", ex.getClass().getSimpleName().equals("InaccessibleObjectException"));
        }
        int maxFileNameLength = maxFileNameLengthField.getInt(null);
        try (TemporaryDirectory temp = new TemporaryDirectory("createUniqueTest")) {
            Path tmpDir = temp.path;
            for (boolean createDirectory : new boolean[]{true, false}) {
                for (String ext : new String[]{"", ".bgv", ".graph-strings"}) {
                    for (int i = 0; i < maxFileNameLength + 5; i++) {
                        String id = new String(new char[i]).replace('\0', 'i');
                        String label = "";
                        PathUtilities.createUnique(tmpDir.toString(), id, label, ext, createDirectory);

                        id = "";
                        label = new String(new char[i]).replace('\0', 'l');
                        PathUtilities.createUnique(tmpDir.toString(), id, label, ext, createDirectory);
                    }
                }
            }
        }
    }
}
