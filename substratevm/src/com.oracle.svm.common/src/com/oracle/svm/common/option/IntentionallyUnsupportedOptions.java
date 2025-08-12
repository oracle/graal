/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.common.option;

import java.util.HashSet;
import java.util.Set;

import jdk.graal.compiler.core.common.util.CompilationAlarm;
import jdk.graal.compiler.hotspot.CompilerConfigurationFactory;
import jdk.graal.compiler.options.OptionKey;

/**
 * Native image uses its own mechanisms to handle certain options, resulting in some Graal options
 * being completely unused in native image. Being unused results in the options being silently
 * ignored if set by the user. All such options should be listed here so that the native image
 * options processing can reject them as unsupported.
 */
public final class IntentionallyUnsupportedOptions {

    private static final Set<OptionKey<?>> unsupportedOptions = new HashSet<>();

    static {
        unsupportedOptions.add(CompilerConfigurationFactory.Options.CompilerConfiguration);
        unsupportedOptions.add(CompilationAlarm.Options.CompilationNoProgressPeriod);
    }

    private IntentionallyUnsupportedOptions() {
        throw new IllegalStateException("Should not be initialized");
    }

    public static boolean contains(OptionKey<?> optionKey) {
        return unsupportedOptions.contains(optionKey);
    }
}
