/**
 * @license
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates.
 * Licensed under The Universal Permissive License (UPL), Version 1.0
 * as shown at https://oss.oracle.com/licenses/upl/
 * @ignore
 */
define(["ojs/ojcore","ojs/ojcollectiontabledatasource","ojs/ojdataprovideradapter"],function(t){"use strict";class e{constructor(e){this.collection=e,this._dataProviderAdapter=new t.TableDataSourceAdapter(new t.CollectionTableDataSource(e)),this.addEventListener=this._dataProviderAdapter.addEventListener.bind(this._dataProviderAdapter),this.removeEventListener=this._dataProviderAdapter.removeEventListener.bind(this._dataProviderAdapter),this.dispatchEvent=this._dataProviderAdapter.dispatchEvent.bind(this._dataProviderAdapter)}destroy(){this._dataProviderAdapter.destroy()}fetchFirst(t){return this._dataProviderAdapter.fetchFirst(t)}fetchByKeys(t){return this._dataProviderAdapter.fetchByKeys(t)}containsKeys(t){return this._dataProviderAdapter.containsKeys(t)}fetchByOffset(t){return this._dataProviderAdapter.fetchByOffset(t)}getCapability(t){return this._dataProviderAdapter.getCapability(t)}getTotalSize(){return this._dataProviderAdapter.getTotalSize()}isEmpty(){return this._dataProviderAdapter.isEmpty()}}
/**
 * @preserve Copyright 2013 jQuery Foundation and other contributors
 * Released under the MIT license.
 * http://jquery.org/license
 */
return t.CollectionDataProvider=e,t.CollectionDataProvider=e,e});