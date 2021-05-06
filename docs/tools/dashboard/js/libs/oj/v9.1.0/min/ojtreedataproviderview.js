/**
 * @license
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates.
 * Licensed under The Universal Permissive License (UPL), Version 1.0
 * as shown at https://oss.oracle.com/licenses/upl/
 * @ignore
 */
define(["ojs/ojcore","jquery","ojs/ojlistdataproviderview","ojs/ojcomponentcore","ojs/ojeventtarget","ojs/ojdataprovider"],function(t,e,i){"use strict";class r{constructor(t,e){this.dataProvider=t,this.options=e,this._listDataProviderView=new i(t,e)}getChildDataProvider(t,e){let i=this.dataProvider.getChildDataProvider(t,e);return i?new r(i,this.options):null}containsKeys(t){return this._listDataProviderView.containsKeys(t)}fetchByKeys(t){return this._listDataProviderView.fetchByKeys(t)}fetchByOffset(t){return this._listDataProviderView.fetchByOffset(t)}fetchFirst(t){return this._listDataProviderView.fetchFirst(t)}getCapability(t){return this._listDataProviderView.getCapability(t)}getTotalSize(){return this._listDataProviderView.getTotalSize()}isEmpty(){return this._listDataProviderView.isEmpty()}addEventListener(t,e){this._listDataProviderView.addEventListener(t,e)}removeEventListener(t,e){this._listDataProviderView.removeEventListener(t,e)}dispatchEvent(t){return this._listDataProviderView.dispatchEvent(t)}}
/**
 * @preserve Copyright 2013 jQuery Foundation and other contributors
 * Released under the MIT license.
 * http://jquery.org/license
 */
return t.TreeDataProviderView=r,t.TreeDataProviderView=r,r});