/**
 * Copyright ${year} imperial
 * Contact: imperial <weikun.wang11@imperial.ac.uk>
 *
 *    Licensed under the BSD 3-Clause License (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://opensource.org/licenses/BSD-3-Clause
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package imperial.modaclouds.monitoring.datacollectors.monitors;

import imperial.modaclouds.monitoring.datacollectors.basic.AbstractMonitor;
import it.polimi.modaclouds.monitoring.ddaapi.DDAConnector;
import it.polimi.modaclouds.monitoring.ddaapi.ValidationErrorException;
import it.polimi.modaclouds.monitoring.kb.api.KBConnector;
import it.polimi.modaclouds.qos_models.monitoring_ontology.DataCollector;
import it.polimi.modaclouds.qos_models.monitoring_ontology.KBEntity;
import it.polimi.modaclouds.qos_models.monitoring_ontology.Parameter;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import polimi.deib.csparql_rest_api.exception.ServerErrorException;
import polimi.deib.csparql_rest_api.exception.StreamErrorException;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;


/**
 * The monitoring collector for Flexiant cloud.
 */
public class FlexiMonitor extends AbstractMonitor {

	/**
	 * Flexi monitor thread.
	 */
	private Thread fmt;

	/**
	 * The channel variable.
	 */
	private Channel channel;

	/**
	 * The session variable.
	 */
	private	Session session;

	/**
	 * Monitoring period.
	 */
	private int period;

	/**
	 * DDa connector.
	 */
	private DDAConnector ddaConnector;

	/**
	 * Knowledge base connector.
	 */
	private KBConnector kbConnector;

	/**
	 * Object store connector.
	 */
	//private ObjectStoreConnector objectStoreConnector;

	/**
	 * The unique monitored target.
	 */
	private String monitoredTarget;

	/**
	 * The sampling probability.
	 */
	private double samplingProb;


	/**
	 * Constructor of the class.
	 * @throws MalformedURLException 
	 * @throws FileNotFoundException 
	 */
	public FlexiMonitor(String ownURI, String mode) throws MalformedURLException, FileNotFoundException  {
		//this.monitoredResourceID = "FrontendVM";
		//this.monitoredTarget = monitoredResourceID;
		super(ownURI, mode);
		monitorName = "flexiant";

		ddaConnector = DDAConnector.getInstance();
		kbConnector = KBConnector.getInstance();

		//ddaConnector.setDdaURL(objectStoreConnector.getDDAUrl());
	}

	@Override
	public void run() {
		String monitoredMachineAddress = null;
		String user = null;
		String password = null;
		String host = null;

		Set<KBEntity> dcConfig = kbConnector.getAll(DataCollector.class);
		for (KBEntity kbEntity: dcConfig) {
			DataCollector dc = (DataCollector) kbEntity;
			if (dc.getTargetResources().iterator().next().getUri().equals(ownURI)) {

				if (ModacloudsMonitor.findCollector(dc.getCollectedMetric()).equals("flexi")) {

					Set<Parameter> parameters = dc.getParameters();

					monitoredTarget = dc.getTargetResources().iterator().next().getUri();

					for (Parameter par: parameters) {
						switch (par.getName()) {
						case "monitoredMachineAddress":
							monitoredMachineAddress = par.getValue();
							break;
						case "user":
							user = par.getValue();
							break;
						case "password":
							password = par.getValue();
							break;
						case "host":
							host = par.getValue();
							break;
						}
					}
					break;
				}
			}
		}

		JSch jsch = new JSch();

		try {
			session = jsch.getSession(user, host, 22);

			session.setPassword(password);

			session.setConfig("StrictHostKeyChecking", "no");

			session.connect(10*1000);

			long startTime = 0;

			while (!fmt.isInterrupted()) {
				if (System.currentTimeMillis() - startTime > 60000) {
					dcConfig = kbConnector.getAll(DataCollector.class);
					for (KBEntity kbEntity: dcConfig) {
						DataCollector dc = (DataCollector) kbEntity;
						if (ModacloudsMonitor.findCollector(dc.getCollectedMetric()).equals("flexi")) {

							Set<Parameter> parameters = dc.getParameters();

							for (Parameter par: parameters) {
								switch (par.getName()) {
								case "samplingTime":
									period = Integer.valueOf(par.getValue());
									break;
								case "samplingProbability":
									samplingProb = Double.valueOf(par.getValue());
									break;
								}
							}
							break;
						}
					}
					startTime = System.currentTimeMillis();
				}


				boolean isSent = false;
				if (Math.random() < samplingProb) {
					isSent = true;
				}
				channel = session.openChannel("exec");

				String command = "snmpwalk -c public -v 1 "+monitoredMachineAddress+" .1.3.6.1.4.1.2021.51";
				//command = "ls -l";
				((ChannelExec)channel).setCommand(command);
				channel.setInputStream(null);
				((ChannelExec)channel).setErrStream(System.err);
				channel.setOutputStream(System.out);
				InputStream in = channel.getInputStream();


				channel.connect();

				BufferedReader buf = new BufferedReader(new InputStreamReader(in));
				String line = "";

				int count = 0;
				boolean isFirst = true;
				String metricName = null;

				while ((line = buf.readLine()) != null) {
					Pattern p = Pattern.compile("\"([^\"]*)\"");
					Matcher m = p.matcher(line);

					while (m.find()) {
						if (isFirst) {
							if (m.group(1).equals("NodeID")) {
								count++;
								metricName = "NodeID";
								System.out.println("NodeID");
								isFirst = false;
							}
							continue;
						}
						else {
							if (count%2 == 1) {
								try {
									if (isSent) {
										ddaConnector.sendSyncMonitoringDatum(m.group(1), metricName, monitoredTarget);
									}
								} catch (Exception e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								} 
								//sendMonitoringDatum(Double.valueOf(m.group(1)), ResourceFactory.createResource(MC.getURI() + metricName), monitoredResourceURL, monitoredResource);
								//System.out.println(metricName+"   "+m.group(1));
								count ++;
							}
							else {
								metricName = m.group(1);
								count ++;
							}
						}
					}
				}

				Thread.sleep(period);
			}

		} catch (JSchException e1) {
			e1.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (NumberFormatException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}


	}

	@Override
	public void start() {
		fmt = new Thread( this, "fm-mon");	
	}

	@Override
	public void init() {
		fmt.start();
		System.out.println("Flexiant cloud monitor running!");
	}

	@Override
	public void stop() {
		channel.disconnect();
		session.disconnect();
		while (!fmt.isInterrupted()){
			fmt.interrupt();
		}
		System.out.println("Flexiant cloud monitor stopped!");
	}

}
