/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.test;

import static java.lang.reflect.Modifier.isFinal;
import static java.lang.reflect.Modifier.isPrivate;
import static java.lang.reflect.Modifier.isStatic;
import static java.lang.reflect.Modifier.isVolatile;

import java.lang.reflect.Field;

import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.PhaseSuite;
import jdk.graal.compiler.phases.SingleRunSubphase;
import jdk.graal.compiler.phases.VerifyPhase;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;

/**
 * Verifies that compiler phases (subclasses of {@link BasePhase}) are stateless, at least to the
 * extent that only subclasses of {@link SingleRunSubphase} contain fields that are not declared
 * {@code final}.
 */
public class VerifyStatelessPhases extends VerifyPhase<CoreProviders> {

    @Override
    protected void verify(StructuredGraph graph, CoreProviders context) {
    }

    @Override
    public void verifyClass(Class<?> clazz, MetaAccessProvider metaAccess) {
        if (BasePhase.class.isAssignableFrom(clazz)) {
            if (PhaseSuite.class.isAssignableFrom(clazz)) {
                // Phase suites contain the compilation pipeline which is currently built up
                // incrementally and is therefore mutable.
                return;
            }
            if (SingleRunSubphase.class.isAssignableFrom(clazz)) {
                // Such subphases may contain mutable state, but each instance can only be applied
                // once (checked at runtime).
                return;
            }

            // Any other phase must not contain mutable state, except maybe special
            // @SharedGlobalPhaseState fields.
            for (Field field : clazz.getDeclaredFields()) {
                int modifiers = field.getModifiers();

                if (field.getAnnotation(SharedGlobalPhaseState.class) != null) {
                    if (!(isPrivate(modifiers) && isStatic(modifiers) && isVolatile(modifiers))) {
                        throw new VerificationError("Compiler phase fields marked @SharedGlobalPhaseState must be private static volatile, but found %s.", field);
                    }
                    continue;
                }

                ResolvedJavaField f = metaAccess.lookupJavaField(field);

                String name = f.getName();
                if (f.isFinal() && name.equals("$VALUES") || name.equals("ENUM$VALUES")) {
                    // generated int[] field for EnumClass::values()
                    continue;
                } else if (name.startsWith("$SwitchMap$") || name.startsWith("$SWITCH_TABLE$")) {
                    // javac and ecj generate a static field in an inner class for a switch on an
                    // enum named $SwitchMap$p$k$g$EnumClass and $SWITCH_TABLE$p$k$g$EnumClass,
                    // respectively
                    continue;
                }

                if (!isFinal(modifiers)) {
                    throw new VerificationError("Compiler phases must be stateless but %s contains the non-final field %s. Please make this field final or consider using SingleRunSubphase.",
                                    clazz, field);
                }
            }
        }
    }
}
