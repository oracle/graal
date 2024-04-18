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
package com.oracle.svm.truffle.api;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.core.code.ImageCodeInfo;
import com.oracle.svm.core.util.HostedStringDeduplication;
import com.oracle.svm.graal.meta.SubstrateMethod;
import com.oracle.truffle.compiler.PartialEvaluationMethodInfo;

public class SubstrateTruffleMethod extends SubstrateMethod implements TruffleMethod {

    /**
     * In practice, there are very few distinct flag combinations, i.e., only a few
     * {@link PartialEvaluationMethodInfo} instances in the image heap, so a single object reference
     * is more compact than storing individual flags directly in each method object.
     */
    private final PartialEvaluationMethodInfo truffleMethodInfo;

    @Platforms(Platform.HOSTED_ONLY.class)
    public SubstrateTruffleMethod(AnalysisMethod aMethod, ImageCodeInfo imageCodeInfo, HostedStringDeduplication stringTable, PartialEvaluationMethodInfo truffleMethodInfo) {
        super(aMethod, imageCodeInfo, stringTable);
        this.truffleMethodInfo = truffleMethodInfo;
    }

    @Override
    public PartialEvaluationMethodInfo getTruffleMethodInfo() {
        return truffleMethodInfo;
    }
}
