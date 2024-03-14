/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Array;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.blocking.EspressoLock;
import com.oracle.truffle.espresso.bytecode.BytecodeStream;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.impl.ArrayKlass;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method.MethodVersion;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.jdwp.api.CallFrame;
import com.oracle.truffle.espresso.jdwp.api.FieldRef;
import com.oracle.truffle.espresso.jdwp.api.Ids;
import com.oracle.truffle.espresso.jdwp.api.JDWPContext;
import com.oracle.truffle.espresso.jdwp.api.JDWPSetup;
import com.oracle.truffle.espresso.jdwp.api.KlassRef;
import com.oracle.truffle.espresso.jdwp.api.MethodRef;
import com.oracle.truffle.espresso.jdwp.api.ModuleRef;
import com.oracle.truffle.espresso.jdwp.api.MonitorStackInfo;
import com.oracle.truffle.espresso.jdwp.api.RedefineInfo;
import com.oracle.truffle.espresso.jdwp.api.TagConstants;
import com.oracle.truffle.espresso.jdwp.api.VMEventListenerImpl;
import com.oracle.truffle.espresso.jdwp.impl.DebuggerController;
import com.oracle.truffle.espresso.jdwp.impl.JDWPInstrument;
import com.oracle.truffle.espresso.jdwp.impl.TypeTag;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.BciProvider;
import com.oracle.truffle.espresso.nodes.EspressoInstrumentableRootNode;
import com.oracle.truffle.espresso.nodes.EspressoRootNode;
import com.oracle.truffle.espresso.nodes.IntrinsicSubstitutorNode;
import com.oracle.truffle.espresso.nodes.quick.interop.ForeignArrayUtils;
import com.oracle.truffle.espresso.redefinition.ChangePacket;
import com.oracle.truffle.espresso.redefinition.ClassRedefinition;
import com.oracle.truffle.espresso.redefinition.HotSwapClassInfo;
import com.oracle.truffle.espresso.redefinition.InnerClassRedefiner;
import com.oracle.truffle.espresso.redefinition.RedefinitionNotSupportedException;
import com.oracle.truffle.espresso.redefinition.plugins.impl.RedefinitionPluginHandler;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.threads.State;

public final class JDWPContextImpl implements JDWPContext {

    public static final String JAVA_LANG_STRING = "Ljava/lang/String;";
    private static final InteropLibrary UNCACHED = InteropLibrary.getUncached();

    private final EspressoContext context;
    private final Ids<Object> ids;
    private final JDWPSetup setup;
    private ClassRedefinition classRedefinition;
    private final InnerClassRedefiner innerClassRedefiner;
    private RedefinitionPluginHandler redefinitionPluginHandler;
    private final ArrayList<ReloadingAction> classInitializerActions = new ArrayList<>(1);
    private DebuggerController controller;

    public JDWPContextImpl(EspressoContext context) {
        this.context = context;
        this.ids = new Ids<>(StaticObject.NULL);
        this.setup = new JDWPSetup();
        this.innerClassRedefiner = new InnerClassRedefiner(context);
    }

    public void jdwpInit(TruffleLanguage.Env env, Object mainThread, VMEventListenerImpl vmEventListener) {
        Debugger debugger = env.lookup(env.getInstruments().get("debugger"), Debugger.class);
        this.controller = env.lookup(env.getInstruments().get(JDWPInstrument.ID), DebuggerController.class);
        ids.injectController(controller);
        vmEventListener.activate(mainThread, controller, this);
        setup.setup(debugger, controller, context.getEspressoEnv().JDWPOptions, this, mainThread, vmEventListener);
        redefinitionPluginHandler = RedefinitionPluginHandler.create(context);
        classRedefinition = context.createClassRedefinition(ids, redefinitionPluginHandler, controller);
    }

