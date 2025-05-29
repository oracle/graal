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

package org.graalvm.visualizer.upgrader.impl;

import org.graalvm.visualizer.upgrader.Upgrader;
import org.openide.ErrorManager;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Updates (custom) filter definitions used in old IGV / graal to work with
 * qualified names in 'class' attribute.
 *
 * @author sdedic
 */
public final class UpgradeFromOldDev extends Upgrader {
    private static final String IMPORTING_VERSION = "dev"; // NOI18N
    /**
     * Should match colorize("class", "someSimpleIdentifier"....
     */
    private static final String PATTERN_STRING = "(?<func>(?:colorize|split))\\(\\p{Space}*['\"]class['\"]\\p{Space}*,\\p{Space}*.(?<value>['\"][\\p{Alnum}_$]+['\"]).*"; // NOI18N
    private static final Pattern USE_CLASS_PATTERN = Pattern.compile(PATTERN_STRING);
    private static final String GROUP_VALUE = "value"; // NOI18N

    @Override
    protected void doVersionImport() {
        doImport();
    }

    public static void doImport() {
        FileObject root = FileUtil.getConfigFile("Filters"); // NOI18N
        if (root == null) {
            return;
        }
        for (FileObject f : root.getChildren()) {
            StringBuilder sb = new StringBuilder();
            boolean changed = false;

            try {
                for (String line : f.asLines("UTF-8")) { // NOI18N
                    Matcher m = USE_CLASS_PATTERN.matcher(line);
                    int start = 0;
                    String orig = line;
                    while (start < line.length() && m.find(start)) {
                        start = m.end();

                        String func = m.group("func"); // NOI18N
                        String val = m.group(GROUP_VALUE);

                        if (func == null || val == null) {
                            continue;
                        }
                        int valStart = m.start(GROUP_VALUE);
                        int valEnd = m.end(GROUP_VALUE);

                        String replacement = "classSimpleName(" + val + ")"; // NOI18N
                        line = line.substring(0, valStart)
                                + replacement
                                + line.substring(valEnd);
                        start += (replacement.length() - val.length());
                    }
                    // repackage filter references
                    line = line.replaceAll("com.sun.hotspot.igv", "org.graalvm.visualizer"); // NOI18N
                    sb.append(line).append("\n"); // NOI18N
                    changed |= orig.equals(line);
                }

                if (changed) {
                    try (Writer w = new OutputStreamWriter(f.getOutputStream(), "UTF-8")) { // NOI18N
                        w.write(sb.toString());
                    }
                }
            } catch (IOException ex) {
                ErrorManager.getDefault().notify(ErrorManager.WARNING, ex);
            }
        }
    }

    @Override
    protected String getImportingVersion() {
        return IMPORTING_VERSION;
    }

    @Override
    protected String getChangesInfo() {
        return "Updating filter definitions used in old IGV, using pattern: " + PATTERN_STRING;
    }
}
