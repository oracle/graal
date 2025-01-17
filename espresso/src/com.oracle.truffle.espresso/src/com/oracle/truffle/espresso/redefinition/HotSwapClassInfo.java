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
import java.util.Arrays;

import com.oracle.truffle.espresso.classfile.descriptors.Name;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

// Represents ClassInfo instances for classes about to be hotswapped
public final class HotSwapClassInfo extends ClassInfo {

    private ObjectKlass thisKlass;
    private byte[] bytes;
    private byte[] patchedBytes;
    private final StaticObject classLoader;
    private final Symbol<Name> originalName;
    private Symbol<Name> newName;

    // below fields constitute the "fingerprint" of the class relevant for matching
    private String classFingerprint;
    private String methodFingerprint;
    private String fieldFingerprint;
    private String enclosingMethodFingerprint;

    final String finalClassFingerprint;
    final String finalMethodFingerprint;
    final String finalFieldFingerprint;
    final String finalEnclosingMethodFingerprint;

    private final ArrayList<HotSwapClassInfo> innerClasses;
    private HotSwapClassInfo outerClassInfo;
    private int nextNewClass = 1;

    HotSwapClassInfo(ObjectKlass klass, Symbol<Name> originalName, StaticObject classLoader, String classFingerprint, String methodFingerprint, String fieldFingerprint,
                    String enclosingMethodFingerprint,
                    ArrayList<HotSwapClassInfo> inners, byte[] bytes, boolean isEnumSwitchmapHelper, boolean isNewInnerTestKlass) {
        super(isEnumSwitchmapHelper, isNewInnerTestKlass);
        this.thisKlass = klass;
        this.originalName = originalName;
        this.classLoader = classLoader;
        this.classFingerprint = classFingerprint;
        this.methodFingerprint = methodFingerprint;
        this.fieldFingerprint = fieldFingerprint;
        this.enclosingMethodFingerprint = enclosingMethodFingerprint;
        this.innerClasses = inners;
        this.bytes = bytes;

        this.finalClassFingerprint = classFingerprint;
        this.finalMethodFingerprint = methodFingerprint;
        this.finalFieldFingerprint = fieldFingerprint;
        this.finalEnclosingMethodFingerprint = enclosingMethodFingerprint;
    }

    public static HotSwapClassInfo createForSuperClassChanged(ObjectKlass klass) {
        return new HotSwapClassInfo(klass, null, null, null, null, null, null, null, null, false, false);
    }

    @Override
    public ObjectKlass getKlass() {
        return thisKlass;
    }

    public void setKlass(ObjectKlass klass) {
        thisKlass = klass;
    }

    @Override
    public Symbol<Name> getName() {
        return originalName;
    }

    public Symbol<Name> getNewName() {
        return newName != null ? newName : originalName;
    }

    public void rename(Symbol<Name> name) {
        this.newName = name;
    }

    public boolean isRenamed() {
        return newName != null && !newName.equals(originalName);
    }

    @Override
    public ArrayList<? extends ClassInfo> getInnerClasses() {
        return innerClasses;
    }

    public void addInnerClass(HotSwapClassInfo inner) {
        innerClasses.add(inner);
        inner.setOuterClass(this);
    }

    public boolean knowsInnerClass(Symbol<Name> innerName) {
        for (ClassInfo innerClass : innerClasses) {
            if (innerName.equals(innerClass.getName())) {
                return true;
            }
        }
        return false;
    }

    private void setOuterClass(HotSwapClassInfo classInfo) {
        outerClassInfo = classInfo;
    }

    public HotSwapClassInfo getOuterClassInfo() {
        return outerClassInfo;
    }

    void outerRenamed(String oldName, String replacementName) {
        methodFingerprint = methodFingerprint != null ? methodFingerprint.replace(oldName, replacementName) : null;
        fieldFingerprint = fieldFingerprint != null ? fieldFingerprint.replace(oldName, replacementName) : null;
    }

    @Override
    public String getClassFingerprint() {
        return classFingerprint;
    }

    @Override
    public String getMethodFingerprint() {
        return methodFingerprint;
    }

    @Override
    public String getFieldFingerprint() {
        return fieldFingerprint;
    }

    @Override
    public String getEnclosingMethodFingerprint() {
        return enclosingMethodFingerprint;
    }

    @Override
    public StaticObject getClassLoader() {
        return classLoader;
    }

    @Override
    public byte[] getBytes() {
        return bytes;
    }

    public void patchBytes(byte[] patchBytes) {
        this.patchedBytes = patchBytes;
    }

    public boolean isPatched() {
        return patchedBytes != null && !Arrays.equals(patchedBytes, bytes);
    }

    public byte[] getPatchedBytes() {
        return patchedBytes;
    }

    public String addHotClassMarker() {
        return getNewName() + InnerClassRedefiner.HOT_CLASS_MARKER + nextNewClass++;
    }

    public ArrayList<HotSwapClassInfo> getHotSwapInnerClasses() {
        return innerClasses;
    }
}
