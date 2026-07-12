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

import java.util.ArrayList;

// small overview of the whole circuit drawn in the top-right corner of the canvas
// (the bottom-right corner is used by the info box).  Shows simplified element
// geometry (wires as thin lines, other elements as small rects) plus the current
// viewport; clicking or dragging inside it re-centers the viewport.
public class Minimap {

    UIManager ui;
    CirSim app;

    static final int MAX_WIDTH = 160;
    static final int MAX_HEIGHT = 120;
    static final int MARGIN = 10;	// distance from canvas corner
    static final int PAD = 4;		// inner padding so edge content is visible

    // screen-space rect of the minimap as of the last frame
    int mapX, mapY, mapWidth, mapHeight;
    boolean visible;

    // mapping from grid coords to minimap coords:
    // screenX = mapX+PAD + (gridX-worldX)*mapScale
    double worldX, worldY, mapScale;

    Minimap(UIManager ui) {
	this.ui = ui;
	this.app = ui.app;
    }

    boolean isEnabled() {
	return ui.menus.minimapCheckItem != null && ui.menus.minimapCheckItem.getState();
    }

    // is the (screen-space) point over the visible minimap?
    boolean contains(int x, int y) {
	return visible && x >= mapX && x < mapX+mapWidth && y >= mapY && y < mapY+mapHeight;
    }

    int mapCoordX(double gx) { return mapX+PAD + (int)((gx-worldX)*mapScale); }
    int mapCoordY(double gy) { return mapY+PAD + (int)((gy-worldY)*mapScale); }

    // re-center the viewport on the grid point under minimap screen coords (sx, sy)
    void navigateTo(int sx, int sy) {
	if (!visible || mapScale <= 0)
	    return;
	double gx = worldX + (sx-(mapX+PAD))/mapScale;
	double gy = worldY + (sy-(mapY+PAD))/mapScale;
	app.transform[4] = app.circuitArea.width /2 - gx*app.transform[0];
	app.transform[5] = app.circuitArea.height/2 - gy*app.transform[3];
	ui.repaint();
    }

    // draw the minimap; the graphics context must be in (unzoomed) screen space
    void draw(Graphics g) {
	visible = false;
	if (!isEnabled())
	    return;
	Rectangle bounds = ui.getCircuitBounds();
	if (bounds == null)
	    return;	// empty circuit, nothing to navigate

	MouseManager mouse = ui.mouse;
	if (mouse == null || app.transform[0] == 0)
	    return;

	// viewport in grid coordinates
	int vx1 = mouse.inverseTransformX(0);
	int vy1 = mouse.inverseTransformY(0);
	int vx2 = mouse.inverseTransformX(app.circuitArea.width);
	int vy2 = mouse.inverseTransformY(app.circuitArea.height);

	// hide when the whole circuit is already visible
	if (vx1 <= bounds.x && vy1 <= bounds.y &&
	    vx2 >= bounds.x+bounds.width && vy2 >= bounds.y+bounds.height)
	    return;

	// world rect covers circuit and viewport so the viewport marker always fits
	int wx1 = Math.min(bounds.x, vx1);
	int wy1 = Math.min(bounds.y, vy1);
	int wx2 = Math.max(bounds.x+bounds.width,  vx2);
	int wy2 = Math.max(bounds.y+bounds.height, vy2);
	int ww = Math.max(wx2-wx1, 1);
	int wh = Math.max(wy2-wy1, 1);

	worldX = wx1;
	worldY = wy1;
	mapScale = Math.min((MAX_WIDTH -2.*PAD)/ww, (MAX_HEIGHT-2.*PAD)/wh);
	mapWidth  = (int)(ww*mapScale) + 2*PAD;
	mapHeight = (int)(wh*mapScale) + 2*PAD;
	mapX = app.circuitArea.width - mapWidth - MARGIN;
	mapY = MARGIN;
	if (mapX < 0 || mapY+mapHeight > app.circuitArea.height)
	    return;	// canvas too small
	visible = true;

	boolean printable = ui.menus.printableCheckItem.getState();
	g.context.save();
	g.context.setGlobalAlpha(0.85);
	g.setColor(printable ? "#eee" : "#111");
	g.fillRect(mapX, mapY, mapWidth, mapHeight);
	g.context.restore();
	g.setColor(printable ? "#999" : "#666");
	g.drawRect(mapX, mapY, mapWidth, mapHeight);

	// simplified content: wires as thin lines, other elements as small rects
	String elmColor = printable ? "#555" : "#aaa";
	g.setColor(elmColor);
	g.setLineWidth(1.0);
	for (CircuitElm ce : ui.elmList) {
	    if (ce instanceof RoutedWireElm) {
		ArrayList<Point> pts = ((RoutedWireElm) ce).routePoints;
		if (pts != null && pts.size() >= 2) {
		    for (int i = 0; i < pts.size()-1; i++)
			g.drawLine(mapCoordX(pts.get(i).x), mapCoordY(pts.get(i).y),
				   mapCoordX(pts.get(i+1).x), mapCoordY(pts.get(i+1).y));
		    continue;
		}
	    }
	    if (ce instanceof WireElm) {
		g.drawLine(mapCoordX(ce.x), mapCoordY(ce.y), mapCoordX(ce.x2), mapCoordY(ce.y2));
		continue;
	    }
	    Rectangle bb = ce.getBoundingBox();
	    if (bb == null)
		continue;
	    int rx = mapCoordX(bb.x);
	    int ry = mapCoordY(bb.y);
	    int rw = Math.max((int)(bb.width *mapScale), 2);
	    int rh = Math.max((int)(bb.height*mapScale), 2);
	    g.fillRect(rx, ry, rw, rh);
	}

	// current viewport
	g.setColor(CircuitElm.selectColor);
	g.drawRect(mapCoordX(vx1), mapCoordY(vy1),
		   Math.max((int)((vx2-vx1)*mapScale), 2), Math.max((int)((vy2-vy1)*mapScale), 2));
    }
}
