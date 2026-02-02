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
package com.oracle.svm.interpreter.ristretto.meta;

import java.util.BitSet;
import java.util.function.Function;

import com.oracle.svm.core.graal.meta.SubstrateReplacements;
import com.oracle.svm.graal.meta.SubstrateField;
import com.oracle.svm.graal.meta.SubstrateMethod;
import com.oracle.svm.graal.meta.SubstrateType;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaField;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaType;

import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.spi.DelegatingReplacements;
import jdk.graal.compiler.options.OptionValues;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * The replacements implementation for the Ristretto JIT compiler at runtime. All snippets and
 * method substitutions are pre-compiled at image generation (and not on demand when a snippet is
 * instantiated) - additionally all JVMCI references ({@link ResolvedJavaMethod} ,
 * {@link jdk.vm.ci.meta.ResolvedJavaType} and {@link jdk.vm.ci.meta.ResolvedJavaField}) from the
 * snippet graphs are replaced with their Ristretto counterparts.
 */
public class RistrettoReplacements extends DelegatingReplacements {

    public RistrettoReplacements(SubstrateReplacements svmReplacements) {
        super(svmReplacements);
    }

    @Override
    public StructuredGraph getSnippet(ResolvedJavaMethod method, ResolvedJavaMethod recursiveEntry, Object[] args, BitSet nonNullParameters, boolean trackNodeSourcePosition,
                    NodeSourcePosition replaceePosition, OptionValues options) {
        Function<Object, Object> t = o -> {
            if (o instanceof SubstrateType substrateType) {
                return RistrettoType.create((InterpreterResolvedJavaType) substrateType.getHub().getInterpreterType());
            } else if (o instanceof SubstrateMethod substrateMethod &&
                            /*
                             * Note that we exclude native methods and other snippets here. If we
                             * call a native methods, it's normally a node intrinsic. If we call
                             * another snippet method we also will not have interpreter methods for
                             * it.
                             */
                            !isSnippet(substrateMethod) && !substrateMethod.isNative()) {
                RistrettoMethod rMethod = replaceWithRistrettoMethod(substrateMethod);
                if (rMethod != null) {
                    return rMethod;
                }
                throw GraalError.shouldNotReachHere("Cannot find iMethod for " + substrateMethod.getName());
            } else if (o instanceof SubstrateField substrateField) {
                RistrettoField rField = replaceWithRistrettoField(substrateField);
                if (rField != null) {
                    return rField;
                }
                throw GraalError.shouldNotReachHere("Cannot find iField for " + substrateField.getName());
            }
            return o;
        };
        return ((SubstrateReplacements) delegate).getSnippet(method, args, trackNodeSourcePosition, options, t);
    }

    private static RistrettoMethod replaceWithRistrettoMethod(SubstrateMethod substrateMethod) {
        InterpreterResolvedJavaType iType = (InterpreterResolvedJavaType) substrateMethod.getDeclaringClass().getHub().getInterpreterType();
        for (var iMeth : iType.getDeclaredMethods()) {
            if (iMeth.getName().equals(substrateMethod.getName()) && iMeth.getSignature().toMethodDescriptor().equals(substrateMethod.getSignature().toMethodDescriptor())) {
                return RistrettoMethod.create(iMeth);
            }
        }
        return null;
    }

    private static RistrettoField replaceWithRistrettoField(SubstrateField substrateField) {
        InterpreterResolvedJavaType iType = (InterpreterResolvedJavaType) substrateField.getDeclaringClass().getHub().getInterpreterType();
        if (substrateField.isStatic()) {
            for (var iField : iType.getStaticFields()) {
                if (iField.getName().equals(substrateField.getName())) {
                    return RistrettoField.create((InterpreterResolvedJavaField) iField);
                }
            }
        } else {
            for (var iField : iType.getInstanceFields(true)) {
                if (iField.getName().equals(substrateField.getName())) {
                    return RistrettoField.create((InterpreterResolvedJavaField) iField);
                }
            }
        }
        return null;
    }

}
