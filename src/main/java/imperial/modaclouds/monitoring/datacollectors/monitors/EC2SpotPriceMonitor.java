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
import imperial.modaclouds.monitoring.datacollectors.basic.Config;
import imperial.modaclouds.monitoring.datacollectors.basic.ConfigurationException;
import it.polimi.tower4clouds.data_collector_library.DCAgent;
import it.polimi.tower4clouds.model.ontology.VM;

import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

	private Logger logger = LoggerFactory.getLogger(EC2SpotPriceMonitor.class);

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
	 * The sampling probability
	 */
	private double samplingProb;


	/**
	 * The unique monitored target.
	 */
	private String monitoredTarget;

	private DCAgent dcAgent;


	/**
	 * Constructor of the class.
	 * @throws MalformedURLException 
	 * @throws FileNotFoundException 
	 */
	public EC2SpotPriceMonitor (String resourceId, String mode)  {
		//this.monitoredResourceID = "FrontendVM";
		//this.monitoredTarget = monitoredResourceID;
		super(resourceId, mode);
		monitorName = "ec2-spotPrice";

		monitoredTarget = resourceId;
	}

	@Override
	public void run() {

		String accessKeyId = null;

		String secretKey = null;

		long startTime = 0;

		while (!spmt.isInterrupted()) {

			if (System.currentTimeMillis() - startTime > 10000) {
				spotInstanceVec = new ArrayList<SpotInstance>();

				for (String metric : getProvidedMetrics()) {
					try {
						VM resource = new VM(Config.getInstance().getVmType(), 
								Config.getInstance().getVmId());
						if (dcAgent.shouldMonitor(resource, metric)) {

							Map<String, String> parameters = dcAgent.getParameters(resource, metric);

							String endpoint = null;
							String productDes = null;
							String instanceType = null;

							accessKeyId = parameters.get("accessKey");
							secretKey = parameters.get("secretKey");
							endpoint = parameters.get("endPoint");
							productDes = parameters.get("productDescription");
							instanceType = parameters.get("productDescription");
							period = Integer.valueOf(parameters.get("samplingTime"))*1000;
							samplingProb = Double.valueOf(parameters.get("samplingProbability"));


							SpotInstance spotInstance = new SpotInstance();
							spotInstance.productDes = new ArrayList<String>();
							spotInstance.instanceType = new ArrayList<String>();

							spotInstance.endpoint = endpoint;
							spotInstance.productDes.add(productDes);
							spotInstance.instanceType.add(instanceType);

							spotInstanceVec.add(spotInstance);
						}
					} catch (NumberFormatException | ConfigurationException e) {
						e.printStackTrace();
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
								logger.info("Sending datum: {} {} {}",temp, "SpotPrice", monitoredTarget);
								dcAgent.send(new VM(Config.getInstance().getVmType(), 
										Config.getInstance().getVmId()), "SpotPrice", temp); 
							} catch (Exception e) {
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

	private Set<String> getProvidedMetrics() {
		Set<String> metrics = new HashSet<String>();
		metrics.add("EC2-SpotPrice");
		return metrics;
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

	@Override
	public void setDCAgent(DCAgent dcAgent) {
		this.dcAgent = dcAgent;
	}
}
