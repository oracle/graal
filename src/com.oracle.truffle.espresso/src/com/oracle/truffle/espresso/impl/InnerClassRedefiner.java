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
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.jdwp.api.ErrorCodes;
import com.oracle.truffle.espresso.jdwp.api.RedefineInfo;
import com.oracle.truffle.espresso.jdwp.impl.JDWPLogger;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.StaticObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.instrument.IllegalClassFormatException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class InnerClassRedefiner implements DefineKlassListener {

    public static final Pattern ANON_INNER_CLASS_PATTERN = Pattern.compile(".*\\$\\d+.*");
    public static final int METHOD_FINGERPRINT_EQUALS = 8;
    public static final int ENCLOSING_METHOD_FINGERPRINT_EQUALS = 4;
    public static final int FIELD_FINGERPRINT_EQUALS = 2;
    public static final int NUMBER_INNER_CLASSES = 1;
    public static final int MAX_SCORE = METHOD_FINGERPRINT_EQUALS + ENCLOSING_METHOD_FINGERPRINT_EQUALS + FIELD_FINGERPRINT_EQUALS + NUMBER_INNER_CLASSES;

    public static final String HOT_CLASS_MARKER = "$hot";

    private final EspressoContext context;

    // map from classloader to a map of class names to inner class infos
    private final Map<StaticObject, Map<String, ImmutableClassInfo>> innerClassInfoMap = new WeakHashMap<>();

    // map from classloader to a map of Type to
    private final Map<StaticObject, Map<Symbol<Symbol.Type>, Set<ObjectKlass>>> innerKlassCache = new WeakHashMap<>();

    // list of class info for all top-level classed about to be redefined
    private final Map<String, HotSwapClassInfo> hotswapState = new HashMap<>();

    public InnerClassRedefiner(EspressoContext context) {
        this.context = context;
    }

    public HotSwapClassInfo[] matchAnonymousInnerClasses(RedefineInfo[] redefineInfos, List<ObjectKlass> removedInnerClasses) {
        hotswapState.clear();
        ArrayList<RedefineInfo> unhandled = new ArrayList<>(redefineInfos.length);
        Collections.addAll(unhandled, redefineInfos);
        Map<String, HotSwapClassInfo> handled = new HashMap<>(redefineInfos.length);
        // build inner/outer relationship from top-level to leaf class in order
        // each round below handles classes where the outer class was previously
        // handled
        int handledSize = 0;
        int previousHandledSize = -1;
        while (!unhandled.isEmpty() && handledSize > previousHandledSize) {
            Iterator<RedefineInfo> it = unhandled.iterator();
            while (it.hasNext()) {
                RedefineInfo redefineInfo = it.next();
                String klassName = getClassNameFromBytes(redefineInfo.getClassBytes());
                Matcher matcher = ANON_INNER_CLASS_PATTERN.matcher(klassName);
                if (matcher.matches()) {
                    // don't assume that associated old klass instance represents this redefineInfo
                    redefineInfo.clearKlass();
                    // anonymous inner class or nested named
                    // inner class of an anonymous inner class
                    // get the outer classinfo if present
                    HotSwapClassInfo info = handled.get(getOuterClassName(klassName));
                    if (info != null) {
                        HotSwapClassInfo classInfo = ClassInfo.create(klassName, redefineInfo.getClassBytes(), info.getClassLoader(), context);
                        info.addInnerClass(classInfo);
                        handled.put(klassName, classInfo);
                        it.remove();
                    }
                } else {
                    // pure named class
                    HotSwapClassInfo classInfo = ClassInfo.create(redefineInfo, context);
                    it.remove();
                    handled.put(klassName, classInfo);
                    hotswapState.put(klassName, classInfo);
                }
            }
            previousHandledSize = handledSize;
            handledSize = handled.size();
        }

        // store renaming rules to be used for constant pool patching when class renaming happens
        Map<StaticObject, Map<String, String>> renamingRules = new HashMap<>(0);
        // begin matching from collected top-level classes
        for (HotSwapClassInfo info : hotswapState.values()) {
            matchClassInfo(info, removedInnerClasses, renamingRules);
        }

        // get the full list of changed classes
        ArrayList<HotSwapClassInfo> result = new ArrayList<>();
        collectAllHotswapClasses(hotswapState.values(), result);

        // now, do the constant pool patching
        for (HotSwapClassInfo classInfo : result) {
            if (classInfo.getBytes() != null) {
                Map<String, String> rules = renamingRules.get(classInfo.getClassLoader());
                if (rules != null && !rules.isEmpty()) {
                    try {
                        classInfo.patchBytes(ConstantPoolPatcher.patchConstantPool(classInfo.getBytes(), rules));
                    } catch (IllegalClassFormatException ex) {
                        throw new RedefintionNotSupportedException(ErrorCodes.INVALID_CLASS_FORMAT);
                    }
                }
            }
        }
        hotswapState.clear();
        return result.toArray(new HotSwapClassInfo[0]);
    }

    private String getClassNameFromBytes(byte[] bytes) {
        try {
            // pass in a class name we know is not correct only to catch
            // the NoClassDefFoundError which is known to contain the type
            // name of the class in the exception message
            ClassfileParser.parse(new ClassfileStream(bytes, null), "!INVALID!", null, context);
        } catch (EspressoException ex) {
            String message = ex.getMessage();
            if (message != null && message.contains("(wrong name:")) {
                int typeEndIndex = message.indexOf(';');
                if (typeEndIndex != -1) {
                    // name is between 'L' and ';' as the first characters in the message
                    return message.substring(1, typeEndIndex);
                }
            }
        }
        // We only end up here in case the message in the
        // exception changed format in that case, we fail fast
        JDWPLogger.log("Critical failure in fetching class name from bytes", JDWPLogger.LogLevel.ALL);
        throw new RedefintionNotSupportedException(ErrorCodes.INVALID_CLASS);
    }

    private static void collectAllHotswapClasses(Collection<HotSwapClassInfo> infos, ArrayList<HotSwapClassInfo> result) {
        for (HotSwapClassInfo info : infos) {
            result.add(info);
            collectAllHotswapClasses((Collection<HotSwapClassInfo>) info.getInnerClasses(), result);
        }
    }

    private void fetchMissingInnerClasses(HotSwapClassInfo hotswapInfo) {
        StaticObject definingLoader = hotswapInfo.getClassLoader();

        ArrayList<String> innerNames = new ArrayList<>(1);
        try {
            searchConstantPoolForInnerClassNames(hotswapInfo, innerNames);
        } catch (IllegalClassFormatException ex) {
            throw new RedefintionNotSupportedException(ErrorCodes.INVALID_CLASS_FORMAT);
        }

        // poke the defining guest classloader for the resources
        for (String innerName : innerNames) {
            if (!hotswapInfo.knowsInnerClass(innerName)) {
                byte[] classBytes = null;
                StaticObject resourceGuestString = context.getMeta().toGuestString(innerName + ".class");
                StaticObject inputStream = (StaticObject) context.getMeta().java_lang_ClassLoader_getResourceAsStream.invokeDirect(definingLoader, resourceGuestString);
                if (StaticObject.notNull(inputStream)) {
                    classBytes = readAllBytes(inputStream);
                } else {
                    // There is no safe way to retrieve the class bytes using e.g. a scheme using
                    // j.l.ClassLoader#loadClass and special marker for the type to have the call
                    // end up in defineClass where we could grab the bytes. Guest language
                    // classloaders in many cases have caches for the class name that prevents
                    // forcefully attempting to load previously not loadable classes.
                    // without the class bytes, the matching is less precise and cached un-matched
                    // inner class instances that are executed after redefintion will lead to
                    // NoSuchMethod errors because they're marked as removed
                }
                if (classBytes != null) {
                    hotswapInfo.addInnerClass(ClassInfo.create(innerName, classBytes, definingLoader, context));
                }
            }
        }
    }

    private byte[] readAllBytes(StaticObject inputStream) {
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

    private static void searchConstantPoolForInnerClassNames(ClassInfo classInfo, ArrayList<String> innerNames) throws IllegalClassFormatException {
        byte[] bytes = classInfo.getBytes();
        assert bytes != null;

        ConstantPoolPatcher.getDirectInnerClassNames(classInfo.getName(), bytes, innerNames);
    }

    private static String getOuterClassName(String innerName) {
        assert innerName.contains("$");
        return innerName.substring(0, innerName.lastIndexOf('$'));
    }

    private void matchClassInfo(HotSwapClassInfo hotSwapInfo, List<ObjectKlass> removedInnerClasses, Map<StaticObject, Map<String, String>> renamingRules) {
        Klass klass = hotSwapInfo.getKlass();

        // try to fetch all direct inner classes
        // based on the constant pool in the class bytes
        // by means of the defining class loader
        fetchMissingInnerClasses(hotSwapInfo);
        if (klass == null) {
            // non-mapped hotSwapInfo means it's a new inner class that didn't map
            // to any previous inner class
            // we need to generate a new unique name and apply to nested inner classes
            HotSwapClassInfo outerInfo = hotSwapInfo.getOuterClassInfo();
            String name = outerInfo.addHotClassMarker();
            hotSwapInfo.rename(name);
            addRenamingRule(renamingRules, hotSwapInfo.getClassLoader(), hotSwapInfo.getName(), hotSwapInfo.getNewName());
        } else {
            ImmutableClassInfo previousInfo = getGlobalClassInfo(klass);
            ArrayList<ImmutableClassInfo> previousInnerClasses = previousInfo.getImmutableInnerClasses();
            ArrayList<HotSwapClassInfo> newInnerClasses = hotSwapInfo.getHotSwapInnerClasses();

            // apply potential outer rename to inner class fingerprints
            // before matching
            if (hotSwapInfo.isRenamed()) {
                for (HotSwapClassInfo newInnerClass : newInnerClasses) {
                    newInnerClass.outerRenamed(hotSwapInfo.getName(), hotSwapInfo.getNewName());
                }
            }

            if (previousInnerClasses.size() > 0 || newInnerClasses.size() > 0) {
                ArrayList<ImmutableClassInfo> removedClasses = new ArrayList<>(previousInnerClasses);
                for (HotSwapClassInfo info : newInnerClasses) {
                    ImmutableClassInfo bestMatch = null;
                    int maxScore = 0;
                    for (ImmutableClassInfo removedClass : removedClasses) {
                        int score = info.match(removedClass);
                        if (score > 0 && score > maxScore) {
                            maxScore = score;
                            bestMatch = removedClass;
                            if (maxScore == MAX_SCORE) {
                                // found a perfect match, so stop iterating
                                break;
                            }
                        }
                    }
                    if (bestMatch != null) {
                        removedClasses.remove(bestMatch);
                        // rename class and associate with previous klass object
                        if (!info.getName().equals(bestMatch.getName())) {
                            info.rename(bestMatch.getName());
                            addRenamingRule(renamingRules, info.getClassLoader(), info.getName(), info.getNewName());
                        }
                        info.setKlass(bestMatch.getKlass());
                    }
                }
                for (ImmutableClassInfo removedClass : removedClasses) {
                    if (removedClass.getKlass() != null) {
                        removedInnerClasses.add(removedClass.getKlass());
                    }
                }
            }
        }

        for (HotSwapClassInfo innerClass : hotSwapInfo.getHotSwapInnerClasses()) {
            matchClassInfo(innerClass, removedInnerClasses, renamingRules);
        }
    }

    private static void addRenamingRule(Map<StaticObject, Map<String, String>> renamingRules, StaticObject classLoader, String originalName, String newName) {
        Map<String, String> classLoaderRules = renamingRules.get(classLoader);
        if (classLoaderRules == null) {
            classLoaderRules = new HashMap<>(4);
            renamingRules.put(classLoader, classLoaderRules);
        }
        JDWPLogger.log("Renaming inner class: %s to: %s", JDWPLogger.LogLevel.REDEFINE, originalName, newName);
        // add simple class names
        classLoaderRules.put(originalName, newName);
        // add type names
        classLoaderRules.put("L" + originalName + ";", "L" + newName + ";");
        // add <init> signature names
        classLoaderRules.put("(L" + originalName + ";)V", "(L" + newName + ";)V");
    }

    public ImmutableClassInfo getGlobalClassInfo(Klass klass) {
        StaticObject classLoader = klass.getDefiningClassLoader();
        Map<String, ImmutableClassInfo> infos = innerClassInfoMap.get(classLoader);

        if (infos == null) {
            infos = new HashMap<>(1);
            innerClassInfoMap.put(classLoader, infos);
        }

        ImmutableClassInfo result = infos.get(klass.getNameAsString());

        if (result == null) {
            result = ClassInfo.create(klass, this);
            infos.put(klass.getNameAsString(), result);
        }
        return result;
    }

    Set<ObjectKlass> findLoadedInnerClasses(Klass klass) {
        // we use a cache to store inner/outer class mappings
        Map<Symbol<Symbol.Type>, Set<ObjectKlass>> classLoaderMap = innerKlassCache.get(klass.getDefiningClassLoader());
        if (classLoaderMap == null) {
            // register a listener on the registry to fill in
            // future loaded anonymous inner classes
            ClassRegistry classRegistry = context.getRegistries().getClassRegistry(klass.getDefiningClassLoader());
            classRegistry.registerOnLoadListener(this);

            // do a one-time look up of all currently loaded
            // classes for this loader and fill in the map
            List<Klass> loadedKlasses = classRegistry.getLoadedKlasses();
            HashSet<ObjectKlass> innerKlassList = new HashSet<>(1);
            for (Klass loadedKlass : loadedKlasses) {
                if (loadedKlass instanceof ObjectKlass) {
                    ObjectKlass objectKlass = (ObjectKlass) loadedKlass;
                    String klassName = loadedKlass.getNameAsString();
                    if (klassName.contains("$")) {
                        String outerName = InnerClassRedefiner.getOuterClassName(klassName);
                        if (klass.getNameAsString().equals(outerName)) {
                            innerKlassList.add(objectKlass);
                        }
                    }
                }
            }
            // add to cache
            classLoaderMap = new HashMap<>();
            classLoaderMap.put(klass.getType(), innerKlassList);
            innerKlassCache.put(klass.getDefiningClassLoader(), classLoaderMap);
        }
        Set<ObjectKlass> innerClasses = classLoaderMap.get(klass.getType());
        return innerClasses != null ? innerClasses : new HashSet<>(0);
    }

    @Override
    public void onKlassDefined(ObjectKlass klass) {
        Matcher matcher = ANON_INNER_CLASS_PATTERN.matcher(klass.getNameAsString());

        if (matcher.matches()) {
            Map<Symbol<Symbol.Type>, Set<ObjectKlass>> classLoaderMap = innerKlassCache.get(klass.getDefiningClassLoader());
            // found inner class, now hunt down the outer
            String outerName = InnerClassRedefiner.getOuterClassName(klass.getNameAsString());
            Symbol<Symbol.Type> outerType = context.getTypes().fromClassGetName(outerName);

            Set<ObjectKlass> innerKlasses = classLoaderMap.get(outerType);
            if (innerKlasses == null) {
                innerKlasses = new HashSet<>(1);
                classLoaderMap.put(outerType, innerKlasses);
            }
            innerKlasses.add(klass);
        }
    }

    public void commit(HotSwapClassInfo[] infos) {
        // first remove the previous info
        for (HotSwapClassInfo info : infos) {
            StaticObject classLoader = info.getClassLoader();
            Map<String, ImmutableClassInfo> classLoaderMap = innerClassInfoMap.get(classLoader);
            if (classLoaderMap != null) {
                classLoaderMap.remove(info.getNewName());
            }
        }
        for (HotSwapClassInfo hotSwapInfo : infos) {
            StaticObject classLoader = hotSwapInfo.getClassLoader();
            Map<String, ImmutableClassInfo> classLoaderMap = innerClassInfoMap.get(classLoader);
            if (classLoaderMap == null) {
                classLoaderMap = new HashMap<>(1);
                innerClassInfoMap.put(classLoader, classLoaderMap);
            }
            // update the cache with the new class info
            classLoaderMap.put(hotSwapInfo.getName(), ClassInfo.copyFrom(hotSwapInfo));
        }
    }
}
