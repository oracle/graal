/*
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.test.builtins;

import org.graalvm.compiler.truffle.options.PolyglotCompilerOptions;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.options.OptionDescriptor;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Looks up the value of an option in {@link PolyglotCompilerOptions}. In the future this builtin
 * might be extended to lookup other options as well.
 */
@NodeInfo(shortName = "getOption")
public abstract class SLGetOptionBuiltin extends SLGraalRuntimeBuiltin {

    @Specialization
    @TruffleBoundary
    public Object getOption(TruffleString name,
                    @Cached TruffleString.ToJavaStringNode toJavaStringNode) {
        final OptionDescriptor option = PolyglotCompilerOptions.getDescriptors().get(toJavaStringNode.execute(name));
        if (option == null) {
            throw new SLAssertionError("No such option named \"" + name + "\" found in " + PolyglotCompilerOptions.class.getName(), this);
        }
        return convertValue(((OptimizedCallTarget) getRootNode().getCallTarget()).getOptionValue(option.getKey()));
    }

    private static Object convertValue(Object value) {
        // Improve this method as you need it.
        if (value instanceof Integer) {
            return (long) (int) value;
        }
        return value;
    }

}
