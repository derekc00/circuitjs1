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

import java.util.HashSet;
import java.util.Vector;

import com.google.gwt.canvas.client.Canvas;
import com.google.gwt.canvas.dom.client.Context2d;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.lushprojects.circuitjs1.client.util.Locale;

// Dialog for the "Frequency Response..." (AC sweep / Bode plot) feature.
// Lets the user pick an A/C voltage source as input and a probe/labeled node/
// analog output as output, runs a time-domain sweep (see FrequencyAnalysis)
// and draws magnitude (dB) and phase (degrees) vs log frequency.

class FrequencyAnalysisDialog extends Dialog implements FrequencyAnalysis.Listener {

    CirSim app;
    ListBox sourceBox, outputBox;
    TextBox startFreqBox, stopFreqBox, pointsBox, cyclesBox;
    Button runButton, exportButton, closeButton;
    Label statusLabel;
    Canvas canvas;
    Vector<VoltageElm> sources;
    Vector<CircuitElm> outputs;
    FrequencyAnalysis analysis;

    static final int CANVAS_WIDTH = 640;
    static final int CANVAS_HEIGHT = 400;

    FrequencyAnalysisDialog(CirSim app_) {
	super();
	app = app_;
	closeOnEnter = false;
	setText(Locale.LS("Frequency Response"));
	VerticalPanel vp = new VerticalPanel();
	setWidget(vp);

	findSources();
	findOutputs();

	HorizontalPanel hp = new HorizontalPanel();
	hp.setStyleName("topSpace");
	hp.add(new Label(Locale.LS("Input Source") + ": "));
	sourceBox = new ListBox();
	int i;
	for (i = 0; i != sources.size(); i++)
	    sourceBox.addItem(sourceName(sources.get(i), i));
	hp.add(sourceBox);
	hp.add(new Label("   " + Locale.LS("Output") + ": "));
	outputBox = new ListBox();
	for (i = 0; i != outputs.size(); i++)
	    outputBox.addItem(outputName(outputs.get(i)));
	hp.add(outputBox);
	vp.add(hp);

	hp = new HorizontalPanel();
	hp.setStyleName("topSpace");
	hp.add(new Label(Locale.LS("Start Frequency (Hz)") + ": "));
	startFreqBox = new TextBox();
	startFreqBox.setText("20");
	startFreqBox.setVisibleLength(8);
	hp.add(startFreqBox);
	hp.add(new Label("   " + Locale.LS("Stop Frequency (Hz)") + ": "));
	stopFreqBox = new TextBox();
	stopFreqBox.setText("20000");
	stopFreqBox.setVisibleLength(8);
	hp.add(stopFreqBox);
	vp.add(hp);

	hp = new HorizontalPanel();
	hp.setStyleName("topSpace");
	hp.add(new Label(Locale.LS("Number of Points") + ": "));
	pointsBox = new TextBox();
	pointsBox.setText("30");
	pointsBox.setVisibleLength(5);
	hp.add(pointsBox);
	hp.add(new Label("   " + Locale.LS("Settling Cycles") + ": "));
	cyclesBox = new TextBox();
	cyclesBox.setText("5");
	cyclesBox.setVisibleLength(5);
	hp.add(cyclesBox);
	vp.add(hp);

	canvas = Canvas.createIfSupported();
	if (canvas != null) {
	    canvas.setWidth(CANVAS_WIDTH + "px");
	    canvas.setHeight(CANVAS_HEIGHT + "px");
	    canvas.setCoordinateSpaceWidth(CANVAS_WIDTH);
	    canvas.setCoordinateSpaceHeight(CANVAS_HEIGHT);
	    vp.add(canvas);
	}

	statusLabel = new Label("");
	vp.add(statusLabel);

	hp = new HorizontalPanel();
	hp.setStyleName("topSpace");
	hp.add(runButton = new Button(Locale.LS("Run Analysis")));
	hp.add(exportButton = new Button(Locale.LS("Export CSV")));
	hp.add(closeButton = new Button(Locale.LS("Close")));
	vp.add(hp);

	runButton.addClickHandler(new ClickHandler() {
	    public void onClick(ClickEvent event) { startAnalysis(); }
	});
	exportButton.addClickHandler(new ClickHandler() {
	    public void onClick(ClickEvent event) { exportCSV(); }
	});
	closeButton.addClickHandler(new ClickHandler() {
	    public void onClick(ClickEvent event) { closeDialog(); }
	});
	exportButton.setEnabled(false);

	if (sources.isEmpty()) {
	    statusLabel.setText(Locale.LS("No A/C voltage source found.  Add an A/C voltage source (sine waveform) to use as the sweep input."));
	    runButton.setEnabled(false);
	} else if (outputs.isEmpty()) {
	    statusLabel.setText(Locale.LS("No output found.  Add a voltmeter/scope probe, labeled node or analog output to measure."));
	    runButton.setEnabled(false);
	}

	drawPlot();
	this.center();
    }

