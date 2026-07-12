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

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.RepeatingCommand;
import com.lushprojects.circuitjs1.client.util.Locale;

// Time-domain AC frequency sweep used to produce Bode plots (see FrequencyAnalysisDialog).
//
// For each (log-spaced) frequency point we set the chosen A/C source to that frequency,
// pick a timestep that gives a fixed number of steps per period, reset the circuit,
// run it for a number of settling periods, and then correlate both the source voltage
// and the output voltage against sin/cos at the source frequency over an integer number
// of periods.  That gives us input and output phasors; the magnitude in dB and the phase
// come from the complex ratio of the two.  Correlating over whole periods rejects DC
// offsets and (for the fundamental) harmonic distortion.
//
// The work is chunked with Scheduler.scheduleIncremental() so the browser stays
// responsive.  All simulator state we touch (source frequency, timestep, sim time)
// is saved and restored; the circuit itself is left reset (time = 0), just like
// pressing the Reset button.

class FrequencyAnalysis {

    interface Listener {
	void analysisProgress(int pointsDone, int totalPoints);
	// error == null means completed (or cancelled) normally
	void analysisFinished(String error);
    }

    CirSim app;
    SimulationManager sim;
    Listener listener;
    VoltageElm source;
    CircuitElm output;

    int nPoints;
    int settleCycles;
    static final int MEASURE_CYCLES = 2;
    static final int STEPS_PER_CYCLE = 128;

    // results
    double frequencies[];
    double magnitudeDB[];
    double phaseDegrees[];
    boolean valid[];
    int pointsDone;

    // saved simulator state
    double savedFrequency, savedFreqTimeZero, savedMaxTimeStep;
    boolean wasRunning;

    boolean running, cancelled;

    // per-point state
    int pointIndex, stepIndex, settleSteps, totalSteps;
    double omega;
    double outSin, outCos, inSin, inCos;

    FrequencyAnalysis(CirSim app_, Listener l, VoltageElm src, CircuitElm out,
	    double startFreq, double stopFreq, int points, int settle) {
	app = app_;
	sim = app.sim;
	listener = l;
	source = src;
	output = out;
	nPoints = points;
	settleCycles = settle;

	frequencies = new double[nPoints];
	magnitudeDB = new double[nPoints];
	phaseDegrees = new double[nPoints];
	valid = new boolean[nPoints];
	int i;
	for (i = 0; i != nPoints; i++) {
	    if (nPoints == 1)
		frequencies[i] = startFreq;
	    else
		frequencies[i] = startFreq * Math.pow(stopFreq/startFreq, i/(double)(nPoints-1));
	}
    }

    void start() {
	wasRunning = app.simIsRunning();
	app.setSimRunning(false);

	// save state we're going to mutate
	savedFrequency = source.frequency;
	savedFreqTimeZero = source.freqTimeZero;
	savedMaxTimeStep = sim.maxTimeStep;

	// make sure the circuit is analyzed and stamped so we have matrices and elmArr
	sim.analyzeCircuit();
	try {
	    sim.preStampAndStampCircuit();
	} catch (Exception e) {
	    CirSim.console("frequency analysis: exception in stampCircuit " + e);
	}
	if (app.stopMessage != null || sim.matrices == null || sim.elmArr == null) {
	    String err = (app.stopMessage != null) ? app.stopMessage : Locale.LS("Can't analyze circuit");
	    finish(err);
	    return;
	}

	running = true;
	cancelled = false;
	pointIndex = 0;
	stepIndex = 0;
	pointsDone = 0;
	settleSteps = settleCycles * STEPS_PER_CYCLE;
	totalSteps = (settleCycles + MEASURE_CYCLES) * STEPS_PER_CYCLE;

	Scheduler.get().scheduleIncremental(new RepeatingCommand() {
	    public boolean execute() {
		return doWorkChunk();
	    }
	});
    }

    void cancel() {
	cancelled = true;
	if (!running)
	    return;
    }

    boolean isRunning() { return running; }

    // do up to ~25 ms of simulation work, then yield to the browser
    boolean doWorkChunk() {
	if (!running)
	    return false;
	if (cancelled) {
	    finish(null);
	    return false;
	}
	long deadline = System.currentTimeMillis() + 25;
	while (System.currentTimeMillis() < deadline) {
	    if (pointIndex >= nPoints) {
		finish(null);
		return false;
	    }
	    if (stepIndex == 0) {
		setupPoint();
		if (app.stopMessage != null) {
		    finish(app.stopMessage);
		    return false;
		}
	    }
	    boolean failed = false;
	    int n;
	    // run a batch of timesteps
	    for (n = 0; stepIndex < totalSteps && n != 100; n++) {
		// source phase argument for this step; the voltages we solve for
		// correspond to the source evaluated at sim.t before it advances
		double wt = omega*sim.t + source.phaseShift;
		if (!doTimeStep()) {
		    failed = true;
		    break;
		}
		if (stepIndex >= settleSteps) {
		    double s = Math.sin(wt);
		    double c = Math.cos(wt);
		    double vout = getOutputVoltage();
		    double vin = source.getVoltageDiff();
		    outSin += vout*s;
		    outCos += vout*c;
		    inSin  += vin *s;
		    inCos  += vin *c;
		}
		stepIndex++;
	    }
	    if (app.stopMessage != null) {
		finish(app.stopMessage);
		return false;
	    }
	    if (failed) {
		// convergence failed at this frequency; skip the point
		valid[pointIndex] = false;
		nextPoint();
	    } else if (stepIndex >= totalSteps) {
		finishPoint();
		nextPoint();
	    }
	}
	listener.analysisProgress(pointsDone, nPoints);
	return true;
    }

