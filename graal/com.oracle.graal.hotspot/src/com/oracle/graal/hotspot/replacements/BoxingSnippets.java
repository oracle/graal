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
package com.oracle.graal.hotspot.replacements;

import static com.oracle.graal.replacements.SnippetTemplate.*;

import java.lang.reflect.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.replacements.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.Node.NodeIntrinsic;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.replacements.*;
import com.oracle.graal.replacements.Snippet.Fold;
import com.oracle.graal.replacements.Snippet.Parameter;
import com.oracle.graal.replacements.Snippet.SnippetInliningPolicy;
import com.oracle.graal.replacements.SnippetTemplate.AbstractTemplates;
import com.oracle.graal.replacements.SnippetTemplate.Arguments;
import com.oracle.graal.replacements.SnippetTemplate.Key;
import com.oracle.graal.word.*;

public class BoxingSnippets implements Snippets {

    /**
     * This snippet inlining policy differs from the default one in that it does normal inlining of
     * boxing methods like {@link Integer#valueOf(int)} (as opposed to method substitution).
     */
    public static class BoxingSnippetInliningPolicy implements SnippetInliningPolicy {

        @Override
        public boolean shouldInline(ResolvedJavaMethod method, ResolvedJavaMethod caller) {
            if (Modifier.isNative(method.getModifiers())) {
                return false;
            }
            if (method.getAnnotation(Fold.class) != null) {
                return false;
            }
            if (method.getAnnotation(NodeIntrinsic.class) != null) {
                return false;
            }
            if (method.getAnnotation(Word.Operation.class) != null) {
                return false;
            }
            return true;
        }

        @Override
        public boolean shouldUseReplacement(ResolvedJavaMethod callee, ResolvedJavaMethod methodToParse) {
            return false;
        }
    }

    @Snippet(inlining = BoxingSnippetInliningPolicy.class)
    public static Boolean valueOf(@Parameter("value") boolean value) {
        valueOfCounter.inc();
        return UnsafeCastNode.unsafeCast(Boolean.valueOf(value), StampFactory.forNodeIntrinsic());
    }

    @Snippet(inlining = BoxingSnippetInliningPolicy.class)
    public static Byte valueOf(@Parameter("value") byte value) {
        valueOfCounter.inc();
        return UnsafeCastNode.unsafeCast(Byte.valueOf(value), StampFactory.forNodeIntrinsic());
    }

    @Snippet(inlining = BoxingSnippetInliningPolicy.class)
    public static Character valueOf(@Parameter("value") char value) {
        valueOfCounter.inc();
        return UnsafeCastNode.unsafeCast(Character.valueOf(value), StampFactory.forNodeIntrinsic());
    }

    @Snippet(inlining = BoxingSnippetInliningPolicy.class)
    public static Double valueOf(@Parameter("value") double value) {
        valueOfCounter.inc();
        return UnsafeCastNode.unsafeCast(Double.valueOf(value), StampFactory.forNodeIntrinsic());
    }

    @Snippet(inlining = BoxingSnippetInliningPolicy.class)
    public static Float valueOf(@Parameter("value") float value) {
        valueOfCounter.inc();
        return UnsafeCastNode.unsafeCast(Float.valueOf(value), StampFactory.forNodeIntrinsic());
    }

    @Snippet(inlining = BoxingSnippetInliningPolicy.class)
    public static Integer valueOf(@Parameter("value") int value) {
        valueOfCounter.inc();
        return UnsafeCastNode.unsafeCast(Integer.valueOf(value), StampFactory.forNodeIntrinsic());
    }

    @Snippet(inlining = BoxingSnippetInliningPolicy.class)
    public static Long valueOf(@Parameter("value") long value) {
        valueOfCounter.inc();
        return UnsafeCastNode.unsafeCast(Long.valueOf(value), StampFactory.forNodeIntrinsic());
    }

    @Snippet(inlining = BoxingSnippetInliningPolicy.class)
    public static Short valueOf(@Parameter("value") short value) {
        valueOfCounter.inc();
        return UnsafeCastNode.unsafeCast(Short.valueOf(value), StampFactory.forNodeIntrinsic());
    }

