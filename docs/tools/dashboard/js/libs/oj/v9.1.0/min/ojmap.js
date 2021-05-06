/**
 * @license
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates.
 * Licensed under The Universal Permissive License (UPL), Version 1.0
 * as shown at https://oss.oracle.com/licenses/upl/
 * @ignore
 */
define(["ojs/ojcore","ojs/ojkeysetimpl"],function(e,t){"use strict";var s=function(){this._map=new Map,this._keyset=new t},i=s.prototype;return Object.defineProperty(i,"size",{get:function(){return this._map.size}}),i.clear=function(){this._map.clear(),this._keyset._keys.clear()},i.delete=function(e){var t=this._keyset.get(e);return t!==this._keyset.NOT_A_KEY&&(this._keyset._keys.delete(t),this._map.delete(t))},i.forEach=function(e){this._map.forEach(e)},i.get=function(e){var t=this._keyset.get(e);return this._map.get(t)},i.has=function(e){return this._keyset.has(e)},i.set=function(e,t){var s=this._keyset.get(e);return s===this._keyset.NOT_A_KEY?(this._keyset._keys.add(e),this._map.set(e,t)):this._map.set(s,t),this},s});