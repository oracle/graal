/**
 * @license
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates.
 * Licensed under The Universal Permissive License (UPL), Version 1.0
 * as shown at https://oss.oracle.com/licenses/upl/
 * @ignore
 */
define(["ojs/ojcore","ojs/ojeventtarget"],function(t){"use strict";var e,r,i=t.GenericEvent;
/**
 * @preserve Copyright 2013 jQuery Foundation and other contributors
 * Released under the MIT license.
 * http://jquery.org/license
 */
/**
 * @preserve Copyright 2013 jQuery Foundation and other contributors
 * Released under the MIT license.
 * http://jquery.org/license
 */
/**
 * @preserve Copyright 2013 jQuery Foundation and other contributors
 * Released under the MIT license.
 * http://jquery.org/license
 */
/**
 * @preserve Copyright 2013 jQuery Foundation and other contributors
 * Released under the MIT license.
 * http://jquery.org/license
 */
/**
 * @preserve Copyright 2013 jQuery Foundation and other contributors
 * Released under the MIT license.
 * http://jquery.org/license
 */
/**
 * @preserve Copyright 2013 jQuery Foundation and other contributors
 * Released under the MIT license.
 * http://jquery.org/license
 */
/**
 * @preserve Copyright 2013 jQuery Foundation and other contributors
 * Released under the MIT license.
 * http://jquery.org/license
 */
/**
 * @preserve Copyright 2013 jQuery Foundation and other contributors
 * Released under the MIT license.
 * http://jquery.org/license
 */
/**
 * @preserve Copyright 2013 jQuery Foundation and other contributors
 * Released under the MIT license.
 * http://jquery.org/license
 */
/**
 * @preserve Copyright 2013 jQuery Foundation and other contributors
 * Released under the MIT license.
 * http://jquery.org/license
 */
/**
 * @preserve Copyright 2013 jQuery Foundation and other contributors
 * Released under the MIT license.
 * http://jquery.org/license
 */
/**
 * @preserve Copyright 2013 jQuery Foundation and other contributors
 * Released under the MIT license.
 * http://jquery.org/license
 */
/**
 * @preserve Copyright 2013 jQuery Foundation and other contributors
 * Released under the MIT license.
 * http://jquery.org/license
 */
/**
 * @preserve Copyright 2013 jQuery Foundation and other contributors
 * Released under the MIT license.
 * http://jquery.org/license
 */
/**
 * @preserve Copyright 2013 jQuery Foundation and other contributors
 * Released under the MIT license.
 * http://jquery.org/license
 */
/**
 * @preserve Copyright 2013 jQuery Foundation and other contributors
 * Released under the MIT license.
 * http://jquery.org/license
 */
/**
 * @preserve Copyright 2013 jQuery Foundation and other contributors
 * Released under the MIT license.
 * http://jquery.org/license
 */
/**
 * @preserve Copyright 2013 jQuery Foundation and other contributors
 * Released under the MIT license.
 * http://jquery.org/license
 */
/**
 * @preserve Copyright 2013 jQuery Foundation and other contributors
 * Released under the MIT license.
 * http://jquery.org/license
 */!function(t){let e;!function(t){t.$co="$co",t.$eq="$eq",t.$ew="$ew",t.$pr="$pr",t.$gt="$gt",t.$ge="$ge",t.$lt="$lt",t.$le="$le",t.$ne="$ne",t.$regex="$regex",t.$sw="$sw"}(e=t.AttributeOperator||(t.AttributeOperator={}))}(e||(e={})),t.AttributeFilterOperator=e,t.AttributeFilterOperator.AttributeOperator=e.AttributeOperator,function(t){let e;!function(t){t.$and="$and",t.$or="$or"}(e=t.CompoundOperator||(t.CompoundOperator={}))}(r||(r={})),t.CompoundFilterOperator=r,t.CompoundFilterOperator.CompoundOperator=r.CompoundOperator;
/**
 * @preserve Copyright 2013 jQuery Foundation and other contributors
 * Released under the MIT license.
 * http://jquery.org/license
 */
class a{constructor(){this._handleMutationAdd=function(e){var r,i;let n=this,s=e[a._BEFOREKEYS],o=e[a._KEYS],l=[];o.forEach(function(t){l.push(t)});let f=e[a._DATA],u=e[a._METADATA],c=e[a._INDEXES];if(l&&l.length>0)if(c)l.forEach(function(t,e){n._items.splice(c[e],0,new n.Item(u[e],f[e]))});else if(s){let o,c,h,p,_,y=Object.assign([],s),d=Object.assign(new Set,e[a._KEYS]),m=Object.assign([],e[a._DATA]),g=Object.assign([],e[a._METADATA]),E=[];for(o=0;o<s.length;o++){if(h=s[o],_=!0,null!=h){for(c=0;c<l.length;c++)if(t.Object.compareValues(l[c],h)){_=!1;break}if(_)for(c=0;c<n._items.length;c++)if(t.Object.compareValues(null===(i=null===(r=n._items[c])||void 0===r?void 0:r.metadata)||void 0===i?void 0:i.key,h)){_=!1;break}}else _=!1;_&&E.push(h)}let b=s.length;for(;b>0;){for(o=0;o<s.length;o++)if(p=s[o],E.indexOf(p)>=0){E.push(p);break}b--}for(o=y.length-1;o>=0;o--)E.indexOf(y[o])>=0&&(delete y[o],d.delete(y[o]),delete m[o],delete g[o]);y.forEach(function(e,r){var i,a;if(null===e)n._items.push(new n.Item(u[r],f[r]));else for(o=0;o<n._items.length;o++)if(t.Object.compareValues(null===(a=null===(i=n._items[o])||void 0===i?void 0:i.metadata)||void 0===a?void 0:a.key,e)){n._items.splice(o,0,new n.Item(u[r],f[r]));break}})}else if(n._fetchParams&&null!=n._fetchParams.sortCriteria){let t=n._fetchParams.sortCriteria;if(t){let e,r,i,a=n._getSortComparator(t),s=[];f.forEach(function(t,o){for(e=0;e<n._items.length;e++)if(r=n._items[e].data,(i=a(t,r))<0){n._items.splice(e,0,new n.Item(u[o],f[o])),s.push(o);break}}),f.forEach(function(t,e){s.indexOf(e)<0&&n._items.push(new n.Item(u[e],f[e]))})}}else f.forEach(function(t,e){n._items.push(new n.Item(u[e],f[e]))})},this._handleMutationRemove=function(e){let r=this,i=e[a._KEYS];if(i&&i.size>0){let e;i.forEach(function(i){for(e=r._items.length-1;e>=0;e--)if(t.Object.compareValues(r._items[e].metadata.key,i)){r._items.splice(e,1);break}})}},this._handleMutationUpdate=function(e){let r=this,i=e[a._KEYS],n=e[a._DATA],s=e[a._METADATA];if(n&&n.length>0){let e,a=0;i.forEach(function(i){for(e=r._items.length-1;e>=0;e--)if(t.Object.compareValues(r._items[e].metadata.key,i)){r._items.splice(e,1,new r.Item(s[a],n[a]));break}a++})}},this.Item=class{constructor(t,e){this.metadata=t,this.data=e,this[a._METADATA]=t,this[a._DATA]=e}},this.FetchByKeysResults=class{constructor(t,e){this.fetchParameters=t,this.results=e,this[a._FETCHPARAMETERS]=t,this[a._RESULTS]=e}},this.FetchByOffsetResults=class{constructor(t,e,r){this.fetchParameters=t,this.results=e,this.done=r,this[a._FETCHPARAMETERS]=t,this[a._RESULTS]=e,this[a._DONE]=r}},this._items=[]}addListResult(t){let e=this,r=[];t.value.data.forEach(function(i,a){r.push(new e.Item(t.value.metadata[a],i))}),this._items=this._items.concat(r),this._done=t.done}getDataList(t,e){this._fetchParams=t;let r=25;null!=t.size&&(r=-1==t.size?this.getSize():t.size);let i=this._items.slice(e,e+r),a=[],n=[];return i.forEach(function(t){a.push(t.data),n.push(t.metadata)}),{fetchParameters:t,data:a,metadata:n}}getDataByKeys(t){let e=this,r=new Map;if(t&&t.keys){let i;t.keys.forEach(function(t){for(i=0;i<e._items.length;i++)if(e._items[i].metadata.key==t){r.set(t,e._items[i]);break}})}return new this.FetchByKeysResults(t,r)}getDataByOffset(t){let e=[];return t&&(e=this._items.slice(t.offset,t.offset+t.size)),new this.FetchByOffsetResults(t,e,!0)}processMutations(t){null!=t.remove&&this._handleMutationRemove(t.remove),null!=t.add&&this._handleMutationAdd(t.add),null!=t.update&&this._handleMutationUpdate(t.update)}reset(){this._items=[],this._done=!1}getSize(){return this._items.length}isDone(){return this._done}_getSortComparator(t){let e=this;return function(r,i){let n,s,o,l,f,u;for(n=0;n<t.length;n++){s=t[n][a._DIRECTION],o=t[n][a._ATTRIBUTE],l=null,f=e._getVal(r,o),u=e._getVal(i,o);let c=0,h="string"==typeof f?f:new String(f).toString(),p="string"==typeof u?u:new String(u).toString();if(0!=(c="ascending"==s?h.localeCompare(p,void 0,{numeric:!0,sensitivity:"base"}):p.localeCompare(h,void 0,{numeric:!0,sensitivity:"base"})))return c}return 0}}_getVal(t,e){if("string"==typeof e){let r=e.indexOf(".");if(r>0){let i=e.substring(0,r),a=e.substring(r+1),n=t[i];if(n)return this._getVal(n,a)}}return"function"==typeof t[e]?t[e]():t[e]}}a._DATA="data",a._METADATA="metadata",a._ITEMS="items",a._BEFOREKEYS="addBeforeKeys",a._KEYS="keys",a._INDEXES="indexes",a._FROM="from",a._OFFSET="offset",a._REFRESH="refresh",a._MUTATE="mutate",a._SIZE="size",a._FETCHPARAMETERS="fetchParameters",a._SORTCRITERIA="sortCriteria",a._DIRECTION="direction",a._ATTRIBUTE="attribute",a._VALUE="value",a._DONE="done",a._RESULTS="results",a._CONTAINSPARAMETERS="containsParameters",a._DEFAULT_SIZE=25,a._CONTAINSKEYS="containsKeys",a._FETCHBYKEYS="fetchByKeys",a._FETCHBYOFFSET="fetchByOffset",a._FETCHFIRST="fetchFirst",a._FETCHATTRIBUTES="attributes",t.DataCache=a;
/**
 * @preserve Copyright 2013 jQuery Foundation and other contributors
 * Released under the MIT license.
 * http://jquery.org/license
 */
class n extends i{constructor(t){let e={};e[n._DETAIL]=t,super("mutate",e)}}n._DETAIL="detail",t.DataProviderMutationEvent=n,t.DataProviderMutationEvent=n;
/**
 * @preserve Copyright 2013 jQuery Foundation and other contributors
 * Released under the MIT license.
 * http://jquery.org/license
 */
/**
 * @preserve Copyright 2013 jQuery Foundation and other contributors
 * Released under the MIT license.
 * http://jquery.org/license
 */
/**
 * @preserve Copyright 2013 jQuery Foundation and other contributors
 * Released under the MIT license.
 * http://jquery.org/license
 */
class s extends i{constructor(){super("refresh")}}t.DataProviderRefreshEvent=s,t.DataProviderRefreshEvent=s,
/**
 * @preserve Copyright 2013 jQuery Foundation and other contributors
 * Released under the MIT license.
 * http://jquery.org/license
 */
/**
 * @preserve Copyright 2013 jQuery Foundation and other contributors
 * Released under the MIT license.
 * http://jquery.org/license
 */
t.DataProvider=function(){};
/**
 * @preserve Copyright 2013 jQuery Foundation and other contributors
 * Released under the MIT license.
 * http://jquery.org/license
 */
/**
 * @preserve Copyright 2013 jQuery Foundation and other contributors
 * Released under the MIT license.
 * http://jquery.org/license
 */
/**
 * @preserve Copyright 2013 jQuery Foundation and other contributors
 * Released under the MIT license.
 * http://jquery.org/license
 */
/**
 * @preserve Copyright 2013 jQuery Foundation and other contributors
 * Released under the MIT license.
 * http://jquery.org/license
 */
class o{fetchByKeys(t){let e=0,r=this.getIterationLimit?this.getIterationLimit():-1,i={size:25},a=new Map,n=this.fetchFirst(i)[Symbol.asyncIterator]();return function t(i,a,n){return a.next().then(function(s){let o=s.value,l=o.data,f=o.metadata,u=f.map(function(t){return t.key}),c=!0;return i.keys.forEach(function(t){n.has(t)||u.map(function(e,r){e==t&&n.set(e,{metadata:f[r],data:l[r]})}),n.has(t)||(c=!1)}),e+=l.length,c||s.done?n:-1!=r&&e>=r?n:t(i,a,n)})}(t,n,a).then(function(e){let r=new Map;return e.forEach(function(t,e){let i=[t];r.set(e,i[0])}),{fetchParameters:t,results:r}})}containsKeys(t){return this.fetchByKeys(t).then(function(e){let r=new Set;return t.keys.forEach(function(t){null!=e.results.get(t)&&r.add(t)}),Promise.resolve({containsParameters:t,results:r})})}getCapability(t){if("fetchByKeys"==t)return{implementation:"iteration"};let e=null;if(!0!==this._ojSkipLastCapability){this._ojSkipLastCapability=!0;let r=1;for(;this["_ojLastGetCapability"+r];)++r;for(--r;r>0&&!(e=this["_ojLastGetCapability"+r](t));r--);delete this._ojSkipLastCapability}return e}static applyMixin(t){let e=t.prototype.getCapability;if([o].forEach(e=>{Object.getOwnPropertyNames(e.prototype).forEach(r=>{"constructor"!==r&&(t.prototype[r]=e.prototype[r])})}),e){let r=1;for(;t.prototype["_ojLastGetCapability"+r];)++r;t.prototype["_ojLastGetCapability"+r]=e}}}t.FetchByKeysMixin=o,t.FetchByKeysMixin.applyMixin=o.applyMixin;
/**
 * @preserve Copyright 2013 jQuery Foundation and other contributors
 * Released under the MIT license.
 * http://jquery.org/license
 */
/**
 * @preserve Copyright 2013 jQuery Foundation and other contributors
 * Released under the MIT license.
 * http://jquery.org/license
 */
class l{fetchByOffset(t){let e=t&&t.size>0?t.size:25,r=t?t.sortCriteria:null,i=t&&t.offset>0?t.offset:0,a=0,n=this.getIterationLimit?this.getIterationLimit():-1,s=!1,o={};o.size=e,o.sortCriteria=r;let l=new Array,f=this.fetchFirst(o)[Symbol.asyncIterator]();return function t(r,o,l){return o.next().then(function(f){s=f.done;let u=f.value,c=u.data,h=u.metadata,p=c.length;if(i<a+p)for(let t=i<=a?0:i-a;t<p&&l.length!=e;t++)l.push({metadata:h[t],data:c[t]});return a+=p,l.length<e&&!s?-1!=n&&a>=n?l:t(r,o,l):l})}(t,f,l).then(function(e){return{fetchParameters:t,results:e,done:s}})}getCapability(t){if("fetchByOffset"==t)return{implementation:"iteration"};let e=null;if(!0!==this._ojSkipLastCapability){this._ojSkipLastCapability=!0;let r=1;for(;this["_ojLastGetCapability"+r];)++r;for(--r;r>0&&!(e=this["_ojLastGetCapability"+r](t));r--);delete this._ojSkipLastCapability}return e}static applyMixin(t){let e=t.prototype.getCapability;if([l].forEach(e=>{Object.getOwnPropertyNames(e.prototype).forEach(r=>{"constructor"!==r&&(t.prototype[r]=e.prototype[r])})}),e){let r=1;for(;t.prototype["_ojLastGetCapability"+r];)++r;t.prototype["_ojLastGetCapability"+r]=e}}}t.FetchByOffsetMixin=l,t.FetchByOffsetMixin.applyMixin=l.applyMixin;
/**
 * @preserve Copyright 2013 jQuery Foundation and other contributors
 * Released under the MIT license.
 * http://jquery.org/license
 */
/**
 * @preserve Copyright 2013 jQuery Foundation and other contributors
 * Released under the MIT license.
 * http://jquery.org/license
 */
class f{constructor(t){t=t||{},this._textFilterAttributes=t.filterOptions?t.filterOptions.textFilterAttributes:null;let e=t.filterDef;e&&(e.op?(this.op=e.op,e.value?(this.value=e.value,e.attribute&&(this.attribute=e.attribute)):e.criteria&&(this.criteria=e.criteria)):e.text&&(this.text=e.text))}filter(e,r,i){return t.FilterUtils.satisfy(f._transformFilter(this),e)}static _transformFilter(t){let e;if(t){let r,i=t.op;if(t.text?i="$regex":"$le"===i?i="$lte":"$ge"===i?i="$gte":"$pr"===i&&(i="$exists"),"$and"!=i&&"$or"!=i){r=t.text?new RegExp(t.text.replace(/[.*+\-?^${}()|[\]\\]/g,"\\$&"),"i"):t.value,e={};let a=t.attribute;if(a){let t={};"$sw"!==i&&"$ew"!==i&&"$co"!==i||(i="$regex",r=f._fixStringExpr(i,r)),t[i]=r,e[a]=t}else if(t.text){let a={};if(a[i]=r,t._textFilterAttributes){let r=[];t._textFilterAttributes.forEach(function(t){let e={};e[t]=a,r.push(e)}),e.$or=r}else e["*"]=a}else{let t=[];f._transformObjectExpr(r,i,null,t),e.$and=t}}else{let r=[];t.criteria.forEach(function(t){r.push(f._transformFilter(t))}),(e={})[i]=r}}return e}static _transformObjectExpr(t,e,r,i){if(Object.keys(t).length>0)Object.keys(t).forEach(function(a){let n=t[a],s=r?r+"."+a:a;if(n instanceof Object)f._transformObjectExpr(n,e,s,i);else{let t={};"$sw"!==e&&"$ew"!==e&&"$co"!==e||(e="$regex",n=f._fixStringExpr(e,n)),t[e]=n;let r={};r[s]=t,i.push(r)}});else{let a={};a[e]=t;let n={};n[r]=a,i.push(n)}}static _fixStringExpr(t,e){return("string"==typeof e||e instanceof String)&&("$sw"===t?e="^"+e:"$ew"===t&&(e+="$")),e}}t.FilterFactory=class{static getFilter(t){return new f(t)}},
/**
 * @preserve Copyright 2013 jQuery Foundation and other contributors
 * Released under the MIT license.
 * http://jquery.org/license
 */
/**
 * @preserve Copyright 2013 jQuery Foundation and other contributors
 * Released under the MIT license.
 * http://jquery.org/license
 */
t.FilterUtils=function(){function t(t,e){var r=!1;for(var a in e)if(e.hasOwnProperty(a)){var n=e[a];if(r||!i(a))throw new Error("parsing error "+e);t.operator=a,t.right=n,r=!0}}function e(t,e,r){var i;if("$lt"===t)return(r=(i=n(r,e))[0])<(e=i[1]);if("$gt"===t)return(r=(i=n(r,e))[0])>(e=i[1]);if("$lte"===t)return(r=(i=n(r,e))[0])<=(e=i[1]);if("$gte"===t)return(r=(i=n(r,e))[0])>=(e=i[1]);if("$eq"===t)return r===e;if("$ne"===t)return r!==e;if("$regex"===t){if(r){if("string"!=typeof r&&!(r instanceof String))if(r instanceof Object){if("[object Object]"==(r=r.toString()))return!1}else r=new String(r);return null!==r.match(e)}return!1}if("$exists"===t)return e?null!=r:null==r;throw new Error("not a valid expression! "+expTree)}function r(t){return"$and"===t||"$or"===t}function i(t){return"$lt"===t||"$gt"===t||"$lte"===t||"$gte"===t||"$eq"===t||"$ne"===t||"$regex"===t||"$exists"===t}function a(t){return null!=t&&(t instanceof String||"string"==typeof t)}function n(t,e){return a(t)&&null==e?e="":a(e)&&null==t&&(t=""),[t,e]}function s(t,e){for(var r=t.split("."),i=e,a=0;a<r.length;a++)i=i[r[a]];return i}return{satisfy:function(a,n){return!a||function t(a,n){var o=a.operator;if(r(o)){if(!a.left&&a.array instanceof Array){for(var l,f=a.array,u=0;u<f.length;u++){var c=t(f[u],n);if("$or"===o&&!0===c)return!0;if("$and"===o&&!1===c)return!1;l=c}return l}throw new Error("invalid expression tree!"+a)}if(i(o)){var h,p=a.right;if("*"!=a.left)return h=s(a.left,n),e(o,p,h);var _,y=Object.keys(n);for(_=0;_<y.length;_++)if(h=s(y[_],n),e(o,p,h))return!0;return!1}throw new Error("not a valid expression!"+a)}(function e(a){var n,s=[];for(var o in a)if(a.hasOwnProperty(o)){var l=a[o];if(0===o.indexOf("$")){if(r(o)){if(!(l instanceof Array))throw new Error("not a valid expression: "+a);n={operator:o,array:[]};for(var f=0;f<l.length;f++){var u=e(l[f]);n.array.push(u)}}else if(i(o))throw new Error("not a valid expression: "+a)}else if("object"!=typeof l)s.push({left:o,right:l,operator:"$eq"});else{var c={left:o};t(c,l),s.push(c)}}return s.length>1?n={operator:"$and",array:s}:1===s.length&&(n=s[0]),n}(a),n)},getValue:s,assembleObject:function(t,e){var r;if(e){r={};for(var i=0;i<e.length;i++)for(var a=r,n=t,s=e[i].split("."),o=0;o<s.length;o++)n=n[s[o]],!a[s[o]]&&o<s.length-1&&(a[s[o]]={}),o===s.length-1?a[s[o]]=n:a=a[s[o]]}else r=t;return r}}}();
/**
 * @preserve Copyright 2013 jQuery Foundation and other contributors
 * Released under the MIT license.
 * http://jquery.org/license
 */
/**
 * @preserve Copyright 2013 jQuery Foundation and other contributors
 * Released under the MIT license.
 * http://jquery.org/license
 */
/**
 * @preserve Copyright 2013 jQuery Foundation and other contributors
 * Released under the MIT license.
 * http://jquery.org/license
 */
/**
 * @preserve Copyright 2013 jQuery Foundation and other contributors
 * Released under the MIT license.
 * http://jquery.org/license
 */
var u={};return u.FetchByKeysMixin=t.FetchByKeysMixin,u.FetchByOffsetMixin=t.FetchByOffsetMixin,u.FilterFactory=t.FilterFactory,u.DataProviderRefreshEvent=t.DataProviderRefreshEvent,u.DataProviderMutationEvent=t.DataProviderMutationEvent,u.AttributeFilterOperator=t.AttributeFilterOperator,u.CompoundFilterOperator=t.CompoundFilterOperator,u.DataCache=t.DataCache,u});