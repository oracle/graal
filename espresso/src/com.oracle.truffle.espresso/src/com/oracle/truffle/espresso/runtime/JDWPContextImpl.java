/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.oracle.truffle.api.ThreadLocalAction;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLogger;
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
import com.oracle.truffle.espresso.classfile.bytecode.BytecodeStream;
import com.oracle.truffle.espresso.classfile.descriptors.Name;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.Type;
import com.oracle.truffle.espresso.descriptors.EspressoSymbols.Types;
import com.oracle.truffle.espresso.impl.ArrayKlass;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.jdwp.api.CallFrame;
import com.oracle.truffle.espresso.jdwp.api.FieldRef;
import com.oracle.truffle.espresso.jdwp.api.Ids;
import com.oracle.truffle.espresso.jdwp.api.JDWPContext;
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
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.BciProvider;
import com.oracle.truffle.espresso.nodes.EspressoInstrumentableRootNode;
import com.oracle.truffle.espresso.nodes.EspressoRootNode;
import com.oracle.truffle.espresso.nodes.quick.interop.ForeignArrayUtils;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.threads.State;
import com.oracle.truffle.espresso.vm.InterpreterToVM;

public final class JDWPContextImpl implements JDWPContext {

    public static final TruffleLogger LOGGER = TruffleLogger.getLogger(EspressoLanguage.ID, JDWPContextImpl.class);
    private static final InteropLibrary UNCACHED = InteropLibrary.getUncached();

    private static final long SUSPEND_TIMEOUT = 100;

    private final EspressoContext context;
    private final Ids<Object> ids;
    private DebuggerController controller;
    private VMEventListenerImpl vmEventListener;

    public JDWPContextImpl(EspressoContext context) {
        this.context = context;
        this.ids = new Ids<>(StaticObject.NULL);
    }

    public void jdwpInit(TruffleLanguage.Env env, Object mainThread, VMEventListenerImpl eventListener) {
        Debugger debugger = env.lookup(env.getInstruments().get("debugger"), Debugger.class);
        this.controller = env.lookup(env.getInstruments().get(JDWPInstrument.ID), DebuggerController.class);
        vmEventListener = eventListener;
        eventListener.activate(mainThread, controller, this);
        controller.initialize(debugger, context.getEspressoEnv().JDWPOptions, this, mainThread, eventListener);
    }

    public void finalizeContext() {
        if (context.getEspressoEnv().JDWPOptions != null) {
            if (controller != null) { // in case we exited before initializing the controller field
                controller.disposeDebugger(false);
            }
        }
    }

    @Override
    public void replaceController(DebuggerController newController) {
        this.controller = newController;
        vmEventListener.replaceController(newController);
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
        if (thread instanceof StaticObject staticObject) {
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
        if (threadGroup instanceof StaticObject staticObject) {
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
                Symbol<Type> type = context.getTypes().fromClassGetName(componentType);
                KlassRef[] klassRefs = context.getRegistries().findLoadedClassAny(type);
                KlassRef[] result = new KlassRef[klassRefs.length];
                for (int i = 0; i < klassRefs.length; i++) {
                    result[i] = klassRefs[i].getArrayClass(dimensions);
                }
                return result;
            }
        } else {
            // regular type
            Symbol<Type> type = context.getTypes().fromClassGetName(slashName);
            return context.getRegistries().findLoadedClassAny(type);
        }
    }

    @Override
    public KlassRef[] getAllLoadedClasses() {
        return context.getRegistries().getAllLoadedClasses().toArray(new KlassRef[0]);
    }

    @Override
    public List<? extends KlassRef> getInitiatedClasses(Object classLoader) {
        return context.getRegistries().getLoadedClassesByLoader((StaticObject) classLoader);
    }

