/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

import javax.script.ScriptEngine;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.util.VMError;

@TargetClass(className = "jdk.nashorn.api.scripting.NashornScriptEngineFactory", onlyWith = JDK14OrEarlier.class)
public final class Target_jdk_nashorn_api_scripting_NashornScriptEngineFactory {
    @Substitute
    @SuppressWarnings({"unused", "static-method"})
    public ScriptEngine getScriptEngine() {
        throw VMError.unsupportedFeature(Util.errorMessage);
    }

    @Substitute
    @SuppressWarnings({"unused", "static-method"})
    private ScriptEngine newEngine(String[] args, ClassLoader appLoader, Target_jdk_nashorn_api_scripting_ClassFilter classFilter) {
        throw VMError.unsupportedFeature(Util.errorMessage);
    }
}

class Util {
    static final String errorMessage = "The Nashorn scripting engine is not supported by native-image due to its use of invokedynamic. " +
                    "The native-image static analysis runs under a closed-world assumption " +
                    "which requires that all called methods and their call sites are known ahead-of-time, " +
                    "whereas invokedynamic can introduce calls at run time or change the method that is invoked." +
                    "Therefore, only specific use cases of invokedynamic are supported, " +
                    "namely when the invokedynamic can be reduced to a single virtual call or field access during native image generation.";
}
