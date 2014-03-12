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
package imperial.modaclouds.monitoring.datacollectors.monitors;

import imperial.modaclouds.monitoring.datacollectors.basic.AbstractMonitor;
import it.polimi.modaclouds.monitoring.ddaapi.DDAConnector;
import it.polimi.modaclouds.monitoring.ddaapi.ValidationErrorException;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

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
	 * The unique monitored resource ID.
	 */
	private String monitoredResourceID;

	/**
	 * Constructor of the class.
	 * @throws MalformedURLException 
	 */
	public EC2SpotPriceMonitor () throws MalformedURLException {
		this.monitoredResourceID = UUID.randomUUID().toString();
		monitorName = "ec2-spotPrice";
		ddaConnector = DDAConnector.getInstance();
	}

	@Override
	public void run() {

		String accessKeyId = null;

		String secretKey = null;

		spotInstanceVec = new ArrayList<SpotInstance>();

		try {
			String filePath = System.getProperty("user.dir") + "/config/configuration_SpotPrice.xml";
			File file = new File(filePath);

			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder;
			dBuilder = dbFactory.newDocumentBuilder();
			Document doc = dBuilder.parse(file);

			doc.getDocumentElement().normalize();

			NodeList nList = doc.getElementsByTagName("spotPrice");

			for (int i = 0; i < nList.getLength(); i++) {

				Node nNode = nList.item(i);

				if (nNode.getNodeType() == Node.ELEMENT_NODE) {

					Element eElement = (Element) nNode;

					accessKeyId = eElement.getElementsByTagName("accessKey").item(0).getTextContent();
					secretKey = eElement.getElementsByTagName("secretKey").item(0).getTextContent();
					period = Integer.valueOf(eElement.getElementsByTagName("monitorPeriod").item(0).getTextContent());
					//maxResult = Integer.valueOf(eElement.getElementsByTagName("maxResult").item(0).getTextContent());
					//String startTime_str = eElement.getElementsByTagName("startTime").item(0).getTextContent();
					//SimpleDateFormat sdf = new SimpleDateFormat("dd-M-yyyy");
					//startTime = sdf.parse(startTime_str);
				}
			}

			NodeList nList_instance = doc.getElementsByTagName("instance");

			for (int temp = 0; temp < nList_instance.getLength(); temp++) {

				Node nNode = nList_instance.item(temp);
				SpotInstance spotInstance = new SpotInstance();
				spotInstance.productDes = new ArrayList<String>();
				spotInstance.instanceType = new ArrayList<String>();


				if (nNode.getNodeType() == Node.ELEMENT_NODE) {

					Element eElement = (Element) nNode;

					spotInstance.endpoint = eElement.getElementsByTagName("endPoint").item(0).getTextContent();
					spotInstance.productDes.add(eElement.getElementsByTagName("productDescription").item(0).getTextContent());
					spotInstance.instanceType.add(eElement.getElementsByTagName("instanceType").item(0).getTextContent());
				}	
				spotInstanceVec.add(spotInstance);
			}

		} catch (ParserConfigurationException e1) {
			e1.printStackTrace();
		} catch (SAXException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} 

		AWSCredentials credentials = new BasicAWSCredentials(accessKeyId, secretKey);
		AmazonEC2 ec2 = new AmazonEC2Client(credentials);

		while (!spmt.isInterrupted()) {
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
