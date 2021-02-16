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
package com.oracle.svm.bench.scaladacapo;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

@TargetClass(className = "org.dacapo.harness.CommandLineArgs")
final class Target_org_dacapo_harness_CommandLineArgs {

    /*
     * Parsing benchmarks from files nested in dacapo jar using numeration isn't supported with
     * native image so we implement a substitution to return the benchmark list.
     */
    @Substitute
    static List<String> extractBenchmarkSet() {
        String[] benchmarks = {"kiama", "scalariform", "factorie", "scalac", "scalap", "scalatest", "specs", "apparat", "scaladoc", "scalaxb", "tmt"};
        return Arrays.stream(benchmarks).collect(Collectors.toList());
    }
}
