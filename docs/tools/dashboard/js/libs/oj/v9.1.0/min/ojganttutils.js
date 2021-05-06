/**
 * @license
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates.
 * Licensed under The Universal Permissive License (UPL), Version 1.0
 * as shown at https://oss.oracle.com/licenses/upl/
 * @ignore
 */
define(["ojs/ojcore"],function(e){"use strict";var t=function(){};return t.computeTableColumnHeaderHeight=function(n,a,r){var i=0,o=r.majorAxis,l=r.minorAxis;function s(e,n){if(null==e)return 0;var r=e.height,i=t._getDefaultTimeAxisHeight(a,n);return isNaN(r)?i:Math.max(i,r)}return i+=s(o,"major"),i+=s(l,"minor"),e.AgentUtils.getAgentInfo().browser!==e.AgentUtils.BROWSER.EDGE&&e.AgentUtils.getAgentInfo().browser!==e.AgentUtils.BROWSER.IE||(i-=1),i},t._getDefaultTimeAxisHeight=function(e,t){var n="oj-gantt-"+t+"-axis-label",a=document.createElement("div");null!=e&&(a.className=e.className+" "),a.className=a.className+"oj-gantt oj-dvtbase";var r=document.createElement("div");r.className=n,r.innerHTML="FooBar",a.appendChild(r);var i=null!=e?e:document.body;i.appendChild(a);var o=parseInt(window.getComputedStyle(r).height,10);i.removeChild(a);var l=Math.max(o+4,21);return Math.max(l,22)+1},t});