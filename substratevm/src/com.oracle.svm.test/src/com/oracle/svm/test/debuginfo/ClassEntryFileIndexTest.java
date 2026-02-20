/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.test.debuginfo;

import java.lang.reflect.Method;
import java.nio.file.Path;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.objectfile.debugentry.ClassEntry;
import com.oracle.objectfile.debugentry.DirEntry;
import com.oracle.objectfile.debugentry.FileEntry;
import com.oracle.objectfile.debugentry.LoaderEntry;

public class ClassEntryFileIndexTest {
    @Test
    public void testClassFileIsIndexZero() throws Exception {
        DirEntry dir = new DirEntry(Path.of("/tmp"));
        FileEntry classFile = new FileEntry("Hello.java", dir);
        FileEntry otherFile = new FileEntry("Other.java", dir);

        ClassEntry classEntry = new ClassEntry(
                        "hello.Hello",
                        0,
                        0L,
                        1L,
                        2L,
                        3L,
                        null,
                        classFile,
                        new LoaderEntry("test"));

        Method addFile = ClassEntry.class.getDeclaredMethod("addFile", FileEntry.class);
        addFile.setAccessible(true);
        addFile.invoke(classEntry, otherFile);

        classEntry.seal();

        Assert.assertEquals("Primary class file should be at index 0.", 0, classEntry.getFileIdx(classFile));
        Assert.assertEquals("Non-primary file should be shifted to index 1.", 1, classEntry.getFileIdx(otherFile));
    }
}
