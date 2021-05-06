/**
 * @license
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates.
 * Licensed under The Universal Permissive License (UPL), Version 1.0
 * as shown at https://oss.oracle.com/licenses/upl/
 * @ignore
 */
define([],function(){"use strict";var e=function(e){this.observers=[],this._value=e};e.prototype.subscribe=function(e,s,i){let r=e;"function"==typeof r?r={next:e,error:s,complete:i}:"object"!=typeof r&&(r={}),this.observers.push(r);let o=new t(this,r);return o&&!o.closed&&r.next(this._value),o},e.prototype.next=function(e){this._value=e;let{observers:t}=this,s=t.length,i=t.slice();for(let t=0;t<s;t++)i[t].next(e)};var t=function(e,t){this.subject=e,this.subscriber=t,this.closed=!1};return t.prototype.unsubscribe=function(){if(this.closed)return;this.closed=!0;let e=this.subject.observers;if(this.subject=null,!e||0===e.length)return;let t=e.indexOf(this.subscriber);-1!==t&&e.splice(t,1)},t.prototype.closed=function(){return this.closed},{BehaviorSubject:e}});