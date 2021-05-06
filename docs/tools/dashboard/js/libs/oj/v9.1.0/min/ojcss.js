/**
 * @license
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates.
 * Licensed under The Universal Permissive License (UPL), Version 1.0
 * as shown at https://oss.oracle.com/licenses/upl/
 * @ignore
 */
"use strict";define(["css","require"],function(e,r){var i={};return void 0!==i&&(i.load=function(r,i,s,n){var t=!1;if(n){var a=n.ojcss,l=r+".css";this.isExcluded(l,a)&&(t=!0)}t?s(r):e.load(r,i,s,n)},i.pluginBuilder="css-builder",i.normalize=e.normalize,i.isExcluded=function(e,r){var i=!1;if(r&&r.include){var s=r.include;if(Array.isArray(s)){if(s.length>0){i=!0;for(var n=0;n<s.length;n++){var t=s[n];if(e.substr(0,t.length)===t){i=!1;break}}}}else this.makeRegExp(s).test(e)&&(i=!1)}if(!i&&r&&r.exclude){var a=r.exclude;if(Array.isArray(a))for(var l=0;l<a.length;l++){var u=a[l];if(e.substr(0,u.length)===u){i=!0;break}}else this.makeRegExp(a).test(e)&&(i=!0)}return i},i.makeRegExp=function(e){var r=null;return e&&(r="string"==typeof e?new RegExp(e):e),r}),i});