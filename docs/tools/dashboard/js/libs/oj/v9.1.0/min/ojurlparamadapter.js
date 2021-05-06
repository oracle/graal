/**
 * @license
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates.
 * Licensed under The Universal Permissive License (UPL), Version 1.0
 * as shown at https://oss.oracle.com/licenses/upl/
 * @ignore
 */
define(["ojs/ojcore","ojs/ojurlpathadapter","ojs/ojlogger"],function(t,r,n){"use strict";return function(){var t="ojr",n="_ojCoreRouter";function o(){var t=document.location.search?document.location.search.substring(1):"",r=[];return t&&t.split("&").forEach(function(t){var n=t.split("=");r.push(n)}),r}function e(){var t,r=o().find(function(t){return t[0]===n});return r&&(t=r[1],decodeURIComponent(t))}function u(t){var r=encodeURIComponent(t),e=o(),u=e.find(function(t){return t[0]===n});return u?u[1]=r:e.push([n,r]),e.map(function(t){return t[0]+"="+t[1]}).join("&")}function i(){this._pathAdapter=new r(""),void 0===e()&&(n=t)}return i.prototype.getRoutesForUrl=function(){var t=e()||"";return this._pathAdapter.getRoutesForUrl(t)},i.prototype.getUrlForRoutes=function(t){var r=this._pathAdapter.getUrlForRoutes(t);return r.indexOf("?")>-1&&(r=r.substring(0,r.indexOf("?"))),"?"+u(r)},i}()});