package org.eclipse.swt.snippets;

import org.eclipse.swt.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.*;

public class Snippet285 {
	static void loadPath(Region region, float[] points, byte[] types) {
		int start = 0, end = 0;
		for (int i = 0; i < types.length; i++) {
			switch (types[i]) {
				case SWT.PATH_MOVE_TO: {
					if (start != end) {
						int n = 0;
						int[] temp = new int[end - start];
						for (int k = start; k < end; k++) {
							temp[n++] = Math.round(points[k]);
						}
						region.add(temp);
					}
					start = end;
					end += 2;
					break;
				}
				case SWT.PATH_LINE_TO: {
					end += 2;
					break;
				}
				case SWT.PATH_CLOSE: {
					if (start != end) {
						int n = 0;
						int[] temp = new int[end - start];
						for (int k = start; k < end; k++) {
							temp[n++] = Math.round(points[k]);
						}
						region.add(temp);
					}
					start = end;
					break;
				}
			}
		}
	}
	public static void main(String[] args) {
		int width = 250, height = 250;
		final Display display = new Display();
		final Shell shell = new Shell(display, SWT.NO_TRIM);
		final Path path = new Path(display);
		path.addArc(0, 0, width, height, 0, 360);
		Path path2 = new Path(display, path, 0.1f);
		path.dispose();
		PathData data = path2.getPathData();
		path2.dispose();
		Region region = new Region(display);
		loadPath(region, data.points, data.types);
		shell.setRegion(region);
		Listener listener = new Listener() {
			public void handleEvent(Event event) {
				switch (event.type) {
					case SWT.MouseDown: {
						shell.dispose();
						break;
					}
					case SWT.Paint: {
						GC gc = event.gc;
						Rectangle rect = shell.getClientArea();
						Pattern pattern = new Pattern(display, rect.x, rect.y
								+ rect.height, rect.x + rect.width, rect.y, display
								.getSystemColor(SWT.COLOR_BLUE), display
								.getSystemColor(SWT.COLOR_WHITE));
						gc.setBackgroundPattern(pattern);
						gc.fillRectangle(rect);
						pattern.dispose();
						break;
					}
				}
			}
		};
		shell.addListener(SWT.MouseDown, listener);
		shell.addListener(SWT.Paint, listener);
		shell.setSize(width, height);
		shell.open();
		while (!shell.isDisposed()) {
			if (!display.readAndDispatch())
				display.sleep();
		}
		display.dispose();
	}
	
}
