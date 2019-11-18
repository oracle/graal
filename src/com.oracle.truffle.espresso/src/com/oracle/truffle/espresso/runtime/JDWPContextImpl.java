package com.oracle.truffle.espresso.runtime;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.espresso.jdwp.api.FieldRef;
import com.oracle.truffle.espresso.jdwp.api.JDWPContext;
import com.oracle.truffle.espresso.jdwp.api.JDWPSetup;
import com.oracle.truffle.espresso.jdwp.impl.JDWPVirtualMachine;
import com.oracle.truffle.espresso.jdwp.api.MethodRef;
import com.oracle.truffle.espresso.jdwp.api.KlassRef;
import com.oracle.truffle.espresso.jdwp.api.NullKlass;
import com.oracle.truffle.espresso.jdwp.impl.ClassObjectId;
import com.oracle.truffle.espresso.jdwp.api.Ids;
import com.oracle.truffle.espresso.jdwp.impl.JDWPCallFrame;
import com.oracle.truffle.espresso.jdwp.impl.JDWPVirtualMachineImpl;
import com.oracle.truffle.espresso.jdwp.impl.TagConstants;
import com.oracle.truffle.espresso.impl.ArrayKlass;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.EspressoRootNode;
import com.oracle.truffle.espresso.substitutions.Target_java_lang_Thread;

import java.lang.reflect.Array;
import java.util.ArrayList;

public final class JDWPContextImpl implements JDWPContext {

    public static final String JAVA_LANG_STRING = "Ljava/lang/String;";
    public static final NullKlass NULL_KLASS = new NullKlass();

    private final EspressoContext context;
    private final JDWPVirtualMachine vm;
    private final Ids<Object> ids;

    public JDWPContextImpl(EspressoContext context) {
        this.context = context;
        this.vm = new JDWPVirtualMachineImpl();
        this.ids = new Ids<>(StaticObject.NULL);
    }

    public void jdwpInit(TruffleLanguage.Env env) {
        // enable JDWP instrumenter only if options are set (assumed valid if non-null)
        if (context.JDWPOptions != null) {
            JDWPSetup.setup(env, context.JDWPOptions, this);
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
    public KlassRef getNullKlass() {
        return NULL_KLASS;
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
    public Object getHost2GuestThread(Thread hostThread) {
        return context.getGuestThreadFromHost(hostThread);
    }

    @Override
    public Thread getGuest2HostThread(Object thread) {
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
    public JDWPVirtualMachine getVirtualMachine() {
        return vm;
    }

    @Override
    public KlassRef getKlassFromRootNode(RootNode root) {
        if (root != null && root instanceof EspressoRootNode) {
            return ((EspressoRootNode) root).getMethod().getDeclaringKlass();
        }
        return null;
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
            if (StaticObject.NULL == object) {
                // null object
                return getNullKlass();
            } else {
                return ((StaticObject) object).getKlass();
            }
        } else {
            return ((ClassObjectId) object).getKlassRef();
        }
    }

    @Override
    public byte getTag(Object object) {
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
                }
            }
        }
        return tag;
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
        return new Object[] {context.getSystemThreadGroup()};
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
    public boolean verifyArrayLength(Object array, int length) {
        StaticObject staticObject = (StaticObject) array;
        return staticObject.length() < length;
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
        return field.getValue(((ObjectKlass)field.getDeclaringKlass()).tryInitializeAndGetStatics());
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
    public Object toGuest(Object object) {
        // be sure that current thread has set context
        Object previous = null;
        try {
            previous = context.getEnv().getContext().enter();
            return context.getMeta().toGuestBoxed(object);
        } finally {
            if (previous != null) {
                context.getEnv().getContext().leave(previous);
            }
        }
    }

    @Override
    public Object toGuestString(String string) {
        // be sure that current thread has set context
        Object previous = null;
        try {
            previous = context.getEnv().getContext().enter();
            return context.getMeta().toGuestString(string);
        } finally {
            if (previous != null) {
                context.getEnv().getContext().leave(previous);
            }
        }
    }

    @Override
    public Object getGuestException(Throwable exception) {
        if (exception instanceof EspressoException) {
            EspressoException ex = (EspressoException) exception;
            return ex.getExceptionObject();
        }
        else {
            throw new RuntimeException("unknown exception type: " + exception.getClass());
        }
    }

    @Override
    public JDWPCallFrame[] getStackTrace(Object thread) {
        // TODO(Gregersen) - implement this method when we can get stack frames
        // for arbitrary threads.
        return new JDWPCallFrame[0];
    }

    @Override
    public boolean isInstanceOf(Object object, KlassRef klass) {
        StaticObject staticObject = (StaticObject) object;
        return klass.isAssignable(staticObject.getKlass());
    }
}
