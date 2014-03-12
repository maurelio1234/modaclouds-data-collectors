/**
 * Copyright (c) 2012-2013, Imperial College London, developed under the MODAClouds, FP7 ICT Project, grant agreement n�� 318484
 * All rights reserved.
 * 
 *  Contact: imperial <weikun.wang11@imperial.ac.uk>
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package imperial.modaclouds.monitoring.datacollectors.management;

import imperial.modaclouds.monitoring.datacollectors.monitors.ModacloudsMonitor;

import java.io.IOException;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.restlet.data.MediaType;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;

import com.google.gson.Gson;

/**
 * The rest call to receive the command of managing monitors.
 */
public class ManageMonitors extends ServerResource {

	@Post
	public void getCommand(Representation entity) {

		Gson gson = new Gson();
		String results = new String();

		try {
			results = entity.getText();
			
			JSONParser parser = new JSONParser();

			Object obj = parser.parse(results);

			JSONObject jsonObject = (JSONObject) obj;

			String dc = (String) jsonObject.get("DC");

			if (jsonObject.get("Activate")!=null) {

				String activate = (String) jsonObject.get("Activate");

				if (activate.equals("True")) {
					ModacloudsMonitor.runMonitoring(new String[] {dc});
				}
				else {
					ModacloudsMonitor.stopMonitoring(new String[] {dc});
				}
			}
			if (jsonObject.get("SamplingProb")!=null) {
				String reconfig = String.valueOf(jsonObject.get("SamplingProb"));

				ModacloudsMonitor.reconfigMonitor(dc, Double.valueOf(reconfig));
			}
			

		} catch (IOException e) {
			e.printStackTrace();
		} catch (ParseException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		this.getResponse().setStatus(Status.SUCCESS_OK, "Result succesfully received");
		this.getResponse().setEntity(gson.toJson("Result succesfully received"), MediaType.APPLICATION_JSON);


	}
}
