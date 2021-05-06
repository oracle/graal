/**
 * @license
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates.
 * Licensed under The Universal Permissive License (UPL), Version 1.0
 * as shown at https://oss.oracle.com/licenses/upl/
 * @ignore
 */
define(["ojs/ojcore"],function(t){"use strict";return function(e){this.NOT_A_KEY={},this.has=function(t){return this.get(t)!==this.NOT_A_KEY},this.get=function(e){var i,s,n=this.NOT_A_KEY,o=this;if(this._keys.has(e))return e;if(e!==Object(e))return this.NOT_A_KEY;if("function"==typeof Symbol&&"function"==typeof Set.prototype[Symbol.iterator])for(s=(i=this._keys[Symbol.iterator]()).next();!s.done;){if(t.KeyUtils.equals(s.value,e))return s.value;s=i.next()}else this._keys.forEach(function(i){n===o.NOT_A_KEY&&t.KeyUtils.equals(i,e)&&(n=i)});return n},this.InitializeWithKeys=function(t){this._keys=new Set(t)},this.InitializeWithKeys(e)}});