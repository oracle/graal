package com.oracle.truffle.espresso.nodes.commands;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.nodes.interop.LookupDeclaredMethod;
import com.oracle.truffle.espresso.nodes.interop.LookupDeclaredMethodNodeGen;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;

public class AddPathToBindingsCache {
    static final String NEW_FILE = "<init>/(Ljava/lang/String;)V";
    static final String TO_URI = "toURI/()Ljava/net/URI;";
    static final String TO_URL = "toURL/()Ljava/net/URL;";
    static final String ADD_URL = "addURL/(Ljava/net/URL;)V";
    static final String FILE_CLASSNAME = "java/io/File";
    static final String URI_CLASSNAME = "java/net/URI";

    private final ObjectKlass fileKlass;
    private final DirectCallNode newFile;
    private final DirectCallNode toUri;
    private final DirectCallNode toUrl;
    private final DirectCallNode addUrl;
    private final StaticObject loader;

    public AddPathToBindingsCache(EspressoContext context) {
        fileKlass = (ObjectKlass) context.getMeta().loadKlassOrNull(context.getTypes().fromClassGetName(FILE_CLASSNAME), StaticObject.NULL, StaticObject.NULL);
        ObjectKlass uriKlass = (ObjectKlass) context.getMeta().loadKlassOrNull(context.getTypes().fromClassGetName(URI_CLASSNAME), StaticObject.NULL, StaticObject.NULL);

        LookupDeclaredMethod lookup = LookupDeclaredMethodNodeGen.getUncached();

        newFile = DirectCallNode.create(doLookup(fileKlass, NEW_FILE, lookup).getCallTargetForceInit());
        toUri = DirectCallNode.create(doLookup(fileKlass, TO_URI, lookup).getCallTargetForceInit());
        toUrl = DirectCallNode.create(doLookup(uriKlass, TO_URL, lookup).getCallTargetForceInit());
        loader = context.getBindings().getBindingsLoader();
        addUrl = DirectCallNode.create(doLookup(loader.getKlass(), ADD_URL, lookup).getCallTargetForceInit());
    }

    @Specialization
    public void execute(StaticObject path) throws UnsupportedTypeException {
        StaticObject file = fileKlass.allocateInstance();
        newFile.call(file, path);

        Object uri = toUri.call(file);
        Object url = toUrl.call(uri);
        addUrl.call(loader, url);
    }

    private static Method doLookup(Klass klass, String key, LookupDeclaredMethod lookup) {
        try {
            return lookup.execute(klass, key, false, false, -1)[0];

        } catch (InteropException e) {
            throw EspressoError.shouldNotReachHere();
        }
    }
}
