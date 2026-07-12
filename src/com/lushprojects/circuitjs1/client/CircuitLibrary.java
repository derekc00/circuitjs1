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

import java.util.Collections;
import java.util.Comparator;
import java.util.Vector;

import com.google.gwt.core.client.Duration;
import com.google.gwt.storage.client.Storage;
import com.lushprojects.circuitjs1.client.util.Locale;

// Storage layer for "My Circuits": named local saves with version history.
//
// Each saved circuit is stored in localStorage under one key:
//     circuitjsLibrary.<name>
// The value is a small line-based record:
//     line 0:  format version ("1")
//     line 1+: <timestamp millis> <space> <compressed circuit dump>
// Revisions are ordered newest first; only the last MAX_REVISIONS are kept.
// Dumps are compressed with LZString (compressToEncodedURIComponent), which
// produces URI-safe output (no spaces or newlines), so the line format is safe.
public class CircuitLibrary {

    static final String KEY_PREFIX = "circuitjsLibrary.";
    static final int MAX_REVISIONS = 5;
    static final String FORMAT_VERSION = "1";

    // library name of the currently edited circuit (set when loading from or
    // saving to the library) so "Save to Library" can default to it
    static String currentName;

    static class Revision {
	double time;		// millis since epoch
	String compressed;	// LZString-compressed circuit dump
    }

    static native String compress(String dump) /*-{
	return $wnd.LZString.compressToEncodedURIComponent(dump);
    }-*/;

    static native String decompress(String dump) /*-{
	return $wnd.LZString.decompressFromEncodedURIComponent(dump);
    }-*/;

    static Storage getStorage() {
	return Storage.getLocalStorageIfSupported();
    }

    static boolean isSupported() {
	return getStorage() != null;
    }

    static String keyForName(String name) {
	return KEY_PREFIX + name;
    }

    static boolean exists(String name) {
	Storage stor = getStorage();
	return stor != null && stor.getItem(keyForName(name)) != null;
    }

    // list of saved circuit names, most recently modified first
    static Vector<String> getNames() {
	Vector<String> names = new Vector<String>();
	Storage stor = getStorage();
	if (stor == null)
	    return names;
	int i;
	for (i = 0; i != stor.getLength(); i++) {
	    String key = stor.key(i);
	    if (key != null && key.startsWith(KEY_PREFIX))
		names.add(key.substring(KEY_PREFIX.length()));
	}
	Collections.sort(names, new Comparator<String>() {
	    public int compare(String a, String b) {
		double d = getLastModified(b)-getLastModified(a);
		if (d != 0)
		    return (d > 0) ? 1 : -1;
		return a.compareTo(b);
	    }
	});
	return names;
    }

    static Vector<Revision> getRevisions(String name) {
	Vector<Revision> revs = new Vector<Revision>();
	Storage stor = getStorage();
	if (stor == null)
	    return revs;
	String val = stor.getItem(keyForName(name));
	if (val == null)
	    return revs;
	String lines[] = val.split("\n");
	int i;
	for (i = 1; i < lines.length; i++) {
	    int sp = lines[i].indexOf(' ');
	    if (sp <= 0)
		continue;
	    try {
		Revision r = new Revision();
		r.time = Double.parseDouble(lines[i].substring(0, sp));
		r.compressed = lines[i].substring(sp+1);
		revs.add(r);
	    } catch (Exception e) {
		// skip corrupted revision line
	    }
	}
	return revs;
    }

    static String formatRevisions(Vector<Revision> revs) {
	String s = FORMAT_VERSION;
	int i;
	for (i = 0; i != revs.size(); i++) {
	    Revision r = revs.get(i);
	    // print timestamp without exponent or fraction
	    s += "\n" + (long)r.time + " " + r.compressed;
	}
	return s;
    }

    static double getLastModified(String name) {
	Vector<Revision> revs = getRevisions(name);
	if (revs.size() == 0)
	    return 0;
	return revs.get(0).time;
    }

    // get the decompressed circuit dump of revision index (0 = newest), or null
    static String getRevisionDump(String name, int index) {
	Vector<Revision> revs = getRevisions(name);
	if (index < 0 || index >= revs.size())
	    return null;
	try {
	    String dump = decompress(revs.get(index).compressed);
	    if (dump == null || dump.length() == 0)
		return null;
	    return dump;
	} catch (Exception e) {
	    return null;
	}
    }

    // save dump under name, pushing a new revision.  Returns null on success
    // or a (localized) error message.
    static String saveCircuit(String name, String dump) {
	Storage stor = getStorage();
	if (stor == null)
	    return Locale.LS("Local storage is not supported in this browser.");
	Vector<Revision> revs = getRevisions(name);
	Revision r = new Revision();
	r.time = Duration.currentTimeMillis();
	r.compressed = compress(dump);
	revs.insertElementAt(r, 0);
	while (revs.size() > MAX_REVISIONS)
	    revs.remove(revs.size()-1);
	try {
	    stor.setItem(keyForName(name), formatRevisions(revs));
	} catch (Exception e) {
	    // most likely QuotaExceededError
	    return Locale.LS("Could not save circuit.  Local storage may be full.");
	}
	currentName = name;
	return null;
    }

    static void delete(String name) {
	Storage stor = getStorage();
	if (stor == null)
	    return;
	stor.removeItem(keyForName(name));
	if (name.equals(currentName))
	    currentName = null;
    }

    // rename oldName to newName (overwriting newName if present).
    // Returns null on success or a (localized) error message.
    static String rename(String oldName, String newName) {
	Storage stor = getStorage();
	if (stor == null)
	    return Locale.LS("Local storage is not supported in this browser.");
	String val = stor.getItem(keyForName(oldName));
	if (val == null)
	    return Locale.LS("Circuit not found.");
	try {
	    stor.setItem(keyForName(newName), val);
	} catch (Exception e) {
	    return Locale.LS("Could not save circuit.  Local storage may be full.");
	}
	stor.removeItem(keyForName(oldName));
	if (oldName.equals(currentName))
	    currentName = newName;
	return null;
    }

    // short relative-time string for display ("5 minutes ago" etc.)
    static String relativeTime(double time) {
	double secs = (Duration.currentTimeMillis()-time)/1000;
	if (secs < 60)
	    return Locale.LS("just now");
	if (secs < 3600) {
	    int m = (int)(secs/60);
	    return m + " " + Locale.LS(m == 1 ? "minute ago" : "minutes ago");
	}
	if (secs < 86400) {
	    int h = (int)(secs/3600);
	    return h + " " + Locale.LS(h == 1 ? "hour ago" : "hours ago");
	}
	int d = (int)(secs/86400);
	return d + " " + Locale.LS(d == 1 ? "day ago" : "days ago");
    }
}
