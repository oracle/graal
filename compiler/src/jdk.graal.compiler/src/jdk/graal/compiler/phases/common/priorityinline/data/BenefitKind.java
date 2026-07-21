/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.phases.common.priorityinline.data;

import java.util.EnumSet;
import java.util.function.Supplier;

import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeInputList;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.java.AbstractNewObjectNode;
import jdk.graal.compiler.nodes.virtual.AllocatedObjectNode;

public enum BenefitKind {
    Constant,
    NonNull,
    Exact,
    Type,
    Devirtualization,
    NewAllocation;

    public static EnumSet<BenefitKind> estimateBenefit(Invoke invoke) {
        return estimateBenefit(invoke, () -> StampFactory.createParameterStamps(invoke.asNode().graph().getAssumptions(), invoke.callTarget().targetMethod()));
    }

    public static EnumSet<BenefitKind> estimateBenefit(Invoke invoke, Supplier<Stamp[]> parameterStamps) {
        EnumSet<BenefitKind> result = estimateBenefit(getArgumentStamps(invoke), parameterStamps.get());
        for (ValueNode node : invoke.callTarget().arguments()) {
            if (node instanceof AllocatedObjectNode || node instanceof AbstractNewObjectNode) {
                result.add(NewAllocation);
            }
        }
        return result;
    }

    private static EnumSet<BenefitKind> estimateBenefit(Stamp[] argumentStamps, Stamp[] parameterStamps) {
        assert argumentStamps.length == parameterStamps.length : argumentStamps + " !=(length does not match) " + parameterStamps;
        EnumSet<BenefitKind> result = EnumSet.noneOf(BenefitKind.class);
        for (int i = 0; i < argumentStamps.length; ++i) {
            Stamp argStamp = argumentStamps[i];
            Stamp parameterStamp = parameterStamps[i];
            collectBenefits(result, argStamp, parameterStamp);
        }

        return result;
    }

    private static void collectBenefits(EnumSet<BenefitKind> result, Stamp argStamp, Stamp parameterStamp) {
        if (argStamp.asConstant() != null) {
            // Argument reduces to a constant.
            result.add(BenefitKind.Constant);
            if (argStamp instanceof ObjectStamp && ((ObjectStamp) argStamp).nonNull()) {
                result.add(BenefitKind.NonNull);
                result.add(BenefitKind.Exact);
            }
        } else if (argStamp.getStackKind().isObject()) {
            assert parameterStamp.getStackKind().isObject() : "Should be object " + parameterStamp.getStackKind() + " " + parameterStamp;
            // Argument is an object.
            ObjectStamp argStampObj = (ObjectStamp) argStamp;
            ObjectStamp parameterStampObj = (ObjectStamp) parameterStamp;

            if (argStampObj.nonNull() && !parameterStampObj.nonNull()) {
                // We know that the argument is never null.
                result.add(BenefitKind.NonNull);
            }

            if (argStampObj.isExactType() && argStampObj.nonNull() && !parameterStampObj.isExactType()) {
                // The argument is likely a newly allocated object.
                result.add(BenefitKind.Exact);
            }

            if (argStampObj.type() != null && !argStampObj.type().equals(parameterStampObj.type()) &&
                            (parameterStampObj.type() == null || parameterStampObj.type().isAssignableFrom(argStampObj.type()))) {
                // We know a better type for the argument.
                assert parameterStampObj.type() == null || parameterStampObj.type().isAssignableFrom(argStampObj.type()) : "Must have a better stamp " + parameterStampObj.type() + " vs " +
                                argStampObj.type();
                result.add(BenefitKind.Type);
            }
        } else {
            assert parameterStamp.getStackKind().isPrimitive() : "Must be primitive " + parameterStamp.getStackKind() + " " + parameterStamp;
        }
    }

    public static Stamp[] getArgumentStamps(Invoke invoke) {
        NodeInputList<ValueNode> arguments = invoke.callTarget().arguments();
        int size = arguments.size();
        Stamp[] result = new Stamp[size];
        for (int i = 0; i < size; ++i) {
            result[i] = arguments.get(i).stamp(NodeView.DEFAULT);
        }
        return result;
    }

    public static boolean containsTypeOrConstant(EnumSet<BenefitKind> benefits) {
        return benefits.contains(Type) || benefits.contains(Constant);
    }
}