    void findSources() {
	sources = new Vector<VoltageElm>();
	int i;
	for (i = 0; i != app.elmList.size(); i++) {
	    CircuitElm ce = app.getElm(i);
	    if (ce instanceof VoltageElm && ((VoltageElm) ce).waveform == VoltageElm.WF_AC)
		sources.add((VoltageElm) ce);
	}
    }

    void findOutputs() {
	outputs = new Vector<CircuitElm>();
	HashSet<String> labelsSeen = new HashSet<String>();
	int i;
	for (i = 0; i != app.elmList.size(); i++) {
	    CircuitElm ce = app.getElm(i);
	    if (ce instanceof ProbeElm || ce instanceof OutputElm)
		outputs.add(ce);
	    else if (ce instanceof LabeledNodeElm) {
		LabeledNodeElm ln = (LabeledNodeElm) ce;
		if (ln.isInternal() || labelsSeen.contains(ln.text))
		    continue;
		labelsSeen.add(ln.text);
		outputs.add(ce);
	    }
	}
    }

    String sourceName(VoltageElm v, int idx) {
	String type = (v instanceof RailElm) ? Locale.LS("A/C Source (1-terminal)") : Locale.LS("A/C Source");
	return type + " " + (idx+1) + " (" +
	    CircuitElm.getShortUnitText(v.maxVoltage, "V") + ", " +
	    CircuitElm.getShortUnitText(v.frequency, "Hz") + ")";
    }

    String outputName(CircuitElm ce) {
	if (ce instanceof LabeledNodeElm)
	    return Locale.LS("Labeled Node") + " \"" + ((LabeledNodeElm) ce).text + "\"";
	String type = (ce instanceof ProbeElm) ? Locale.LS("Probe") : Locale.LS("Analog Output");
	return type + " (" + ce.x + "," + ce.y + ")";
    }

    void startAnalysis() {
	if (analysis != null && analysis.isRunning())
	    return;
	double f0, f1;
	int points, cycles;
	try {
	    f0 = Double.parseDouble(startFreqBox.getText());
	    f1 = Double.parseDouble(stopFreqBox.getText());
	    points = Integer.parseInt(pointsBox.getText().trim());
	    cycles = Integer.parseInt(cyclesBox.getText().trim());
	} catch (NumberFormatException e) {
	    statusLabel.setText(Locale.LS("Invalid input"));
	    return;
	}
	if (!(f0 > 0) || !(f1 > f0)) {
	    statusLabel.setText(Locale.LS("Stop frequency must be greater than start frequency, and both must be positive"));
	    return;
	}
	if (points < 2 || points > 500) {
	    statusLabel.setText(Locale.LS("Number of points must be between 2 and 500"));
	    return;
	}
	if (cycles < 1 || cycles > 1000) {
	    statusLabel.setText(Locale.LS("Settling cycles must be between 1 and 1000"));
	    return;
	}
	int si = sourceBox.getSelectedIndex();
	int oi = outputBox.getSelectedIndex();
	if (si < 0 || oi < 0)
	    return;
	VoltageElm src = sources.get(si);
	CircuitElm out = outputs.get(oi);
	// make sure the elements are still in the circuit
	if (app.locateElm(src) < 0 || app.locateElm(out) < 0) {
	    statusLabel.setText(Locale.LS("Selected element was deleted; reopen this dialog"));
	    return;
	}
	runButton.setEnabled(false);
	exportButton.setEnabled(false);
	statusLabel.setText(Locale.LS("Running") + "...");
	analysis = new FrequencyAnalysis(app, this, src, out, f0, f1, points, cycles);
	analysis.start();
    }

