/**
 * @license
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates.
 * Licensed under The Universal Permissive License (UPL), Version 1.0
 * as shown at https://oss.oracle.com/licenses/upl/
 * @ignore
 */
"use strict";define(["ojs/ojcore","ojs/ojkeysetimpl"],function(e,t){return class{constructor(e){this.initialKeys=e;let s=this;this._set=new Set,this._keyset=new t,e&&e.forEach(function(e){s.add(e)}),Object.defineProperty(this,"size",{get:function(){return this._set.size}}),this[Symbol.iterator]=function(){return this._set[Symbol.iterator]()}}clear(){this._set.clear(),this._keyset._keys.clear()}delete(e){var t=this._keyset.get(e);return t!==this._keyset.NOT_A_KEY&&(this._keyset._keys.delete(t),this._set.delete(t))}forEach(e,t){this._set.forEach(e,t)}keys(){return this._set.keys()}values(){return this._set.values()}entries(){return this._set.entries()}has(e){return this._keyset.has(e)}add(e){return this._keyset.get(e)===this._keyset.NOT_A_KEY&&(this._keyset._keys.add(e),this._set.add(e)),this}get[Symbol.toStringTag](){return Set[Symbol.toStringTag]()}}});