package com.oracle.truffle.espresso.jni;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.intrinsics.Target_java_lang_Object;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObjectClass;

import java.lang.reflect.Method;

public class JniEnv {

    // Load native library nespresso.dll (Windows) or libnespresso.so (Unixes)
    // at runtime
    private final static TruffleObject nespressoLibrary = loadLibrary(System.getProperty("nespresso.library", "nespresso"));
    private final static TruffleObject createJniEnv = lookupAndBind(nespressoLibrary, "createJniEnv",
                    "(env, " +

                                    "(string): pointer" + // fetch_by_name

                                    "): pointer" // returns JNIEnv*
    );

    private final static TruffleObject dupClosureRef = lookup(nespressoLibrary, "dupClosureRef");

    private final static TruffleObject disposeJniEnv = lookupAndBind(nespressoLibrary, "disposeJniEnv",
                    "(env): void");

    private static TruffleObject dupClosureRefAndCast(String signature) {
        return bind(dupClosureRef, "(env, " + signature + ")" + ": pointer");
    }

    private long jniEnvPtr;

    private JniEnv() {
        try {
            TruffleObject ptr = (TruffleObject) ForeignAccess.sendExecute(Message.EXECUTE.createNode(), createJniEnv,
                            new Callback(1, args -> {
                                String name = (String) args[0];
                                try {
                                    if ("GetVersion".equals(name)) {
                                        return ForeignAccess.sendExecute(Message.EXECUTE.createNode(),
                                                        dupClosureRefAndCast("(pointer): sint32"),
                                                        new Callback(1, args2 -> GetVersion()));
                                    }
                                    if ("GetArrayLength".equals(name)) {
                                        return ForeignAccess.sendExecute(Message.EXECUTE.createNode(),
                                                        dupClosureRefAndCast("(pointer, object): sint32"),
                                                        new Callback(2, args2 -> GetArrayLength(args2[1])));
                                    }
                                    throw EspressoError.shouldNotReachHere();
                                } catch (UnsupportedTypeException | UnsupportedMessageException | ArityException e) {
                                    throw EspressoError.shouldNotReachHere();
                                }
                            }));

            this.jniEnvPtr = (long) ForeignAccess.sendUnbox(Message.UNBOX.createNode(), ptr);
            assert this.jniEnvPtr != 0;
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            throw EspressoError.shouldNotReachHere("Cannot initialize Espresso native interface");
        }
    }

    private static TruffleObject lookup(TruffleObject library, String method) {
        try {
            return (TruffleObject) ForeignAccess.sendRead(Message.READ.createNode(), library, method);
        } catch (UnknownIdentifierException | UnsupportedMessageException e) {
            throw EspressoError.shouldNotReachHere("Cannot find " + method);
        }
    }

    private static TruffleObject bind(TruffleObject symbol, String signature) {
        try {
            return (TruffleObject) ForeignAccess.sendInvoke(Message.INVOKE.createNode(), symbol, "bind", signature);
        } catch (UnsupportedTypeException | ArityException | UnknownIdentifierException | UnsupportedMessageException e) {
            throw EspressoError.shouldNotReachHere("Cannot bind 'createJniEnv'");
        }
    }

    private static TruffleObject lookupAndBind(TruffleObject library, String method, String signature) {
        try {
            TruffleObject symbol = (TruffleObject) ForeignAccess.sendRead(Message.READ.createNode(), library, method);
            return (TruffleObject) ForeignAccess.sendInvoke(Message.INVOKE.createNode(), symbol, "bind", signature);
        } catch (UnsupportedTypeException | ArityException | UnknownIdentifierException | UnsupportedMessageException e) {
            throw EspressoError.shouldNotReachHere("Cannot bind 'createJniEnv'");
        }
    }

    public static JniEnv create() {
        return new JniEnv();
    }

    private static TruffleObject loadLibrary(String lib) {
        Source source = Source.newBuilder("nfi", String.format("load(RTLD_LAZY) '%s'", lib), "loadLibrary").build();
        CallTarget target = EspressoLanguage.getCurrentContext().getEnv().parse(source);
        return (TruffleObject) target.call();
    }

    public long getJniEnvPtr() {
        return jniEnvPtr;
    }

    public void dispose() {
        assert jniEnvPtr == 0L : "JNIEnv already disposed";
        try {
            ForeignAccess.sendExecute(Message.EXECUTE.createNode(), disposeJniEnv, jniEnvPtr);
            jniEnvPtr = 0L;
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            throw EspressoError.shouldNotReachHere("Cannot initialize Espresso native interface");
        }
        assert jniEnvPtr == 0L;
    }

    public int GetVersion() {
        return 0x00001001;
    }

    public int GetArrayLength(Object arr) {
        return EspressoLanguage.getCurrentContext().getVm().arrayLength(arr);
    }

    // jclass jfieldID
    public Meta.Field GetFieldID(Meta.Klass clazz, String name, String signature) {
        // TODO(peterssen): Signature checks.
        return clazz.field(name);
    }

    public Object GetObjectField(StaticObject obj, Meta.Field fieldID) {
        // TODO(peterssen): obj.fieldID exists check.
        return fieldID.get(obj);
    }

    public Meta.Klass GetObjectClass(Object obj) {
        // TODO(peterssen): Null check.
        return Meta.meta(((StaticObjectClass) Target_java_lang_Object.getClass(obj)).getMirror());
    }

    public boolean GetBooleanField(StaticObject object, Meta.Field field) {
        return (boolean) field.get(object);
    }

    public byte GetByteField(StaticObject object, Meta.Field field) {
        return (byte) field.get(object);
    }

    public char GetCharField(StaticObject object, Meta.Field field) {
        return (char) field.get(object);
    }

    public short GetShortField(StaticObject object, Meta.Field field) {
        return (short) field.get(object);
    }

    public int GetIntField(StaticObject object, Meta.Field field) {
        return (int) field.get(object);
    }

    public long GetLongField(StaticObject object, Meta.Field field) {
        return (long) field.get(object);
    }

    public float GetFloatField(StaticObject object, Meta.Field field) {
        return (float) field.get(object);
    }

    public double GetDoubleField(StaticObject object, Meta.Field field) {
        return (double) field.get(object);
    }
}
