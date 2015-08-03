package org.ripple.power.txns.data;

import org.json.JSONObject;

public class MarketDepthBidsResponse {
	public int id;
	public Bids result = new Bids();
	public String status;
	public String type;

	public void from(JSONObject obj) {
		if (obj != null) {
			this.id = obj.optInt("id");
			this.status = obj.optString("status");
			this.type = obj.optString("type");
			this.result.from(obj.optJSONObject("result"));
		}
	}
}
