/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jfr;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.heap.GCCause;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.guest.staging.util.AbstractImageHeapList;

public class JfrGCCauseSerializer implements JfrSerializer {
    @Platforms(Platform.HOSTED_ONLY.class)
    public JfrGCCauseSerializer() {
    }

    @Override
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Used on OOME for emergency dumps")
    public void write(JfrChunkWriter writer) {
        // GCCauses has null entries
        AbstractImageHeapList<GCCause> causes = GCCause.getGCCauses();
        int nonNullItems = 0;

        for (int i = 0; i < causes.size(); i++) {
            if (causes.get(i) != null) {
                nonNullItems++;
            }
        }

        assert nonNullItems > 0;

        writer.writeCompressedLong(JfrType.GCCause.getId());
        writer.writeCompressedLong(nonNullItems);
        for (int i = 0; i < causes.size(); i++) {
            if (causes.get(i) != null) {
                writer.writeCompressedLong(causes.get(i).getId());
                writer.writeString(causes.get(i).getName());
            }
        }
    }
}
