/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.core.jfr.utils;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.struct.SizeOf;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.collections.AbstractUninterruptibleHashtable;
import com.oracle.svm.core.collections.UninterruptibleEntry;
import com.oracle.svm.core.nmt.NmtCategory;

public final class JfrVisitedTable extends AbstractUninterruptibleHashtable {
    @Platforms(Platform.HOSTED_ONLY.class)
    public JfrVisitedTable() {
        super(NmtCategory.JFR);
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected JfrVisited[] createTable(int size) {
        return new JfrVisited[size];
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public JfrVisited[] getTable() {
        return (JfrVisited[]) super.getTable();
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected boolean isEqual(UninterruptibleEntry v0, UninterruptibleEntry v1) {
        JfrVisited a = (JfrVisited) v0;
        JfrVisited b = (JfrVisited) v1;
        return a.getId() == b.getId();
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected UninterruptibleEntry copyToHeap(UninterruptibleEntry visitedOnStack) {
        return copyToHeap(visitedOnStack, SizeOf.unsigned(JfrVisited.class));
    }
}
