/**
 * @license
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates.
 * Licensed under The Universal Permissive License (UPL), Version 1.0
 * as shown at https://oss.oracle.com/licenses/upl/
 * @ignore
 */
define(["ojs/ojcore","jquery"],function(t,e){"use strict";return t.DataCollectionEditUtils={},t.DataCollectionEditUtils.basicHandleEditEnd=function(n,i){null!=i&&null!=i.cellContext||(i=n.detail);var l=e(i.cellContext.parentElement).find(".oj-component-initnode")[0],o=t.Components.__GetWidgetConstructor(l);i.cancelEdit?o("reset"):(o("validate"),o("isValid")||n.preventDefault())},t.DataCollectionEditUtils.basicHandleRowEditEnd=function(n,i){null!=i&&null!=i.rowContext||(i=n.detail);for(var l=e(i.rowContext.parentElement).find(".oj-component-initnode"),o=0;o<l.length;o++){var a=t.Components.__GetWidgetConstructor(l[o]),d=i.cancelEdit;try{if(d)a("reset");else if(a("validate"),!a("isValid"))return!1}catch(t){}}return!0},t.DataCollectionEditUtils});