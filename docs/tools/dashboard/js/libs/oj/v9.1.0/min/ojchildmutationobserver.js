/**
 * @license
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates.
 * Licensed under The Universal Permissive License (UPL), Version 1.0
 * as shown at https://oss.oracle.com/licenses/upl/
 * @ignore
 */
define(["ojs/ojcore","ojs/ojcustomelement"],function(e){"use strict";return function(t,n){var r=t,o=n,a=e.BaseCustomElementBridge.getRegistered(t.tagName)?e.BaseCustomElementBridge.getTrackChildrenOption(t):"none",s=function(t){var n=function(t){for(var n=[],o=0;o<t.length;o++)for(var s=t[o],i="childList"===s.type?s.target:s.target.parentNode;i;)i===r?(n.push(s),i=null):i="nearestCustomElement"!==a||e.ElementUtils.isValidCustomElementName(i.localName)?null:i.parentNode;return n}(t);n.length>0&&o(n)},i=new MutationObserver(s);return{observe:function(){"none"!==a&&i.observe(r,{attributes:!0,childList:!0,subtree:!0,characterData:!0})},disconnect:function(){var e=i.takeRecords();e&&e.length>0&&s(e),i.disconnect()}}}});