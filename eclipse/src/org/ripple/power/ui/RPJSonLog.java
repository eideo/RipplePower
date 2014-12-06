package org.ripple.power.ui;

import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Toolkit;

import org.json.JSONObject;
import org.ripple.power.config.LSystem;
import org.ripple.power.ui.RPToast.Style;
import org.ripple.power.ui.graphics.geom.Point;

public class RPJSonLog {

	private int _limit = 2000;

	private int _count;

	private static RPJSonLog instance = null;

	public synchronized static RPJSonLog get() {
		if (instance == null) {
			instance = new RPJSonLog();
		} else if (instance.isClose()) {
			instance._tool.close();
			instance = new RPJSonLog();
		}
		return instance;
	}

	private RPPushTool _tool;

	public boolean isClose() {
		return _tool.isClose();
	}

	public void setVisible(boolean v) {
		_tool.setVisible(v);
	}

	public boolean isVisible() {
		return _tool.isVisible();
	}

	public RPJSonLog() {
		Dimension size = Toolkit.getDefaultToolkit().getScreenSize();
		Insets screenInsets = Toolkit.getDefaultToolkit().getScreenInsets(
				LSystem.applicationMain.getGraphicsConfiguration());
		Dimension panSize = new Dimension(180, 220);
		lConsole = new JConsole();
		lConsole.setPreferredSize(panSize);
		lConsole.setSize(panSize);
		_tool = RPPushTool.pop(new Point(20, size.getHeight()),
				(int) (screenInsets.bottom + lConsole.getHeight()
						+ (RPPushTool.TITLE_SIZE) + 260), "RPC", lConsole);
	}

	private JConsole lConsole;

	public void print(String line) {
		if (lConsole != null) {
			if (_count > _limit) {
				lConsole.clear();
				_count = 0;
			}
			lConsole.uiprint(line);
			_count++;
		}
	}

	public void println(JSONObject o) {
		println(o, true);
	}

	public void println(JSONObject o, boolean show) {
		if (o != null && o.has("result")) {
			JSONObject result = o.getJSONObject("result");
			if (lConsole != null) {
				if (_count > _limit) {
					lConsole.clear();
					_count = 0;
				}
				lConsole.uiprint(result + LSystem.LS);
				_count++;
			}
			if (show) {
				int engine_result_code = -1;
				if (result.has("engine_result_code")) {
					engine_result_code = result.getInt("engine_result_code");
				}
				if (result.has("engine_result_message")) {
					String engine_result_message = result
							.getString("engine_result_message");
					RPToast toast = RPToast.makeText(LSystem.applicationMain,
							"Result_message:" + engine_result_message,
							(engine_result_code == 0 ? Style.SUCCESS
									: Style.ERROR));
					toast.setDuration(6000);
					toast.display();
				}
			}
		}
	}

	public void println() {
		if (lConsole != null) {
			if (_count > _limit) {
				lConsole.clear();
				_count = 0;
			}
			lConsole.uiprint(LSystem.LS);
			_count++;
		}
	}

	public void println(String line) {
		if (lConsole != null) {
			if (_count > _limit) {
				lConsole.clear();
				_count = 0;
			}
			lConsole.uiprint(line + LSystem.LS);
			_count++;
		}
	}

	public int getLimit() {
		return _limit;
	}

	public void setLimit(int l) {
		this._limit = l;
	}

	public int getCount() {
		return _count;
	}

}
