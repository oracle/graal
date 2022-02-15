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
package com.oracle.svm.hosted.analysis;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.reports.CallTreePrinter;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.util.UserError;
import com.oracle.graal.pointsto.reports.AnalysisReportsOptions;
import com.oracle.svm.hosted.NativeImageOptions;

/**
 * Checks if all user defined limitations such as the number of types are satisfied.
 */
public class UserLimitationsChecker {

    public static void check(BigBang bb) {
        HostedOptionKey<Integer> maxReachableTypesOption = NativeImageOptions.MaxReachableTypes;
        int maxReachableTypes = maxReachableTypesOption.getValue();
        if (maxReachableTypes >= 0) {
            CallTreePrinter callTreePrinter = new CallTreePrinter(bb);
            callTreePrinter.buildCallTree();
            int numberOfTypes = callTreePrinter.classesSet(false).size();
            if (numberOfTypes > maxReachableTypes) {
                throw UserError.abort("Reachable %d types but only %d allowed (because the %s option is set). To see all reachable types use %s; to change the maximum number of allowed types use %s.",
                                numberOfTypes, maxReachableTypes, maxReachableTypesOption.getName(), AnalysisReportsOptions.PrintAnalysisCallTree.getName(), maxReachableTypesOption.getName());
            }
        }
    }
}