    @Snippet(inlining = BoxingSnippetInliningPolicy.class)
    public static boolean booleanValue(@Parameter("value") Boolean value) {
        valueOfCounter.inc();
        return value.booleanValue();
    }

    @Snippet(inlining = BoxingSnippetInliningPolicy.class)
    public static byte byteValue(@Parameter("value") Byte value) {
        valueOfCounter.inc();
        return value.byteValue();
    }

    @Snippet(inlining = BoxingSnippetInliningPolicy.class)
    public static char charValue(@Parameter("value") Character value) {
        valueOfCounter.inc();
        return value.charValue();
    }

    @Snippet(inlining = BoxingSnippetInliningPolicy.class)
    public static double doubleValue(@Parameter("value") Double value) {
        valueOfCounter.inc();
        return value.doubleValue();
    }

    @Snippet(inlining = BoxingSnippetInliningPolicy.class)
    public static float floatValue(@Parameter("value") Float value) {
        valueOfCounter.inc();
        return value.floatValue();
    }

    @Snippet(inlining = BoxingSnippetInliningPolicy.class)
    public static int intValue(@Parameter("value") Integer value) {
        valueOfCounter.inc();
        return value.intValue();
    }

    @Snippet(inlining = BoxingSnippetInliningPolicy.class)
    public static long longValue(@Parameter("value") Long value) {
        valueOfCounter.inc();
        return value.longValue();
    }

    @Snippet(inlining = BoxingSnippetInliningPolicy.class)
    public static short shortValue(@Parameter("value") Short value) {
        valueOfCounter.inc();
        return value.shortValue();
    }

    public static class Templates extends AbstractTemplates<BoxingSnippets> {

        private final ResolvedJavaMethod[] valueOfMethods = new ResolvedJavaMethod[Kind.values().length];
        private final ResolvedJavaMethod[] unboxMethods = new ResolvedJavaMethod[Kind.values().length];

        public Templates(CodeCacheProvider runtime, Replacements replacements, TargetDescription target) {
            super(runtime, replacements, target, BoxingSnippets.class);
            for (Kind kind : new Kind[]{Kind.Boolean, Kind.Byte, Kind.Char, Kind.Double, Kind.Float, Kind.Int, Kind.Long, Kind.Short}) {
                valueOfMethods[kind.ordinal()] = snippet("valueOf", kind.toJavaClass());
                unboxMethods[kind.ordinal()] = snippet(kind.getJavaName() + "Value", kind.toBoxedJavaClass());
            }
        }

        private ResolvedJavaMethod getValueOf(Kind kind) {
            assert valueOfMethods[kind.ordinal()] != null;
            return valueOfMethods[kind.ordinal()];
        }

        private ResolvedJavaMethod getUnbox(Kind kind) {
            assert unboxMethods[kind.ordinal()] != null;
            return unboxMethods[kind.ordinal()];
        }

        public void lower(BoxNode boxingValueOf) {
            Key key = new Key(getValueOf(boxingValueOf.getBoxingKind()));
            Arguments arguments = new Arguments().add("value", boxingValueOf.getValue());
            SnippetTemplate template = cache.get(key);
            Debug.log("Lowering integerValueOf in %s: node=%s, template=%s, arguments=%s", boxingValueOf.graph(), boxingValueOf, template, arguments);
            template.instantiate(runtime, boxingValueOf, DEFAULT_REPLACER, arguments);
        }

        public void lower(UnboxNode unbox) {
            Key key = new Key(getUnbox(unbox.getBoxingKind()));
            Arguments arguments = new Arguments().add("value", unbox.getValue());
            SnippetTemplate template = cache.get(key);
            Debug.log("Lowering integerValueOf in %s: node=%s, template=%s, arguments=%s", unbox.graph(), unbox, template, arguments);
            template.instantiate(runtime, unbox, DEFAULT_REPLACER, arguments);
        }
    }

    private static final SnippetCounter.Group integerCounters = GraalOptions.SnippetCounters ? new SnippetCounter.Group("Integer intrinsifications") : null;
    private static final SnippetCounter valueOfCounter = new SnippetCounter(integerCounters, "valueOf", "valueOf intrinsification");

}
