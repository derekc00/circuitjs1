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

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.event.dom.client.KeyUpHandler;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.TextBox;
import com.lushprojects.circuitjs1.client.util.Locale;

// Collapsible component palette docked at the left edge of the canvas.
// The entries are derived from the same catalog that builds the Draw menu
// (recorded by Menus.composeMainMenu into Menus.paletteCategories), so the
// palette and the menu can never get out of sync.
public class PaletteBar extends FlowPanel {

    static final int WIDTH = 160;
    static final int COLLAPSED_WIDTH = 22;

    UIManager ui;
    TextBox searchBox;
    FlowPanel body;
    Label collapseLabel;
    Label titleLabel;
    boolean collapsed;

    static class PaletteEntry {
	String cls;		// element class string, e.g. "ResistorElm"
	String labelLower;	// localized label, lower case (for searching)
	HTML button;
    }

    static class Section {
	Label header;
	FlowPanel items;
	boolean open;
	String title;		// localized title
	Vector<PaletteEntry> entries = new Vector<PaletteEntry>();
    }

    Vector<Section> sections = new Vector<Section>();
    HashMap<String, HTML> buttonsByClass = new HashMap<String, HTML>();
    HTML activeButton;

    PaletteBar(UIManager ui_) {
	ui = ui_;
	setStyleName("paletteBar");
	makeIconMap();

	FlowPanel topBar = new FlowPanel();
	topBar.setStyleName("paletteTop");
	collapseLabel = new Label("\u00ab");
	collapseLabel.setStyleName("paletteCollapse");
	collapseLabel.setTitle(Locale.LS("Collapse"));
	collapseLabel.addClickHandler(new ClickHandler() {
	    public void onClick(ClickEvent event) {
		setCollapsed(!collapsed);
	    }
	});
	titleLabel = new Label(Locale.LS("Components"));
	titleLabel.setStyleName("paletteTitle");
	topBar.add(titleLabel);
	topBar.add(collapseLabel);
	add(topBar);

	searchBox = new TextBox();
	searchBox.setStyleName("paletteSearch");
	searchBox.getElement().setAttribute("placeholder", Locale.LS("Search..."));
	searchBox.addKeyUpHandler(new KeyUpHandler() {
	    public void onKeyUp(KeyUpEvent event) {
		applyFilter();
	    }
	});
	add(searchBox);

	body = new FlowPanel();
	add(body);

	Vector<Menus.PaletteCategory> cats = ui.menus.paletteCategories;
	boolean first = true;
	for (Menus.PaletteCategory cat : cats) {
	    addSection(cat, first);
	    first = false;
	}
    }

    private void addSection(Menus.PaletteCategory cat, boolean open) {
	final Section s = new Section();
	s.title = Locale.LS(cat.name);
	s.open = open;
	s.header = new Label();
	s.header.setStyleName("paletteHeader");
	s.header.addClickHandler(new ClickHandler() {
	    public void onClick(ClickEvent event) {
		setSectionOpen(s, !s.open);
	    }
	});
	s.items = new FlowPanel();
	int i;
	for (i = 0; i != cat.classes.size(); i++) {
	    PaletteEntry en = makeEntry(cat.labels.get(i), cat.classes.get(i));
	    s.entries.add(en);
	    s.items.add(en.button);
	}
	body.add(s.header);
	body.add(s.items);
	sections.add(s);
	setSectionOpen(s, s.open);
    }

    private PaletteEntry makeEntry(String englishLabel, final String cls) {
	final PaletteEntry en = new PaletteEntry();
	String label = Locale.LS(englishLabel);
	en.cls = cls;
	en.labelLower = label.toLowerCase();
	String icon = iconMap.get(cls);
	String iconHtml = (icon == null) ? "" : Toolbar.makeSvg(icon, 18);
	final HTML b = new HTML("<span class=\"paletteIcon\">" + iconHtml +
		"</span><span class=\"paletteLabel\">" + SafeHtmlUtils.htmlEscape(label) + "</span>");
	b.setStyleName("paletteButton");
	b.setTitle(label);
	b.addClickHandler(new ClickHandler() {
	    public void onClick(ClickEvent event) {
		// clicking the active button again returns to Select mode,
		// like the toolbar buttons
		if (b == activeButton)
		    new MyCommand("main", "Select").execute();
		else
		    new MyCommand("main", cls).execute();
	    }
	});
	en.button = b;
	buttonsByClass.put(cls, b);
	return en;
    }

