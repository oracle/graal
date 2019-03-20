package com.oracle.truffle.espresso.nodes;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObjectImpl;

public class MHInvokeBasicNode extends EspressoBaseNode {

    // TODO(garcia) Cache.
    @Child BasicNode node;

    public MHInvokeBasicNode(Method method) {
        super(method);
        this.node = BasicNodeGen.create();
    }

    @Override
    public Object invokeNaked(VirtualFrame frame) {
        Meta meta = getMeta();
        StaticObjectImpl mh = (StaticObjectImpl) frame.getArguments()[0];

        return node.executeBasic(mh, frame.getArguments(), meta);
    }
}

abstract class BasicNode extends Node {
    final static String vmtarget = "vmtarget";

    static final int INLINE_CACHE_SIZE_LIMIT = 3;

    public abstract Object executeBasic(StaticObjectImpl methodHandle, Object[] args, Meta meta);

    @SuppressWarnings("unused")
    @Specialization(limit = "INLINE_CACHE_SIZE_LIMIT", guards = {"methodHandle == cachedHandle", "getBooleanField(lform, meta.isCompiled)"})
    Object directBasic(StaticObjectImpl methodHandle, Object[] args, Meta meta,
                    @Cached("methodHandle") StaticObjectImpl cachedHandle,
                    @Cached("getSOIField(methodHandle, meta.form)") StaticObjectImpl lform,
                    @Cached("getSOIField(lform, meta.vmentry)") StaticObjectImpl mname,
                    @Cached("getMethodHiddenField(mname, vmtarget)") Method target) {
        return target.invokeDirect(null, args);
    }

    @Specialization(replaces = "directBasic")
    Object normalBasic(StaticObjectImpl methodHandle, Object[] args, Meta meta) {
        StaticObjectImpl lform = (StaticObjectImpl) methodHandle.getField(meta.form);
        StaticObjectImpl mname = (StaticObjectImpl) lform.getField(meta.vmentry);
        Method target = (Method) mname.getHiddenField("vmtarget");
        return target.invokeDirect(null, args);
    }

    static StaticObjectImpl getSOIField(StaticObjectImpl object, Field field) {
        return (StaticObjectImpl) object.getField(field);
    }

    static Method getMethodHiddenField(StaticObjectImpl object, String name) {
        return (Method) object.getHiddenField(name);
    }

    static boolean getBooleanField(StaticObjectImpl object, Field field) {
        return (boolean) object.getField(field);
    }
}