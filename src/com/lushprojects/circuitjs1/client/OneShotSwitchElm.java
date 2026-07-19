/*
    Copyright (C) Paul Falstad and Iain Sharp

    This file is part of CircuitJS1.

    CircuitJS1 is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 2 of the License, or
    (at your option) any later version.

    CircuitJS1 is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with CircuitJS1.  If not, see <http://www.gnu.org/licenses/>.
*/

package com.lushprojects.circuitjs1.client;

import com.google.gwt.xml.client.Document;
import com.google.gwt.xml.client.Element;

// SPST one-shot switch; clicking it closes it for a fixed amount of simulated
// time, then it reopens by itself.  Clicking it again while closed reopens it
// immediately.
class OneShotSwitchElm extends SwitchElm {
    double duration;
    double pulseStartTime;

    public OneShotSwitchElm(int xx, int yy) {
	super(xx, yy);
	position = 1;
	duration = 1;
    }
    public OneShotSwitchElm(int xa, int ya, int xb, int yb, int f,
			    StringTokenizer st) {
	super(xa, ya, xb, yb, f, st);
	duration = Double.parseDouble(st.nextToken());
    }
    int getDumpType() { return 437; }

    void dumpXml(Document doc, Element elem) {
	super.dumpXml(doc, elem);
	XMLSerializer.dumpAttr(elem, "dur", duration);
    }

    void undumpXml(XMLDeserializer xml) {
	super.undumpXml(xml);
	duration = xml.parseDoubleAttr("dur", duration);
    }

    void reset() {
	super.reset();
	position = 1;
    }

    void toggle() {
	TestManager.recordSwitchToggle(this);
	if (position == 1) {
	    position = 0;
	    pulseStartTime = sim.t;
	} else
	    position = 1;
    }

    void stepFinished() {
	if (position == 0 && sim.t - pulseStartTime >= duration) {
	    position = 1;
	    CirSim.theApp.needAnalyze();
	}
    }

    String getElmType() { return "one-shot switch (SPST)"; }
    void getInfo(String arr[]) {
	super.getInfo(arr);
	arr[0] = "one-shot switch (SPST)";
	if (position == 0)
	    arr[4] = "time left = " +
		getUnitText(pulseStartTime + duration - sim.t, "s");
    }

    public EditInfo getEditInfo(int n) {
	if (n == 0) {
	    EditInfo ei = new EditInfo("On Time (s)", duration, 1e-3, 10);
	    ei.setPositive();
	    return ei;
	}
	return super.getEditInfo(n);
    }
    public void setEditValue(int n, EditInfo ei) {
	if (n == 0) {
	    if (ei.value > 0)
		duration = ei.value;
	    return;
	}
	super.setEditValue(n, ei);
    }

    int getShortcut() { return 0; }
}
