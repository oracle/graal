/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2023, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.core.jfr.oldobject;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.jdk.JavaLangSubstitutions;
import com.oracle.svm.core.jfr.JfrChunkFileWriter.StringEncoding;
import com.oracle.svm.core.jfr.JfrNativeEventWriter;
import com.oracle.svm.core.jfr.JfrNativeEventWriterData;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

public class JfrOldObjectDescriptionWriter {
    private static final int OBJECT_DESCRIPTION_MAX_SIZE = 100;
    private final char[] buffer = new char[OBJECT_DESCRIPTION_MAX_SIZE];
    private int index;
    private StringEncoding encoding;

    @Platforms(Platform.HOSTED_ONLY.class)
    public JfrOldObjectDescriptionWriter() {
    }

    @Uninterruptible(reason = "Locking without transition and result is only valid until epoch changes.", callerMustBe = true)
    void write(String text) {
        if (text == null) {
            encoding = StringEncoding.NULL;
            return;
        }

        if (text.isEmpty()) {
            encoding = StringEncoding.EMPTY_STRING;
            return;
        }

        encoding = StringEncoding.UTF8_BYTE_ARRAY;
        final int length = text.length();
        for (int i = 0; i < length && index < buffer.length - 3; i++) {
            char ch = JavaLangSubstitutions.StringUtil.charAt(text, i);
            buffer[index] = ch;
            index++;
        }

        if (index == buffer.length - 3) {
            buffer[index++] = '.';
            buffer[index++] = '.';
            buffer[index++] = '.';
        }
    }

    @Uninterruptible(reason = "Locking without transition and result is only valid until epoch changes.", callerMustBe = true)
    void finish(JfrNativeEventWriterData data) {
        switch (encoding) {
            case NULL -> JfrNativeEventWriter.putChars(data, null, 0);
            case EMPTY_STRING -> JfrNativeEventWriter.putChars(data, buffer, 0);
            case UTF8_BYTE_ARRAY -> JfrNativeEventWriter.putChars(data, buffer, index);
        }

        index = 0;
    }
}
