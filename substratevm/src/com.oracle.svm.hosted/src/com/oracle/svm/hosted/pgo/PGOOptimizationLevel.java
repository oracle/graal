/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.pgo;

import org.graalvm.collections.EconomicMap;

import com.oracle.svm.core.SubstrateOptions;

import jdk.graal.compiler.options.OptionKey;

/**
 * The instrumented build ({@code --pgo-instrument}) runs at {@code -O3}, the level of the optimized
 * build. Below {@code -O3} the priority inliner explores less, so the instrumented build would
 * profile code that only {@code -O3} inlines without its caller's context.
 * <p>
 * The builder sets {@code -O} to its default before it parses the command line, so an explicit
 * {@code -O2} cannot be told apart from none. A level that still holds the default is raised to
 * {@code 3} through {@link SubstrateOptions#Optimize}, which also applies {@code -O3}'s own side
 * effects, as {@code -O3} does. Any other level given before the PGO option, and any {@code -O}
 * given after it, is kept.
 */
final class PGOOptimizationLevel {

    private PGOOptimizationLevel() {
    }

    static void raiseDefaultToO3(EconomicMap<OptionKey<?>, Object> values) {
        Object level = values.get(SubstrateOptions.Optimize);
        if (level == null || SubstrateOptions.Optimize.getDefaultValue().equals(level)) {
            SubstrateOptions.Optimize.update(values, "3");
        }
    }
}
