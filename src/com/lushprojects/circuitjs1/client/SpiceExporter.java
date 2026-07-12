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

import java.util.HashMap;
import java.util.Vector;

import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.lushprojects.circuitjs1.client.util.Locale;

// Exports the current circuit as a SPICE netlist.  Builds its own node numbering
// by merging posts that share a location, collapsing wires/closed switches, and
// mapping ground to SPICE node 0 (this matches the node merging the simulator's
// analysis does, but works even if the circuit hasn't been analyzed successfully).
public class SpiceExporter {
    CirSim app;

    // union-find over connection points
    Vector<Integer> ufParent;
    HashMap<String,Integer> pointIds;
    HashMap<Integer,Integer> spiceNodeNums;   // union-find root -> spice node number
    int nextSpiceNode;
    int groundId;

    // .model bookkeeping
    Vector<String> modelLines;
    HashMap<String,String> modelMap;          // dedupe key -> spice model name

    SpiceExporter(CirSim a) {
	app = a;
    }

    public static void doExport(CirSim app) {
	SpiceExporter exporter = new SpiceExporter(app);
	String netlist = exporter.generate();
	CirSim.dialogShowing = new SpiceExportDialog(netlist);
	CirSim.dialogShowing.show();
    }

    // ---------- union-find ----------

    int newUfNode() {
	ufParent.add(ufParent.size());
	return ufParent.size()-1;
    }

    int find(int i) {
	while (ufParent.get(i) != i) {
	    ufParent.set(i, ufParent.get(ufParent.get(i)));
	    i = ufParent.get(i);
	}
	return i;
    }

    void union(int a, int b) {
	int ra = find(a);
	int rb = find(b);
	if (ra != rb)
	    ufParent.set(rb, ra);
    }

    int idForKey(String key) {
	Integer id = pointIds.get(key);
	if (id == null) {
	    id = newUfNode();
	    pointIds.put(key, id);
	}
	return id;
    }

    int postId(CircuitElm ce, int n) {
	Point p = ce.getPost(n);
	return idForKey(p.x + "," + p.y + "," + p.z);
    }

    // spice node number (as string) for post n of an element
    String node(CircuitElm ce, int n) {
	int root = find(postId(ce, n));
	Integer num = spiceNodeNums.get(root);
	if (num == null) {
	    num = nextSpiceNode++;
	    spiceNodeNums.put(root, num);
	}
	return num.toString();
    }

    // ---------- formatting helpers ----------

    static String fmt(double v) {
	if (v == 0)
	    return "0";
	String s = Double.toString(v);
	if (s.endsWith(".0"))
	    s = s.substring(0, s.length()-2);
	return s;
    }

