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

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.lushprojects.circuitjs1.client.util.Locale;

// dialog for "Save to Library...": prompts for a name and saves the current
// circuit to localStorage, pushing a new revision if the name already exists
public class SaveToLibraryDialog extends Dialog {

    CirSim sim;
    VerticalPanel vp;
    TextBox textBox;

    public SaveToLibraryDialog(CirSim asim) {
	super();
	sim = asim;
	Button okButton, cancelButton;
	vp = new VerticalPanel();
	setWidget(vp);
	setText(Locale.LS("Save to Library"));
	vp.add(new Label(Locale.LS("Circuit name:")));
	textBox = new TextBox();
	textBox.setWidth("250px");
	if (CircuitLibrary.currentName != null)
	    textBox.setText(CircuitLibrary.currentName);
	vp.add(textBox);
	Label note = new Label(Locale.LS("Saving to an existing name keeps the last 5 revisions."));
	note.setStyleName("topSpace");
	vp.add(note);

	HorizontalPanel hp = new HorizontalPanel();
	hp.setWidth("100%");
	hp.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_LEFT);
	hp.setStyleName("topSpace");
	vp.add(hp);
	hp.add(okButton = new Button(Locale.LS("OK")));
	hp.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_RIGHT);
	hp.add(cancelButton = new Button(Locale.LS("Cancel")));
	okButton.addClickHandler(new ClickHandler() {
	    public void onClick(ClickEvent event) {
		if (apply())
		    closeDialog();
	    }
	});
	cancelButton.addClickHandler(new ClickHandler() {
	    public void onClick(ClickEvent event) {
		closeDialog();
	    }
	});
	this.center();
	show();
    }

    boolean apply() {
	String name = textBox.getText().trim();
	if (name.length() == 0) {
	    Window.alert(Locale.LS("Please enter a name."));
	    return false;
	}
	String err = CircuitLibrary.saveCircuit(name, sim.dumpCircuit());
	if (err != null) {
	    Window.alert(err);
	    return false;
	}
	sim.unsavedChanges = false;
	return true;
    }
}
