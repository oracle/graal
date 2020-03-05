/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.core.jdk.jfr.recorder.storage.operations;

import com.oracle.svm.core.jdk.jfr.recorder.checkpoint.JfrMetadataEvent;
import com.oracle.svm.core.jdk.jfr.recorder.repository.JfrChunkWriter;

public class JfrContentOperations {

    public interface Content {
        boolean process();

        int elements();
    }

    public static class MetadataEventContent implements Content {
        private final JfrChunkWriter writer;

        public MetadataEventContent(JfrChunkWriter writer) {
            this.writer = writer;
        }

        public boolean process() {
            JfrMetadataEvent.write(writer);
            return true;
        }

        public int elements() {
            return 1;
        }
    }

    public static class Write implements Content {
        private final long startTime;
        private long endTime;
        private final JfrChunkWriter writer;
        private final Content content;
        private final long startOffset;

        public Write(JfrChunkWriter writer, Content content) {
            this.startTime = System.currentTimeMillis();
            this.endTime = -1;
            this.writer = writer;
            this.content = content;
            this.startOffset = writer.getCurrentOffset();
            assert (writer.isValid());
        }

        public boolean process() {
            content.process();
            this.endTime = System.currentTimeMillis();
            return 0 != content.elements();
        }

        public int elements() {
            return content.elements();
        }
    }
}