    public void finalizeContext() {
        if (context.getEspressoEnv().JDWPOptions != null) {
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
    public boolean isValidThread(Object thread, boolean checkTerminated) {
        if (thread instanceof StaticObject) {
            StaticObject staticObject = (StaticObject) thread;
            if (context.getMeta().java_lang_Thread.isAssignableFrom(staticObject.getKlass())) {
                if (checkTerminated) {
                    // check if thread has been terminated
                    return getThreadStatus(thread) != State.TERMINATED.value;
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isValidThreadGroup(Object threadGroup) {
        if (threadGroup instanceof StaticObject) {
            StaticObject staticObject = (StaticObject) threadGroup;
            return context.getMeta().java_lang_ThreadGroup.isAssignableFrom(staticObject.getKlass());
        } else {
            return false;
        }
    }

    @Override
    public Object getNullObject() {
        return StaticObject.NULL;
    }

    @Override
    public KlassRef[] findLoadedClass(String slashName) {
        if (slashName.length() == 1) {
            switch (slashName) {
                case "I":
                    return new KlassRef[]{context.getMeta()._int};
                case "Z":
                    return new KlassRef[]{context.getMeta()._boolean};
                case "S":
                    return new KlassRef[]{context.getMeta()._short};
                case "C":
                    return new KlassRef[]{context.getMeta()._char};
                case "B":
                    return new KlassRef[]{context.getMeta()._byte};
                case "J":
                    return new KlassRef[]{context.getMeta()._long};
                case "D":
                    return new KlassRef[]{context.getMeta()._double};
                case "F":
                    return new KlassRef[]{context.getMeta()._float};
                default:
                    throw new IllegalStateException("invalid primitive component type " + slashName);
            }
        } else if (slashName.startsWith("[")) {
            // array type
            int dimensions = 0;
            for (char c : slashName.toCharArray()) {
                if ('[' == c) {
                    dimensions++;
                } else {
                    break;
                }
            }
            String componentRawName = slashName.substring(dimensions);
            if (componentRawName.length() == 1) {
                // primitive
                switch (componentRawName) {
                    case "I":
                        return new KlassRef[]{context.getMeta()._int.getArrayClass(dimensions)};
                    case "Z":
                        return new KlassRef[]{context.getMeta()._boolean.getArrayClass(dimensions)};
                    case "S":
                        return new KlassRef[]{context.getMeta()._short.getArrayClass(dimensions)};
                    case "C":
                        return new KlassRef[]{context.getMeta()._char.getArrayClass(dimensions)};
                    case "B":
                        return new KlassRef[]{context.getMeta()._byte.getArrayClass(dimensions)};
                    case "J":
                        return new KlassRef[]{context.getMeta()._long.getArrayClass(dimensions)};
                    case "D":
                        return new KlassRef[]{context.getMeta()._double.getArrayClass(dimensions)};
                    case "F":
                        return new KlassRef[]{context.getMeta()._float.getArrayClass(dimensions)};
                    default:
                        throw new RuntimeException("invalid primitive component type " + componentRawName);
                }
            } else {
                // object type
                String componentType = componentRawName.substring(1, componentRawName.length() - 1);
                Symbol<Symbol.Type> type = context.getTypes().fromClassGetName(componentType);
                KlassRef[] klassRefs = context.getRegistries().findLoadedClassAny(type);
                KlassRef[] result = new KlassRef[klassRefs.length];
                for (int i = 0; i < klassRefs.length; i++) {
                    result[i] = klassRefs[i].getArrayClass(dimensions);
                }
                return result;
            }
        } else {
            // regular type
            Symbol<Symbol.Type> type = context.getTypes().fromClassGetName(slashName);
            return context.getRegistries().findLoadedClassAny(type);
        }
    }

    @Override
    public KlassRef[] getAllLoadedClasses() {
        return context.getRegistries().getAllLoadedClasses();
    }

    @Override
    public List<? extends KlassRef> getInitiatedClasses(Object classLoader) {
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
        return context.getThreadAccess().getHost((StaticObject) thread);
    }

    @Override
    public boolean isVirtualThread(Object thread) {
        return context.getThreadAccess().isVirtualThread((StaticObject) thread);
    }

    @Override
    public boolean isSingleSteppingDisabled() {
        return context.getLanguage().getThreadLocalState().isSteppingDisabled();
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
            return (String) InteropLibrary.getUncached().toDisplayString(staticObject, false);
        }
        return object.toString();
    }

    @Override
    public MethodRef getMethodFromRootNode(RootNode root) {
        if (root != null && root instanceof EspressoRootNode) {
            return ((EspressoRootNode) root).getMethodVersion();
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
            if (staticObject.getKlass().getType() == Symbol.Type.java_lang_Class) {
                return (KlassRef) context.getMeta().HIDDEN_MIRROR_KLASS.getHiddenObject(staticObject);
            }
        }
        return null;
    }

    @Override
    public KlassRef[] getNestedTypes(KlassRef klass) {
        if (klass instanceof ObjectKlass) {
            ArrayList<KlassRef> result = new ArrayList<>();
            ObjectKlass objectKlass = (ObjectKlass) klass;
            List<Symbol<Symbol.Name>> nestedTypeNames = objectKlass.getNestedTypeNames();

            StaticObject classLoader = objectKlass.getDefiningClassLoader();
            for (Symbol<Symbol.Name> nestedType : nestedTypeNames) {
                Symbol<Symbol.Type> type = context.getTypes().fromClassGetName(nestedType.toString());
                KlassRef loadedKlass = context.getRegistries().findLoadedClass(type, classLoader);
                if (loadedKlass != null && loadedKlass != klass) {
                    result.add(loadedKlass);
                }
            }
            return result.toArray(new KlassRef[0]);
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
                } else if (context.getMeta().java_lang_Thread.isAssignableFrom(staticObject.getKlass())) {
                    tag = TagConstants.THREAD;
                } else if (context.getMeta().java_lang_ThreadGroup.isAssignableFrom(staticObject.getKlass())) {
                    tag = TagConstants.THREAD_GROUP;
                } else if (staticObject.getKlass() == context.getMeta().java_lang_Class) {
                    tag = TagConstants.CLASS_OBJECT;
                } else if (context.getMeta().java_lang_ClassLoader.isAssignableFrom(staticObject.getKlass())) {
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
        return context.getThreadAccess().getThreadName((StaticObject) thread);
    }

    @Override
    public int getThreadStatus(Object thread) {
        return context.getThreadAccess().getState((StaticObject) thread);
    }

    @Override
    public Object getThreadGroup(Object thread) {
        return context.getThreadAccess().getThreadGroup((StaticObject) thread);
    }

    @Override
    public Object[] getTopLevelThreadGroups() {
        return new Object[]{context.getMainThreadGroup()};
    }

    @Override
    public int getArrayLength(Object array) {
        StaticObject staticObject = (StaticObject) array;
        EspressoLanguage language = context.getLanguage();
        if (staticObject.isForeignObject()) {
            try {
                long arrayLength = UNCACHED.getArraySize(staticObject.rawForeignObject(language));
                if (arrayLength > Integer.MAX_VALUE) {
                    return -1;
                }
                return (int) arrayLength;
            } catch (UnsupportedMessageException e) {
                return -1;
            }
        }
        return staticObject.length(language);
    }

    @Override
    public <T> T getUnboxedArray(Object array) {
        StaticObject staticObject = (StaticObject) array;
        EspressoLanguage language = staticObject.getKlass().getContext().getLanguage();
        return staticObject.unwrap(language);
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
        return maxIndex <= getArrayLength(array);
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
        if (arrayRef.isForeignObject()) {
            value = ForeignArrayUtils.readForeignArrayElement(arrayRef, index, context.getLanguage(), context.getMeta(), InteropLibrary.getUncached(), BranchProfile.create());
            if (!(value instanceof StaticObject)) {
                // For JDWP we have to have a ref type, so here we have to create a copy
                // value when possible as a StaticObject based on the foreign type.
                // Note: we only support Host String conversion for now
                if (String.class.isInstance(value)) {
                    return context.getMeta().toGuestString((String) value);
                } else {
                    throw new IllegalStateException("foreign object conversion not supported");
                }
            }
        } else if (((ArrayKlass) arrayRef.getKlass()).getComponentType().isPrimitive()) {
            // primitive array type needs wrapping
            Object boxedArray = getUnboxedArray(array);
            value = Array.get(boxedArray, index);
        } else {
            value = arrayRef.get(context.getLanguage(), index);
        }
        return value;
    }

    @Override
    public void setArrayValue(Object array, int index, Object value) {
        StaticObject arrayRef = (StaticObject) array;
        context.getInterpreterToVM().setArrayObject(context.getLanguage(), (StaticObject) value, index, arrayRef);
    }

    @Override
    public Object newArray(KlassRef klass, int length) {
        return StaticObject.createArray((ArrayKlass) klass, new StaticObject[length], context);
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
            return ex.getGuestException();
        } else {
            throw new RuntimeException("unknown exception type: " + exception.getClass(), exception);
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

    @Override
    public void stopThread(Object guestThread, Object guestThrowable) {
        Object previous = null;
        try {
            previous = controller.enterTruffleContext();
            context.getThreadAccess().stop((StaticObject) guestThread, (StaticObject) guestThrowable);
        } finally {
            controller.leaveTruffleContext(previous);
        }
    }

    @Override
    public void interruptThread(Object thread) {
        Object previous = null;
        try {
            previous = controller.enterTruffleContext();
            context.interruptThread((StaticObject) thread);
        } finally {
            controller.leaveTruffleContext(previous);
        }
    }

    @Override
    public boolean systemExitImplemented() {
        return true;
    }

    @Override
    public void exit(int exitCode) {
        Object previous = null;
        try {
            previous = controller.enterTruffleContext();
            context.truffleExit(null, exitCode);
        } finally {
            controller.leaveTruffleContext(previous);
        }
    }

    @Override
    public List<Path> getClassPath() {
        return context.getVmProperties().classpath();
    }

    @Override
    public List<Path> getBootClassPath() {
        return context.getVmProperties().bootClasspath();
    }

    @Override
    public int getCatchLocation(MethodRef method, Object guestException, int bci) {
        if (guestException instanceof StaticObject) {
            MethodVersion guestMethod = (MethodVersion) method;
            return guestMethod.getMethod().getCatchLocation(bci, (StaticObject) guestException);
        } else {
            return -1;
        }
    }

    @Override
    public int getNextBCI(RootNode callerRoot, Frame frame) {
        if (callerRoot instanceof EspressoRootNode) {
            EspressoRootNode espressoRootNode = (EspressoRootNode) callerRoot;
            int bci = (int) readBCIFromFrame(callerRoot, frame);
            if (bci >= 0) {
                BytecodeStream bs = new BytecodeStream(espressoRootNode.getMethodVersion().getOriginalCode());
                return bs.nextBCI(bci);
            }
        }
        return -1;
    }

    @Override
    public long readBCIFromFrame(RootNode root, Frame frame) {
        if (root instanceof EspressoRootNode && frame != null) {
            EspressoRootNode rootNode = (EspressoRootNode) root;
            return rootNode.readBCI(frame);
        }
        return -1;
    }

    @Override
    public CallFrame locateObjectWaitFrame() {
        Object currentThread = asGuestThread(Thread.currentThread());
        KlassRef klass = context.getMeta().java_lang_Object;
        MethodRef method = context.getMeta().java_lang_Object_wait.getMethodVersion();
        return new CallFrame(ids.getIdAsLong(currentThread), TypeTag.CLASS, ids.getIdAsLong(klass), method, ids.getIdAsLong(method), 0, null, null, null, null, null, controller);
    }

    @Override
    public Object getMonitorOwnerThread(Object object) {
        if (object instanceof StaticObject) {
            EspressoLock lock = ((StaticObject) object).getLock(context);
            return asGuestThread(lock.getOwnerThread());
        }
        return null;
    }

    @Override
    public int getMonitorEntryCount(Object monitor) {
        if (monitor instanceof StaticObject) {
            EspressoLock lock = ((StaticObject) monitor).getLock(context);
            return lock.getEntryCount();
        }
        return -1;
    }

    @Override
    public MonitorStackInfo[] getOwnedMonitors(CallFrame[] callFrames) {
        List<MonitorStackInfo> result = new ArrayList<>();
        int stackDepth = 0;
        for (CallFrame callFrame : callFrames) {
            RootNode rootNode = callFrame.getRootNode();
            if (rootNode instanceof EspressoRootNode) {
                EspressoRootNode espressoRootNode = (EspressoRootNode) rootNode;
                if (espressoRootNode.usesMonitors()) {
                    StaticObject[] monitors = espressoRootNode.getMonitorsOnFrame(callFrame.getFrame());
                    for (StaticObject monitor : monitors) {
                        if (monitor != null) {
                            result.add(new MonitorStackInfo(monitor, stackDepth));
                        }
                    }
                }
            }
            stackDepth++;
        }
        return result.toArray(new MonitorStackInfo[result.size()]);
    }

    @Override
    public void clearFrameMonitors(CallFrame frame) {
        RootNode rootNode = frame.getRootNode();
        if (rootNode instanceof EspressoRootNode) {
            EspressoRootNode espressoRootNode = (EspressoRootNode) rootNode;
            espressoRootNode.abortInternalMonitors(frame.getFrame());
        }
    }

    @Override
    public void abort(int exitCode) {
        context.doExit(197);
    }

    @Override
    public Class<? extends TruffleLanguage<?>> getLanguageClass() {
        return EspressoLanguage.class;
    }

    private BciProvider getBciProviderNode(Node node) {
        if (node instanceof BciProvider bciProvider) {
            return bciProvider;
        }
        Node currentNode = node.getParent();
        while (currentNode != null) {
            if (currentNode instanceof BciProvider) {
                return (BciProvider) currentNode;
            }
            currentNode = currentNode.getParent();
        }
        Node instrumentableNode = getInstrumentableNode(node.getRootNode());
        if (instrumentableNode instanceof BciProvider bciProvider) {
            return bciProvider;
        }
        return null;
    }

    public long getBCI(Node rawNode, Frame frame) {
        BciProvider bciProvider = getBciProviderNode(rawNode);
        if (bciProvider == null) {
            return -1;
        }
        int bci = bciProvider.getBci(frame);
        if (bci == -2) { // native or substituted method
            // for substituted java methods we're expected to return 0
            return rawNode instanceof IntrinsicSubstitutorNode ? 0 : -1;
        }
        return bci;
    }

    @Override
    public Node getInstrumentableNode(RootNode rootNode) {
        if (rootNode instanceof EspressoRootNode) {
            EspressoInstrumentableRootNode baseMethodNode = ((EspressoRootNode) rootNode).getMethodNode();
            if (baseMethodNode instanceof InstrumentableNode.WrapperNode) {
                return ((InstrumentableNode.WrapperNode) baseMethodNode).getDelegateNode();
            } else {
                return baseMethodNode;
            }
        }
        return rootNode;
    }

    @Override
    public boolean isMemberOf(Object guestObject, KlassRef klass) {
        if (guestObject instanceof StaticObject) {
            StaticObject staticObject = (StaticObject) guestObject;
            return klass.isAssignable(staticObject.getKlass());
        } else {
            return false;
        }
    }

    @Override
    public ModuleRef[] getAllModulesRefs() {
        return context.getRegistries().getAllModuleRefs();
    }

    public void rerunclinit(ObjectKlass oldKlass) {
        classInitializerActions.add(new ReloadingAction(oldKlass));
    }

    public synchronized int redefineClasses(List<RedefineInfo> redefineInfos) {
        // list to collect all changed classes
        List<ObjectKlass> changedKlasses = new ArrayList<>(redefineInfos.size());
        try {
            controller.fine(() -> "Redefining " + redefineInfos.size() + " classes");

            // begin redefine transaction
            classRedefinition.begin();

            // clear synthetic fields, which forces re-resolution
            classRedefinition.clearDelegationFields();

            // invalidate missing fields assumption, which forces re-resolution
            classRedefinition.invalidateMissingFields();

            // redefine classes based on direct code changes first
            doRedefine(redefineInfos, changedKlasses);

            // Now, collect additional classes to redefine in response
            // to the redefined classes above
            List<RedefineInfo> additional = Collections.synchronizedList(new ArrayList<>());
            classRedefinition.addExtraReloadClasses(redefineInfos, additional);
            // redefine additional classes now
            doRedefine(additional, changedKlasses);

            // re-run all registered class initializers before ending transaction
            classInitializerActions.forEach((reloadingAction) -> {
                try {
                    reloadingAction.fire();
                } catch (Throwable t) {
                    // Some anomalies when rerunning class initializers
                    // to be expected. Treat them as non-fatal.
                    controller.warning(() -> "exception while re-running a class initializer!");
                }
            });
            assert !changedKlasses.contains(null);
            // run post redefinition plugins before ending the redefinition transaction
            try {
                classRedefinition.runPostRedefinitionListeners(changedKlasses.toArray(new ObjectKlass[changedKlasses.size()]));
            } catch (Throwable t) {
                controller.severe(() -> JDWPContextImpl.class.getName() + ": redefineClasses: " + t.getMessage());
            }
        } catch (RedefinitionNotSupportedException ex) {
            return ex.getErrorCode();
        } finally {
            classRedefinition.end();
        }
        return 0;
    }

    private void doRedefine(List<RedefineInfo> redefineInfos, List<ObjectKlass> changedKlasses) throws RedefinitionNotSupportedException {
        // list to hold removed inner classes that must be marked removed
        List<ObjectKlass> removedInnerClasses = new ArrayList<>(0);
        // list of classes that need to refresh due to
        // changes in other classes for things like vtable
        List<ObjectKlass> invalidatedClasses = new ArrayList<>();
        // list of all classes that have been redefined within this transaction
        List<ObjectKlass> redefinedClasses = new ArrayList<>();

        // match anon inner classes with previous state
        HotSwapClassInfo[] matchedInfos = innerClassRedefiner.matchAnonymousInnerClasses(redefineInfos, removedInnerClasses);

        // detect all changes to all classes, throws if redefinition cannot be completed
        // due to the nature of the changes
        List<ChangePacket> changePackets = classRedefinition.detectClassChanges(matchedInfos);

        // We have to redefine super classes prior to subclasses
        Collections.sort(changePackets, new HierarchyComparator());

        for (ChangePacket packet : changePackets) {
            controller.fine(() -> "Redefining class " + packet.info.getNewName());
            int result = classRedefinition.redefineClass(packet, invalidatedClasses, redefinedClasses);
            if (result != 0) {
                throw new RedefinitionNotSupportedException(result);
            }
        }

        // refresh invalidated classes if not already redefined
        Collections.sort(invalidatedClasses, new SubClassHierarchyComparator());
        for (ObjectKlass invalidatedClass : invalidatedClasses) {
            if (!redefinedClasses.contains(invalidatedClass)) {
                controller.fine(() -> "Refreshing invalidated class " + invalidatedClass.getName());
                invalidatedClass.swapKlassVersion(ids);
            }
        }

        // include invalidated classes in all changed classes list
        changedKlasses.addAll(invalidatedClasses);

        // update the JWDP IDs for renamed inner classes
        for (ChangePacket changePacket : changePackets) {
            ObjectKlass klass = changePacket.info.getKlass();
            if (klass != null) {
                changedKlasses.add(klass);
                if (changePacket.info.isRenamed()) {
                    ids.updateId(klass);
                }
            }
        }

        // tell the InnerClassRedefiner to commit the changes to cache
        innerClassRedefiner.commit(matchedInfos);

        for (ObjectKlass removed : removedInnerClasses) {
            removed.removeByRedefinition();
        }
    }

    public void registerExternalHotSwapHandler(StaticObject handler) {
        redefinitionPluginHandler.registerExternalHotSwapHandler(handler);
    }

    private static class HierarchyComparator implements Comparator<ChangePacket> {
        public int compare(ChangePacket packet1, ChangePacket packet2) {
            Klass k1 = packet1.info.getKlass();
            Klass k2 = packet2.info.getKlass();
            // we need to do this check because isAssignableFrom is true in this case
            // and we would get an order that doesn't exist
            if (k1 == null || k2 == null || k1.equals(k2)) {
                return 0;
            }
            if (k1.isAssignableFrom(k2)) {
                return -1;
            } else if (k2.isAssignableFrom(k1)) {
                return 1;
            }
            // no hierarchy, check anon inner classes
            Matcher m1 = InnerClassRedefiner.ANON_INNER_CLASS_PATTERN.matcher(k1.getNameAsString());
            Matcher m2 = InnerClassRedefiner.ANON_INNER_CLASS_PATTERN.matcher(k2.getNameAsString());
            if (!m1.matches()) {
                return -1;
            } else {
                if (m2.matches()) {
                    return 0;
                } else {
                    return 1;
                }
            }
        }
    }

    private static class SubClassHierarchyComparator implements Comparator<ObjectKlass> {
        public int compare(ObjectKlass k1, ObjectKlass k2) {
            // we need to do this check because isAssignableFrom is true in this case
            // and we would get an order that doesn't exist
            if (k1.equals(k2)) {
                return 0;
            }
            if (k1.isAssignableFrom(k2)) {
                return -1;
            } else if (k2.isAssignableFrom(k1)) {
                return 1;
            }
            // no hierarchy
            return 0;
        }
    }

    private final class ReloadingAction {
        private ObjectKlass klass;

        private ReloadingAction(ObjectKlass klass) {
            this.klass = klass;
        }

        private void fire() {
            klass.reRunClinit();
        }
    }
}
