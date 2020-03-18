/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.tools.chromeinspector.types;

import java.io.PrintWriter;
import java.util.Collection;
import java.util.List;

import com.oracle.truffle.api.debug.DebugException;
import com.oracle.truffle.api.debug.DebugScope;
import com.oracle.truffle.api.debug.DebugValue;
import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.tools.chromeinspector.InspectorExecutionContext;
import com.oracle.truffle.tools.chromeinspector.RemoteObjectsHandler;
import com.oracle.truffle.tools.utils.json.JSONArray;
import com.oracle.truffle.tools.utils.json.JSONObject;

/**
 * Custom preview of an object through devtools formatter, which is registered as
 * <code>devtoolsFormatters</code> array on the global object. The array contains formatter objects,
 * which may have <code>header</code>, <code>hasBody</code> and <code>body</code> properties, whose
 * values are executables. The executables accept the object to format and an optional
 * <code>context</code>. The return value of <code>header</code> and <code>body</code> is a JsonML
 * <code>[tag-name, attributes, element-list]</code> tuple that is then converted into a DOM element
 * in the client UI. The return value of <code>hasBody</code> is just <code>true</code> or
 * <code>false</code> depending on whether the body should be shown, or not. The <code>header</code>
 * may return <code>null</code> if the formatter should not be applied on the object.
 * <p>
 * The element list may contain child template objects of the following form:
 * <code>["object", {"object": objectToInspect, "config": configObject}]</code> The child element is
 * then created by applying formatter to <code>objectToInspect</code> and <code>configObject<code>.
 * <p>
 * The following HTML tags are allowed: <code>div, span, ol, li, table, tr, td</code>.
 */
public final class CustomPreview {

    private static final String DEVTOOLS_FORMATTERS = "devtoolsFormatters";
    private static final String HEADER = "header";
    private static final String HAS_BODY = "hasBody";
    private static final String BODY = "body";
    private static final String BODY_GETTER_ID = "bodyGetterId";

    private CustomPreview() {
    }

    static JSONObject create(DebugValue debugValue, LanguageInfo language, InspectorExecutionContext context) {
        return create(debugValue, null, language, context);
    }

    private static JSONObject create(DebugValue debugValue, DebugValue config, LanguageInfo language, InspectorExecutionContext context) {
        DebuggerSession debuggerSession = context.getDebuggerSession();
        if (debuggerSession == null) {
            return null;
        }
        DebugScope topScope = debuggerSession.getTopScope(language.getId());
        DebugValue formatters = null;
        while (topScope != null) {
            formatters = topScope.getDeclaredValue(DEVTOOLS_FORMATTERS);
            if (formatters != null) {
                break;
            } else {
                topScope = topScope.getParent();
            }
        }
        if (formatters == null || !formatters.isArray()) {
            return null;
        }
        for (DebugValue formatter : formatters.getArray()) {
            DebugValue headerFunction;
            try {
                headerFunction = formatter.getProperty(HEADER);
                if (headerFunction == null || !headerFunction.canExecute()) {
                    continue;
                }
            } catch (DebugException ex) {
                continue;
            }
            DebugValue header = (config != null) ? headerFunction.execute(debugValue, config) : headerFunction.execute(debugValue);
            if (header.isNull()) {
                continue;
            }
            boolean hasBody = false;
            DebugValue hasBodyFunction;
            try {
                hasBodyFunction = formatter.getProperty(HAS_BODY);
                if (hasBodyFunction != null && hasBodyFunction.canExecute()) {
                    DebugValue hasBodyValue = (config != null) ? hasBodyFunction.execute(debugValue, config) : hasBodyFunction.execute(debugValue);
                    hasBody = hasBodyValue.isBoolean() ? hasBodyValue.asBoolean() : false;
                }
            } catch (DebugException ex) {
                PrintWriter err = context.getErr();
                if (err != null) {
                    ex.printStackTrace(err);
                }
            }
            DebugValue bodyFunction = null;
            if (hasBody) {
                try {
                    bodyFunction = formatter.getProperty(BODY);
                    if (bodyFunction != null && !bodyFunction.canExecute()) {
                        bodyFunction = null;
                    }
                } catch (DebugException ex) {
                    PrintWriter err = context.getErr();
                    if (err != null) {
                        ex.printStackTrace(err);
                    }
                }
            }
            return createJSON(debugValue, config, header, bodyFunction, context);
        }
        return null;
    }

    private static JSONObject createJSON(DebugValue debugValue, DebugValue config, DebugValue headerValue, DebugValue bodyFunction, InspectorExecutionContext context) {
        JSONObject json = new JSONObject();
        json.put(HEADER, value2JSON(headerValue, context).toString());
        if (bodyFunction != null) {
            RemoteObject bodyFunctionRemote = context.getRemoteObjectsHandler().getRemote(bodyFunction);
            RemoteObjectsHandler objectsHandler = context.getRemoteObjectsHandler();
            objectsHandler.registerCustomPreviewBody(bodyFunctionRemote.getId(), bodyFunction);
            if (config != null) {
                // If there is an config object, register it for callFunctionOn() call.
                String objectId = objectsHandler.getRemote(debugValue).getId();
                objectsHandler.registerCustomPreviewConfig(objectId, config);
            }
            json.put(BODY_GETTER_ID, bodyFunctionRemote.getId());
        }
        return json;
    }

    public static Object value2JSON(DebugValue value, InspectorExecutionContext context) {
        if (value.isArray()) {
            JSONArray json = new JSONArray();
            List<DebugValue> array = value.getArray();
            if (array.size() == 2 && array.get(0).isReadable() && array.get(1).isReadable() &&
                            "object".equals(array.get(0).asString()) && array.get(1).getProperty("config") != null) {
                // Child object:
                json.put(value2JSON(array.get(0), context));
                DebugValue child = array.get(1);
                DebugValue object = child.getProperty("object");
                DebugValue config = child.getProperty("config");
                RemoteObject remoteConfig = context.getRemoteObjectsHandler().getRemote(object);
                JSONObject childJSON = remoteConfig.toJSON();
                JSONObject customPreview = create(object, config, object.getOriginalLanguage(), context);
                if (customPreview != null) {
                    childJSON.put("customPreview", customPreview);
                }
                json.put(childJSON);
            } else {
                for (DebugValue element : array) {
                    if (element.isReadable()) {
                        json.put(value2JSON(element, context));
                    } else {
                        json.put(InspectorExecutionContext.VALUE_NOT_READABLE);
                    }
                }
            }
            return json;
        } else {
            Collection<DebugValue> properties = value.getProperties();
            if (properties != null) {
                JSONObject json = new JSONObject();
                for (DebugValue property : properties) {
                    try {
                        if (property.isReadable() && !property.canExecute() && !property.isInternal()) {
                            Object rawValue = getPrimitiveValue(property);
                            // Do not allow inner objects
                            if (rawValue != null) {
                                json.put(property.getName(), rawValue);
                            }
                        }
                    } catch (DebugException ex) {
                        if (ex.isInternalError()) {
                            PrintWriter err = context.getErr();
                            if (err != null) {
                                ex.printStackTrace(err);
                            }
                        } else {
                            throw ex;
                        }
                    }
                }
                return json;
            } else {
                return getPrimitiveValue(value);
            }
        }
    }

    private static Object getPrimitiveValue(DebugValue value) {
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        if (value.isNumber()) {
            return TypeInfo.toNumber(value);
        }
        if (value.isString()) {
            return value.asString();
        }
        return null;
    }

}
