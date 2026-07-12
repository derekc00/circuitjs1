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

import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.lushprojects.circuitjs1.client.util.Locale;

// Imports a basic subset of SPICE netlists: R/C/L/V/I/D cards with numeric
// values (engineering suffixes supported) and V sources with DC, SIN(...) or
// PULSE(...) specifications.  Elements are auto-placed on a grid, one column
// per element, with one horizontal wire rail per node.
public class SpiceImporter extends Dialog {

    CirSim sim;
    TextArea textArea;
    String lastError;

    public SpiceImporter(CirSim asim) {
	super();
	sim = asim;
	closeOnEnter = false;
	Button okButton, cancelButton;
	VerticalPanel vp = new VerticalPanel();
	setWidget(vp);
	setText(Locale.LS("Import SPICE Netlist"));
	vp.add(new Label(Locale.LS("Paste a SPICE netlist here...")));
	vp.add(textArea = new TextArea());
	textArea.setWidth("300px");
	textArea.setHeight("200px");
	HorizontalPanel hp = new HorizontalPanel();
	vp.add(hp);
	hp.add(okButton = new Button(Locale.LS("OK")));
	okButton.addClickHandler(new ClickHandler() {
	    public void onClick(ClickEvent event) {
		String dump = convertNetlist(textArea.getText());
		if (dump == null) {
		    Window.alert(Locale.LS(lastError != null ? lastError : "Could not parse netlist"));
		    return;
		}
		sim.undoManager.pushUndo();
		closeDialog();
		sim.importCircuitFromText(dump, false);
	    }
	});
	hp.add(cancelButton = new Button(Locale.LS("Cancel")));
	cancelButton.addClickHandler(new ClickHandler() {
	    public void onClick(ClickEvent event) {
		closeDialog();
	    }
	});
	this.center();
	show();
    }

    // one parsed netlist card
    static class SpiceItem {
	char type;	// 'r','c','l','v','i','d'
	int n1, n2;	// node numbers; 0 = ground
	double value;	// ohms/farads/henries/amps
	double ic;	// initial condition (V for caps, A for inductors)
	int waveform = VoltageElm.WF_DC;
	double freq = 40, max, bias, duty = .5, phase;
    }

    static boolean isDigit(char c) { return c >= '0' && c <= '9'; }

    // parse a SPICE value with optional engineering suffix (f p n u m k meg g t)
    static double parseValue(String s0) {
	String s = s0.trim().toLowerCase();
	int n = s.length();
	if (n == 0)
	    throw new NumberFormatException("empty value");
	int i = 0;
	if (s.charAt(0) == '+' || s.charAt(0) == '-')
	    i++;
	while (i < n && (isDigit(s.charAt(i)) || s.charAt(i) == '.'))
	    i++;
	// optional exponent
	if (i < n && s.charAt(i) == 'e') {
	    int j = i+1;
	    if (j < n && (s.charAt(j) == '+' || s.charAt(j) == '-'))
		j++;
	    if (j < n && isDigit(s.charAt(j))) {
		i = j;
		while (i < n && isDigit(s.charAt(i)))
		    i++;
	    }
	}
	double base = Double.parseDouble(s.substring(0, i));
	String suf = s.substring(i);
	double mult = 1;
	if (suf.startsWith("meg"))
	    mult = 1e6;
	else if (suf.startsWith("t"))
	    mult = 1e12;
	else if (suf.startsWith("g"))
	    mult = 1e9;
	else if (suf.startsWith("k"))
	    mult = 1e3;
	else if (suf.startsWith("m"))
	    mult = 1e-3;
	else if (suf.startsWith("u"))
	    mult = 1e-6;
	else if (suf.startsWith("n"))
	    mult = 1e-9;
	else if (suf.startsWith("p"))
	    mult = 1e-12;
	else if (suf.startsWith("f"))
	    mult = 1e-15;
	return base*mult;
    }

    int nodeNum(String s, HashMap<String,Integer> map, int nextNode[]) {
	s = s.toLowerCase();
	if (s.equals("0") || s.equals("gnd") || s.equals("ground"))
	    return 0;
	Integer n = map.get(s);
	if (n == null) {
	    n = Integer.valueOf(nextNode[0]++);
	    map.put(s, n);
	}
	return n.intValue();
    }

    // split the argument list of SIN(...)/PULSE(...) found in rest (lowercased)
    static String[] sourceArgs(String rest, int idx) {
	int p1 = rest.indexOf('(', idx);
	if (p1 < 0)
	    return null;
	int p2 = rest.indexOf(')', p1);
	if (p2 < 0)
	    p2 = rest.length();
	String inner = rest.substring(p1+1, p2).trim();
	if (inner.length() == 0)
	    return new String[0];
	return inner.split("[ \t,]+");
    }

