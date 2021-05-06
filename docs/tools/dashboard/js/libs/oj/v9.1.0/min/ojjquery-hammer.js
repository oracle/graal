/**
 * @license
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates.
 * Licensed under The Universal Permissive License (UPL), Version 1.0
 * as shown at https://oss.oracle.com/licenses/upl/
 * @ignore
 */
define(["ojs/ojcore","jquery","hammerjs","ojs/ojlogger"],function(e,t,a,r){"use strict";var o;a?(t.fn.ojHammer=function(e){switch(e){case"instance":return this.data("ojHammer");case"destroy":return this.each(function(){var e=t(this),a=e.data("ojHammer");a&&(a.destroy(),e.removeData("ojHammer"))});default:return this.each(function(){var r=t(this);r.data("ojHammer")||r.data("ojHammer",new a.Manager(r[0],e))})}},a.Manager.prototype.emit=(o=a.Manager.prototype.emit,function(e,a){o.call(this,e,a),t(this.element).trigger({type:e,gesture:a})})):r.warn("Hammer jQuery extension loaded without Hammer.")});