    void setSectionOpen(Section s, boolean open) {
	s.open = open;
	s.items.setVisible(open);
	s.header.setText((open ? "\u25be " : "\u25b8 ") + s.title);
    }

    void applyFilter() {
	String q = searchBox.getText().trim().toLowerCase();
	boolean searching = q.length() > 0;
	for (Section s : sections) {
	    int matches = 0;
	    for (PaletteEntry en : s.entries) {
		boolean m = !searching || en.labelLower.contains(q);
		en.button.setVisible(m);
		if (m)
		    matches++;
	    }
	    s.header.setVisible(matches > 0);
	    // when searching, show all matching items even in closed sections
	    s.items.setVisible(matches > 0 && (searching || s.open));
	    if (!searching)
		setSectionOpen(s, s.open);
	}
    }

    // highlight the button matching the current mouse mode (or none)
    void highlightButton(String cls) {
	if (activeButton != null)
	    activeButton.removeStyleName("paletteButtonActive");
	activeButton = buttonsByClass.get(cls);
	if (activeButton != null)
	    activeButton.addStyleName("paletteButtonActive");
    }

    void setCollapsed(boolean c) {
	collapsed = c;
	searchBox.setVisible(!c);
	body.setVisible(!c);
	titleLabel.setVisible(!c);
	collapseLabel.setText(c ? "\u00bb" : "\u00ab");
	collapseLabel.setTitle(c ? Locale.LS("Expand") : Locale.LS("Collapse"));
	ui.setPaletteCollapsed(c);
    }

    // mini-icon map for the most common components; reuses the toolbar's
    // hand-drawn SVG schematic symbols.  Entries without an icon just show
    // their name.
    static HashMap<String, String> iconMap;

    static void makeIconMap() {
	if (iconMap != null)
	    return;
	iconMap = new HashMap<String, String>();
	iconMap.put("WireElm", Toolbar.wireIcon);
	iconMap.put("ResistorElm", Toolbar.resistorIcon);
	iconMap.put("GroundElm", Toolbar.groundIcon);
	iconMap.put("CapacitorElm", Toolbar.capacitorIcon);
	iconMap.put("InductorElm", Toolbar.inductIcon);
	iconMap.put("DiodeElm", Toolbar.diodeIcon);
	iconMap.put("SwitchElm", Toolbar.switchIcon);
	iconMap.put("Switch2Elm", Toolbar.spdtIcon);
	iconMap.put("DPDTSwitchElm", Toolbar.dpdtIcon);
	iconMap.put("AnalogSwitchElm", Toolbar.aswitch1Icon);
	iconMap.put("AnalogSwitch2Elm", Toolbar.aswitch2Icon);
	iconMap.put("DCVoltageElm", Toolbar.voltage2Icon);
	iconMap.put("ACVoltageElm", Toolbar.acSrcIcon);
	iconMap.put("RailElm", Toolbar.railIcon);
	iconMap.put("NTransistorElm", Toolbar.transistorIcon);
	iconMap.put("PTransistorElm", Toolbar.pnpTransistorIcon);
	iconMap.put("NMosfetElm", Toolbar.fetIcon);
	iconMap.put("PMosfetElm", Toolbar.fetIcon2);
	iconMap.put("OpAmpElm", Toolbar.opAmpBotIcon);
	iconMap.put("OpAmpSwapElm", Toolbar.opAmpTopIcon);
	iconMap.put("InverterElm", Toolbar.inverterIcon);
	iconMap.put("AndGateElm", Toolbar.andIcon);
	iconMap.put("OrGateElm", Toolbar.orIcon);
	iconMap.put("NandGateElm", Toolbar.nandIcon);
	iconMap.put("NorGateElm", Toolbar.norIcon);
	iconMap.put("XorGateElm", Toolbar.xorIcon);
    }
}
