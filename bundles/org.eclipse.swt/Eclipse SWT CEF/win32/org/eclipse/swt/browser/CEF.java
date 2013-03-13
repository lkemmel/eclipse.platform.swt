/*******************************************************************************
 * Copyright (c) 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.swt.browser;


import java.io.*;

import org.eclipse.swt.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.internal.*;
import org.eclipse.swt.internal.cef3.*;
import org.eclipse.swt.internal.win32.*;
import org.eclipse.swt.widgets.*;

public class CEF extends WebBrowser {
	boolean ignoreDispose, creatingBrowser;
	CEFClient client;
	CEFBrowser cefBrowser;
	Object[] pendingText, pendingUrl;
	long /*int*/ windowHandle;

	static boolean LibraryLoaded;
	static CEFApp App;
	static String InitError;
	
	public static cef_string_t STRING_EMPTY;
	
	static final String ABOUT_BLANK = "about:blank"; //$NON-NLS-1$
	static final String CEF3_PATH = "org.eclipse.swt.browser.CEF3Path"; //$NON-NLS-1$
	static final String URI_FILEROOT = "file:///"; //$NON-NLS-1$
	static final int MAX_PROGRESS = 100;

	static {
		/*
		 * To use CEF3 the full path of its extracted package must be provided in the
		 * org.eclipse.swt.browser.CEF3Path java property.  If a value representing a
		 * valid directory is set for this property then add it to the library lookup
		 * path and then attempt to load the swt-cef3 library.
		 */
		
		// TODO is InitError really needed?  Some failure branches below don't set it
		String CEF3Path = System.getProperty(CEF3_PATH);
		if (CEF3Path != null && CEF3Path.length() > 0) {
			File file = new File(CEF3Path);
			if (!(file.exists() && file.isDirectory())) {
				InitError = "org.eclipse.swt.browser.CEF3Path value is not valid: " + CEF3Path;  // $NON-NLS-1$
			} else {
				TCHAR buffer = new TCHAR(0, CEF3Path, true);
				boolean success = OS.SetDllDirectory(buffer);
				if (success) {
					try {
						Library.loadLibrary("swt-cef3"); // $NON-NLS-1$

						/* initialize STRING_EMPTY */
						long /*int*/ string = CreateCEFString("");
						STRING_EMPTY = new cef_string_t();
						CEF3.memmove(STRING_EMPTY, string, cef_string_t.sizeof);

						/* initialize CEF3 */
						cef_main_args_t args = new cef_main_args_t();
						args.instance = OS.GetModuleHandle(null);
						App = new CEFApp();
						App.add_ref();
						int exitCode = CEF3.cef_execute_process(args, App.getAddress());
						if (exitCode < 0) {

							/* extract the subprocess executable */
							String NAME_SUBPROC_EXE = "swt-cef3subproc-win32.exe"; //$NON-NLS-1$
							File directory = new File(System.getProperty("user.home"), Library.SWT_LIB_DIR()); //$NON-NLS-1$
							file = new File(directory, NAME_SUBPROC_EXE);
							if (!file.exists()) {
								java.io.InputStream is = Library.class.getResourceAsStream("/" + NAME_SUBPROC_EXE); //$NON-NLS-1$
								if (is != null) {
									if (!directory.exists()) {
										directory.mkdirs();
									}
									int read;
									byte[] bytes = new byte[4096];
									try {
										FileOutputStream os = new FileOutputStream(file);
										while ((read = is.read(bytes)) != -1) {
											os.write(bytes, 0, read);
										}
										os.close();
										is.close();
									} catch (Exception e) {
									}
								}
							}

							if (!file.exists()) {
								InitError = "Failed to extract SWT's cef3 subprocess executable: " + NAME_SUBPROC_EXE; //$NON-NLS-1$
							} else {
								cef_settings_t settings = new cef_settings_t();
								settings.size = cef_settings_t.sizeof;
								settings.multi_threaded_message_loop = 1;
								settings.release_dcheck_enabled = 1;
								long /*int*/ subprocessPath = CreateCEFString(file.getAbsolutePath());
								settings.browser_subprocess_path = new cef_string_t();
								CEF3.memmove(settings.browser_subprocess_path, subprocessPath, cef_string_t.sizeof);
								App.add_ref();
								int rc = CEF3.cef_initialize(args, settings, App.getAddress());
								if (rc != 0) {
									LibraryLoaded = true;
								} else {
									InitError = "cef3 initialization failed"; //$NON-NLS-1$
								}
							}
						}
					} catch (Throwable e) {
						InitError = "Failed to load the swt-cef3 library, org.eclipse.swt.browser.CEF3Path: " + CEF3Path; //$NON-NLS-1$
					}
				}
			}
		}

		if (LibraryLoaded) {
			Display.getCurrent().addListener(SWT.Dispose,  new Listener() {
				public void handleEvent(Event event) {
					App.release();
					App = null;
				}
			});

			NativeClearSessions = new Runnable() {
				public void run() {
					if (!LibraryLoaded) return;
					// TODO do it
				}
			};

			NativeGetCookie = new Runnable() {
				public void run() {
					if (!LibraryLoaded) return;
					// TODO use CookieUrl and CookieName, set value of CookieValue
				}
			};

			NativeSetCookie = new Runnable() {
				public void run() {
					if (!LibraryLoaded) return;
					// TODO set CookieValue on CookieUrl, set CookieResult to true if successful
				}
			};

			if (NativePendingCookies != null) {
				SetPendingCookies(NativePendingCookies);
				NativePendingCookies = null;
			}
		} else {
			if (App != null) {
				App.release();
				App = null;
			}
		}
	}


