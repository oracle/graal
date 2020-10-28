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

import com.oracle.truffle.espresso.jdwp.api.KlassRef;
import com.oracle.truffle.espresso.jdwp.api.RedefineInfo;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class InnerClassRedefiner {

    public static final Pattern ANON_INNER_CLASS_PATTERN = Pattern.compile(".*\\$\\d+.*");
    public static final int METHOD_FINGERPRINT_EQUALS = 8;
    public static final int ENCLOSING_METHOD_FINGERPRINT_EQUALS = 4;
    public static final int FIELD_FINGERPRINT_EQUALS = 2;


    // map from classloader to a map of class names to inner class infos
    private static final Map<StaticObject, Map<String, ClassInfo>> innerClassInfoMap = new WeakHashMap<>();
    // list of class info for all top-level classed about to be redefined
    private static final Map<String, ClassInfo> hotswapState = new HashMap<>();

    public static ClassInfo[] matchAnonymousInnerClasses(RedefineInfo[] redefineInfos, EspressoContext context, List<ObjectKlass> removedInnerClasses) {
        hotswapState.clear();
        ArrayList<RedefineInfo> unhandled = new ArrayList<>(redefineInfos.length);
        Map<String, ClassInfo> handled = new HashMap<>(redefineInfos.length);
        Collections.addAll(unhandled, redefineInfos);
        // build inner/outer relationship from top-level to leaf class in order
        // each round below handles classes where the outer class was previously
        // handled
        while (!unhandled.isEmpty()) {
            Iterator<RedefineInfo> it = unhandled.iterator();
            while (it.hasNext()) {
                RedefineInfo redefineInfo = it.next();
                Klass klass = (Klass) redefineInfo.getKlass();
                String klassName = klass.getNameAsString();
                Matcher matcher = ANON_INNER_CLASS_PATTERN.matcher(klassName);
                if (matcher.matches()) {
                    // anonymous inner class or nested named
                    // inner class of an anonymous inner class
                    // get the outer classinfo if present
                    ClassInfo info = handled.get(getOuterClassName(klassName));
                    if (info != null) {
                        Klass ref = (Klass) redefineInfo.getKlass();
                        ClassInfo classInfo = ClassInfo.create(ref.getNameAsString(), redefineInfo.getClassBytes(), ref.getDefiningClassLoader(), context);
                        info.addInnerClass(classInfo);
                        handled.put(klassName, classInfo);
                        it.remove();
                    }
                } else {
                    // pure named class
                    ClassInfo classInfo = ClassInfo.create(redefineInfo, context);
                    it.remove();
                    hotswapState.put(klassName, classInfo);
                    handled.put(klassName, classInfo);
                }
            }
        }

        // begin matching from collected top-level classes
        for (ClassInfo info : hotswapState.values()) {
            matchClassInfo(info, context, removedInnerClasses);
        }
        ArrayList<ClassInfo> result = new ArrayList<>();
        getAllHotswapClasses(hotswapState.values(), result);
        hotswapState.clear();
        return result.toArray(new ClassInfo[0]);
    }

    private static void getAllHotswapClasses(Collection<ClassInfo> infos, ArrayList<ClassInfo> result) {
        for (ClassInfo info : infos) {
            result.add(info);
            getAllHotswapClasses(Arrays.asList(info.getInnerClasses()), result);
        }
    }

    private static void fetchMissingInnerClasses(ClassInfo classInfo, EspressoContext context) {
        StaticObject definingLoader = classInfo.getClassLoader();

        ArrayList<String> innerNames = new ArrayList<>(1);
        searchConstantPoolForInnerClassNames(classInfo, innerNames);

        // poke the defining guest classloader for the resources
        for (String innerName : innerNames) {
            if (!classInfo.knowsInnerClass(innerName)) {
                StaticObject guestString = context.getMeta().toGuestString(innerName + ".class");
                StaticObject inputStream = (StaticObject) context.getMeta().java_lang_ClassLoader_getResourceAsStream.invokeDirect(definingLoader, guestString);

                if (StaticObject.notNull(inputStream)) {
                    byte[] classBytes = readAllBytes(inputStream, context);
                    classInfo.addInnerClass(ClassInfo.create(innerName, classBytes, definingLoader, context));
                }
            }
        }
    }

    private static byte[] readAllBytes(StaticObject inputStream, EspressoContext context) {
        byte[] buf = new byte[4 * 0x400];
        StaticObject guestBuf = StaticObject.wrap(buf, context.getMeta());
        int readLen;

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            while ((readLen = (int) context.getMeta().java_io_InputStream_read.invokeDirect(inputStream, guestBuf, 0, buf.length)) != -1) {
                byte[] bytes = guestBuf.unwrap();
                outputStream.write(bytes, 0, readLen);
            }
            return outputStream.toByteArray();
        } catch (IOException ex) {
            return new byte[0];
        } finally {
            context.getMeta().java_io_InputStream_close.invokeDirect(inputStream);
        }
    }

    private static void searchConstantPoolForInnerClassNames(ClassInfo classInfo, ArrayList<String> innerNames) {
        byte[] bytes = classInfo.getBytes();
        assert bytes != null;

        ConstantPoolPatcher.getDirectInnerClassNames(classInfo.getOriginalName(), bytes, innerNames);
    }

    static String getOuterClassName(String innerName) {
        assert innerName.contains("$");
        return innerName.substring(0, innerName.lastIndexOf('$'));
    }

    private static void matchClassInfo(ClassInfo hotSwapInfo, EspressoContext context, List<ObjectKlass> removedInnerClasses) {
        Klass klass = hotSwapInfo.getKlass();

        // try to fetch all direct inner classes
        // based on the constant pool in the class bytes
        // by means of the defining class loader
        fetchMissingInnerClasses(hotSwapInfo, context);

        if (klass == null) {
            // non-mapped hotSwapInfo means it's a new inner class that didn't map
            // to any previous inner class
            // we need to generate a new unique name and apply to nested inner classes
            ClassInfo outerInfo = hotSwapInfo.getOuterClassInfo();
            // check if outer existed previously
            ClassInfo existingInfo = InnerClassRedefiner.getGlobalClassInfo(outerInfo.getNewName(), outerInfo.getClassLoader());

            if (existingInfo != null) {
                outerInfo = existingInfo;
            }
            String name = outerInfo.generateNextUniqueInnerName();
            hotSwapInfo.rename(name);
        } else {
            ClassInfo previousInfo = getGlobalClassInfo(klass, context);
            ClassInfo[] previousInnerClasses = previousInfo.getInnerClasses();
            ClassInfo[] newInnerClasses = hotSwapInfo.getInnerClasses();

            // apply potential outer rename to inner class fingerprints
            // before matching
            if (hotSwapInfo.isRenamed()) {
                for (ClassInfo newInnerClass : newInnerClasses) {
                    newInnerClass.outerRenamed(hotSwapInfo.getOriginalName(), hotSwapInfo.getNewName());
                }
            }

            if (previousInnerClasses.length > 0 || newInnerClasses.length > 0) {
                ArrayList<ClassInfo> removedClasses = new ArrayList<>(Arrays.asList(previousInnerClasses));
                for (ClassInfo info : newInnerClasses) {
                    ClassInfo bestMatch = null;
                    int maxScore = 0;
                    for (ClassInfo removedClass : removedClasses) {
                        int score = info.match(removedClass);
                        if (score > 0 && score > maxScore) {
                            maxScore = score;
                            bestMatch = removedClass;
                        }
                    }
                    if (bestMatch != null) {
                        removedClasses.remove(bestMatch);
                        // rename class and associate with previous klass object
                        if (!info.getOriginalName().equals(bestMatch.getOriginalName())) {
                            info.rename(bestMatch.getOriginalName());
                        }
                        info.setKlass(bestMatch.getKlass());
                    }
                }
                for (ClassInfo removedClass : removedClasses) {
                    removedInnerClasses.add(removedClass.getKlass());
                }
            }
        }

        for (ClassInfo innerClass : hotSwapInfo.getInnerClasses()) {
            matchClassInfo(innerClass, context, removedInnerClasses);
        }
    }

    public static ClassInfo getGlobalClassInfo(Klass klass, EspressoContext context) {
        StaticObject classLoader = klass.getDefiningClassLoader();
        Map<String, ClassInfo> infos = innerClassInfoMap.get(classLoader);

        if (infos == null) {
            infos = new HashMap<>(1);
            innerClassInfoMap.put(classLoader, infos);
        }

        ClassInfo result = infos.get(klass.getNameAsString());

        if (result == null) {
            result = ClassInfo.create(klass, context);
            infos.put(klass.getNameAsString(), result);
        }
        return result;
    }

    public static ClassInfo getGlobalClassInfo(String name, StaticObject classLoader) {
        Map<String, ClassInfo> infos = innerClassInfoMap.get(classLoader);
        if (infos == null) {
            infos = new HashMap<>(1);
            innerClassInfoMap.put(classLoader, infos);
        }
        return infos.get(name);
    }

    public static Klass[] findLoadedInnerClasses(Klass klass, EspressoContext context) {
        ArrayList<Klass> result = new ArrayList<>(1);
        String name = klass.getNameAsString();
        Klass[] loadedKlasses = context.getRegistries().getClassRegistry(klass.getDefiningClassLoader()).getLoadedKlasses();

        for (Klass loadedKlass : loadedKlasses) {
            String klassName = loadedKlass.getNameAsString();
            if (klassName.contains("$")) {
                String outerName = InnerClassRedefiner.getOuterClassName(klassName);
                if (name.equals(outerName)) {
                    result.add(loadedKlass);
                }
            }
        }
        return result.toArray(new Klass[result.size()]);
    }
}
