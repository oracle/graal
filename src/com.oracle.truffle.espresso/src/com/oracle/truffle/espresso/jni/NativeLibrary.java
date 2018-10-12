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
import com.oracle.truffle.espresso.meta.EspressoError;

public class NativeLibrary {

    public static TruffleObject loadLibrary(String lib) {
        Source source = Source.newBuilder("nfi", String.format("load(RTLD_LAZY) '%s'", lib), "loadLibrary").build();
        CallTarget target = EspressoLanguage.getCurrentContext().getEnv().parse(source);
        return (TruffleObject) target.call();
    }

    private static long load(String name) {
        TruffleObject lib = null;
        try {
            lib = loadLibrary(name);
        } catch (UnsatisfiedLinkError e) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(UnsatisfiedLinkError.class);
        }
        return EspressoLanguage.getCurrentContext().addNativeLibrary(lib);
    }

    public static TruffleObject lookup(TruffleObject library, String method) {
        try {
            return (TruffleObject) ForeignAccess.sendRead(Message.READ.createNode(), library, method);
        } catch (UnknownIdentifierException | UnsupportedMessageException e) {
            throw EspressoError.shouldNotReachHere("Cannot find " + method);
        }
    }

    public static TruffleObject bind(TruffleObject symbol, String signature) {
        try {
            return (TruffleObject) ForeignAccess.sendInvoke(Message.INVOKE.createNode(), symbol, "bind", signature);
        } catch (UnsupportedTypeException | ArityException | UnknownIdentifierException | UnsupportedMessageException e) {
            throw EspressoError.shouldNotReachHere("Cannot bind " + signature);
        }
    }

    public static TruffleObject lookupAndBind(TruffleObject library, String method, String signature) {
        try {
            TruffleObject symbol = (TruffleObject) ForeignAccess.sendRead(Message.READ.createNode(), library, method);
            return (TruffleObject) ForeignAccess.sendInvoke(Message.INVOKE.createNode(), symbol, "bind", signature);
        } catch (UnsupportedTypeException | ArityException | UnknownIdentifierException | UnsupportedMessageException e) {
            throw EspressoError.shouldNotReachHere("Cannot bind " + method);
        }
    }

}