static long /*int*/ CreateCEFString(String string) {
	char[] chars = new char[string.length()];
	string.getChars(0, string.length(), chars, 0);
	long /*int*/ result = CEF3.cef_string_userfree_alloc();
	CEF3.cef_string_set(chars, chars.length, result, 1);
	return result;
}

static String ExtractCEFString(long /*int*/ stringPointer) {
	if (stringPointer == 0) {
		return "";
	}
	
	cef_string_t cefStringUrl = new cef_string_t();
	CEF3.memmove(cefStringUrl, stringPointer, CEF3.cef_string_t_sizeof());

	int length = (int)/*64*/cefStringUrl.length;
	char[] chars = new char[length]; 
	OS.memmove(chars, (int)/*64*/cefStringUrl.str, length * 2); 
	return new String(chars); 
}

public static String getUrl(CEFFrame frame) {
	long /*int*/ url = frame.get_url();
	String javaStringUrl = ExtractCEFString(url); 
	
	/*
	 * If the URI indicates that the page is being rendered from memory
	 * (via setText()) then set it to about:blank to be consistent with IE.
	 */
	if (javaStringUrl.equals (URI_FILEROOT)) {
		javaStringUrl = ABOUT_BLANK;
	} else {
		int length = URI_FILEROOT.length ();
		if (javaStringUrl.startsWith (URI_FILEROOT) && javaStringUrl.charAt (length) == '#') {
			javaStringUrl = ABOUT_BLANK + javaStringUrl.substring (length);
		}
	}
	return javaStringUrl;
}



static boolean IsInstalled() {
	if (!LibraryLoaded) return false;
	// TODO if CEF3 has API to get its version then verify that it is supported, for now assume that it is
	return true;
}

public void create(Composite parent, int style) {
	cef_window_info_t windowInfo = new cef_window_info_t();
	int extStyle = OS.WS_EX_NOINHERITLAYOUT;
	if ((style & SWT.BORDER) != 0) extStyle |= OS.WS_EX_CLIENTEDGE;
	if ((style & SWT.RIGHT_TO_LEFT) != 0) extStyle |= OS.WS_EX_LAYOUTRTL;
	windowInfo.ex_style = extStyle; 
	windowInfo.style = OS.WS_CHILD | OS.WS_VISIBLE | OS.WS_CLIPSIBLINGS;
	windowInfo.x = windowInfo.width = OS.CW_USEDEFAULT;
	windowInfo.ex_style = 0; 
	windowInfo.window_name = STRING_EMPTY;
	windowInfo.parent_window = browser.handle;

	cef_browser_settings_t browserSettings = new cef_browser_settings_t();
	browserSettings.size = cef_browser_settings_t.sizeof;

	long /*int*/ url = CreateCEFString("about:blank");
	client = new CEFClient(this);
	client.add_ref();
	int rc = CEF3.cef_browser_host_create_browser(windowInfo, client.getAddress(), url, browserSettings);
	if (rc == 0) {
		return;
	}

	creatingBrowser = true;
	Listener listener = new Listener() {
		public void handleEvent(Event event) {
			switch (event.type) {
				case SWT.Dispose: {
					/* make this handler run after other dispose listeners */
					if (ignoreDispose) {
						ignoreDispose = false;
						break;
					}
					ignoreDispose = true;
					browser.notifyListeners(event.type, event);
					event.type = SWT.NONE;
					onDispose(event);
					break;
				}
				case SWT.Resize: {
					onResize();
					break;
				}
			}
		}
	};
	browser.addListener(SWT.Dispose, listener);
	// browser.addListener(SWT.Resize, listener); // TODO when SWT.EMBEDDED style is removed
	browser.addListener(SWT.KeyDown, listener); /* needed for tabbing into the Browser */
}

public boolean back() {
	if (cefBrowser == null || cefBrowser.can_go_back() == 0) return false;
	cefBrowser.go_back();
	return true;
}

public boolean close() {
	if (cefBrowser == null) return false;

	// TODO
	return false;
}

