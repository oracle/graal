/**
 * @license
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates.
 * Licensed under The Universal Permissive License (UPL), Version 1.0
 * as shown at https://oss.oracle.com/licenses/upl/
 * @ignore
 */
define(["ojs/ojcore","jquery","ojs/ojcomponentcore"],function(e,t){"use strict";var n={properties:{disabled:{type:"boolean",value:!1},value:{type:"any"}},methods:{setProperty:{},getProperty:{},refresh:{},setProperties:{},getNodeBySubId:{},getSubIdByNode:{}},extension:{}};function o(n){function o(n){var o=e.BaseCustomElementBridge.getSlotMap(n),i=["startIcon","","endIcon"];t.each(o,function(e,o){-1===i.indexOf(e)&&function(e,n){t.each(n,function(t,n){e.removeChild(n)})}(n,o)}),t.each(i,function(e,i){o[i]&&function(e,n){t.each(n,function(t,n){e.appendChild(n)})}(n,o[i])})}this.updateDOM=function(){var e=n.element.customOptionRenderer;o(n.element),e&&"function"==typeof e&&e(n.element)}}n.properties.customOptionRenderer={},n.extension._CONSTRUCTOR=o,e.CustomElementBridge.register("oj-option",{metadata:n})});