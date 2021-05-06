/**
 * @license
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates.
 * Licensed under The Universal Permissive License (UPL), Version 1.0
 * as shown at https://oss.oracle.com/licenses/upl/
 * @ignore
 */
define(["ojs/ojcore","jquery","knockout","ojs/ojcomponentcore","ojs/ojeventtarget","ojs/ojdataprovider"],function(t,e,r){"use strict";class n{constructor(t,e){this._dataProvider=t,this._capabilityFunc=e,this._DATAPROVIDER="dataProvider",this.AsyncIterable=class{constructor(t){this._asyncIterator=t,this[Symbol.asyncIterator]=function(){return this._asyncIterator}}},this.AsyncIterator=class{constructor(t){this._asyncIteratorPromise=t}next(){return this._asyncIteratorPromise.then(function(t){return t.next()})}}}fetchFirst(t){let e=this._getDataProvider().then(function(e){return e.fetchFirst(t)[Symbol.asyncIterator]()});return new this.AsyncIterable(new this.AsyncIterator(e))}fetchByKeys(t){return this._getDataProvider().then(function(e){return e.fetchByKeys(t)})}containsKeys(t){return this._getDataProvider().then(function(e){return e.containsKeys(t)})}fetchByOffset(t){return this._getDataProvider().then(function(e){return e.fetchByOffset(t)})}getTotalSize(){return this._getDataProvider().then(function(t){return t.getTotalSize()})}isEmpty(){return this[this._DATAPROVIDER]?this[this._DATAPROVIDER].isEmpty():"unknown"}getCapability(t){return this._capabilityFunc?this._capabilityFunc(t):null}addEventListener(t,e){this._getDataProvider().then(function(r){r.addEventListener(t,e)})}removeEventListener(t,e){this._getDataProvider().then(function(r){r.removeEventListener(t,e)})}dispatchEvent(t){return!!this[this._DATAPROVIDER]&&this[this._DATAPROVIDER].dispatchEvent(t)}_getDataProvider(){let e=this;return this._dataProvider.then(function(r){if(t.DataProviderFeatureChecker.isDataProvider(r))return e[e._DATAPROVIDER]||(e[e._DATAPROVIDER]=r),r;throw new Error("Invalid data type. DeferredDataProvider takes a Promise<DataProvider>")})}}
/**
 * @preserve Copyright 2013 jQuery Foundation and other contributors
 * Released under the MIT license.
 * http://jquery.org/license
 */
return t.DeferredDataProvider=n,n});