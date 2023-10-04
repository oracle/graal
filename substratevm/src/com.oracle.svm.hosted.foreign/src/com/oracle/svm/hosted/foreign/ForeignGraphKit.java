/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.foreign;

import java.lang.invoke.MethodType;
import java.util.List;

import org.graalvm.collections.Pair;
import jdk.compiler.graal.debug.DebugContext;
import jdk.compiler.graal.nodes.ValueNode;

import com.oracle.graal.pointsto.infrastructure.GraphProvider;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.hosted.phases.HostedGraphKit;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

class ForeignGraphKit extends HostedGraphKit {
    ForeignGraphKit(DebugContext debug, HostedProviders providers, ResolvedJavaMethod method, GraphProvider.Purpose purpose) {
        super(debug, providers, method, purpose);
    }

    Pair<List<ValueNode>, ValueNode> unpackArgumentsAndExtractNEP(ValueNode argumentsArray, MethodType methodType) {
        List<ValueNode> args = loadArrayElements(argumentsArray, JavaKind.Object, methodType.parameterCount() + 1);
        ValueNode nep = args.remove(args.size() - 1);
        return Pair.create(args, nep);
    }

    List<ValueNode> unboxArguments(List<ValueNode> args, MethodType methodType) {
        assert args.size() == methodType.parameterCount() : args.size() + " " + methodType.parameterCount();
        for (int i = 0; i < args.size(); ++i) {
            ValueNode argument = args.get(i);
            argument = createUnboxing(argument, JavaKind.fromJavaClass(methodType.parameterType(i)));
            args.set(i, argument);
        }
        return args;
    }

    public ValueNode boxAndReturn(ValueNode returnValue, MethodType methodType) {
        JavaKind returnKind = JavaKind.fromJavaClass(methodType.returnType());
        if (returnKind.equals(JavaKind.Void)) {
            return createReturn(createObject(null), JavaKind.Object);
        }

        var boxed = getMetaAccess().lookupJavaType(returnKind.toBoxedJavaClass());
        return createReturn(createBoxing(returnValue, returnKind, boxed), JavaKind.Object);
    }
}
