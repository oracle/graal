/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.redefinition;

import java.util.ArrayList;
import java.util.Set;
import java.util.regex.Matcher;

import com.oracle.truffle.espresso.classfile.ClassfileParser;
import com.oracle.truffle.espresso.classfile.ClassfileStream;
import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.classfile.attributes.EnclosingMethodAttribute;
import com.oracle.truffle.espresso.classfile.constantpool.NameAndTypeConstant;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.impl.ParserField;
import com.oracle.truffle.espresso.impl.ParserKlass;
import com.oracle.truffle.espresso.impl.ParserMethod;
import com.oracle.truffle.espresso.jdwp.api.RedefineInfo;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

public abstract class ClassInfo {

    private final boolean isNewInnerTestKlass;
    private final boolean isEnumSwitchmapHelper;

    public ClassInfo(boolean isEnumSwitchmapHelper, boolean isNewInnerTestKlass) {
        this.isEnumSwitchmapHelper = isEnumSwitchmapHelper;
        this.isNewInnerTestKlass = isNewInnerTestKlass;
    }

    public boolean isNewInnerTestKlass() {
        return isNewInnerTestKlass;
    }

    public boolean isEnumSwitchmapHelper() {
        return isEnumSwitchmapHelper;
    }

    public static ImmutableClassInfo create(Klass klass, InnerClassRedefiner innerClassRedefiner) {
        StringBuilder hierarchy = new StringBuilder();
        StringBuilder methods = new StringBuilder();
        StringBuilder fields = new StringBuilder();
        StringBuilder enclosing = new StringBuilder();
        Symbol<Name> name = klass.getName();

        boolean enumHelper = false;
        Matcher matcher = InnerClassRedefiner.ANON_INNER_CLASS_PATTERN.matcher(name.toString());
        if (matcher.matches()) {
            if (klass.getDeclaredFields().length == 1) {
                if (klass.getDeclaredFields()[0].getName().toString().startsWith("$SwitchMap$")) {
                    // filter out enum switchmap helper classes
                    enumHelper = true;
                }
            }
            if (!enumHelper) {
                // fingerprints are only relevant for inner classes
                hierarchy.append(klass.getSuperClass().getTypeAsString()).append(";");
                for (Klass itf : klass.getImplementedInterfaces()) {
                    hierarchy.append(itf.getTypeAsString()).append(";");
                }

                for (Method method : klass.getDeclaredMethods()) {
                    methods.append(method.getNameAsString()).append(";");
                    methods.append(method.getSignatureAsString()).append(";");
                }

                for (Field field : klass.getDeclaredFields()) {
                    fields.append(field.getTypeAsString()).append(";");
                    fields.append(field.getNameAsString()).append(";");
                }

                ObjectKlass objectKlass = (ObjectKlass) klass;
                ConstantPool pool = klass.getConstantPool();
                NameAndTypeConstant nmt = pool.nameAndTypeAt(objectKlass.getEnclosingMethod().getMethodIndex());
                enclosing.append(nmt.getName(pool)).append(";").append(nmt.getDescriptor(pool));
            }
        }
        // find all currently loaded direct inner classes and create class infos
        ArrayList<ImmutableClassInfo> inners = new ArrayList<>(1);
        Set<ObjectKlass> loadedInnerClasses = innerClassRedefiner.findLoadedInnerClasses(klass);
        for (Klass inner : loadedInnerClasses) {
            matcher = InnerClassRedefiner.ANON_INNER_CLASS_PATTERN.matcher(inner.getNameAsString());
            // only add anonymous inner classes
            if (matcher.matches()) {
                inners.add(innerClassRedefiner.getGlobalClassInfo(inner));
            }
        }
        return new ImmutableClassInfo((ObjectKlass) klass, name, klass.getDefiningClassLoader(), hierarchy.toString(), methods.toString(), fields.toString(), enclosing.toString(),
                        inners, null, enumHelper, false);
    }

    public static HotSwapClassInfo create(RedefineInfo redefineInfo, EspressoContext context, boolean isNewInnerTestKlass) {
        ObjectKlass klass = (ObjectKlass) redefineInfo.getKlass();
        return create(klass, klass.getName(), redefineInfo.getClassBytes(), klass.getDefiningClassLoader(), context, isNewInnerTestKlass);
    }

    public static HotSwapClassInfo create(Symbol<Name> name, byte[] bytes, StaticObject definingLoader, EspressoContext context, boolean isNewInnerTestKlass) {
        return create(null, name, bytes, definingLoader, context, isNewInnerTestKlass);
    }

