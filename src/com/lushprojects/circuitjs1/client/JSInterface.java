package com.lushprojects.circuitjs1.client;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;

public class JSInterface {

    CirSim app;

    JSInterface(CirSim app) {
	this.app = app;
    }

    void setExtVoltage(String name, double v) {
	int i;
	for (i = 0; i != app.elmList.size(); i++) {
	    CircuitElm ce = app.getElm(i);
	    if (ce instanceof ExtVoltageElm) {
		ExtVoltageElm eve = (ExtVoltageElm) ce;
		if (eve.getName().equals(name))
		    eve.setVoltage(v);
	    }
	}
    }

    // toggle the first switch whose label matches name, as if it were clicked.
    // returns false if no switch with that label was found.
    boolean toggleSwitch(String name) {
	int i;
	for (i = 0; i != app.elmList.size(); i++) {
	    CircuitElm ce = app.getElm(i);
	    if (ce instanceof SwitchElm) {
		SwitchElm se = (SwitchElm) ce;
		if (name.equals(se.label)) {
		    se.toggle();
		    app.needAnalyze();
		    app.repaint();
		    return true;
		}
	    }
	}
	return false;
    }

    // set the value of the sidebar slider whose label matches name.
    // the value is clamped to the slider's range.  returns false if not found.
    boolean setSliderValue(String name, double value) {
	int i;
	for (i = 0; i != app.adjustables.size(); i++) {
	    Adjustable adj = app.adjustables.get(i);
	    if (adj.sharedSlider == null && adj.slider != null &&
		    name.equals(adj.sliderText)) {
		adj.setSliderValue(value);
		adj.execute();
		return true;
	    }
	}
	return false;
    }

    native JavaScriptObject makeScopeObject(String label) /*-{
	return { label: label, plots: [] };
    }-*/;
    native JavaScriptObject makePlotObject(JavaScriptObject scope, String elm,
					   String units, double samplePeriod) /*-{
	var plot = { elm: elm, units: units, samplePeriod: samplePeriod,
		     min: [], max: [] };
	scope.plots.push(plot);
	return plot;
    }-*/;
    native void addPlotSample(JavaScriptObject plot, double mn, double mx) /*-{
	plot.min.push(mn);
	plot.max.push(mx);
    }-*/;

    // return an array of scope data objects, one per visible scope:
    // { label, plots: [ { elm, units, samplePeriod, min: [], max: [] } ] }
    // min/max are in chronological order (oldest first); each entry is the
    // min/max of the values seen during one sample period.
    JsArray<JavaScriptObject> getScopeData() {
	int i;
	JsArray<JavaScriptObject> arr = getJSArray();
	ScopeManager sm = app.scopeManager;
	for (i = 0; i != sm.scopeCount; i++) {
	    Scope s = sm.scopes[i];
	    JavaScriptObject so = makeScopeObject(s.getScopeLabelOrText());
	    int j;
	    for (j = 0; j != s.visiblePlots.size(); j++) {
		ScopePlot p = s.visiblePlots.get(j);
		if (p.minValues == null)
		    continue;
		JavaScriptObject po = makePlotObject(so,
		    p.elm == null ? "" : p.elm.getElmType(),
		    Scope.getScaleUnitsText(p.units),
		    p.scopePlotSpeed * app.sim.maxTimeStep);
		int spc = p.scopePointCount;
		int k;
		for (k = 0; k != spc; k++) {
		    int ip = (p.ptr + 1 + k) & (spc - 1);
		    addPlotSample(po, p.minValues[ip], p.maxValues[ip]);
		}
	    }
	    arr.push(so);
	}
	return arr;
    }

    native JsArray<JavaScriptObject> getJSArray() /*-{ return []; }-*/;

    JsArray<JavaScriptObject> getJSElements() {
	int i;
	JsArray<JavaScriptObject> arr = getJSArray();
	for (i = 0; i != app.elmList.size(); i++) {
	    CircuitElm ce = app.getElm(i);
	    ce.addJSMethods();
	    arr.push(ce.getJavaScriptObject());
	}
	return arr;
    }

    double getLabeledNodeVoltage(String name) { return app.sim.getLabeledNodeVoltage(name); }

