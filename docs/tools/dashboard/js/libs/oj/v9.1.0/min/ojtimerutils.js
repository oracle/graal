/**
 * @license
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates.
 * Licensed under The Universal Permissive License (UPL), Version 1.0
 * as shown at https://oss.oracle.com/licenses/upl/
 * @ignore
 */
define([],function(){"use strict";var n={};function e(){}return e.prototype.getPromise=function(){},e.prototype.clear=function(){},n.getTimer=function(e){return new n._TimerImpl(e)},n._TimerImpl=function(n){var e,t,i;function o(n){i=null,t(n)}this.getPromise=function(){return e},this.clear=function(){window.clearTimeout(i),i=null,o(!1)},e="undefined"==typeof window?Promise.reject():new Promise(function(e){t=e,i=window.setTimeout(o.bind(null,!0),n)})},n});