    public void analysisProgress(int pointsDone, int totalPoints) {
	statusLabel.setText(Locale.LS("Running") + "... " + pointsDone + "/" + totalPoints);
	drawPlot();
    }

    public void analysisFinished(String error) {
	runButton.setEnabled(true);
	if (error != null)
	    statusLabel.setText(Locale.LS("Analysis failed") + ": " + Locale.LS(error));
	else if (analysis != null && countValid() == 0)
	    statusLabel.setText(Locale.LS("Analysis produced no valid points (check that the output is connected)"));
	else
	    statusLabel.setText(Locale.LS("Done"));
	exportButton.setEnabled(analysis != null && countValid() > 0);
	drawPlot();
    }

    int countValid() {
	if (analysis == null)
	    return 0;
	int i, count = 0;
	for (i = 0; i != analysis.pointsDone; i++)
	    if (analysis.valid[i])
		count++;
	return count;
    }

    void exportCSV() {
	if (analysis == null)
	    return;
	Scope.downloadCSV(analysis.getCSV(), "bode.csv");
    }

    public void closeDialog() {
	if (analysis != null && analysis.isRunning())
	    analysis.cancel();
	super.closeDialog();
    }

    // ---- plot drawing ----

    static final String COL_BG = "#101010";
    static final String COL_GRID = "#333333";
    static final String COL_GRID_MINOR = "#222222";
    static final String COL_TEXT = "#bbbbbb";
    static final String COL_MAG = "#7ef17e";
    static final String COL_PHASE = "#ffb84d";
    static final String COL_3DB = "#ff6666";

