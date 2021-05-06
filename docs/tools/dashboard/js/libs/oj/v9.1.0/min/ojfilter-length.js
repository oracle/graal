/**
 * @license
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates.
 * Licensed under The Universal Permissive License (UPL), Version 1.0
 * as shown at https://oss.oracle.com/licenses/upl/
 * @ignore
 */
define(["ojs/ojcore","ojs/ojfilter"],function(t,o){"use strict";return class{constructor(t){if(this.options=t,!t.max)throw new Error("length filter's max option cannot be less than 1. max option is "+t.max);if(isNaN(t.max))throw new Error("length filter's max option is not a number. max option is "+t.max);if(null!==t.max&&t.max<1)throw new Error("length filter's max option cannot be less than 1. max option is "+t.max);t.countBy=void 0===t.countBy?"codePoint":t.countBy}filter(t,o){return this.calcLength(o)<=this.options.max?o:t.slice(0,this.options.max)}calcLength(t){let o=this.options.countBy;if(""==t||null==t||null==t)return 0;let n,e=t.length,i=0;switch(o){case"codePoint":for(let o=0;o<e;o++)55296==(63488&t.charCodeAt(o))&&(i+=1);n=e-i/2;break;case"codeUnit":default:n=e}return n}}});