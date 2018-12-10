/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.truffle.runtime.TruffleRuntimeOptions;
import org.graalvm.compiler.truffle.runtime.TruffleRuntimeOptions.TruffleRuntimeOptionsOverrideScope;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.sl.runtime.SLFunction;
import org.graalvm.options.OptionDescriptor;

/**
 * Overrides the value of an option in {@link TruffleRuntimeOptions}, calls a given function and
 * then undoes the override.
 */
@NodeInfo(shortName = "callWithOptionOverride")
public abstract class SLCallWithOptionOverrideBuiltin extends SLGraalRuntimeBuiltin {

    private static final Object[] EMPTY_ARGS = new Object[0];

    @Child private IndirectCallNode indirectCall = Truffle.getRuntime().createIndirectCallNode();

    @Specialization
    public SLFunction callWithOptionOverride(SLFunction function, String name, Object value) {
        TruffleRuntimeOptionsOverrideScope scope = override(name, value);
        OptimizedCallTarget target = ((OptimizedCallTarget) function.getCallTarget());
        indirectCall.call(target, EMPTY_ARGS);
        close(scope);
        return function;
    }

    @TruffleBoundary
    private TruffleRuntimeOptionsOverrideScope override(String name, Object value) {
        final OptionDescriptor option = TruffleRuntimeOptions.getOptions().getDescriptors().get(name);
        if (option == null) {
            throw new SLAssertionError("No such option named \"" + name + "\" found in " + TruffleRuntimeOptions.class.getName(), this);
        }
        return TruffleRuntimeOptions.overrideOptions(option.getKey(), convertValue(value));
    }

    private static Object convertValue(Object value) {
        // Improve this method as you need it.
        if (value instanceof Long) {
            long longValue = (long) value;
            if (longValue == (int) longValue) {
                return (int) longValue;
            }
        }
        return value;
    }

    @TruffleBoundary
    private static void close(TruffleRuntimeOptionsOverrideScope scope) {
        scope.close();
    }
}
