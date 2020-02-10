package com.oracle.truffle.espresso.nodes.helper;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.StaticObject;

import java.util.function.IntFunction;

public final class LoadKlassNode extends RootNode {

    private final String targetClassName;

    public LoadKlassNode(EspressoLanguage language, String targetClassName) {
        super(language);
        this.targetClassName = targetClassName;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        try {
            assert frame.getArguments().length == 0;
            // TODO(peterssen): Class name is converted to host, then reserialized to guest.
            String className = InteropLibrary.getFactory().getUncached().asString(targetClassName);
            EspressoContext context = EspressoLanguage.getCurrentContext();
            Meta meta = context.getMeta();
            StaticObject appClassLoader = (StaticObject) meta.java_lang_ClassLoader_getSystemClassLoader.invokeDirect(null);
            StaticObject guestClass = (StaticObject) meta.java_lang_Class.lookupDeclaredMethod(Name.forName, Signature.Class_String_boolean_ClassLoader).invokeDirect(null,
                            meta.toGuestString(className), false, appClassLoader);
            return guestClass.getMirrorKlass();
        } catch (EspressoException e) {
            StaticObject guestException = e.getExceptionObject();
            guestException.getKlass().lookupMethod(Name.printStackTrace, Signature._void).invokeDirect(guestException);
            return StaticObject.NULL;
        } catch (Throwable e) {
            CompilerDirectives.transferToInterpreter();
            return StaticObject.NULL;
        }
    }

    private static StaticObject toGuestArguments(EspressoContext context, String... args) {
        Meta meta = context.getMeta();
        return meta.java_lang_String.allocateReferenceArray(args.length, new IntFunction<StaticObject>() {
            @Override
            public StaticObject apply(int i) {
                return meta.toGuestString(args[i]);
            }
        });
    }
}
