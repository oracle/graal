package com.oracle.truffle.espresso.debugger.api;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.espresso.debugger.jdwp.Ids;

public interface JDWPContext {

    TruffleLanguage.Env getEnv();

    Object getHost2GuestThread(Thread hostThread);

    klassRef getNullKlass();

    klassRef[] findLoadedClass(String slashName);

    JDWPVirtualMachine getVirtualMachine();

    klassRef getKlassFromRootNode(RootNode root);

    MethodRef getMethodFromRootNode(RootNode root);

    Ids getIds();

    Object[] getAllGuestThreads();

    Object toGuestString(String string);

    klassRef getRefType(Object object);

    byte getSpecificObjectTag(Object object);

    byte getTag(Object value);

    Object getNullObject();

    String getStringValue(Object object);

    String getThreadName(Object thread);

    int getThreadStatus(Object thread);

    Object getThreadGroup(Object thread);

    int getArrayLength(Object array);

    byte getTypeTag(Object array);

    <T> T getUnboxedArray(Object array);

    klassRef[] getInitiatedClasses(Object classLoader);

    Object getStaticFieldValue(FieldRef field);

    void setStaticFieldValue(FieldRef field, klassRef klassRef, Object value);

    Object getArrayValue(Object array, int i);

    void setArrayValue(Object array, int i, Object value);
}
