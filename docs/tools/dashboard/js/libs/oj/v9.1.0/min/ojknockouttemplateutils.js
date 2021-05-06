/**
 * @license
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates.
 * Licensed under The Universal Permissive License (UPL), Version 1.0
 * as shown at https://oss.oracle.com/licenses/upl/
 * @ignore
 */
define(["jquery","knockout"],function(e,t){"use strict";var n={getRenderer:function(n,r){var a=function(a){var l=a._parentElement||a.parentElement,o=t.contextFor(a.componentElement);if(o){var c=o.createChildContext(a.data,null,function(e){e.$context=a});t.renderTemplate(n,c,{afterRender:function(t){e(t)._ojDetectCleanData()}},l,r?"replaceNode":"replaceChildren")}return null};return function(t){if(t.componentElement.classList&&t.componentElement.classList.contains("oj-dvtbase")){var r=document.createElement("div");r.style.display="none",r._dvtcontext=t._dvtcontext,t.componentElement.appendChild(r),Object.defineProperty(t,"_parentElement",{value:r,enumerable:!1}),Object.defineProperty(t,"_templateCleanup",{value:function(){e(r).remove()},enumerable:!1}),Object.defineProperty(t,"_templateName",{value:n,enumerable:!1}),a(t);var l=r.children[0];return l?(r.removeChild(l),e(r).remove(),{insert:l}):{preventDefault:!0}}return a(t)}}};return n});