void browserCreated(long /*int*/ handle) {
	cefBrowser = new CEFBrowser(handle);
	cefBrowser.add_ref(); // TODO release() when disposed
	creatingBrowser = false;

	CEFBrowserHost host = new CEFBrowserHost(cefBrowser.get_host());
	windowHandle = host.get_window_handle();

	/* if browser content has been provided by the client then set it now */
	if (pendingText != null) {
		setText((String)pendingText[0], ((Boolean)pendingText[1]).booleanValue());
	} else if (pendingUrl != null) {
		setUrl((String)pendingUrl[0], (String)pendingUrl[1], (String[])pendingUrl[2]);
	}
	pendingText = pendingUrl = null;
}

public boolean execute(String script) {
	if (cefBrowser == null) return false;

	// TODO
	return false;
}

public boolean forward() {
	if (cefBrowser == null || cefBrowser.can_go_forward() == 0) return false;
	cefBrowser.go_forward();
	return true;
}

public String getBrowserType() {
	// TODO should this return "CEF3"?
	return "webkit"; //$NON-NLS-1$
}

public String getText() {
	if (cefBrowser == null) return "";

	// TODO
	return null;
}

public String getUrl() {
	if (cefBrowser == null) {
		/*
		 * If the browser is being created then return ABOUT_BLANK in order to be
		 * consistent with the other Browser implementations which auto-navigate
		 * to ABOUT_BLANK when opened.
		 */
		if (!creatingBrowser) return "";
		return ABOUT_BLANK;
	}

	long /*int*/ result = cefBrowser.get_main_frame();
	if (result == 0) {
		return null;
	}	
	CEFFrame frame = new CEFFrame(result);
	
	return getUrl(frame);
}

public boolean isBackEnabled() {
	return cefBrowser != null && cefBrowser.can_go_back() != 0;
}

public boolean isForwardEnabled() {
	return cefBrowser != null && cefBrowser.can_go_forward() != 0;
}

void onDispose(Event e) {
	if (client != null) {
		client.release();
	}
	client = null;
	if (cefBrowser != null) {
		cefBrowser.release();
	}
	cefBrowser = null;
	// TODO more clean up
}

void onResize() {
	Rectangle bounds = browser.getClientArea();
	OS.SetWindowPos(windowHandle, 0, bounds.x, bounds.y, bounds.width, bounds.height, OS.SWP_NOZORDER | OS.SWP_DRAWFRAME | OS.SWP_NOACTIVATE | OS.SWP_ASYNCWINDOWPOS);
}

public void onLoadComplete() {
	ProgressEvent progress = new ProgressEvent (browser);
	progress.display = browser.getDisplay ();
	progress.widget = browser;
	progress.current = MAX_PROGRESS;
	progress.total = MAX_PROGRESS;
	for (int i = 0; i < progressListeners.length; i++) {
		progressListeners[i].completed (progress);
	}
}

public void onLocationChange(String location, boolean top) {
	LocationEvent event = new LocationEvent(browser);
	event.display = browser.getDisplay ();
	event.widget = browser;
	event.location = location;
	event.top = top;
	for (int i=0; i < locationListeners.length; i++) {
		locationListeners[i].changed (event);
	}
}

public void onTitleChange(String title) {
	TitleEvent event = new TitleEvent(browser);
	event.display = browser.getDisplay ();
	event.widget = browser;
	event.title = title;
	for (int i=0; i < titleListeners.length; i++) {
		titleListeners[i].changed (event);
	}
}

public void onStatusMessage(String status) {
	StatusTextEvent event = new StatusTextEvent(browser);
	event.display = browser.getDisplay ();
	event.widget = browser;
	event.text = status;
	for (int i = 0; i < statusTextListeners.length; i++) {
		statusTextListeners[i].changed (event);
	}
}

public void refresh() {
	if (cefBrowser == null) return;
	cefBrowser.reload();
}

public boolean setText(String html, boolean trusted) {
	if (cefBrowser == null) {
		if (!creatingBrowser) return false;
		pendingText = new Object[] {html, new Boolean (trusted)};
		pendingUrl = null;
		return true;
	}

	// TODO
	return false;
}

public boolean setUrl(String url, String postData, String[] headers) {
	if (cefBrowser == null) {
		if (!creatingBrowser) return false;
		pendingText = null;
		pendingUrl = new Object[] {url, postData, headers};
		return true;
	}

	// TODO postData, headers...
	long /*int*/ result = cefBrowser.get_main_frame();
	if (result == 0) {
		return false;
	}

	CEFFrame frame = new CEFFrame(result);
	long /*int*/ urlPtr = CreateCEFString(url);
	frame.load_url(urlPtr);
	return true;
}

public void stop() {
	if (cefBrowser == null) {
		pendingText = pendingUrl = null;
		return;
	}
	cefBrowser.stop_load();
}

}
