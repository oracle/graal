/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.impl;

import com.oracle.truffle.espresso.classfile.ClassfileParser;
import com.oracle.truffle.espresso.classfile.ClassfileStream;
import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.classfile.attributes.EnclosingMethodAttribute;
import com.oracle.truffle.espresso.classfile.constantpool.NameAndTypeConstant;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.jdwp.api.RedefineInfo;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;

import java.util.ArrayList;
import java.util.regex.Matcher;

public final class ClassInfo {
    private ObjectKlass thisKlass;
    private byte[] bytes;
    private final StaticObject classLoader;
    private final String originalName;
    private String newName;

    // below fields constitute the "fingerprint" of the class relevant for matching
    private final String classFingerprint;
    private String methodFingerprint;
    private String fieldFingerprint;
    private final String enclosingMethodFingerprint;

    private final ArrayList<ClassInfo> innerClasses;
    private ClassInfo outerClassInfo;
    private int nextNewClass = 1;

    private ClassInfo(ObjectKlass klass, String originalName, StaticObject classLoader, String classFingerprint, String methodFingerprint, String fieldFingerprint, String enclosingMethodFingerprint,
                      ArrayList<ClassInfo> inners, byte[] bytes) {
        this.thisKlass = klass;
        this.originalName = originalName;
        this.classLoader = classLoader;
        this.classFingerprint = classFingerprint;
        this.methodFingerprint = methodFingerprint;
        this.fieldFingerprint = fieldFingerprint;
        this.enclosingMethodFingerprint = enclosingMethodFingerprint;
        this.innerClasses = inners;
        this.bytes = bytes;
    }

    public static ClassInfo create(Klass klass, EspressoContext context) {

        StringBuilder hierarchy = new StringBuilder();
        StringBuilder methods = new StringBuilder();
        StringBuilder fields = new StringBuilder();
        StringBuilder enclosing = new StringBuilder();
        String name = klass.getNameAsString();

        Matcher matcher = InnerClassRedefiner.ANON_INNER_CLASS_PATTERN.matcher(name);
        if (matcher.matches()) {
            // fingerprints are only relevant for inner classes
            hierarchy.append(klass.getSuperClass().getTypeAsString()).append(";");
            for (ObjectKlass itf : klass.getInterfaces()) {
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
        // find all currently loaded direct inner classes and create class infos
        ArrayList<ClassInfo> inners = new ArrayList<>(1);

        Klass[] loadedInnerClasses = InnerClassRedefiner.findLoadedInnerClasses(klass, context);
        for (Klass inner : loadedInnerClasses) {
            matcher = InnerClassRedefiner.ANON_INNER_CLASS_PATTERN.matcher(inner.getNameAsString());
            // only add anonymous inner classes
            if (matcher.matches()) {
                inners.add(InnerClassRedefiner.getGlobalClassInfo(inner, context));
            }
        }

        return new ClassInfo((ObjectKlass) klass, name, klass.getDefiningClassLoader(), hierarchy.toString(), methods.toString(), fields.toString(), enclosing.toString(), inners, null);
    }

    public static ClassInfo create(RedefineInfo redefineInfo, EspressoContext context) {
        ObjectKlass klass = (ObjectKlass) redefineInfo.getKlass();
        return create(klass, klass.getNameAsString(), redefineInfo.getClassBytes(), klass.getDefiningClassLoader(), context);
    }

    public static ClassInfo create(String name, byte[] bytes, StaticObject definingLoader, EspressoContext context) {
        return create(null, name, bytes, definingLoader, context);
    }

    public static ClassInfo create(ObjectKlass klass, String name, byte[] bytes, StaticObject definingLoader, EspressoContext context) {
        ParserKlass parserKlass = ClassfileParser.parse(new ClassfileStream(bytes, null), "L" + name + ";", null, context);

        StringBuilder hierarchy = new StringBuilder();
        StringBuilder methods = new StringBuilder();
        StringBuilder fields = new StringBuilder();
        StringBuilder enclosing = new StringBuilder();

        Matcher matcher = InnerClassRedefiner.ANON_INNER_CLASS_PATTERN.matcher(name);
        if (matcher.matches()) {
            // fingerprints are only relevant for inner classes
            hierarchy.append(parserKlass.getSuperKlass().toString()).append(";");
            for (Symbol<Symbol.Type> itf : parserKlass.getSuperInterfaces()) {
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

        return new ClassInfo(klass, name, definingLoader, hierarchy.toString(), methods.toString(), fields.toString(), enclosing.toString(), new ArrayList<>(1), bytes);

    }

    public String getOriginalName() {
        return originalName;
    }

    public StaticObject getClassLoader() {
        return classLoader;
    }

    public void setKlass(ObjectKlass klass) {
        thisKlass = klass;
    }

    public void rename(String name) {
        this.newName = name;
    }

    void outerRenamed(String oldName, String newName) {
        methodFingerprint = methodFingerprint.replace(oldName, newName);
        fieldFingerprint = fieldFingerprint.replace(oldName, newName);
    }

    public boolean isRenamed() {
        return newName != null && !newName.equals(originalName);
    }

    public String getNewName() {
        return newName != null ? newName : originalName;
    }

    public StaticObject[] getPatches() {
        return new StaticObject[0];
    }

    public void addInnerClass(ClassInfo inner) {
        innerClasses.add(inner);
        inner.setOuterClass(this);
    }

    private void setOuterClass(ClassInfo classInfo) {
        outerClassInfo = classInfo;
    }

    public ClassInfo getOuterClassInfo() {
        return outerClassInfo;
    }

    public ClassInfo[] getInnerClasses() {
        return innerClasses.toArray(new ClassInfo[innerClasses.size()]);
    }

    public ObjectKlass getKlass() {
        return thisKlass;
    }

    public byte[] getBytes() {
        return bytes;
    }

    public boolean knowsInnerClass(String innerName) {
        for (ClassInfo innerClass : innerClasses) {
            if (innerName.equals(innerClass.getOriginalName())) {
                return true;
            }
        }
        return false;
    }

    public int match(ClassInfo other) {
        if (!classFingerprint.equals(other.classFingerprint)) {
            // always mark super hierachy changes as incompatible
            return 0;
        }
        int score = 0;
        score += methodFingerprint.equals(other.methodFingerprint) ? InnerClassRedefiner.METHOD_FINGERPRINT_EQUALS : 0;
        score += enclosingMethodFingerprint.equals(other.enclosingMethodFingerprint) ? InnerClassRedefiner.ENCLOSING_METHOD_FINGERPRINT_EQUALS : 0;
        score += fieldFingerprint.equals(other.fieldFingerprint) ? InnerClassRedefiner.FIELD_FINGERPRINT_EQUALS : 0;
        return score;
    }

    public String generateNextUniqueInnerName() {
        return getNewName() + "$hot" + nextNewClass++;
    }

    public void patchBytes(byte[] patchedBytes) {
        this.bytes = patchedBytes;
    }
}
