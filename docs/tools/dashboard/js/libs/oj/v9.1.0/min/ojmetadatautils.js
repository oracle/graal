/**
 * @license
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates.
 * Licensed under The Universal Permissive License (UPL), Version 1.0
 * as shown at https://oss.oracle.com/licenses/upl/
 * @ignore
 */
define(["ojs/ojcore-base"],function(e){"use strict";var t={getDefaultValue:function(r,o){var n=r.value;if(void 0===n){var u=r.properties;if(u){for(var a={},i=Object.keys(u),l=0;l<i.length;l++){var c=t.getDefaultValue(u[i[l]]);void 0!==c&&(a[i[l]]=c)}Object.keys(a).length>0&&(r.value=a,n=a)}}return void 0!==n&&(Array.isArray(n)?n=o?t.deepFreeze(n):n.slice():null!==n&&"object"==typeof n&&(n=o?t.deepFreeze(n):e.CollectionUtils.copyInto({},n,void 0,!0))),n},getDefaultValues:function(e,r){var o={},n=Object.keys(e),u=!1;return n.forEach(function(n){var a=t.getDefaultValue(e[n],r);void 0!==a&&(o[n]=a,u=!0)}),u?o:null},deepFreeze:function(e){if(Object.isFrozen(e))return e;if(Array.isArray(e))e=e.map(e=>t.deepFreeze(e));else if(null!==e&&"object"==typeof e){const r=Object.getPrototypeOf(e);null!==r&&r!==Object.prototype||(Object.keys(e).forEach(function(r){e[r]=t.deepFreeze(e[r])}),Object.freeze(e))}return e}};return t});