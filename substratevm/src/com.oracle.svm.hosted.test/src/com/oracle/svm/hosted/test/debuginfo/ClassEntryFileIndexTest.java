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
package com.oracle.svm.hosted.test.debuginfo;

import java.nio.file.Path;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.objectfile.debugentry.ClassEntry;
import com.oracle.objectfile.debugentry.DirEntry;
import com.oracle.objectfile.debugentry.FileEntry;
import com.oracle.objectfile.debugentry.LoaderEntry;
import com.oracle.objectfile.debugentry.MethodEntry;

public class ClassEntryFileIndexTest {
    private static final DirEntry DIR = new DirEntry(Path.of("/tmp"));

    private static ClassEntry newClassEntry(FileEntry classFile, FileEntry... methodFiles) {
        ClassEntry classEntry = new ClassEntry("hello.Hello", 0, 0L, 1L, 2L, 3L, null, classFile, new LoaderEntry("test"));
        for (FileEntry methodFile : methodFiles) {
            classEntry.addMethod(new MethodEntry(methodFile, 1, "m", classEntry, null, 0, List.of(), "m", false, false, false, false, -1, 0));
        }
        classEntry.seal();
        return classEntry;
    }

    @Test
    public void testClassFileIsIndexZero() {
        FileEntry classFile = new FileEntry("Hello.java", DIR);
        FileEntry otherFile = new FileEntry("Other.java", DIR);
        ClassEntry classEntry = newClassEntry(classFile, otherFile, new FileEntry("Hello.java", DIR));

        Assert.assertTrue(classEntry.isClassFile(new FileEntry("Hello.java", DIR)));
        Assert.assertEquals("Primary class file should be at index 0.", 0, classEntry.getFileIdx(classFile));
        Assert.assertEquals("Non-primary file should be at index 1.", 1, classEntry.getFileIdx(otherFile));
    }

    @Test
    public void testFilesStartAtIndexOneWithoutClassFile() {
        FileEntry otherFile = new FileEntry("Other.java", DIR);
        FileEntry anotherFile = new FileEntry("Another.java", DIR);
        ClassEntry classEntry = newClassEntry(null, otherFile, anotherFile);

        Assert.assertFalse(classEntry.isClassFile(otherFile));
        Assert.assertEquals("Without a class file, index 0 stays reserved.", 1, classEntry.getFileIdx(otherFile));
        Assert.assertEquals(2, classEntry.getFileIdx(anotherFile));
    }
}
