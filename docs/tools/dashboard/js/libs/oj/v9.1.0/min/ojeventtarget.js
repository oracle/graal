/**
 * @license
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates.
 * Licensed under The Universal Permissive License (UPL), Version 1.0
 * as shown at https://oss.oracle.com/licenses/upl/
 * @ignore
 */
define(["ojs/ojcore"],function(e){"use strict";class t{addEventListener(e,t){this._eventListeners||(this._eventListeners=[]),this._eventListeners.push({type:e.toLowerCase(),listener:t})}removeEventListener(e,t){if(this._eventListeners){let s;for(s=this._eventListeners.length-1;s>=0;s--)this._eventListeners[s].type==e&&this._eventListeners[s].listener==t&&this._eventListeners.splice(s,1)}}dispatchEvent(e){if(this._eventListeners){var t,s=this._eventListeners.slice(0);for(t=0;t<s.length;t++){var i=s[t];if(e&&e.type&&i.type==e.type.toLowerCase()&&!1===i.listener.apply(this,[e]))return!1}}return!0}static applyMixin(e){[t].forEach(t=>{Object.getOwnPropertyNames(t.prototype).forEach(s=>{"constructor"!==s&&(e.prototype[s]=t.prototype[s])})})}}e.EventTargetMixin=t;e.GenericEvent=class{constructor(e,t){this.type=e,this.options=t,null!=t&&(this.detail=t.detail)}}});