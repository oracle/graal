/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, 2021, Alibaba Group Holding Limited. All rights reserved.
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
package com.oracle.svm.core.util;

import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.ClassWriter;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class JavaClassUtil {

    private static final String CONSTANT_PLACE_HOLDER = "CONSTANT_PLACE_HOLDER";

    /**
     * Get SHA -256 value of the class for verification usage. But the name of SourceFile attribute
     * is set to empty, because it might get changed from run to run and it's not really used in the
     * class.
     *
     * @return byte array SHA value without SourceFile name
     * @throws NoSuchAlgorithmException
     */
    public static String getSHAWithoutSourceFileInfo(byte[] classDefinition) throws NoSuchAlgorithmException {
        ClassReader cr = new ClassReader(classDefinition);
        ClassWriter writer = new ClassWriter(0);
        ClassVisitor cv = new ClassVisitor(jdk.internal.org.objectweb.asm.Opcodes.ASM5, writer) {
            @Override
            public void visitSource(String source, String debug) {
                // Set constant value to SourceFIle attribute
                super.visitSource(CONSTANT_PLACE_HOLDER, null);
            }
        };
        cr.accept(cv, 0);
        byte[] classBytes = writer.toByteArray();
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return fingerPrint(digest.digest(classBytes), false);
    }

    public static String getClassName(byte[] classDefinition) {
        ClassReader cr = new ClassReader(classDefinition);
        return cr.getClassName().replace('/', '.');
    }

    /**
     * Copied from org.graalvm.component.installer.SystemUtils.
     *
     */
    public static String fingerPrint(byte[] digest, boolean delimiter) {
        if (digest == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(digest.length * 3);
        for (int i = 0; i < digest.length; i++) {
            if (delimiter && i > 0) {
                sb.append(':');
            }
            // Checkstyle: stop
            sb.append(String.format("%02x", (digest[i] & 0xff)));
            // Checkstyle: resume
        }
        return sb.toString();
    }
}
