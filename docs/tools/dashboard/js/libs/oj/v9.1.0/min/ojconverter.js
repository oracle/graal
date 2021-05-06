/**
 * @license
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates.
 * Licensed under The Universal Permissive License (UPL), Version 1.0
 * as shown at https://oss.oracle.com/licenses/upl/
 * @ignore
 */
define(["ojs/ojcore","jquery"],function(t,o){"use strict";var n=function(t){this.Init(t)};return t.Object.createSubclass(n,t.Object,"oj.Converter"),n.prototype.Init=function(t){n.superclass.Init.call(this),this._options=t},n.prototype.getOptions=function(){return this._options||{}},n.prototype.resolvedOptions=function(){var t={};return o.extend(t,this._options),t},n});