/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.runtime;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.espresso.jdwp.api.FieldRef;
import com.oracle.truffle.espresso.jdwp.api.JDWPContext;
import com.oracle.truffle.espresso.jdwp.api.VMListener;
import com.oracle.truffle.espresso.jdwp.api.JDWPSetup;
import com.oracle.truffle.espresso.jdwp.api.MethodRef;
import com.oracle.truffle.espresso.jdwp.api.KlassRef;
import com.oracle.truffle.espresso.jdwp.api.Ids;
import com.oracle.truffle.espresso.jdwp.api.CallFrame;
import com.oracle.truffle.espresso.jdwp.api.TagConstants;
import com.oracle.truffle.espresso.impl.ArrayKlass;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.jdwp.impl.EmptyListener;
import com.oracle.truffle.espresso.jdwp.impl.DebuggerController;
import com.oracle.truffle.espresso.jdwp.impl.JDWPInstrument;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.EspressoRootNode;
import com.oracle.truffle.espresso.substitutions.Target_java_lang_Thread;

import java.lang.reflect.Array;
import java.util.ArrayList;

public final class JDWPContextImpl implements JDWPContext {

    public static final String JAVA_LANG_STRING = "Ljava/lang/String;";
    public static final String JAVA_LANG_THREAD = "Ljava/lang/Thread;";
    public static final String JAVA_LANG_CLASS = "Ljava/lang/Class;";
    public static final String JAVA_LANG_CLASS_LOADER = "Ljava/lang/ClassLoader;";
    public static final String JAVA_LANG_THREAD_GROUP = "Ljava/lang/ThreadGroup;";

    private final EspressoContext context;
    private final Ids<Object> ids;
    private JDWPSetup setup;

    public JDWPContextImpl(EspressoContext context) {
        this.context = context;
        this.ids = new Ids<>(StaticObject.NULL);
        this.setup = new JDWPSetup();
    }

    public VMListener jdwpInit(TruffleLanguage.Env env) {
        // enable JDWP instrumenter only if options are set (assumed valid if non-null)
        if (context.JDWPOptions != null) {
            Debugger debugger = env.lookup(env.getInstruments().get("debugger"), Debugger.class);
            DebuggerController control = env.lookup(env.getInstruments().get(JDWPInstrument.ID), DebuggerController.class);
            setup.setup(debugger, control, context.JDWPOptions, this);
            return control.getEventListener();
        }
        return new EmptyListener();
    }

    public void finalizeContext() {
        if (context.JDWPOptions != null) {
            setup.finalizeSession();
        }
    }

    @Override
    public Ids<Object> getIds() {
        return ids;
    }

    @Override
    public boolean isString(Object string) {
        return Meta.isString(string);
    }

    @Override
    public boolean isValidThread(Object thread) {
        return context.isValidThread(thread);
    }

    @Override
    public boolean isValidThreadGroup(Object threadGroup) {
        return context.isValidThreadGroup(threadGroup);
    }

    @Override
    public Object getNullObject() {
        return StaticObject.NULL;
    }

    @Override
    public KlassRef[] findLoadedClass(String slashName) {
        Symbol<Symbol.Type> type = context.getTypes().fromClassGetName(slashName);
        return context.getRegistries().findLoadedClassAny(type);
    }

    @Override
    public KlassRef[] getAllLoadedClasses() {
        return context.getRegistries().getAllLoadedClasses();
    }

    @Override
    public KlassRef[] getInitiatedClasses(Object classLoader) {
        return context.getRegistries().getLoadedClassesByLoader((StaticObject) classLoader);
    }

    @Override
    public boolean isValidClassLoader(Object object) {
        if (object instanceof StaticObject) {
            StaticObject loader = (StaticObject) object;
            return context.getRegistries().isClassLoader(loader);
        }
        return false;
    }

    @Override
    public Object asGuestThread(Thread hostThread) {
        return context.getGuestThreadFromHost(hostThread);
    }

