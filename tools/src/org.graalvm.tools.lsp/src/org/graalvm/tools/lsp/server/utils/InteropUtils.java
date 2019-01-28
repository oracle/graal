package org.graalvm.tools.lsp.server.utils;

import java.util.Map;

import org.graalvm.tools.lsp.hacks.LanguageSpecificHacks;
import org.graalvm.tools.lsp.interop.ObjectStructures;
import org.graalvm.tools.lsp.interop.ObjectStructures.MessageNodes;

import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.StandardTags.DeclarationTag;
import com.oracle.truffle.api.interop.TruffleObject;

public class InteropUtils {
    public static String getNormalizedSymbolName(Object nodeObject, String symbol) {
        if (!(nodeObject instanceof TruffleObject)) {
            return LanguageSpecificHacks.normalizeSymbol(symbol);
        } else {
            Map<Object, Object> map = ObjectStructures.asMap(new MessageNodes(), (TruffleObject) nodeObject);
            if (map.containsKey(DeclarationTag.NAME)) {
                return map.get(DeclarationTag.NAME).toString();
            }
        }
        return symbol;
    }

    public static Integer getNumberOfArguments(Object nodeObject) {
        if (nodeObject instanceof TruffleObject) {
            Map<Object, Object> map = ObjectStructures.asMap(new MessageNodes(), (TruffleObject) nodeObject);
            if (map.containsKey("numberOfArguments")) {
                Object object = map.get("numberOfArguments");
                return object instanceof Integer ? (Integer) object : null;
            }
        }
        return null;
    }

    public static boolean isPrimitive(Object object) {
        Class<?> clazz = object.getClass();
        return (clazz == Byte.class ||
                        clazz == Short.class ||
                        clazz == Integer.class ||
                        clazz == Long.class ||
                        clazz == Float.class ||
                        clazz == Double.class ||
                        clazz == Character.class ||
                        clazz == Boolean.class ||
                        clazz == String.class);
    }

    public static String getNodeObjectName(InstrumentableNode node) {
        Object nodeObject = node.getNodeObject();
        if (nodeObject instanceof TruffleObject) {
            Map<Object, Object> map = ObjectStructures.asMap(new MessageNodes(), (TruffleObject) nodeObject);
            if (map.containsKey("name")) {
                return map.get("name").toString();
            }
        }
        return null;
    }
}