    // parse the source specification of a V card into item; returns false on failure
    boolean parseVoltageSpec(String rest, SpiceItem item) {
	try {
	    int idx = rest.indexOf("sin");
	    if (idx >= 0) {
		String args[] = sourceArgs(rest, idx);
		if (args == null)
		    return false;
		item.waveform = VoltageElm.WF_AC;
		item.bias = (args.length > 0) ? parseValue(args[0]) : 0;
		item.max  = (args.length > 1) ? parseValue(args[1]) : 0;
		if (args.length > 2) {
		    double f = parseValue(args[2]);
		    if (f > 0)
			item.freq = f;
		}
		if (args.length > 5)
		    item.phase = parseValue(args[5])*Math.PI/180;
		return true;
	    }
	    idx = rest.indexOf("pulse");
	    if (idx >= 0) {
		String args[] = sourceArgs(rest, idx);
		if (args == null || args.length < 2)
		    return false;
		double v1 = parseValue(args[0]);
		double v2 = parseValue(args[1]);
		double pw  = (args.length > 5) ? parseValue(args[5]) : 0;
		double per = (args.length > 6) ? parseValue(args[6]) : 0;
		item.waveform = VoltageElm.WF_PULSE;
		item.bias = v1;
		item.max = v2-v1;
		if (per > 0) {
		    item.freq = 1/per;
		    double duty = pw/per;
		    if (duty <= 0)
			duty = .5;
		    if (duty > .99)
			duty = .99;
		    item.duty = duty;
		}
		return true;
	    }
	    // DC value: skip "dc" keywords, take the first parseable number
	    String toks[] = rest.split("[ \t]+");
	    int i;
	    for (i = 0; i != toks.length; i++) {
		String t = toks[i];
		if (t.length() == 0 || t.equals("dc"))
		    continue;
		if (t.startsWith("ac"))
		    break;	// small-signal AC spec, not supported
		try {
		    item.waveform = VoltageElm.WF_DC;
		    item.max = parseValue(t);
		    return true;
		} catch (NumberFormatException e) {
		    return false;
		}
	    }
	} catch (Exception e) {
	}
	return false;
    }

    // convert a SPICE netlist to circuitjs dump format; returns null and sets
    // lastError on failure
    String convertNetlist(String text) {
	lastError = null;
	if (text == null)
	    text = "";

	// split lines, join "+" continuation lines
	String rawLines[] = text.split("\n");
	Vector<String> lines = new Vector<String>();
	int i;
	for (i = 0; i != rawLines.length; i++) {
	    String s = rawLines[i].replace("\r", "").trim();
	    if (s.startsWith("+") && lines.size() > 0)
		lines.set(lines.size()-1, lines.get(lines.size()-1) + " " + s.substring(1));
	    else
		lines.add(s);
	}

	Vector<SpiceItem> items = new Vector<SpiceItem>();
	HashMap<String,Integer> nodeNames = new HashMap<String,Integer>();
	int nextNode[] = new int[] { 1 };
	int unsupported = 0;
	boolean firstCard = true;

	for (i = 0; i != lines.size(); i++) {
	    String s = lines.get(i);
	    if (s.length() == 0 || s.startsWith("*"))
		continue;
	    if (s.startsWith(".")) {
		firstCard = false;
		String lower = s.toLowerCase();
		if (lower.startsWith(".end") && !lower.startsWith(".ends"))
		    break;
		continue;	// ignore .model/.tran/other dot-cards
	    }
	    String tokens[] = s.split("[ \t]+");
	    char c = Character.toUpperCase(s.charAt(0));
	    boolean looksLikeCard = "RCLVID".indexOf(c) >= 0 &&
		    tokens.length >= ((c == 'D') ? 3 : 4);
	    if (firstCard) {
		firstCard = false;
		if (!looksLikeCard)
		    continue;	// title line
	    }
	    if (!looksLikeCard) {
		unsupported++;
		continue;
	    }
	    try {
		SpiceItem item = new SpiceItem();
		item.type = Character.toLowerCase(c);
		item.n1 = nodeNum(tokens[1], nodeNames, nextNode);
		item.n2 = nodeNum(tokens[2], nodeNames, nextNode);
		int j;
		switch (c) {
		case 'R':
		    item.value = parseValue(tokens[3]);
		    break;
		case 'C':
		case 'L':
		    item.value = parseValue(tokens[3]);
		    for (j = 4; j < tokens.length; j++) {
			String t = tokens[j].toLowerCase();
			if (t.startsWith("ic="))
			    item.ic = parseValue(t.substring(3));
		    }
		    break;
		case 'D':
		    break;	// model name ignored; generic diode used
		case 'V': {
		    StringBuilder rest = new StringBuilder();
		    for (j = 3; j < tokens.length; j++) {
			if (j > 3)
			    rest.append(" ");
			rest.append(tokens[j]);
		    }
		    if (!parseVoltageSpec(rest.toString().toLowerCase(), item)) {
			unsupported++;
			continue;
		    }
		    break;
		}
		case 'I': {
		    boolean gotValue = false;
		    for (j = 3; j < tokens.length && !gotValue; j++) {
			String t = tokens[j].toLowerCase();
			if (t.length() == 0 || t.equals("dc"))
			    continue;
			try {
			    item.value = parseValue(t);
			    gotValue = true;
			} catch (NumberFormatException e) {
			    break;
			}
		    }
		    if (!gotValue) {
			unsupported++;
			continue;
		    }
		    break;
		}
		}
		items.add(item);
	    } catch (Exception e) {
		unsupported++;
	    }
	}

	if (items.isEmpty()) {
	    lastError = (unsupported > 0) ?
		    "No supported elements found in netlist (only R, C, L, V, I and D cards are supported)" :
		    "No circuit elements found in netlist";
	    return null;
	}

	return buildDump(items);
    }

