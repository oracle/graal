/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jfr;

import java.io.IOException;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.thread.VMOperation;

/**
 * This class is only used for Java-level JFR events.
 */
public class JfrStringRepository implements JfrRepository {
    @Platforms(Platform.HOSTED_ONLY.class)
    public JfrStringRepository() {
    }

    @Uninterruptible(reason = "Epoch must not change while in this method.")
    public boolean add(boolean expectedEpoch, long id, String value) {
        boolean currentEpoch = SubstrateJVM.get().getEpoch();
        if (currentEpoch == expectedEpoch) {
            // TODO: insert the string into an uninterruptible datastructure.
        }
        return currentEpoch;
    }

    @Override
    public void write(JfrChunkWriter writer) throws IOException {
        assert VMOperation.isInProgressAtSafepoint();
        writer.writeCompressedLong(JfrTypes.String.getId());
        writer.writeCompressedLong(0);

        // TODO: write encoding (null and empty String have special values as well)
        // TODO: write string data in the correct encoding
    }
}
