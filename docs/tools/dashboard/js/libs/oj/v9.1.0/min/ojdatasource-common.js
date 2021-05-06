/**
 * @license
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates.
 * Licensed under The Universal Permissive License (UPL), Version 1.0
 * as shown at https://oss.oracle.com/licenses/upl/
 * @ignore
 */
define(["ojs/ojcore","jquery"],function(a,e){"use strict";
/**
 * @preserve Copyright 2013 jQuery Foundation and other contributors
 * Released under the MIT license.
 * http://jquery.org/license
 */a.DataSource=function(a){this.data=a,this.Init()},a.Object.createSubclass(a.DataSource,a.EventSource,"oj.DataSource"),a.DataSource.prototype.Init=function(){a.DataSource.superclass.Init.call(this)},a.TreeDataSource=function(e){a.TreeDataSource.superclass.constructor.call(this,e)},a.Object.createSubclass(a.TreeDataSource,a.DataSource,"oj.TreeDataSource"),
/**
 * @preserve Copyright 2013 jQuery Foundation and other contributors
 * Released under the MIT license.
 * http://jquery.org/license
 */
a.TableDataSource=function(e,t){if(this.constructor===a.TableDataSource){var r=a.TableDataSource._LOGGER_MSG._ERR_TABLE_DATASOURCE_INSTANTIATED_SUMMARY,o=a.TableDataSource._LOGGER_MSG._ERR_TABLE_DATASOURCE_INSTANTIATED_DETAIL;throw new Error(r+"\n"+o)}this.data=e,this.options=t,this.isFetching=!1,this._startIndex=0,this.Init()},a.Object.createSubclass(a.TableDataSource,a.DataSource,"oj.TableDataSource"),a.TableDataSource.prototype.Init=function(){a.TableDataSource.superclass.Init.call(this)},a.TableDataSource.prototype.sortCriteria=null,a.TableDataSource.prototype.totalSizeConfidence=function(){return"actual"},a.TableDataSource.EventType={ADD:"add",REMOVE:"remove",RESET:"reset",REFRESH:"refresh",SORT:"sort",CHANGE:"change",REQUEST:"request",SYNC:"sync",ERROR:"error"},a.TableDataSource._LOGGER_MSG={_ERR_TABLE_DATASOURCE_INSTANTIATED_SUMMARY:"oj.TableDataSource constructor called.",_ERR_TABLE_DATASOURCE_INSTANTIATED_DETAIL:"Please do not instantiate oj.TableDataSource. Please use one of the subclasses instead such as oj.ArrayTableDataSource or oj.CollectionTableDataSource.",_ERR_DATA_INVALID_TYPE_SUMMARY:"Invalid data type.",_ERR_DATA_INVALID_TYPE_DETAIL:"Please specify the appropriate data type."},a.DataGridDataSource=function(e){a.DataGridDataSource.superclass.constructor.call(this,e)},a.Object.createSubclass(a.DataGridDataSource,a.DataSource,"oj.DataGridDataSource"),a.DiagramDataSource=function(e){a.DiagramDataSource.superclass.constructor.call(this,e)},a.Object.createSubclass(a.DiagramDataSource,a.DataSource,"oj.DiagramDataSource"),a.DiagramDataSource.EventType={ADD:"add",REMOVE:"remove",CHANGE:"change"};var t={};return t.DataGridDataSource=a.DataGridDataSource,t});