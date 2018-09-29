package de.hpi.swa.trufflelsp.hacks;

import java.util.Map;

import com.oracle.truffle.api.interop.TruffleObject;

import de.hpi.swa.trufflelsp.interop.ObjectStructures;

public class LanguageSpecificHacks {
    public static boolean enableLanguageSpecificHacks = true;

    public static String formatMetaObject(Object metaObject, String langId) {
        if (enableLanguageSpecificHacks) {
            if (langId.equals("js")) {
                if (metaObject instanceof TruffleObject) {
                    // JSMetaObject has no nice toString() impl
                    Map<Object, Object> map = ObjectStructures.asMap(new ObjectStructures.MessageNodes(), (TruffleObject) metaObject);
                    return "" + map.get("type");
                }
            }
        }
        return null;
    }

    public static String getDocumentation(Object metaObject, String langId) {
        if (enableLanguageSpecificHacks) {
            if (metaObject != null) {
                if (langId.equals("js")) {
                    Map<Object, Object> map = ObjectStructures.asMap(new ObjectStructures.MessageNodes(), (TruffleObject) metaObject);
                    return "" + (map.containsKey("description") ? map.get("description") : "");
                }
            }
        }
        return null;
    }

    public static String normalizeSymbol(String definitionSearchSymbol) {
        if (enableLanguageSpecificHacks) {
            int idx = definitionSearchSymbol.indexOf('(');
            if (idx > -1) {
                return definitionSearchSymbol.substring(0, idx);
            }
        }
        return definitionSearchSymbol;
    }
}