    void setupPoint() {
	double freq = frequencies[pointIndex];
	omega = 2*Math.PI*freq;

	// reset circuit state so each point starts from the same initial conditions
	int i;
	for (i = 0; i != sim.elmArr.length; i++)
	    sim.elmArr[i].reset();
	sim.resetTime();

	source.frequency = freq;
	source.freqTimeZero = 0;

	// pick a timestep with a fixed number of steps per period, and restamp
	// (companion models for capacitors/inductors depend on the timestep)
	double dt = 1/(freq*STEPS_PER_CYCLE);
	sim.maxTimeStep = dt;
	sim.timeStep = dt;
	sim.stampCircuit();

	outSin = outCos = inSin = inCos = 0;
    }

    void nextPoint() {
	pointIndex++;
	pointsDone = pointIndex;
	stepIndex = 0;
    }

    void finishPoint() {
	int n = totalSteps - settleSteps;
	double ao = 2*outSin/n, bo = 2*outCos/n;
	double ai = 2*inSin /n, bi = 2*inCos /n;
	double ampOut = Math.sqrt(ao*ao + bo*bo);
	double ampIn  = Math.sqrt(ai*ai + bi*bi);
	if (ampIn < 1e-12) {
	    valid[pointIndex] = false;
	    return;
	}
	double gain = ampOut/ampIn;
	magnitudeDB[pointIndex] = 20*Math.log10(Math.max(gain, 1e-12));
	double ph = Math.toDegrees(Math.atan2(bo, ao) - Math.atan2(bi, ai));
	// normalize to (-180, 180]
	while (ph > 180)
	    ph -= 360;
	while (ph <= -180)
	    ph += 360;
	phaseDegrees[pointIndex] = ph;
	valid[pointIndex] = true;
    }

    double getOutputVoltage() {
	// LabeledNodeElm has one node; getVoltageDiff() would index past volts[]
	if (output instanceof LabeledNodeElm)
	    return output.volts[0];
	return output.getVoltageDiff();
    }

    // run one simulation timestep.  This mirrors the inner loop of
    // SimulationManager.runCircuit() without the real-time pacing, scope updates
    // or adaptive-timestep logic.  Returns false on convergence failure.
    boolean doTimeStep() {
	int i, j, subiter;
	CircuitElm elmArr[] = sim.elmArr;
	CircuitMatrix matrices[] = sim.matrices;
	if (matrices == null)
	    return false;
	for (i = 0; i != elmArr.length; i++)
	    elmArr[i].startIteration();
	final int subiterCount = 5000;
	for (subiter = 0; subiter != subiterCount; subiter++) {
	    sim.converged = true;
	    sim.subIterations = subiter;
	    for (int mi = 0; mi != matrices.length; mi++) {
		CircuitMatrix m = matrices[mi];
		for (i = 0; i != m.size; i++)
		    m.rightSide[i] = m.origRightSide[i];
		if (m.nonLinear) {
		    for (i = 0; i != m.size; i++)
			for (j = 0; j != m.size; j++)
			    m.matrix[i][j] = m.origMatrix[i][j];
		}
	    }
	    for (i = 0; i != elmArr.length; i++)
		elmArr[i].doStep();
	    if (app.stopMessage != null)
		return false;
	    for (int mi = 0; mi != matrices.length; mi++) {
		CircuitMatrix m = matrices[mi];
		if (m.nonLinear) {
		    if (sim.converged && subiter > 0)
			continue;
		    if (!SimulationManager.lu_factor(m.matrix, m.size, m.permute, m))
			return false;
		}
		SimulationManager.lu_solve(m.matrix, m.size, m.permute, m.rightSide, m);
		sim.applySolvedRightSide(m);
	    }
	    if (!sim.circuitNonLinear)
		break;
	    if (sim.converged && subiter > 0)
		break;
	}
	if (subiter == subiterCount)
	    return false;
	sim.t += sim.timeStep;
	for (i = 0; i != elmArr.length; i++)
	    elmArr[i].stepFinished();
	return true;
    }

    // restore everything we mutated and leave the circuit in a freshly reset state
    void finish(String error) {
	running = false;

	source.frequency = savedFrequency;
	source.freqTimeZero = savedFreqTimeZero;
	sim.maxTimeStep = savedMaxTimeStep;
	sim.timeStep = savedMaxTimeStep;

	if (sim.elmArr != null) {
	    int i;
	    for (i = 0; i != sim.elmArr.length; i++)
		sim.elmArr[i].reset();
	}
	sim.resetTime();

	// force a re-analysis/restamp with the restored timestep
	app.needAnalyze();
	if (app.stopMessage == null)
	    app.setSimRunning(wasRunning);
	app.repaint();
	listener.analysisFinished(error);
    }

    // build CSV of the results
    String getCSV() {
	StringBuilder sb = new StringBuilder();
	sb.append("freq,magnitude_dB,phase_deg\n");
	int i;
	for (i = 0; i != nPoints; i++) {
	    if (!valid[i] || i >= pointsDone)
		continue;
	    sb.append(frequencies[i]);
	    sb.append(",");
	    sb.append(magnitudeDB[i]);
	    sb.append(",");
	    sb.append(phaseDegrees[i]);
	    sb.append("\n");
	}
	return sb.toString();
    }
}
