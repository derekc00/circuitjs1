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
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.lushprojects.circuitjs1.client.util.Locale;

// dialog showing the scope's sample data as CSV, with copy-to-clipboard and download
// buttons.  Modeled on ExportAsTextDialog.
public class ScopeCSVExportDialog extends Dialog {

    VerticalPanel vp;
    TextArea textArea;

    public ScopeCSVExportDialog(Scope scope) {
	super();
	closeOnEnter = false;
	Button okButton, copyButton, downloadButton;
	vp = new VerticalPanel();
	setWidget(vp);
	setText(Locale.LS("Export as CSV"));
	vp.add(new Label(Locale.LS("CSV data for this scope (min and max sample values for each time step)...")));
	vp.add(textArea = new TextArea());
	textArea.setWidth("400px");
	textArea.setHeight("300px");
	textArea.setText(scope.buildCSV());
	HorizontalPanel hp = new HorizontalPanel();
	hp.setWidth("100%");
	hp.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_LEFT);
	hp.setStyleName("topSpace");
	vp.add(hp);
	hp.add(okButton = new Button(Locale.LS("OK")));
	hp.add(copyButton = new Button(Locale.LS("Copy to Clipboard")));
	hp.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_RIGHT);
	hp.add(downloadButton = new Button(Locale.LS("Download")));
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
		Scope.downloadCSV(textArea.getText(), "scope-data.csv");
	    }
	});
	this.center();
    }

    private static native boolean copyToClipboard() /*-{
	return $doc.execCommand('copy');
    }-*/;
}
