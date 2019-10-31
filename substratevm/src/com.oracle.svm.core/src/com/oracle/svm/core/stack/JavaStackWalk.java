/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.stack;

import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.code.UntetheredCodeInfo;

/**
 * An in-progress Java stack walk.
 */
@RawStructure
public interface JavaStackWalk extends PointerBase {
    @RawField
    Pointer getSP();

    @RawField
    void setSP(Pointer sp);

    /**
     * The IP can be stale (outdated) if since its retrieval, {@linkplain Uninterruptible
     * interruptible} code has executed, during which a deoptimization can have happened.
     */
    @RawField
    CodePointer getPossiblyStaleIP();

    @RawField
    void setPossiblyStaleIP(CodePointer ip);

    @RawField
    UntetheredCodeInfo getIPCodeInfo();

    @RawField
    void setIPCodeInfo(UntetheredCodeInfo codeInfo);

    @RawField
    JavaFrameAnchor getAnchor();

    @RawField
    void setAnchor(JavaFrameAnchor anchor);

    // these fields are for diagnostics

    @RawField
    Pointer getStartSP();

    @RawField
    void setStartSP(Pointer sp);

    @RawField
    CodePointer getStartIP();

    @RawField
    void setStartIP(CodePointer ip);
}
