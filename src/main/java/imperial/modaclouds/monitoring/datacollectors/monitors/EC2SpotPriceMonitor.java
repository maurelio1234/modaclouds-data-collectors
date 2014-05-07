/**
 * Copyright ${2014} Imperial
 * Contact: imperial <weikun.wang11@imperial.ac.uk>
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
package imperial.modaclouds.monitoring.datacollectors.monitors;

import imperial.modaclouds.monitoring.datacollectors.basic.AbstractMonitor;
import it.polimi.modaclouds.monitoring.ddaapi.DDAConnector;
import it.polimi.modaclouds.monitoring.ddaapi.ValidationErrorException;
import it.polimi.modaclouds.monitoring.kb.api.KBConnector;
import it.polimi.modaclouds.qos_models.monitoring_ontology.DataCollector;
import it.polimi.modaclouds.qos_models.monitoring_ontology.KBEntity;
import it.polimi.modaclouds.qos_models.monitoring_ontology.Parameter;

import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import polimi.deib.csparql_rest_api.exception.ServerErrorException;
import polimi.deib.csparql_rest_api.exception.StreamErrorException;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.DescribeSpotPriceHistoryRequest;
import com.amazonaws.services.ec2.model.DescribeSpotPriceHistoryResult;

/**
 * The monitoring collector for spot instance price.
 */
public class EC2SpotPriceMonitor extends AbstractMonitor {

	/**
	 * The description of spot instances.
	 */
	private class SpotInstance{
		public List<String> productDes;
		public List<String> instanceType;
		public String endpoint;
	}

	/**
	 * Spot price monitor thread.
	 */
	private Thread spmt;

	/**
	 * Monitoring period.
	 */
	private int period;

	/**
	 * List of spot instances descriptions.
	 */
	private List<SpotInstance> spotInstanceVec;

	/**
	 * Monitoring period.
	 */
	private DDAConnector ddaConnector;

	/**
	 * Knowledge base connector.
	 */
	private KBConnector kbConnector;

	/**
	 * The sampling probability
	 */
	private double samplingProb;
	/**
	 * Object store connector.
	 */
	//private ObjectStoreConnector objectStoreConnector;

	/**
	 * The unique monitored resource ID.
	 */
	private String monitoredResourceID;

	/**
	 * Constructor of the class.
	 * @throws MalformedURLException 
	 * @throws FileNotFoundException 
	 */
	public EC2SpotPriceMonitor () throws MalformedURLException, FileNotFoundException {
		this.monitoredResourceID = "FrontendVM";
		monitorName = "ec2-spotPrice";

		ddaConnector = DDAConnector.getInstance();
		kbConnector = KBConnector.getInstance();

		//ddaConnector.setDdaURL(objectStoreConnector.getDDAUrl());
	}

	@Override
	public void run() {

		String accessKeyId = null;

		String secretKey = null;

		long startTime = 0;

		while (!spmt.isInterrupted()) {

			if (System.currentTimeMillis() - startTime > 60000) {
				spotInstanceVec = new ArrayList<SpotInstance>();

				Set<KBEntity> dcConfig = kbConnector.getAll(DataCollector.class);
				for (KBEntity kbEntity: dcConfig) {
					DataCollector dc = (DataCollector) kbEntity;
					if (ModacloudsMonitor.findCollector(dc.getCollectedMetric()).equals("ec2-spotPrice")) {

						Set<Parameter> parameters = dc.getParameters();

						String endpoint = null;
						String productDes = null;
						String instanceType = null;

						for (Parameter par: parameters) {
							switch (par.getName()) {
							case "accessKey":
								accessKeyId = par.getValue();
								break;
							case "secretKey":
								secretKey = par.getValue();
								break;
							case "endPoint":
								endpoint = par.getValue();
								break;
							case "productDescription":
								productDes = par.getValue();
								break;
							case "instanceType":
								instanceType = par.getValue();
								break;
							case "samplingTime":
								period = Integer.valueOf(par.getValue());
								break;
							case "samplingProbability":
								samplingProb = Double.valueOf(par.getValue());
								break;
							}
						}

						SpotInstance spotInstance = new SpotInstance();
						spotInstance.productDes = new ArrayList<String>();
						spotInstance.instanceType = new ArrayList<String>();

						spotInstance.endpoint = endpoint;
						spotInstance.productDes.add(productDes);
						spotInstance.instanceType.add(instanceType);

						spotInstanceVec.add(spotInstance);
						break;
					}
				}
				startTime = System.currentTimeMillis();
			}

			AWSCredentials credentials = new BasicAWSCredentials(accessKeyId, secretKey);
			AmazonEC2 ec2 = new AmazonEC2Client(credentials);

			//////

			boolean isSent = false;
			if (Math.random() < this.samplingProb) {
				isSent = true;
			}

			for (int i = 0; i < spotInstanceVec.size(); i++ ) {
				ec2.setEndpoint(spotInstanceVec.get(i).endpoint);

				DescribeSpotPriceHistoryRequest request = new DescribeSpotPriceHistoryRequest();

				request.setProductDescriptions(spotInstanceVec.get(i).productDes);
				request.setInstanceTypes(spotInstanceVec.get(i).instanceType);
				//request.setStartTime(startTime);
				//request.setMaxResults(maxResult);

				String nextToken = "";
				//do {
				request.withNextToken(nextToken);

				DescribeSpotPriceHistoryResult result = ec2.describeSpotPriceHistory(request);

				List<String> splitString = Arrays.asList(result.getSpotPriceHistory().get(0).toString().split(","));

				if (isSent) {
					for (String temp : splitString) {
						if (temp.contains("SpotPrice")) {
							temp = temp.replace("SpotPrice: ", "");
							System.out.println(temp);
							try {
								ddaConnector.sendSyncMonitoringDatum(temp, "SpotPrice", monitoredResourceID);
							} catch (ServerErrorException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							} catch (StreamErrorException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							} catch (ValidationErrorException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}
					}
				}
				//for (int j = 0; j < result.getSpotPriceHistory().size(); j++) {
				//System.out.println(result.getSpotPriceHistory().get(0));
				//break;
				//}

				//result = result.withNextToken(result.getNextToken());
				//nextToken = result.getNextToken();
				//} while(!nextToken.isEmpty());
			}
			try {
				Thread.sleep(period);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}

		}
	}

	@Override
	public void start() {
		spmt = new Thread(this, "spm-mon");
	}

	@Override
	public void init() {
		// TODO Auto-generated method stub
		spmt.start();
		System.out.println("Spot history price monitor running!");
	}

	@Override
	public void stop() {
		// TODO Auto-generated method stub
		while (!spmt.isInterrupted()) {
			spmt.interrupt();
		}
		System.out.println("Spot history price monitor stopped!");
	}
}
