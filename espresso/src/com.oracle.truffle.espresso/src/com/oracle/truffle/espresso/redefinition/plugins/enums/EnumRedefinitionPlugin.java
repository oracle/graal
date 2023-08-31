/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.redefinition.plugins.enums;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.jdwp.api.MethodHook;
import com.oracle.truffle.espresso.jdwp.api.MethodRef;
import com.oracle.truffle.espresso.jdwp.api.MethodVariable;
import com.oracle.truffle.espresso.redefinition.plugins.api.InternalRedefinitionPlugin;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

public final class EnumRedefinitionPlugin extends InternalRedefinitionPlugin {

    private ArrayList<ObjectKlass> enumClassesToRerun = new ArrayList<>(4);

    @Override
    public boolean shouldRerunClassInitializer(ObjectKlass klass, boolean changed) {
        // changed enum classes store enum constants in static fields
        if (changed && getContext().getMeta().java_lang_Enum.isAssignable(klass)) {
            enumClassesToRerun.add(klass);
        }
        return false;
    }

    @Override
    public void postClassRedefinition(@SuppressWarnings("unused") ObjectKlass[] changedKlasses) {
        for (ObjectKlass objectKlass : enumClassesToRerun) {
            // existing enum constants will not be re-created because of
            // loss in object identity, so we have to capture constructor
            // arguments and re-run the constructor to re-initialize enum
            // state if any
            HashMap<Method, MethodHook> hooks = new HashMap<>(2);
            for (Method method : objectKlass.getDeclaredMethods()) {
                if (method.isConstructor()) {
                    // add a method hook that will
                    // fire when invoked from clinit
                    MethodHook hook = new MethodHook() {
                        @Override
                        public Kind getKind() {
                            return Kind.INDEFINITE;
                        }

                        @Override
                        public boolean onMethodEnter(MethodRef methodRef, MethodVariable[] variables) {
                            // OK, see if we have a pre-existing enum constant with the same name
                            MethodVariable nameVar = variables[1];
                            String enumName = objectKlass.getMeta().toHostString((StaticObject) nameVar.getValue());

                            Symbol<Symbol.Name> name = objectKlass.getContext().getNames().getOrCreate(enumName);
                            Field field = objectKlass.lookupField(name, objectKlass.getType());
                            Object existingEnumConstant = field.get(objectKlass.getStatics());
                            if (existingEnumConstant != StaticObject.NULL) {
                                // OK, re-run the constructor on the existing object
                                Object[] args = new Object[variables.length - 1];
                                for (int i = 1; i < variables.length; i++) {
                                    args[i - 1] = variables[i].getValue();
                                }
                                // avoid a recursive hook on the constructor call
                                method.removeActiveHook(this);
                                method.invokeDirect(existingEnumConstant, args);
                                method.addMethodHook(this);
                            }
                            return false;
                        }

                        @Override
                        public boolean onMethodExit(@SuppressWarnings("unused") MethodRef m, @SuppressWarnings("unused") Object returnValue) {
                            return false;
                        }
                    };
                    hooks.put(method, hook);
                    method.addMethodHook(hook);
                }
            }
            objectKlass.reRunClinit();
            // remove method hooks
            for (Map.Entry<Method, MethodHook> entry : hooks.entrySet()) {
                Method method = entry.getKey();
                MethodHook hook = entry.getValue();
                method.removeActiveHook(hook);
            }
        }
        for (ObjectKlass changedKlass : changedKlasses) {
            for (Field declaredField : changedKlass.getDeclaredFields()) {
                // ecj compiler generates mappings for ordinals directly
                // in the classes that use them, and they need to be reset
                if (declaredField.getNameAsString().startsWith("$SWITCH_TABLE$")) {
                    declaredField.set(changedKlass.getStatics(), StaticObject.NULL);
                }
            }
        }
        enumClassesToRerun.clear();
    }
}