    @Override
    public boolean isValidClassLoader(Object object) {
        if (object instanceof StaticObject loader) {
            return InterpreterToVM.instanceOf(loader, context.getMeta().java_lang_ClassLoader);
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
    public Object allocateInstance(KlassRef klass) {
        return context.getAllocator().createNew((ObjectKlass) klass);
    }

    @Override
    public void steppingInProgress(Thread t, boolean value) {
        Object previous = null;
        try {
            previous = controller.enterTruffleContext();
            context.getLanguage().getThreadLocalStateFor(t).setSteppingInProgress(value);
        } finally {
            controller.leaveTruffleContext(previous);
        }
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
        if (object instanceof StaticObject staticObject) {
            return (String) InteropLibrary.getUncached().toDisplayString(staticObject, false);
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
        if (classObject instanceof StaticObject staticObject) {
            if (staticObject.getKlass().getType() == Types.java_lang_Class) {
                return (KlassRef) context.getMeta().HIDDEN_MIRROR_KLASS.getHiddenObject(staticObject);
            }
        }
        return null;
    }

    @Override
    public KlassRef[] getNestedTypes(KlassRef klass) {
        if (klass instanceof ObjectKlass objectKlass) {
            ArrayList<KlassRef> result = new ArrayList<>();
            List<Symbol<Name>> nestedTypeNames = objectKlass.getNestedTypeNames();

            StaticObject classLoader = objectKlass.getDefiningClassLoader();
            for (Symbol<Name> nestedType : nestedTypeNames) {
                Symbol<Type> type = context.getTypes().fromClassGetName(nestedType.toString());
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
        if (object instanceof StaticObject staticObject) {
            if (object == StaticObject.NULL) {
                return tag;
            }
            tag = staticObject.getKlass().getTagConstant();
            if (tag == TagConstants.OBJECT) {
                if (staticObject.getKlass() == context.getMeta().java_lang_String) {
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
        if (object instanceof StaticObject staticObject) {
            return staticObject.isArray();
        }
        return false;
    }

    @Override
    public boolean verifyArrayLength(Object array, int maxIndex) {
        return maxIndex <= getArrayLength(array);
    }

    @Override
    public byte getArrayComponentTag(Object array) {
        StaticObject staticObject = (StaticObject) array;
        assert ((StaticObject) array).isArray();
        ArrayKlass arrayKlass = (ArrayKlass) staticObject.getKlass();
        if (arrayKlass.getDimension() > 1) {
            return TagConstants.ARRAY;
        }
        return TagConstants.toTagConstant(arrayKlass.getComponentType().getJavaKind());
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
                if (value instanceof String) {
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
        byte tag = getTag(value);
        switch (tag) {
            case TagConstants.BOOLEAN -> context.getInterpreterToVM().setArrayByte(context.getLanguage(), (boolean) value ? (byte) 1 : 0, index, arrayRef);
            case TagConstants.BYTE -> context.getInterpreterToVM().setArrayByte(context.getLanguage(), (byte) value, index, arrayRef);
            case TagConstants.SHORT -> context.getInterpreterToVM().setArrayShort(context.getLanguage(), (short) value, index, arrayRef);
            case TagConstants.CHAR -> context.getInterpreterToVM().setArrayChar(context.getLanguage(), (char) value, index, arrayRef);
            case TagConstants.INT -> context.getInterpreterToVM().setArrayInt(context.getLanguage(), (int) value, index, arrayRef);
            case TagConstants.FLOAT -> context.getInterpreterToVM().setArrayFloat(context.getLanguage(), (float) value, index, arrayRef);
            case TagConstants.LONG -> context.getInterpreterToVM().setArrayLong(context.getLanguage(), (long) value, index, arrayRef);
            case TagConstants.DOUBLE -> context.getInterpreterToVM().setArrayDouble(context.getLanguage(), (double) value, index, arrayRef);
            case TagConstants.ARRAY, TagConstants.STRING, TagConstants.OBJECT ->
                context.getInterpreterToVM().setArrayObject(context.getLanguage(), (StaticObject) value, index, arrayRef);
            default -> throw new RuntimeException("should not reach here: " + tag);
        }
    }

    @Override
    public Object newArray(KlassRef klass, int length) {
        ArrayKlass arrayKlass = (ArrayKlass) klass;
        Klass componentType = arrayKlass.getComponentType();
        if (componentType.isPrimitive()) {
            return context.getAllocator().createNewPrimitiveArray(componentType, length);
        }
        return context.getAllocator().createNewReferenceArray(componentType, length);
    }

    @Override
    public Object toGuestString(String string) {
        return context.getMeta().toGuestString(string);
    }

    @Override
    public Object getGuestException(Throwable exception) {
        if (exception instanceof EspressoException ex) {
            return ex.getGuestException();
        } else {
            throw new RuntimeException("unknown exception type: " + exception.getClass(), exception);
        }
    }

    @Override
    public CallFrame[] getStackTrace(Object thread) {
        Thread hostThread = asHostThread(thread);

        if (Thread.currentThread() == hostThread) {
            // on current thread, we can just fetch the frames directly
            return controller.getCallFrames(thread);
        } else {
            // on other threads, we have to utilize Truffle safe points
            CollectStackFramesAction action = new CollectStackFramesAction(thread);
            Future<Void> future = context.getEnv().submitThreadLocal(new Thread[]{hostThread}, action);
            try {
                future.get(SUSPEND_TIMEOUT, TimeUnit.MILLISECONDS);
                return action.result;
            } catch (ExecutionException e) {
                throw EspressoError.shouldNotReachHere(e);
            } catch (InterruptedException | TimeoutException e) {
                // OK, when interrupted we can't get stack frames
                future.cancel(true);
                return new CallFrame[0];
            }
        }
    }

    private final class CollectStackFramesAction extends ThreadLocalAction {
        CallFrame[] result;

        final Object guestThread;

        CollectStackFramesAction(Object guestThread) {
            super(false, false);
            this.guestThread = guestThread;
        }

        @Override
        protected void perform(Access access) {
            result = controller.getCallFrames(guestThread);
        }
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
            Method guestMethod = (Method) method;
            return guestMethod.getCatchLocation(bci, (StaticObject) guestException);
        } else {
            return -1;
        }
    }

    @Override
    public int getNextBCI(RootNode callerRoot, Frame frame) {
        if (callerRoot instanceof EspressoRootNode espressoRootNode) {
            int bci = (int) readBCIFromFrame(callerRoot, frame);
            if (bci >= 0) {
                BytecodeStream bs = new BytecodeStream(espressoRootNode.getMethod().getOriginalCode());
                return bs.nextBCI(bci);
            }
        }
        return -1;
    }

    @Override
    public long readBCIFromFrame(RootNode root, Frame frame) {
        if (root instanceof EspressoRootNode rootNode && frame != null) {
            return rootNode.readBCI(frame);
        }
        return -1;
    }

    @Override
    public CallFrame locateObjectWaitFrame() {
        Object currentThread = asGuestThread(Thread.currentThread());
        KlassRef klass = context.getMeta().java_lang_Object;
        MethodRef method = context.getMeta().java_lang_Object_wait;
        return new CallFrame(ids.getIdAsLong(currentThread), TypeTag.CLASS, ids.getIdAsLong(klass), method, ids.getIdAsLong(method), 0, null, null, null, null, null, LOGGER);
    }

    @Override
    public Object getMonitorOwnerThread(Object object) {
        if (object instanceof StaticObject) {
            EspressoLock lock = ((StaticObject) object).getLock(context);
            Thread ownerThread = lock.getOwnerThread();
            if (ownerThread != null) {
                return asGuestThread(ownerThread);
            }
        }
        return null;
    }

    @Override
    public int getMonitorEntryCount(Object monitorOwnerThread, Object monitor) {
        if (!(monitor instanceof StaticObject theMonitor)) {
            return -1;
        }
        Thread hostThread = asHostThread(monitorOwnerThread);
        if (Thread.currentThread() == hostThread) {
            // on current thread, we can get the results directly
            EspressoLock lock = theMonitor.getLock(context);
            return lock.getEntryCount();
        } else {
            // on other threads, we have to utilize Truffle safe points
            GetMonitorEntryCountAction action = new GetMonitorEntryCountAction(theMonitor);
            Future<Void> future = context.getEnv().submitThreadLocal(new Thread[]{hostThread}, action);
            try {
                future.get(SUSPEND_TIMEOUT, TimeUnit.MILLISECONDS);
                return action.result;
            } catch (ExecutionException e) {
                throw EspressoError.shouldNotReachHere(e);
            } catch (InterruptedException | TimeoutException e) {
                future.cancel(true);
                // OK, not possible to get accurate result, but since we know the monitor is
                // currently owned by the owner thread, we return 1 because it's currently locked at
                // least once
                return 1;
            }
        }
    }

    private final class GetMonitorEntryCountAction extends ThreadLocalAction {
        int result;
        StaticObject monitor;

        GetMonitorEntryCountAction(StaticObject monitor) {
            super(false, false);
            this.monitor = monitor;
        }

        @Override
        protected void perform(Access access) {
            // Since by the time we get here this thread might not own the lock, so we'll return 0.
            // In this case it's up to the caller to handle that.
            EspressoLock lock = monitor.getLock(context);
            result = lock.getEntryCount();
        }
    }

    @Override
    public MonitorStackInfo[] getOwnedMonitors(CallFrame[] callFrames) {
        List<MonitorStackInfo> result = new ArrayList<>();
        int stackDepth = 0;
        for (CallFrame callFrame : callFrames) {
            RootNode rootNode = callFrame.getRootNode();
            if (rootNode instanceof EspressoRootNode espressoRootNode) {
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
        if (rootNode instanceof EspressoRootNode espressoRootNode) {
            espressoRootNode.abortInternalMonitors(frame.getFrame());
        }
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
        return bciProvider.getBci(frame);
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
        if (guestObject instanceof StaticObject staticObject) {
            return klass.isAssignable(staticObject.getKlass());
        } else {
            return false;
        }
    }

    @Override
    public ModuleRef[] getAllModulesRefs() {
        return context.getRegistries().getAllModuleRefs();
    }

    public synchronized int redefineClasses(List<RedefineInfo> redefineInfos) {
        return context.getClassRedefinition().redefineClasses(redefineInfos, false, true);
    }
}
