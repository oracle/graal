/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.replacements;

import static com.oracle.graal.compiler.common.GraalOptions.*;
import static com.oracle.graal.replacements.SnippetTemplate.*;

import java.util.*;

import com.oracle.graal.api.replacements.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.phases.util.*;
import com.oracle.graal.replacements.SnippetTemplate.AbstractTemplates;
import com.oracle.graal.replacements.SnippetTemplate.Arguments;
import com.oracle.graal.replacements.SnippetTemplate.SnippetInfo;
import com.oracle.jvmci.code.*;
import com.oracle.jvmci.debug.*;
import com.oracle.jvmci.meta.*;

public class BoxingSnippets implements Snippets {

    @Snippet
    public static Object booleanValueOf(boolean value) {
        valueOfCounter.inc();
        return PiNode.piCast(Boolean.valueOf(value), StampFactory.forNodeIntrinsic());
    }

    @Snippet
    public static Object byteValueOf(byte value) {
        valueOfCounter.inc();
        return PiNode.piCast(Byte.valueOf(value), StampFactory.forNodeIntrinsic());
    }

    @Snippet
    public static Object charValueOf(char value) {
        valueOfCounter.inc();
        return PiNode.piCast(Character.valueOf(value), StampFactory.forNodeIntrinsic());
    }

    @Snippet
    public static Object doubleValueOf(double value) {
        valueOfCounter.inc();
        return PiNode.piCast(Double.valueOf(value), StampFactory.forNodeIntrinsic());
    }

    @Snippet
    public static Object floatValueOf(float value) {
        valueOfCounter.inc();
        return PiNode.piCast(Float.valueOf(value), StampFactory.forNodeIntrinsic());
    }

    @Snippet
    public static Object intValueOf(int value) {
        valueOfCounter.inc();
        return PiNode.piCast(Integer.valueOf(value), StampFactory.forNodeIntrinsic());
    }

    @Snippet
    public static Object longValueOf(long value) {
        valueOfCounter.inc();
        return PiNode.piCast(Long.valueOf(value), StampFactory.forNodeIntrinsic());
    }

    @Snippet
    public static Object shortValueOf(short value) {
        valueOfCounter.inc();
        return PiNode.piCast(Short.valueOf(value), StampFactory.forNodeIntrinsic());
    }

    @Snippet
    public static boolean booleanValue(Boolean value) {
        valueOfCounter.inc();
        return value.booleanValue();
    }

    @Snippet
    public static byte byteValue(Byte value) {
        valueOfCounter.inc();
        return value.byteValue();
    }

    @Snippet
    public static char charValue(Character value) {
        valueOfCounter.inc();
        return value.charValue();
    }

    @Snippet
    public static double doubleValue(Double value) {
        valueOfCounter.inc();
        return value.doubleValue();
    }

    @Snippet
    public static float floatValue(Float value) {
        valueOfCounter.inc();
        return value.floatValue();
    }

    @Snippet
    public static int intValue(Integer value) {
        valueOfCounter.inc();
        return value.intValue();
    }

    @Snippet
    public static long longValue(Long value) {
        valueOfCounter.inc();
        return value.longValue();
    }

    @Snippet
    public static short shortValue(Short value) {
        valueOfCounter.inc();
        return value.shortValue();
    }

    public static FloatingNode canonicalizeBoxing(BoxNode box, MetaAccessProvider metaAccess, ConstantReflectionProvider constantReflection) {
        ValueNode value = box.getValue();
        if (value.isConstant()) {
            JavaConstant sourceConstant = value.asJavaConstant();
            if (sourceConstant.getKind() != box.getBoxingKind() && sourceConstant.getKind().isNumericInteger()) {
                switch (box.getBoxingKind()) {
                    case Boolean:
                        sourceConstant = JavaConstant.forBoolean(sourceConstant.asLong() != 0L);
                        break;
                    case Byte:
                        sourceConstant = JavaConstant.forByte((byte) sourceConstant.asLong());
                        break;
                    case Char:
                        sourceConstant = JavaConstant.forChar((char) sourceConstant.asLong());
                        break;
                    case Short:
                        sourceConstant = JavaConstant.forShort((short) sourceConstant.asLong());
                        break;
                }
            }
            JavaConstant boxedConstant = constantReflection.boxPrimitive(sourceConstant);
            if (boxedConstant != null && sourceConstant.getKind() == box.getBoxingKind()) {
                return ConstantNode.forConstant(boxedConstant, metaAccess, box.graph());
            }
        }
        return null;
    }

    public static class Templates extends AbstractTemplates {

        private final EnumMap<Kind, SnippetInfo> boxSnippets = new EnumMap<>(Kind.class);
        private final EnumMap<Kind, SnippetInfo> unboxSnippets = new EnumMap<>(Kind.class);

        public Templates(Providers providers, SnippetReflectionProvider snippetReflection, TargetDescription target) {
            super(providers, snippetReflection, target);
            for (Kind kind : new Kind[]{Kind.Boolean, Kind.Byte, Kind.Char, Kind.Double, Kind.Float, Kind.Int, Kind.Long, Kind.Short}) {
                boxSnippets.put(kind, snippet(BoxingSnippets.class, kind.getJavaName() + "ValueOf"));
                unboxSnippets.put(kind, snippet(BoxingSnippets.class, kind.getJavaName() + "Value"));
            }
        }

        public void lower(BoxNode box, LoweringTool tool) {
            FloatingNode canonical = canonicalizeBoxing(box, providers.getMetaAccess(), providers.getConstantReflection());
            // if in AOT mode, we don't want to embed boxed constants.
            if (canonical != null && !ImmutableCode.getValue()) {
                box.graph().replaceFixedWithFloating(box, canonical);
            } else {
                Arguments args = new Arguments(boxSnippets.get(box.getBoxingKind()), box.graph().getGuardsStage(), tool.getLoweringStage());
                args.add("value", box.getValue());

                SnippetTemplate template = template(args);
                Debug.log("Lowering integerValueOf in %s: node=%s, template=%s, arguments=%s", box.graph(), box, template, args);
                template.instantiate(providers.getMetaAccess(), box, DEFAULT_REPLACER, args);
            }
        }

        public void lower(UnboxNode unbox, LoweringTool tool) {
            Arguments args = new Arguments(unboxSnippets.get(unbox.getBoxingKind()), unbox.graph().getGuardsStage(), tool.getLoweringStage());
            args.add("value", unbox.getValue());

            SnippetTemplate template = template(args);
            Debug.log("Lowering integerValueOf in %s: node=%s, template=%s, arguments=%s", unbox.graph(), unbox, template, args);
            template.instantiate(providers.getMetaAccess(), unbox, DEFAULT_REPLACER, args);
        }
    }

    private static final SnippetCounter.Group integerCounters = SnippetCounters.getValue() ? new SnippetCounter.Group("Integer intrinsifications") : null;
    private static final SnippetCounter valueOfCounter = new SnippetCounter(integerCounters, "valueOf", "valueOf intrinsification");

}