    public static HotSwapClassInfo create(ObjectKlass klass, Symbol<Name> name, byte[] bytes, StaticObject definingLoader, EspressoContext context, boolean isNewInnerTestKlass) {
        Symbol<Type> type = context.getTypes().fromName(name);
        ParserKlass parserKlass = ClassfileParser.parse(context.getClassLoadingEnv(), new ClassfileStream(bytes, null), definingLoader, type);

        StringBuilder hierarchy = new StringBuilder();
        StringBuilder methods = new StringBuilder();
        StringBuilder fields = new StringBuilder();
        StringBuilder enclosing = new StringBuilder();

        boolean enumHelper = false;
        Matcher matcher = InnerClassRedefiner.ANON_INNER_CLASS_PATTERN.matcher(name.toString());
        if (matcher.matches()) {
            if (parserKlass.getFields().length == 1) {
                if (parserKlass.getFields()[0].getName().toString().startsWith("$SwitchMap$")) {
                    // filter out enum switchmap helper classes
                    enumHelper = true;
                }
            }
            if (!enumHelper) {
                // fingerprints are only relevant for inner classes
                hierarchy.append(parserKlass.getSuperKlass().toString()).append(";");
                for (Symbol<Type> itf : parserKlass.getSuperInterfaces()) {
                    hierarchy.append(itf.toString()).append(";");
                }

                for (ParserMethod method : parserKlass.getMethods()) {
                    methods.append(method.getName().toString()).append(";");
                    methods.append(method.getSignature().toString()).append(";");
                }

                for (ParserField field : parserKlass.getFields()) {
                    fields.append(field.getType().toString()).append(";");
                    fields.append(field.getName().toString()).append(";");
                }

                ConstantPool pool = parserKlass.getConstantPool();
                EnclosingMethodAttribute attr = (EnclosingMethodAttribute) parserKlass.getAttribute(EnclosingMethodAttribute.NAME);
                NameAndTypeConstant nmt = pool.nameAndTypeAt(attr.getMethodIndex());
                enclosing.append(nmt.getName(pool)).append(";").append(nmt.getDescriptor(pool));
            }
        }

        return new HotSwapClassInfo(klass, name, definingLoader, hierarchy.toString(), methods.toString(), fields.toString(), enclosing.toString(), new ArrayList<>(1), bytes, enumHelper,
                        isNewInnerTestKlass);
    }

    public static ImmutableClassInfo copyFrom(HotSwapClassInfo info) {
        ArrayList<ImmutableClassInfo> inners = new ArrayList<>();
        for (HotSwapClassInfo innerClass : info.getHotSwapInnerClasses()) {
            inners.add(copyFrom(innerClass));
        }
        return new ImmutableClassInfo(info.getKlass(), info.getName(), info.getClassLoader(), info.finalClassFingerprint, info.finalMethodFingerprint, info.finalFieldFingerprint,
                        info.finalEnclosingMethodFingerprint, inners, info.getBytes(), info.isEnumSwitchmapHelper(), info.isNewInnerTestKlass());
    }

    public abstract String getClassFingerprint();

    public abstract String getMethodFingerprint();

    public abstract String getFieldFingerprint();

    public abstract String getEnclosingMethodFingerprint();

    public abstract ArrayList<? extends ClassInfo> getInnerClasses();

    public abstract Symbol<Name> getName();

    public abstract StaticObject getClassLoader();

    public abstract ObjectKlass getKlass();

    public abstract byte[] getBytes();

    public int match(ClassInfo other) {
        if (!getClassFingerprint().equals(other.getClassFingerprint())) {
            // always mark super hierarchy changes as incompatible
            return 0;
        }
        if (isEnumSwitchmapHelper) {
            // never match enum switchmap helpers, since we want to
            // spin a new class that automatically runs clinit when
            // used from new code.
            return 0;
        }

        int score = 0;
        score += getMethodFingerprint().equals(other.getMethodFingerprint()) ? InnerClassRedefiner.METHOD_FINGERPRINT_EQUALS : 0;
        score += getEnclosingMethodFingerprint().equals(other.getEnclosingMethodFingerprint()) ? InnerClassRedefiner.ENCLOSING_METHOD_FINGERPRINT_EQUALS : 0;
        score += getFieldFingerprint().equals(other.getFieldFingerprint()) ? InnerClassRedefiner.FIELD_FINGERPRINT_EQUALS : 0;
        score += getInnerClasses().size() == other.getInnerClasses().size() ? InnerClassRedefiner.NUMBER_INNER_CLASSES : 0;
        return score;
    }
}
