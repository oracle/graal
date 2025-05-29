/*
 * Copyright (c) 2012, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes.spi;

import static jdk.graal.compiler.core.common.NativeImageSupport.inBuildtimeCode;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import jdk.graal.compiler.api.replacements.Snippet;
import jdk.graal.compiler.api.replacements.Snippet.ConstantParameter;
import jdk.graal.compiler.api.replacements.Snippet.VarargsParameter;
import jdk.graal.compiler.api.replacements.Snippet.NonNullParameter;
import jdk.graal.compiler.debug.Assertions;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Local;
import jdk.vm.ci.meta.LocalVariableTable;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Encodes info about a snippet's parameters derived from annotations such as
 * {@link ConstantParameter}, {@link VarargsParameter} and {@link NonNullParameter} as well as class
 * file attributes such as {@link LocalVariableTable}. This supports libgraal where annotations and
 * class file attributes are not available.
 */
public final class SnippetParameterInfo {

    public SnippetParameterInfo(ResolvedJavaMethod method) {
        assert method.getAnnotation(Snippet.class) != null : method + " must be annotated with @" + Snippet.class.getSimpleName();
        int parameterCount = method.getSignature().getParameterCount(method.hasReceiver());
        if (parameterCount > Integer.SIZE) {
            throw new UnsupportedOperationException("too many arguments");
        }
        this.count = parameterCount;
        int constant = 0;
        int varargs = 0;
        int nonNull = 0;
        int offset = method.hasReceiver() ? 1 : 0;
        for (int i = offset; i < count; i++) {
            int bit = 1 << i;
            if (method.getParameterAnnotation(ConstantParameter.class, i - offset) != null) {
                constant |= bit;
            }
            if (method.getParameterAnnotation(VarargsParameter.class, i - offset) != null) {
                varargs |= bit;
            }
            if (method.getParameterAnnotation(NonNullParameter.class, i - offset) != null) {
                nonNull |= bit;
            }

            assert ((constant & bit) == 0) || ((varargs & bit) == 0) : "Parameter cannot be annotated with both @" + ConstantParameter.class.getSimpleName() + " and @" +
                            VarargsParameter.class.getSimpleName();
        }
        if (method.hasReceiver()) {
            // Receiver must be constant.
            assert (constant & 1) == 0 : Assertions.errorMessage(constant);
            constant |= 1;
        }
        this.constantParametersBits = constant;
        this.varargsParametersBits = varargs;
        this.nonNullParametersBits = nonNull;

        if (inBuildtimeCode()) {
            // Capture the names during image building in case the image wants them.
            initNames(method, count);
        } else {
            // Retrieve the names only when assertions are turned on.
            assert initNames(method, count);
        }
    }

    private final int count;
    private final int constantParametersBits;
    private final int varargsParametersBits;
    private final int nonNullParametersBits;

    public int getParameterCount() {
        return count;
    }

    /**
     * Is the {@code paramIdx}'th parameter {@linkplain ConstantParameter constant}?
     */
    public boolean isConstantParameter(int paramIdx) {
        return ((constantParametersBits >>> paramIdx) & 1) != 0;
    }

    /**
     * Is the {@code paramIdx}'th parameter {@linkplain VarargsParameter varargs}?
     */
    public boolean isVarargsParameter(int paramIdx) {
        return ((varargsParametersBits >>> paramIdx) & 1) != 0;
    }

    /**
     * Is the {@code paramIdx}'th parameter {@linkplain NonNullParameter non-null}?
     */
    public boolean isNonNullParameter(int paramIdx) {
        return ((nonNullParametersBits >>> paramIdx) & 1) != 0;
    }

    /**
     * Gets the name of the {@code paramIdx}'th parameter. Returns {@code "%" + paramIdx} if name
     * info is not available.
     */
    public String getParameterName(int paramIdx) {
        if (names != null) {
            return names[paramIdx];
        }
        return "%" + paramIdx;
    }

    /**
     * The parameter names, taken from a {@link LocalVariableTable}.
     */
    String[] names;

    private boolean initNames(ResolvedJavaMethod method, int parameterCount) {
        names = new String[parameterCount];
        int offset = 0;
        if (method.hasReceiver()) {
            names[0] = "this";
            offset = 1;
        }
        ResolvedJavaMethod.Parameter[] params = method.getParameters();
        if (params != null) {
            for (int i = offset; i < names.length; i++) {
                if (params[i - offset].isNamePresent()) {
                    names[i] = params[i - offset].getName();
                }
            }
        } else {
            int slotIdx = offset;
            LocalVariableTable localVariableTable = method.getLocalVariableTable();
            if (localVariableTable != null) {
                for (int i = offset; i < names.length; i++) {
                    Local local = localVariableTable.getLocal(slotIdx, 0);
                    if (local != null) {
                        names[i] = local.getName();
                    }
                    JavaKind kind = method.getSignature().getParameterKind(i - offset);
                    slotIdx += kind.getSlotCount();
                }
            }
        }
        return true;
    }

    @Override
    public String toString() {
        List<String> params = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            List<String> parts = new ArrayList<>();
            if (isConstantParameter(i)) {
                parts.add("@" + ConstantParameter.class.getSimpleName());
            }
            if (isNonNullParameter(i)) {
                parts.add("@" + NonNullParameter.class.getSimpleName());
            }
            if (isVarargsParameter(i)) {
                parts.add("@" + VarargsParameter.class.getSimpleName());
            }
            parts.add(getParameterName(i));
            params.add(String.join(" ", parts));
        }
        return "(" + String.join(", ", params) + ")";
    }

    /**
     * Gets a bit set denoting the parameters that are {@linkplain NonNullParameter non-null}
     * according to {@code info}. Bit n is set iff the n'th parameter is non-null.
     */
    public static BitSet getNonNullParameters(SnippetParameterInfo info) {
        BitSet nonNullParameters = new BitSet(info.getParameterCount());
        for (int i = 0; i < info.getParameterCount(); i++) {
            if (info.isNonNullParameter(i)) {
                nonNullParameters.set(i);
            }
        }
        return nonNullParameters;
    }
}
