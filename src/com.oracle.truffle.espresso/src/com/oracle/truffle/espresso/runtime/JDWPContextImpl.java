package com.oracle.truffle.espresso.runtime;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.espresso.impl.ClassRegistries;
import com.oracle.truffle.espresso.jdwp.api.FieldRef;
import com.oracle.truffle.espresso.jdwp.api.JDWPContext;
import com.oracle.truffle.espresso.jdwp.api.JDWPSetup;
import com.oracle.truffle.espresso.jdwp.api.JDWPVirtualMachine;
import com.oracle.truffle.espresso.jdwp.api.MethodRef;
import com.oracle.truffle.espresso.jdwp.api.KlassRef;
import com.oracle.truffle.espresso.jdwp.impl.ClassObjectId;
import com.oracle.truffle.espresso.jdwp.api.Ids;
import com.oracle.truffle.espresso.jdwp.impl.TagConstants;
import com.oracle.truffle.espresso.impl.ArrayKlass;
import com.oracle.truffle.espresso.impl.NullKlass;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.nodes.EspressoRootNode;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Iterator;

public final class JDWPContextImpl implements JDWPContext {

    public static final String JAVA_LANG_STRING = "Ljava/lang/String;";

    private final EspressoContext context;
    private final JDWPVirtualMachine vm;
    private final Ids ids;

    public JDWPContextImpl(EspressoContext context) {
        this.context = context;
        this.vm = new EspressoVirtualMachine();
        this.ids = new Ids(StaticObject.NULL);
    }

    public void jdwpInit() {
        // enable JDWP instrumenter only if options are set (assumed valid if non-null)
        if (context.JDWPOptions != null) {
            JDWPSetup.setup(context.JDWPOptions, this);
        }
    }

    @Override
    public Ids getIds() {
        return ids;
    }

    @Override
    public boolean isString(Object string) {
        return context.getMeta().isString(string);
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
        return NullKlass.getKlass(context);
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
    public boolean isValidClassLoader(Object classLoader) {
        if (classLoader instanceof StaticObject) {
            StaticObject loader = (StaticObject) classLoader;
            return context.getRegistries().isClassLoader(loader);
        }
        return false;
    }

    @Override
    public TruffleLanguage.Env getEnv() {
        return context.getEnv();
    }

    @Override
    public Object getHost2GuestThread(Thread hostThread) {
        return context.getGuestThreadFromHost(hostThread);
    }

    @Override
    public Object[] getAllGuestThreads() {
        Iterable<StaticObject> activeThreads = context.getActiveThreads();
        ArrayList<Object> threads = new ArrayList<>();

        Iterator<StaticObject> it = activeThreads.iterator();
        while (it.hasNext()) {
            threads.add(it.next());
        }
        return threads.toArray(new Object[threads.size()]);
    }

    @Override
    public Object toGuestString(String string) {
        return context.getMeta().toGuestString(string);
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
    public byte getSpecificObjectTag(Object object) {
        if (object instanceof StaticObject) {
            if (((StaticObject) object).isArray()) {
                return TagConstants.ARRAY;
            }
            else if (JAVA_LANG_STRING.equals(((StaticObject) object).getKlass().getType().toString())) {
                return TagConstants.STRING;
            }
        }
        return TagConstants.OBJECT;
    }

    @Override
    public byte getTag(Object object) {
        byte tag = TagConstants.OBJECT;
        if (object instanceof StaticObject) {
            StaticObject staticObject = (StaticObject) object;
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
    public boolean isArray(Object array) {
        if (array instanceof StaticObject) {
            StaticObject staticObject = (StaticObject) array;
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
    public byte getTypeTag(Object object) {
        StaticObject staticObject = (StaticObject) object;
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
                if (JAVA_LANG_STRING.equals(((StaticObject) object).getKlass().getType().toString())) {
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
    public void setStaticFieldValue(FieldRef field, KlassRef klassRef, Object value) {
        field.setValue(((ObjectKlass) klassRef).tryInitializeAndGetStatics(), value);
    }

    @Override
    public Object getArrayValue(Object array, int i) {
        StaticObject arrayRef = (StaticObject) array;
        Object value;
        if (arrayRef.getKlass().getComponentType().isPrimitive()) {
            // primitive array type needs wrapping
            Object boxedArray = getUnboxedArray(array);
            value = Array.get(boxedArray, i);
        } else {
            value = arrayRef.get(i);
        }
        return value;
    }

    @Override
    public void setArrayValue(Object array, int i, Object value) {
        StaticObject arrayRef = (StaticObject) array;
        arrayRef.putObject((StaticObject) value, i, context.getMeta());
    }
}
