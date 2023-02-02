/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto;

import com.oracle.graal.pointsto.meta.AnalysisType;

/**
 * The heap crawling algorithm used for updating type flow is is mostly implemented in
 * {@link AnalysisObjectScanner} and its superclass {@link ObjectScanner}.
 * 
 * This interface is a first small step in a bigger effort to separate heap crawling from the
 * analysis. If these two parts are cleanly separated, it should allow mixing different algorithms
 * and configurations more easily.
 */
public interface HeapScanning {

    /**
     * @return types that should not be crawled
     */
    AnalysisType[] skippedHeapTypes();

    /**
     * @return policy deciding what to scan
     */
    HeapScanningPolicy scanningPolicy();
}