    void drawPlot() {
	if (canvas == null)
	    return;
	Context2d g = canvas.getContext2d();
	int w = CANVAS_WIDTH, h = CANVAS_HEIGHT;
	g.setFillStyle(COL_BG);
	g.fillRect(0, 0, w, h);

	int left = 52, right = 56, top = 24, bottom = 36;
	int pw = w-left-right, ph = h-top-bottom;

	g.setFont("12px sans-serif");

	if (countValid() == 0) {
	    g.setStrokeStyle(COL_GRID);
	    g.strokeRect(left, top, pw, ph);
	    g.setFillStyle(COL_TEXT);
	    String msg = Locale.LS("Run the analysis to see the frequency response");
	    g.fillText(msg, left + (pw - g.measureText(msg).getWidth())/2, top+ph/2);
	    return;
	}

	int n = analysis.pointsDone;
	double lf0 = log10(analysis.frequencies[0]);
	double lf1 = log10(analysis.frequencies[analysis.nPoints-1]);
	if (lf1 <= lf0)
	    lf1 = lf0+1;

	// magnitude range over valid points, padded and rounded to 5 dB
	double magMin = Double.MAX_VALUE, magMax = -Double.MAX_VALUE;
	int i;
	for (i = 0; i != n; i++) {
	    if (!analysis.valid[i])
		continue;
	    if (analysis.magnitudeDB[i] < magMin) magMin = analysis.magnitudeDB[i];
	    if (analysis.magnitudeDB[i] > magMax) magMax = analysis.magnitudeDB[i];
	}
	double peakMag = magMax;
	magMax = Math.ceil((magMax+2)/5)*5;
	magMin = Math.floor((magMin-2)/5)*5;
	if (magMax - magMin < 10)
	    magMin = magMax-10;

	// vertical gridlines at decades, minor lines at 2..9
	int e0 = (int) Math.floor(lf0);
	int e1 = (int) Math.ceil(lf1);
	int e, m;
	for (e = e0; e <= e1; e++) {
	    for (m = 1; m <= 9; m++) {
		double f = m*Math.pow(10, e);
		double lf = log10(f);
		if (lf < lf0-1e-9 || lf > lf1+1e-9)
		    continue;
		int x = left + (int) Math.round((lf-lf0)/(lf1-lf0)*pw);
		g.setStrokeStyle(m == 1 ? COL_GRID : COL_GRID_MINOR);
		drawLine(g, x, top, x, top+ph);
		if (m == 1) {
		    String lbl = CircuitElm.getShortUnitText(f, "Hz");
		    g.setFillStyle(COL_TEXT);
		    g.fillText(lbl, x - g.measureText(lbl).getWidth()/2, top+ph+16);
		}
	    }
	}

	// horizontal magnitude gridlines
	double magStep = pickStep(magMax-magMin);
	double mv;
	for (mv = magMin; mv <= magMax+1e-9; mv += magStep) {
	    int y = yMag(mv, magMin, magMax, top, ph);
	    g.setStrokeStyle(COL_GRID);
	    drawLine(g, left, y, left+pw, y);
	    g.setFillStyle(COL_MAG);
	    String lbl = showFormat(mv);
	    g.fillText(lbl, left-6-g.measureText(lbl).getWidth(), y+4);
	}

	// phase axis labels on the right (-180..180)
	int p;
	for (p = -180; p <= 180; p += 90) {
	    int y = yPhase(p, top, ph);
	    g.setFillStyle(COL_PHASE);
	    g.fillText(p + "°", left+pw+6, y+4);
	}

	// -3 dB line relative to the peak gain
	double m3 = peakMag-3;
	if (m3 > magMin && m3 < magMax) {
	    int y = yMag(m3, magMin, magMax, top, ph);
	    g.setStrokeStyle(COL_3DB);
	    Graphics.setLineDash(g, 4, 4);
	    drawLine(g, left, y, left+pw, y);
	    Graphics.setLineDash(g, 0, 0);
	    g.setFillStyle(COL_3DB);
	    g.fillText("-3 dB", left+4, y-4);
	}

	// frame
	g.setStrokeStyle(COL_GRID);
	g.strokeRect(left, top, pw, ph);

	// phase curve
	g.setStrokeStyle(COL_PHASE);
	g.setLineWidth(1.5);
	g.beginPath();
	boolean started = false;
	for (i = 0; i != n; i++) {
	    if (!analysis.valid[i])
		continue;
	    double lf = log10(analysis.frequencies[i]);
	    double x = left + (lf-lf0)/(lf1-lf0)*pw;
	    double y = yPhase(analysis.phaseDegrees[i], top, ph);
	    if (!started) { g.moveTo(x, y); started = true; }
	    else g.lineTo(x, y);
	}
	g.stroke();

	// magnitude curve
	g.setStrokeStyle(COL_MAG);
	g.setLineWidth(2);
	g.beginPath();
	started = false;
	for (i = 0; i != n; i++) {
	    if (!analysis.valid[i])
		continue;
	    double lf = log10(analysis.frequencies[i]);
	    double x = left + (lf-lf0)/(lf1-lf0)*pw;
	    double y = yMag(analysis.magnitudeDB[i], magMin, magMax, top, ph);
	    if (!started) { g.moveTo(x, y); started = true; }
	    else g.lineTo(x, y);
	}
	g.stroke();
	g.setLineWidth(1);

	// legend
	g.setFillStyle(COL_MAG);
	g.fillText(Locale.LS("Magnitude (dB)"), left, top-8);
	g.setFillStyle(COL_PHASE);
	String phLbl = Locale.LS("Phase (degrees)");
	g.fillText(phLbl, left+pw-g.measureText(phLbl).getWidth(), top-8);
    }

    static double log10(double x) { return Math.log(x)/Math.log(10); }

    int yMag(double v, double magMin, double magMax, int top, int ph) {
	return top + (int) Math.round((magMax-v)/(magMax-magMin)*ph);
    }

    int yPhase(double v, int top, int ph) {
	return top + (int) Math.round((180-v)/360.*ph);
    }

    static double pickStep(double range) {
	double steps[] = { 1, 2, 5, 10, 20, 40, 60, 100 };
	int i;
	for (i = 0; i != steps.length; i++)
	    if (range/steps[i] <= 8)
		return steps[i];
	return 100;
    }

    static String showFormat(double v) {
	// avoid long decimals from floating point noise
	double r = Math.round(v*100)/100.;
	if (r == Math.floor(r))
	    return "" + (int) r;
	return "" + r;
    }

    static void drawLine(Context2d g, int x1, int y1, int x2, int y2) {
	g.beginPath();
	g.moveTo(x1, y1);
	g.lineTo(x2, y2);
	g.stroke();
    }
}
