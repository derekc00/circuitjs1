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

import java.util.Date;
import java.util.Vector;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.i18n.client.DateTimeFormat;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.lushprojects.circuitjs1.client.util.Locale;

// dialog for "My Circuits...": lists circuits saved in the local library,
// with Load/Rename/Delete per entry and an expandable revision history
public class CircuitLibraryDialog extends Dialog {

    CirSim sim;
    VerticalPanel vp;
    FlexTable table;
    ScrollPanel scrollPanel;
    Label emptyLabel;
    String expandedName;	// name whose history is currently expanded

    public CircuitLibraryDialog(CirSim asim) {
	super();
	sim = asim;
	closeOnEnter = false;
	vp = new VerticalPanel();
	setWidget(vp);
	setText(Locale.LS("My Circuits"));

	if (!CircuitLibrary.isSupported())
	    vp.add(new Label(Locale.LS("Local storage is not supported in this browser.")));

	emptyLabel = new Label(Locale.LS("No saved circuits.  Use File > Save to Library to add one."));
	vp.add(emptyLabel);

	table = new FlexTable();
	table.setCellSpacing(4);
	scrollPanel = new ScrollPanel(table);
	scrollPanel.setWidth("500px");
	scrollPanel.setHeight("300px");
	vp.add(scrollPanel);
	buildList();

	HorizontalPanel hp = new HorizontalPanel();
	hp.setWidth("100%");
	hp.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_RIGHT);
	hp.setStyleName("topSpace");
	vp.add(hp);
	Button closeButton = new Button(Locale.LS("Close"));
	hp.add(closeButton);
	closeButton.addClickHandler(new ClickHandler() {
	    public void onClick(ClickEvent event) {
		closeDialog();
	    }
	});
	this.center();
	show();
    }

    void buildList() {
	table.removeAllRows();
	Vector<String> names = CircuitLibrary.getNames();
	emptyLabel.setVisible(names.size() == 0);
	scrollPanel.setVisible(names.size() > 0);
	int row = 0;
	int i;
	for (i = 0; i != names.size(); i++) {
	    final String name = names.get(i);
	    Label nameLabel = new Label(name);
	    nameLabel.getElement().getStyle().setProperty("fontWeight", "bold");
	    table.setWidget(row, 0, nameLabel);
	    table.setWidget(row, 1, new Label(CircuitLibrary.relativeTime(CircuitLibrary.getLastModified(name))));

	    Button loadButton = new Button(Locale.LS("Load"));
	    loadButton.addClickHandler(new ClickHandler() {
		public void onClick(ClickEvent event) {
		    loadRevision(name, 0);
		}
	    });
	    table.setWidget(row, 2, loadButton);

	    Button renameButton = new Button(Locale.LS("Rename"));
	    renameButton.addClickHandler(new ClickHandler() {
		public void onClick(ClickEvent event) {
		    doRename(name);
		}
	    });
	    table.setWidget(row, 3, renameButton);

	    Button deleteButton = new Button(Locale.LS("Delete"));
	    deleteButton.addClickHandler(new ClickHandler() {
		public void onClick(ClickEvent event) {
		    if (Window.confirm(Locale.LS("Delete this circuit and all its revisions?") + " (" + name + ")")) {
			CircuitLibrary.delete(name);
			if (name.equals(expandedName))
			    expandedName = null;
			buildList();
		    }
		}
	    });
	    table.setWidget(row, 4, deleteButton);

	    final boolean expanded = name.equals(expandedName);
	    Button historyButton = new Button(Locale.LS(expanded ? "Hide History" : "History"));
	    historyButton.addClickHandler(new ClickHandler() {
		public void onClick(ClickEvent event) {
		    expandedName = expanded ? null : name;
		    buildList();
		}
	    });
	    table.setWidget(row, 5, historyButton);
	    row++;

	    if (expanded)
		row = addHistoryRows(name, row);
	}
    }

    int addHistoryRows(final String name, int row) {
	Vector<CircuitLibrary.Revision> revs = CircuitLibrary.getRevisions(name);
	DateTimeFormat dtf = DateTimeFormat.getFormat("yyyy-MM-dd HH:mm:ss");
	int j;
	for (j = 0; j != revs.size(); j++) {
	    final int revIndex = j;
	    String when = dtf.format(new Date((long)revs.get(j).time));
	    Label revLabel = new Label(when + (j == 0 ? " (" + Locale.LS("latest") + ")" : ""));
	    revLabel.getElement().getStyle().setProperty("paddingLeft", "20px");
	    table.setWidget(row, 1, revLabel);
	    Button loadRevButton = new Button(Locale.LS("Load"));
	    loadRevButton.addClickHandler(new ClickHandler() {
		public void onClick(ClickEvent event) {
		    loadRevision(name, revIndex);
		}
	    });
	    table.setWidget(row, 2, loadRevButton);
	    row++;
	}
	return row;
    }

    void loadRevision(String name, int index) {
	String dump = CircuitLibrary.getRevisionDump(name, index);
	if (dump == null) {
	    Window.alert(Locale.LS("Could not load circuit from local storage."));
	    return;
	}
	sim.undoManager.pushUndo();
	closeDialog();
	sim.importCircuitFromText(dump, false);
	// mark circuit as coming from the library so Save to Library defaults to this name
	CircuitLibrary.currentName = name;
	sim.setCircuitTitle(name);
    }

    void doRename(String name) {
	String newName = Window.prompt(Locale.LS("New name:"), name);
	if (newName == null)
	    return;
	newName = newName.trim();
	if (newName.length() == 0 || newName.equals(name))
	    return;
	if (CircuitLibrary.exists(newName) &&
		!Window.confirm(Locale.LS("A circuit with this name already exists.  Overwrite it?")))
	    return;
	String err = CircuitLibrary.rename(name, newName);
	if (err != null) {
	    Window.alert(err);
	    return;
	}
	if (name.equals(expandedName))
	    expandedName = newName;
	buildList();
    }
}