    @Override
    public Thread asHostThread(Object thread) {
        return Target_java_lang_Thread.getHostFromGuestThread((StaticObject) thread);
    }

    @Override
    public Object[] getAllGuestThreads() {
        StaticObject[] activeThreads = context.getActiveThreads();
        ArrayList<StaticObject> result = new ArrayList<>(activeThreads.length);
        for (StaticObject activeThread : activeThreads) {
            // don't expose the finalizer and reference handler thread
            if ("Ljava/lang/ref/Reference$ReferenceHandler;".equals(activeThread.getKlass().getType().toString()) ||
                            "Ljava/lang/ref/Finalizer$FinalizerThread;".equals(activeThread.getKlass().getType().toString())) {
                continue;
            }
            result.add(activeThread);
        }
        return result.toArray(new StaticObject[result.size()]);
    }

    @Override
    public String getStringValue(Object object) {
        if (object instanceof StaticObject) {
            StaticObject staticObject = (StaticObject) object;
            return staticObject.asString();
        }
        return object.toString();
    }

    @Override
    public MethodRef getMethodFromRootNode(RootNode root) {
        if (root != null && root instanceof EspressoRootNode) {
            return ((EspressoRootNode) root).getMethod();
        }
        return null;
    }

    @Override
    public KlassRef getRefType(Object object) {
        if (object instanceof StaticObject) {
            return ((StaticObject) object).getKlass();
        } else {
            throw new IllegalStateException("object " + object + " is not a static object");
        }
    }

    @Override
    public KlassRef getReflectedType(Object classObject) {
        if (classObject instanceof StaticObject) {
            StaticObject staticObject = (StaticObject) classObject;
            return (KlassRef) staticObject.getHiddenField(context.getMeta().HIDDEN_MIRROR_KLASS);
        }
        return null;
    }

    @Override
    public byte getTag(Object object) {
        if (object == null) {
            return TagConstants.OBJECT;
        }
        byte tag = TagConstants.OBJECT;
        if (object instanceof StaticObject) {
            StaticObject staticObject = (StaticObject) object;
            if (object == StaticObject.NULL) {
                return tag;
            }
            tag = staticObject.getKlass().getTagConstant();
            if (tag == TagConstants.OBJECT) {
                // check specifically for String
                if (JAVA_LANG_STRING.equals(staticObject.getKlass().getType().toString())) {
                    tag = TagConstants.STRING;
                } else if (staticObject.getKlass().isArray()) {
                    tag = TagConstants.ARRAY;
                } else if (JAVA_LANG_THREAD.equals(staticObject.getKlass().getType().toString())) {
                    tag = TagConstants.THREAD;
                } else if (JAVA_LANG_THREAD_GROUP.equals(staticObject.getKlass().getType().toString())) {
                    tag = TagConstants.THREAD_GROUP;
                } else if (staticObject.getKlass() == context.getMeta().Class) {
                    tag = TagConstants.CLASS_OBJECT;
                } else if (JAVA_LANG_CLASS_LOADER.equals(staticObject.getKlass().getType().toString())) {
                    tag = TagConstants.CLASS_LOADER;
                }
            }
        } else if (isBoxedPrimitive(object.getClass())) {
            tag = TagConstants.getTagFromPrimitive(object);
        }
        return tag;
    }

    private static boolean isBoxedPrimitive(Class<?> clazz) {
        return Number.class.isAssignableFrom(clazz) || Character.class == clazz || Boolean.class == clazz;
    }

    @Override
    public String getThreadName(Object thread) {
        return context.getMeta().Thread_name.get(((StaticObject) thread)).toString();
    }

    @Override
    public int getThreadStatus(Object thread) {
        return (int) context.getMeta().Thread_threadStatus.get((StaticObject) thread);
    }

    @Override
    public Object getThreadGroup(Object thread) {
        return context.getMeta().Thread_group.get((StaticObject) thread);
    }

