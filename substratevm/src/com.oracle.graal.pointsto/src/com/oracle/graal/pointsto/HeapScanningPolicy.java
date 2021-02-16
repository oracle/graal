/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.HashMap;
import java.util.Map;

import com.oracle.graal.pointsto.meta.AnalysisType;

import jdk.vm.ci.meta.JavaConstant;

/**
 * Policy to decide what constants to scan.
 * 
 * This policy is used to control which instances are really scanned. For example the analysis
 * object scanner doensn't need to scan all String instances, but it must scan at least one, if
 * present. Scanning all String instances would not add additonal information to the analysis state.
 * 
 * This policy is also used to control the collection of embedded roots. Similarly, not all Strings
 * need to be collected.
 * 
 * A side effect of skipping scanning some objects is that the object replacers will not see all
 * those objects.
 */
public abstract class HeapScanningPolicy {

    /** Decide if the constant will be stored in the global constant registry. */
    public abstract boolean trackConstant(BigBang bb, JavaConstant constant);

    /** Decide if the constant will be processed by the object scanner. */
    public abstract boolean scanConstant(BigBang bb, JavaConstant constant);

    public static HeapScanningPolicy scanAll() {
        return new ScanAllPolicy();
    }

    public static HeapScanningPolicy skipTypes(AnalysisType... skipTypes) {
        return new SkipTypesPolicy(skipTypes);
    }

    /** Scan all constants. */
    static class ScanAllPolicy extends HeapScanningPolicy {
        @Override
        public boolean trackConstant(BigBang bb, JavaConstant constant) {
            return true;
        }

        @Override
        public boolean scanConstant(BigBang bb, JavaConstant constant) {
            return true;
        }
    }

    /** Skip all but one instace of the specified types. */
    static class SkipTypesPolicy extends HeapScanningPolicy {

        /** Per type skip info. */
        static class SkipData {
            /** Mark types already seen during tracking. */
            volatile boolean seenForTracking;
            /** Mark types already seen during scanning. */
            volatile boolean seenForScanning;
        }

        /**
         * Store the state of types to skip. Although this map is used in a concurrent context (both
         * types tracking and scanning are parallelized) the map itself never gets modified after
         * being created and populated. The flags marking types as tracked/scanned will eventually
         * be set to true, but not necessarily as soon as the first constant of that type is
         * processed. An implementation providing a stronger guarantee that exactly one constant of
         * each type is processed is possible, but this implementation is preferred for performance
         * reasons since constant scanning is hot code.
         */
        final Map<AnalysisType, SkipData> skipTypes;

        SkipTypesPolicy(AnalysisType... types) {
            skipTypes = new HashMap<>();
            for (AnalysisType type : types) {
                skipTypes.put(type, new SkipData());
            }
        }

        @Override
        public boolean trackConstant(BigBang bb, JavaConstant constant) {
            AnalysisType type = bb.getMetaAccess().lookupJavaType(constant.getClass());
            SkipData data = skipTypes.get(type);
            if (data != null) {
                if (data.seenForTracking) {
                    return false;
                }
                data.seenForTracking = true;
                return true;
            }
            return true;
        }

        @Override
        public boolean scanConstant(BigBang bb, JavaConstant constant) {
            AnalysisType type = ObjectScanner.constantType(bb, constant);
            SkipData data = skipTypes.get(type);
            if (data != null) {
                if (data.seenForScanning) {
                    return false;
                }
                data.seenForScanning = true;
                return true;
            }
            return true;
        }
    }
}