    static String sanitize(String s) {
	StringBuilder sb = new StringBuilder();
	int i;
	for (i = 0; i != s.length(); i++) {
	    char c = s.charAt(i);
	    if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') ||
		    (c >= '0' && c <= '9') || c == '_')
		sb.append(c);
	    else
		sb.append('_');
	}
	if (sb.length() == 0)
	    sb.append('X');
	return sb.toString();
    }

    // ---------- .model helpers ----------

    String getDiodeModelName(DiodeElm de) {
	String key = "D:" + de.modelName;
	String nm = modelMap.get(key);
	if (nm != null)
	    return nm;
	nm = "DMOD_" + sanitize(de.modelName);
	DiodeModel m = de.model;
	String params = "IS=" + fmt(m.saturationCurrent) + " N=" + fmt(m.emissionCoefficient);
	if (m.seriesResistance > 0)
	    params += " RS=" + fmt(m.seriesResistance);
	if (m.breakdownVoltage > 0)
	    params += " BV=" + fmt(m.breakdownVoltage);
	modelLines.add(".model " + nm + " D(" + params + ")");
	modelMap.put(key, nm);
	return nm;
    }

    String getBJTModelName(TransistorElm te) {
	String key = "Q:" + te.modelName + ":" + te.pnp + ":" + te.beta;
	String nm = modelMap.get(key);
	if (nm != null)
	    return nm;
	String type = (te.pnp < 0) ? "PNP" : "NPN";
	nm = "QMOD" + (modelMap.size()+1);
	String params = "BF=" + fmt(te.beta);
	if (te.model != null && te.model.satCur > 0)
	    params += " IS=" + fmt(te.model.satCur);
	modelLines.add(".model " + nm + " " + type + "(" + params + ")");
	modelMap.put(key, nm);
	return nm;
    }

    String getMosfetModelName(MosfetElm me) {
	String key = "M:" + me.pnp + ":" + me.vt + ":" + me.beta;
	String nm = modelMap.get(key);
	if (nm != null)
	    return nm;
	String type = (me.pnp < 0) ? "PMOS" : "NMOS";
	nm = "MMOD" + (modelMap.size()+1);
	double vto = (me.pnp < 0) ? -me.vt : me.vt;
	modelLines.add(".model " + nm + " " + type + "(VTO=" + fmt(vto) + " KP=" + fmt(me.beta) + ")");
	modelMap.put(key, nm);
	return nm;
    }

    // ---------- voltage source waveform ----------

    // returns the SPICE source specification for a VoltageElm; appends a
    // warning comment to warn if the waveform can't be represented well
    String voltageSpec(VoltageElm ve, String name, StringBuilder warn) {
	double per = (ve.frequency > 0) ? 1/ve.frequency : 1;
	double hi = ve.bias + ve.maxVoltage;
	double lo = ve.bias - ve.maxVoltage;
	switch (ve.waveform) {
	case VoltageElm.WF_DC:
	    return "DC " + fmt(ve.maxVoltage + ve.bias);
	case VoltageElm.WF_AC:
	    return "SIN(" + fmt(ve.bias) + " " + fmt(ve.maxVoltage) + " " + fmt(ve.frequency) +
		    " 0 0 " + fmt(ve.phaseShift*180/Math.PI) + ")";
	case VoltageElm.WF_SQUARE: {
	    double tr = (ve.riseTime > 0) ? ve.riseTime : per*.001;
	    double pw = per*ve.dutyCycle - tr;
	    if (pw < tr)
		pw = tr;
	    return "PULSE(" + fmt(lo) + " " + fmt(hi) + " 0 " + fmt(tr) + " " + fmt(tr) +
		    " " + fmt(pw) + " " + fmt(per) + ")";
	}
	case VoltageElm.WF_PULSE: {
	    double tr = (ve.riseTime > 0) ? ve.riseTime : per*.001;
	    double pw = per*ve.dutyCycle - tr;
	    if (pw < tr)
		pw = tr;
	    return "PULSE(" + fmt(ve.bias) + " " + fmt(hi) + " 0 " + fmt(tr) + " " + fmt(tr) +
		    " " + fmt(pw) + " " + fmt(per) + ")";
	}
	case VoltageElm.WF_TRIANGLE:
	    return "PULSE(" + fmt(lo) + " " + fmt(hi) + " 0 " + fmt(per*.499) + " " + fmt(per*.499) +
		    " " + fmt(per*.002) + " " + fmt(per) + ")";
	case VoltageElm.WF_SAWTOOTH:
	    return "PULSE(" + fmt(lo) + " " + fmt(hi) + " 0 " + fmt(per*.998) + " " + fmt(per*.001) +
		    " " + fmt(per*.001) + " " + fmt(per) + ")";
	default:
	    warn.append("* WARNING: waveform of source " + name + " not supported, exported as DC\n");
	    return "DC " + fmt(ve.bias);
	}
    }

    // ---------- main entry ----------

    String generate() {
	int i, j;
	ufParent = new Vector<Integer>();
	pointIds = new HashMap<String,Integer>();
	spiceNodeNums = new HashMap<Integer,Integer>();
	nextSpiceNode = 1;
	modelLines = new Vector<String>();
	modelMap = new HashMap<String,String>();
	groundId = newUfNode();

	Vector<CircuitElm> elmList = app.elmList;

	// pass 1: merge nodes connected by wires, grounds, closed switches,
	// labeled nodes and ammeters
	boolean gotGround = false, gotRail = false;
	for (i = 0; i != elmList.size(); i++) {
	    CircuitElm ce = elmList.get(i);
	    if (ce instanceof GroundElm) {
		union(groundId, postId(ce, 0));
		gotGround = true;
	    } else if (ce instanceof WireElm) {
		WireElm we = (WireElm) ce;
		for (j = 0; j != we.busWidth; j++)
		    union(postId(ce, j), postId(ce, j+we.busWidth));
	    } else if (ce instanceof AmmeterElm) {
		union(postId(ce, 0), postId(ce, 1));
	    } else if (ce instanceof SwitchElm && ce.getDumpType() == 's') {
		if (((SwitchElm) ce).position == 0)
		    union(postId(ce, 0), postId(ce, 1));
	    } else if (ce instanceof LabeledNodeElm) {
		LabeledNodeElm ln = (LabeledNodeElm) ce;
		for (j = 0; j != ce.getPostCount(); j++)
		    union(postId(ce, j), idForKey("label:" + ln.text + ":" + j));
	    }
	    if (ce instanceof RailElm)
		gotRail = true;
	}

	// if there's no ground and no rails, treat the first terminal of the
	// first 2-terminal voltage source as ground, like the simulator does
	if (!gotGround && !gotRail) {
	    for (i = 0; i != elmList.size(); i++) {
		CircuitElm ce = elmList.get(i);
		if (ce instanceof VoltageElm && ce.getPostCount() == 2) {
		    union(groundId, postId(ce, 0));
		    break;
		}
	    }
	}
	spiceNodeNums.put(find(groundId), Integer.valueOf(0));

	// pass 2: emit element lines
	StringBuilder body = new StringBuilder();
	int nr = 0, nc = 0, nl = 0, nv = 0, ni = 0, nd = 0, nq = 0, nm = 0;
	int exported = 0;
	for (i = 0; i != elmList.size(); i++) {
	    CircuitElm ce = elmList.get(i);
	    if (ce.getPostCount() == 0)
		continue;	// graphics, scopes, etc.
	    if (ce instanceof GroundElm || ce instanceof WireElm || ce instanceof LabeledNodeElm)
		continue;	// already merged into nodes
	    if (ce instanceof AmmeterElm) {
		body.append("* ammeter treated as wire\n");
		continue;
	    }
	    if (ce instanceof SwitchElm && ce.getDumpType() == 's') {
		body.append((((SwitchElm) ce).position == 0) ?
			"* closed switch treated as wire\n" : "* open switch omitted\n");
		continue;
	    }
	    if (ce instanceof ProbeElm || ce instanceof OutputElm) {
		body.append("* probe/output omitted\n");
		continue;
	    }
	    if (ce instanceof ResistorElm) {
		body.append("R" + (++nr) + " " + node(ce, 0) + " " + node(ce, 1) + " " +
			fmt(((ResistorElm) ce).resistance) + "\n");
		exported++;
		continue;
	    }
	    if (ce instanceof CapacitorElm) {
		CapacitorElm cap = (CapacitorElm) ce;
		String line = "C" + (++nc) + " " + node(ce, 0) + " " + node(ce, 1) + " " +
			fmt(cap.capacitance);
		// 1e-3 is the default "small charge to start oscillators", not a real IC
		if (cap.initialVoltage != 0 && cap.initialVoltage != 1e-3)
		    line += " IC=" + fmt(cap.initialVoltage);
		body.append(line + "\n");
		exported++;
		continue;
	    }
	    if (ce instanceof InductorElm) {
		InductorElm ind = (InductorElm) ce;
		String line = "L" + (++nl) + " " + node(ce, 0) + " " + node(ce, 1) + " " +
			fmt(ind.inductance);
		if (ind.initialCurrent != 0)
		    line += " IC=" + fmt(ind.initialCurrent);
		body.append(line + "\n");
		exported++;
		continue;
	    }
	    if (ce instanceof CurrentElm) {
		// current flows internally from post 0 to post 1, matching SPICE n+ -> n-
		body.append("I" + (++ni) + " " + node(ce, 0) + " " + node(ce, 1) + " DC " +
			fmt(((CurrentElm) ce).currentValue) + "\n");
		exported++;
		continue;
	    }
	    if (ce instanceof DiodeElm) {
		body.append("D" + (++nd) + " " + node(ce, 0) + " " + node(ce, 1) + " " +
			getDiodeModelName((DiodeElm) ce) + "\n");
		exported++;
		continue;
	    }
	    if (ce instanceof TransistorElm) {
		// posts: 0 = base, 1 = collector, 2 = emitter
		body.append("Q" + (++nq) + " " + node(ce, 1) + " " + node(ce, 0) + " " +
			node(ce, 2) + " " + getBJTModelName((TransistorElm) ce) + "\n");
		exported++;
		continue;
	    }
	    if (ce instanceof MosfetElm) {
		MosfetElm mos = (MosfetElm) ce;
		// posts: 0 = gate; 1 = source, 2 = drain for N (swapped for P)
		String g = node(ce, 0);
		String s, d;
		if (mos.pnp < 0) {
		    d = node(ce, 1);
		    s = node(ce, 2);
		} else {
		    s = node(ce, 1);
		    d = node(ce, 2);
		}
		String b = (ce.getPostCount() > 3) ? node(ce, 3) : s;
		body.append("M" + (++nm) + " " + d + " " + g + " " + s + " " + b + " " +
			getMosfetModelName(mos) + "\n");
		exported++;
		continue;
	    }
	    if (ce instanceof VoltageElm) {
		// covers 2-terminal sources and 1-terminal rails (incl. clocks)
		VoltageElm ve = (VoltageElm) ce;
		String name = "V" + (++nv);
		StringBuilder warn = new StringBuilder();
		String spec = voltageSpec(ve, name, warn);
		String nplus, nminus;
		if (ce.getPostCount() == 1) {
		    // rail: voltage source from its post to ground
		    nplus = node(ce, 0);
		    nminus = "0";
		} else {
		    // post 1 is the + terminal
		    nplus = node(ce, 1);
		    nminus = node(ce, 0);
		}
		body.append(warn);
		body.append(name + " " + nplus + " " + nminus + " " + spec + "\n");
		if (ve.internalResistance > 0)
		    body.append("* WARNING: internal resistance of " + name + " (" +
			    fmt(ve.internalResistance) + " ohms) not exported\n");
		exported++;
		continue;
	    }
	    body.append("* WARNING: unsupported element " + ce.getClass().getSimpleName() + "\n");
	}

	// assemble the netlist
	StringBuilder out = new StringBuilder();
	out.append("* SPICE netlist exported from CircuitJS1\n");
	if (exported == 0)
	    out.append("* (no exportable elements found)\n");
	out.append(body);
	for (i = 0; i != modelLines.size(); i++)
	    out.append(modelLines.get(i) + "\n");
	double ts = app.sim.maxTimeStep;
	if (ts > 0)
	    out.append("* suggested transient analysis:\n* .tran " + fmt(ts) + " " + fmt(ts*1000) + " uic\n");
	out.append(".end\n");
	return out.toString();
    }

    // ---------- dialog ----------

    static class SpiceExportDialog extends Dialog {
	TextArea textArea;

	SpiceExportDialog(String s) {
	    super();
	    closeOnEnter = false;
	    VerticalPanel vp = new VerticalPanel();
	    setWidget(vp);
	    setText(Locale.LS("Export as SPICE Netlist"));
	    vp.add(new Label(Locale.LS("SPICE netlist for this circuit is...")));
	    vp.add(textArea = new TextArea());
	    textArea.setWidth("400px");
	    textArea.setHeight("300px");
	    textArea.setText(s);
	    HorizontalPanel hp = new HorizontalPanel();
	    hp.setWidth("100%");
	    hp.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_LEFT);
	    hp.setStyleName("topSpace");
	    vp.add(hp);
	    Button okButton, copyButton, downloadButton;
	    hp.add(okButton = new Button(Locale.LS("OK")));
	    hp.add(copyButton = new Button(Locale.LS("Copy to Clipboard")));
	    hp.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_RIGHT);
	    hp.add(downloadButton = new Button(Locale.LS("Download")));
	    downloadButton.setEnabled(ExportAsLocalFileDialog.downloadIsSupported());
	    okButton.addClickHandler(new ClickHandler() {
		public void onClick(ClickEvent event) {
		    closeDialog();
		}
	    });
	    copyButton.addClickHandler(new ClickHandler() {
		public void onClick(ClickEvent event) {
		    textArea.setFocus(true);
		    textArea.selectAll();
		    copyToClipboard();
		    textArea.setSelectionRange(0, 0);
		}
	    });
	    downloadButton.addClickHandler(new ClickHandler() {
		public void onClick(ClickEvent event) {
		    String url = ExportAsLocalFileDialog.getBlobUrl(textArea.getText());
		    doDownload(url, "circuit.cir");
		}
	    });
	    this.center();
	}

	static native boolean copyToClipboard() /*-{
	    return $doc.execCommand('copy');
	}-*/;

	static native void doDownload(String url, String name) /*-{
	    var a = $doc.createElement('a');
	    a.href = url;
	    a.download = name;
	    $doc.body.appendChild(a);
	    a.click();
	    $doc.body.removeChild(a);
	}-*/;
    }
}
