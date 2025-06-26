/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.webimage.hightiercodegen;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.webimage.hightiercodegen.variables.ResolvedVar;

import jdk.graal.compiler.nodes.ValueNode;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * This class holds utility methods to create instances of {@link IEmitter} to generate various code
 * constructs, such as identifiers and literals of various types.
 */
@Platforms(Platform.HOSTED_ONLY.class)
public class Emitter implements IEmitter {
    private static final Emitter NULL = new Emitter(CodeGenTool::genNull);

    private final Consumer<CodeGenTool> lowerer;

    protected Emitter(Consumer<CodeGenTool> lowerer) {
        this.lowerer = lowerer;
    }

    @Override
    public void lower(CodeGenTool codeGenTool) {
        lowerer.accept(codeGenTool);
    }

    public static Emitter ofNull() {
        return NULL;
    }

    public static Emitter of(String s) {
        Objects.requireNonNull(s);
        return new Emitter((t) -> t.getCodeBuffer().emitText(s));
    }

    public static Emitter stringLiteral(String s) {
        Objects.requireNonNull(s);
        return new Emitter((t) -> t.getCodeBuffer().emitStringLiteral(s));
    }

    public static Emitter of(ResolvedJavaMethod m) {
        Objects.requireNonNull(m);
        return new Emitter((t) -> t.genMethodName(m));
    }

    public static Emitter of(ResolvedJavaField f) {
        Objects.requireNonNull(f);
        return new Emitter((t) -> t.genFieldName(f));
    }

    public static Emitter of(ResolvedJavaType type) {
        Objects.requireNonNull(type);
        return new Emitter((t) -> t.genTypeName(type));
    }

    public static Emitter of(ValueNode n) {
        if (n == null) {
            return ofNull();
        }

        return new Emitter((t) -> t.lowerValue(n));
    }

    public static Emitter of(ResolvedVar v) {
        Objects.requireNonNull(v);
        return new Emitter((t) -> t.getCodeBuffer().emitText(v.getName()));
    }

    public static Emitter of(Class<?> c) {
        Objects.requireNonNull(c);
        return new Emitter((t) -> t.genTypeName(c));
    }

    public static Emitter of(Method m) {
        Objects.requireNonNull(m);
        return new Emitter((t) -> t.genMethodName(m));
    }

    public static Emitter of(Field f) {
        Objects.requireNonNull(f);
        return new Emitter((t) -> t.genFieldName(f));
    }

    public static Emitter of(Integer i) {
        Objects.requireNonNull(i);
        return new Emitter((t) -> t.getCodeBuffer().emitIntLiteral(i));
    }

    public static Emitter of(Byte i) {
        return Emitter.of(i.intValue());
    }

    public static Emitter of(Short i) {
        return Emitter.of(i.intValue());
    }

    public static Emitter of(Character c) {
        return Emitter.of((int) c);
    }

    public static Emitter of(Float f) {
        Objects.requireNonNull(f);
        return new Emitter((t) -> t.getCodeBuffer().emitFloatLiteral(f));
    }

    public static Emitter of(Double d) {
        Objects.requireNonNull(d);
        return new Emitter((t) -> t.getCodeBuffer().emitDoubleLiteral(d));
    }

    public static Emitter of(Boolean b) {
        Objects.requireNonNull(b);
        return new Emitter((t) -> t.getCodeBuffer().emitBoolLiteral(b));
    }

    public static Emitter[] of(List<? extends ValueNode> l) {
        Emitter[] lowerList = new Emitter[l.size()];

        for (int i = 0; i < l.size(); i++) {
            lowerList[i] = Emitter.of(l.get(i));
        }

        return lowerList;
    }

    public static Emitter of(Emitter... emitters) {
        return new Emitter((t) -> {
            for (Emitter emitter : emitters) {
                emitter.lower(t);
            }
        });
    }

    public static Emitter of(Keyword context) {
        return new Emitter((t) -> t.getCodeBuffer().emitKeyword(context));
    }

    public static Emitter ofArray(Emitter... emitters) {
        return new Emitter((t) -> {
            boolean first = true;
            t.getCodeBuffer().emitText("[");
            for (Emitter emitter : emitters) {
                if (first) {
                    first = false;
                } else {
                    t.getCodeBuffer().emitText(",");
                }
                emitter.lower(t);
            }
            t.getCodeBuffer().emitText("]");
        });
    }

    /**
     * Emits a dictionary, using a map of emitters, for which keys are either strings or emitters.
     *
     * If the key is a string, it is treated as a normal string property, and enclosed in double
     * quotes. If the key is an emitter, the emitted code is enclosed in square brackets.
     */
    public static Emitter ofObject(Map<Object, IEmitter> props) {
        return new Emitter((t) -> {
            t.getCodeBuffer().emitScopeBegin();
            for (Map.Entry<Object, IEmitter> entry : props.entrySet()) {
                Object key = entry.getKey();
                if (key instanceof String) {
                    t.getCodeBuffer().emitStringLiteral((String) key);
                } else {
                    t.getCodeBuffer().emitText("[");
                    ((Emitter) key).lower(t);
                    t.getCodeBuffer().emitText("]");
                }
                t.getCodeBuffer().emitText(" : ");
                entry.getValue().lower(t);
                t.getCodeBuffer().emitText(",");
                t.getCodeBuffer().emitNewLine();
            }
            t.getCodeBuffer().emitScopeEnd();
        });
    }
}
