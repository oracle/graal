package com.oracle.truffle.espresso.nodes.interop;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.vm.InterpreterToVM;

/**
 *
 */
public final class LoadKlassNode extends RootNode {

    private final InteropLibrary uncached = InteropLibrary.getFactory().getUncached();

    private final String targetClassName;

    public LoadKlassNode(EspressoLanguage language, String targetClassName) {
        super(language);
        this.targetClassName = targetClassName;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        assert frame.getArguments().length == 0;
        String className = null;
        try {
            className = uncached.asString(targetClassName);
        } catch (UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalArgumentException(e);
        }
        EspressoContext context = EspressoLanguage.getCurrentContext();
        Meta meta = context.getMeta();
        StaticObject appClassLoader = (StaticObject) meta.java_lang_ClassLoader_getSystemClassLoader.invokeDirect(null);

        try {
            StaticObject guestClass = (StaticObject) meta.java_lang_Class_forName_String_boolean_ClassLoader.invokeDirect(null, meta.toGuestString(className), false, appClassLoader);
            return guestClass.getMirrorKlass();
        } catch (EspressoException e) {
            if (InterpreterToVM.instanceOf(e.getExceptionObject(), meta.java_lang_ClassNotFoundException)) {
                return StaticObject.NULL;
            }
            throw e;
        }
    }
}