    @Override
    public Object[] getTopLevelThreadGroups() {
        return new Object[]{context.getSystemThreadGroup()};
    }

    @Override
    public int getArrayLength(Object array) {
        StaticObject staticObject = (StaticObject) array;
        return staticObject.length();
    }

    @Override
    public <T> T getUnboxedArray(Object array) {
        StaticObject staticObject = (StaticObject) array;
        return staticObject.unwrap();
    }

    @Override
    public boolean isArray(Object object) {
        if (object instanceof StaticObject) {
            StaticObject staticObject = (StaticObject) object;
            return staticObject.isArray();
        }
        return false;
    }

    @Override
    public boolean verifyArrayLength(Object array, int maxIndex) {
        StaticObject staticObject = (StaticObject) array;
        return maxIndex <= staticObject.length();
    }

    @Override
    public byte getTypeTag(Object array) {
        StaticObject staticObject = (StaticObject) array;
        byte tag;
        if (staticObject.isArray()) {
            ArrayKlass arrayKlass = (ArrayKlass) staticObject.getKlass();
            tag = arrayKlass.getComponentType().getJavaKind().toTagConstant();
            if (arrayKlass.getDimension() > 1) {
                tag = TagConstants.ARRAY;
            } else if (tag == TagConstants.OBJECT) {
                if (JAVA_LANG_STRING.equals(arrayKlass.getComponentType().getType().toString())) {
                    tag = TagConstants.STRING;
                }
            }
        } else {
            tag = staticObject.getKlass().getTagConstant();
            // Object type, so check for String
            if (tag == TagConstants.OBJECT) {
                if (JAVA_LANG_STRING.equals(((StaticObject) array).getKlass().getType().toString())) {
                    tag = TagConstants.STRING;
                }
            }
        }
        return tag;
    }

    // introspection

    @Override
    public Object getStaticFieldValue(FieldRef field) {
        return field.getValue(((ObjectKlass) field.getDeclaringKlass()).tryInitializeAndGetStatics());
    }

    @Override
    public void setStaticFieldValue(FieldRef field, Object value) {
        field.setValue(((ObjectKlass) field.getDeclaringKlass()).tryInitializeAndGetStatics(), value);
    }

    @Override
    public Object getArrayValue(Object array, int index) {
        StaticObject arrayRef = (StaticObject) array;
        Object value;
        if (arrayRef.getKlass().getComponentType().isPrimitive()) {
            // primitive array type needs wrapping
            Object boxedArray = getUnboxedArray(array);
            value = Array.get(boxedArray, index);
        } else {
            value = arrayRef.get(index);
        }
        return value;
    }

    @Override
    public void setArrayValue(Object array, int index, Object value) {
        StaticObject arrayRef = (StaticObject) array;
        arrayRef.putObject((StaticObject) value, index, context.getMeta());
    }

    @Override
    public Object newArray(KlassRef klass, int length) {
        return StaticObject.createArray((ArrayKlass) klass, new StaticObject[length]);
    }

    @Override
    public Object toGuest(Object object) {
        return context.getMeta().toGuestBoxed(object);
    }

    @Override
    public Object toGuestString(String string) {
        return context.getMeta().toGuestString(string);
    }

    @Override
    public Object getGuestException(Throwable exception) {
        if (exception instanceof EspressoException) {
            EspressoException ex = (EspressoException) exception;
            return ex.getExceptionObject();
        } else {
            throw new RuntimeException("unknown exception type: " + exception.getClass());
        }
    }

    @Override
    public CallFrame[] getStackTrace(Object thread) {
        // TODO(Gregersen) - implement this method when we can get stack frames
        // for arbitrary threads.
        return new CallFrame[0];
    }

    @Override
    public boolean isInstanceOf(Object object, KlassRef klass) {
        StaticObject staticObject = (StaticObject) object;
        return klass.isAssignable(staticObject.getKlass());
    }
}