    // Delegate methods for JSNI access
    void setSimRunning(boolean run) { app.setSimRunning(run); }
    boolean simIsRunning() { return app.simIsRunning(); }
    void doExportAsSVGFromAPI() { app.imageExporter.doExportAsSVGFromAPI(); }
    String dumpCircuit() { return app.dumpCircuit(); }
    void importCircuitFromText(String t, boolean s) { app.importCircuitFromText(t, s); }
    double getTime() { return app.sim.t; }
    double getTimeStep() { return app.sim.timeStep; }
    void setTimeStep(double ts) { app.sim.timeStep = ts; }
    double getMaxTimeStep() { return app.sim.maxTimeStep; }
    void setMaxTimeStep(double ts) { app.sim.maxTimeStep = app.sim.timeStep = ts; }

    native void setupJSInterface() /*-{
	var that = this;
	$wnd.CircuitJS1 = {
	    setSimRunning: $entry(function(run) { that.@com.lushprojects.circuitjs1.client.JSInterface::setSimRunning(Z)(run); } ),
	    getTime: $entry(function() { return that.@com.lushprojects.circuitjs1.client.JSInterface::getTime()(); } ),
	    getTimeStep: $entry(function() { return that.@com.lushprojects.circuitjs1.client.JSInterface::getTimeStep()(); } ),
	    setTimeStep: $entry(function(ts) { that.@com.lushprojects.circuitjs1.client.JSInterface::setTimeStep(D)(ts); } ), // don't use this, see #843
	    getMaxTimeStep: $entry(function() { return that.@com.lushprojects.circuitjs1.client.JSInterface::getMaxTimeStep()(); } ),
	    setMaxTimeStep: $entry(function(ts) { that.@com.lushprojects.circuitjs1.client.JSInterface::setMaxTimeStep(D)(ts); } ),
	    isRunning: $entry(function() { return that.@com.lushprojects.circuitjs1.client.JSInterface::simIsRunning()(); } ),
	    getNodeVoltage: $entry(function(n) { return that.@com.lushprojects.circuitjs1.client.JSInterface::getLabeledNodeVoltage(Ljava/lang/String;)(n); } ),
	    setExtVoltage: $entry(function(n, v) { that.@com.lushprojects.circuitjs1.client.JSInterface::setExtVoltage(Ljava/lang/String;D)(n, v); } ),
	    toggleSwitch: $entry(function(n) { return that.@com.lushprojects.circuitjs1.client.JSInterface::toggleSwitch(Ljava/lang/String;)(n); } ),
	    setSliderValue: $entry(function(n, v) { return that.@com.lushprojects.circuitjs1.client.JSInterface::setSliderValue(Ljava/lang/String;D)(n, v); } ),
	    getScopeData: $entry(function() { return that.@com.lushprojects.circuitjs1.client.JSInterface::getScopeData()(); } ),
	    getElements: $entry(function() { return that.@com.lushprojects.circuitjs1.client.JSInterface::getJSElements()(); } ),
	    getCircuitAsSVG: $entry(function() { return that.@com.lushprojects.circuitjs1.client.JSInterface::doExportAsSVGFromAPI()(); } ),
	    exportCircuit: $entry(function() { return that.@com.lushprojects.circuitjs1.client.JSInterface::dumpCircuit()(); } ),
	    importCircuit: $entry(function(circuit, subcircuitsOnly) { return that.@com.lushprojects.circuitjs1.client.JSInterface::importCircuitFromText(Ljava/lang/String;Z)(circuit, subcircuitsOnly); })
	};
	var hook = $wnd.oncircuitjsloaded;
	if (hook)
	    hook($wnd.CircuitJS1);
    }-*/;

    native void callUpdateHook() /*-{
	var hook = $wnd.CircuitJS1.onupdate;
	if (hook)
	    hook($wnd.CircuitJS1);
    }-*/;

    native void callAnalyzeHook() /*-{
	var hook = $wnd.CircuitJS1.onanalyze;
	if (hook)
	    hook($wnd.CircuitJS1);
    }-*/;

    native void callTimeStepHook() /*-{
	var hook = $wnd.CircuitJS1.ontimestep;
	if (hook)
	    hook($wnd.CircuitJS1);
    }-*/;

    native void callSVGRenderedHook(String svgData) /*-{
	var hook = $wnd.CircuitJS1.onsvgrendered;
	if (hook)
	    hook($wnd.CircuitJS1, svgData);
    }-*/;
}