    // place elements one per column, one horizontal rail per node, and
    // generate circuitjs dump text
    String buildDump(Vector<SpiceItem> items) {
	int i, j;

	// rail order: non-ground nodes in order of appearance, ground last
	Vector<Integer> railOrder = new Vector<Integer>();
	boolean groundUsed = false;
	for (i = 0; i != items.size(); i++) {
	    SpiceItem it = items.get(i);
	    int nn[] = { it.n1, it.n2 };
	    for (j = 0; j != 2; j++) {
		if (nn[j] == 0)
		    groundUsed = true;
		else if (!railOrder.contains(Integer.valueOf(nn[j])))
		    railOrder.add(Integer.valueOf(nn[j]));
	    }
	}
	if (groundUsed)
	    railOrder.add(Integer.valueOf(0));
	HashMap<Integer,Integer> railY = new HashMap<Integer,Integer>();
	for (i = 0; i != railOrder.size(); i++)
	    railY.put(railOrder.get(i), Integer.valueOf(48 + i*64));

	StringBuilder sb = new StringBuilder();
	sb.append("$ 1 0.000005 10.20027730826997 50 5 43 5e-11\n");
	HashMap<Integer,Vector<Integer>> railXs = new HashMap<Integer,Vector<Integer>>();
	int x = 64;
	int placed = 0;

	for (i = 0; i != items.size(); i++) {
	    SpiceItem it = items.get(i);
	    if (it.n1 == it.n2)
		continue;	// element shorted to itself; skip
	    // pick which node connects to point1 (post 0):
	    // for V, post 0 is the - terminal (second netlist node)
	    int p1node = (it.type == 'v') ? it.n2 : it.n1;
	    int p2node = (it.type == 'v') ? it.n1 : it.n2;
	    int y1 = railY.get(Integer.valueOf(p1node)).intValue();
	    int y2 = railY.get(Integer.valueOf(p2node)).intValue();
	    String pos = x + " " + y1 + " " + x + " " + y2;
	    switch (it.type) {
	    case 'r':
		sb.append("r " + pos + " 0 " + SpiceExporter.fmt(it.value) + "\n");
		break;
	    case 'c':
		sb.append("c " + pos + " 0 " + SpiceExporter.fmt(it.value) + " " +
			SpiceExporter.fmt(it.ic) + " " + SpiceExporter.fmt(it.ic) + "\n");
		break;
	    case 'l':
		sb.append("l " + pos + " 0 " + SpiceExporter.fmt(it.value) + " " +
			SpiceExporter.fmt(it.ic) + " " + SpiceExporter.fmt(it.ic) + " 0\n");
		break;
	    case 'd':
		sb.append("d " + pos + " 2 default\n");
		break;
	    case 'i':
		sb.append("i " + pos + " 0 " + SpiceExporter.fmt(it.value) + " 0\n");
		break;
	    case 'v':
		sb.append("v " + pos + " 20 " + it.waveform + " " + SpiceExporter.fmt(it.freq) +
			" " + SpiceExporter.fmt(it.max) + " " + SpiceExporter.fmt(it.bias) +
			" " + SpiceExporter.fmt(it.phase) + " " + SpiceExporter.fmt(it.duty) + "\n");
		break;
	    }
	    // remember connection points on each rail
	    int pn[] = { p1node, p2node };
	    for (j = 0; j != 2; j++) {
		Vector<Integer> xs = railXs.get(Integer.valueOf(pn[j]));
		if (xs == null) {
		    xs = new Vector<Integer>();
		    railXs.put(Integer.valueOf(pn[j]), xs);
		}
		xs.add(Integer.valueOf(x));
	    }
	    placed++;
	    x += 96;
	}

	if (placed == 0) {
	    lastError = "No usable elements found in netlist";
	    return null;
	}

	// connect the points on each rail with wires
	for (i = 0; i != railOrder.size(); i++) {
	    Integer node = railOrder.get(i);
	    Vector<Integer> xs = railXs.get(node);
	    if (xs == null)
		continue;
	    int y = railY.get(node).intValue();
	    for (j = 1; j < xs.size(); j++) {
		int xa = xs.get(j-1).intValue();
		int xb = xs.get(j).intValue();
		if (xa != xb)
		    sb.append("w " + xa + " " + y + " " + xb + " " + y + " 0\n");
	    }
	}

	// add a ground symbol on the ground rail
	if (groundUsed) {
	    Vector<Integer> xs = railXs.get(Integer.valueOf(0));
	    if (xs != null && xs.size() > 0) {
		int gx = xs.get(0).intValue();
		int gy = railY.get(Integer.valueOf(0)).intValue();
		sb.append("g " + gx + " " + gy + " " + gx + " " + (gy+32) + " 0 0\n");
	    }
	}

	return sb.toString();
    }
}
