/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package org.graalvm.visualizer.upgrader;

import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Does copy of objects on filesystems.
 *
 * @author Jaroslav Tulach
 */
final class Copy extends Object {
    private FileObject sourceRoot;
    private FileObject targetRoot;
    private Set thoseToCopy;
    private PathTransformation transformation;

    private Copy(FileObject source, FileObject target, Set thoseToCopy, PathTransformation transformation) {
        this.sourceRoot = source;
        this.targetRoot = target;
        this.thoseToCopy = thoseToCopy;
        this.transformation = transformation;
    }

    /**
     * Does a selective copy of one source tree to another.
     *
     * @param source      file object to copy from
     * @param target      file object to copy to
     * @param thoseToCopy set on which contains (relativeNameOfAFileToCopy)
     *                    is being called to find out whether to copy or not
     * @throws IOException if coping fails
     */
    public static void copyDeep(FileObject source, FileObject target, Set thoseToCopy)
            throws IOException {
        copyDeep(source, target, thoseToCopy, null);
    }

    public static void copyDeep(FileObject source, FileObject target, Set thoseToCopy, PathTransformation transformation)
            throws IOException {
        Copy instance = new Copy(source, target, thoseToCopy, transformation);
        instance.copyFolder(instance.sourceRoot);
    }


    private void copyFolder(FileObject sourceFolder) throws IOException {
        FileObject[] srcChildren = sourceFolder.getChildren();
        for (int i = 0; i < srcChildren.length; i++) {
            FileObject child = srcChildren[i];
            if (child.isFolder()) {
                copyFolder(child);
                // make sure 'include xyz/.*' copies xyz folder's attributes
                if ((thoseToCopy.contains(child.getPath()) || thoseToCopy.contains(child.getPath() + "/")) && //NOI18N
                        child.getAttributes().hasMoreElements()
                ) {
                    copyFolderAttributes(child);
                }
            } else {
                if (thoseToCopy.contains(child.getPath())) {
                    copyFile(child);
                }
            }
        }
    }

    private void copyFolderAttributes(FileObject sourceFolder) throws IOException {
        FileObject targetFolder = FileUtil.createFolder(targetRoot, sourceFolder.getPath());
        if (sourceFolder.getAttributes().hasMoreElements()) {
            FileUtil.copyAttributes(sourceFolder, targetFolder);
        }
    }

    private void copyFile(FileObject sourceFile) throws IOException {
        String targetPath = (transformation != null) ? transformation.transformPath(sourceFile.getPath()) : sourceFile.getPath();
        boolean isTransformed = !targetPath.equals(sourceFile.getPath());
        FileObject tg = targetRoot.getFileObject(targetPath);
        try {
            if (tg == null) {
                // copy the file otherwise keep old content
                FileObject targetFolder = null;
                String name = null, ext = null;
                if (isTransformed) {
                    FileObject targetFile = FileUtil.createData(targetRoot, targetPath);
                    targetFolder = targetFile.getParent();
                    name = targetFile.getName();
                    ext = targetFile.getExt();
                    targetFile.delete();
                } else {
                    targetFolder = FileUtil.createFolder(targetRoot, sourceFile.getParent().getPath());
                    name = sourceFile.getName();
                    ext = sourceFile.getExt();
                }
                tg = FileUtil.copyFile(sourceFile, targetFolder, name, ext);
            }
        } catch (IOException ex) {
            if (sourceFile.getNameExt().endsWith("_hidden")) {
                return;
            }
            throw ex;
        }
        FileUtil.copyAttributes(sourceFile, tg);
    }

    public static void appendSelectedLines(File sourceFile, File targetFolder, String[] regexForSelection)
            throws IOException {
        if (!sourceFile.exists()) {
            return;
        }
        Pattern[] linePattern = new Pattern[regexForSelection.length];
        for (int i = 0; i < linePattern.length; i++) {
            linePattern[i] = Pattern.compile(regexForSelection[i]);
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        File targetFile = new File(targetFolder, sourceFile.getName());
        if (!targetFolder.exists()) {
            targetFolder.mkdirs();
        }
        assert targetFolder.exists();

        if (!targetFile.exists()) {
            targetFile.createNewFile();
        } else {
            //read original content into  ByteArrayOutputStream
            try (FileInputStream targetIS = new FileInputStream(targetFile)) {
                FileUtil.copy(targetIS, bos);
            }
        }
        assert targetFile.exists();


        //append lines into ByteArrayOutputStream
        String line = null;
        try (BufferedReader sourceReader = new BufferedReader(new FileReader(sourceFile))) {
            while ((line = sourceReader.readLine()) != null) {
                if (linePattern != null) {
                    for (int i = 0; i < linePattern.length; i++) {
                        Matcher m = linePattern[i].matcher(line);
                        if (m.matches()) {
                            bos.write(line.getBytes());
                            bos.write('\n');
                            break;
                        }
                    }
                } else {
                    bos.write(line.getBytes());
                    bos.write('\n');
                }
            }
        }

        try (ByteArrayInputStream bin = new ByteArrayInputStream(bos.toByteArray());
             FileOutputStream targetOS = new FileOutputStream(targetFile)) {
            FileUtil.copy(bin, targetOS);
        }
    }
}
