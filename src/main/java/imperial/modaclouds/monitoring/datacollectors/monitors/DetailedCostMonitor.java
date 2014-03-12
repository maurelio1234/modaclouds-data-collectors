/**
 * Copyright (c) 2012-2013, Imperial College London, developed under the MODAClouds, FP7 ICT Project, grant agreement n�� 318484
 * All rights reserved.
 * 
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import polimi.deib.csparql_rest_api.exception.ServerErrorException;
import polimi.deib.csparql_rest_api.exception.StreamErrorException;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.util.StringUtils;

/**
 * The monitoring collector for detailed cost on EC2.
 */
public class DetailedCostMonitor extends AbstractMonitor{

	/**
	 * CloudWatch monitor thread.
	 */
	private Thread dcmt;

	/**
	 * Monitoring period.
	 */
	private int period;

	/**
	 * The Amazon S3 bucket name.
	 */
	private String bucketName;

	/**
	 * The path to keep the cost file.
	 */
	private String filePath;

	/**
	 * The cost of non spot instances.
	 */
	private Map<String,Double> cost_nonspot;

	/**
	 * The cost of spot instance.
	 */
	private Map<String,Double> cost_spot;

	/**
	 * DDa connector.
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
	public DetailedCostMonitor () throws MalformedURLException {
		this.monitoredResourceID = UUID.randomUUID().toString();
		monitorName = "detailedCost";
		ddaConnector = DDAConnector.getInstance();
	}

	@Override
	public void run() {

		String accessKeyId = null;

		String secretKey = null;

		cost_nonspot = new HashMap<String,Double>();

		cost_spot = new HashMap<String,Double>();

		try {
			String filePath = System.getProperty("user.dir") + "/config/configuration_DetailedCost.xml";
			File file = new File(filePath);
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder;
			dBuilder = dbFactory.newDocumentBuilder();
			Document doc = dBuilder.parse(file);

			doc.getDocumentElement().normalize();

			NodeList nList = doc.getElementsByTagName("detailedCost");

			for (int i = 0; i < nList.getLength(); i++) {

				Node nNode = nList.item(i);

				if (nNode.getNodeType() == Node.ELEMENT_NODE) {

					Element eElement = (Element) nNode;

					accessKeyId = eElement.getElementsByTagName("accessKey").item(0).getTextContent();
					secretKey = eElement.getElementsByTagName("secretKey").item(0).getTextContent();
					period = Integer.valueOf(eElement.getElementsByTagName("monitorPeriod").item(0).getTextContent());
					bucketName = eElement.getElementsByTagName("bucket").item(0).getTextContent();
					filePath = eElement.getElementsByTagName("filePath").item(0).getTextContent();
				}
			}
		} catch (ParserConfigurationException e1) {
			e1.printStackTrace();
		} catch (SAXException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} 

		AWSCredentials credentials = new BasicAWSCredentials(accessKeyId, secretKey);
		AmazonS3Client s3Client = new AmazonS3Client(credentials);        

		ObjectListing objects = s3Client.listObjects(bucketName);

		String key = "aws-billing-detailed-line-items-with-resources-and-tags-";
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM");
		String date = sdf.format(new Date());
		key = key+date+".csv.zip";

		while (!dcmt.isInterrupted()) {

			String fileName = null;
			do {
				for (S3ObjectSummary objectSummary : objects.getObjectSummaries()) {
					System.out.println(objectSummary.getKey() + "\t" +
							objectSummary.getSize() + "\t" +
							StringUtils.fromDate(objectSummary.getLastModified()));
					if (objectSummary.getKey().contains(key)) {
						fileName = objectSummary.getKey();
						s3Client.getObject(
								new GetObjectRequest(bucketName, fileName),
								new File(filePath+fileName)
								);
						break;
					}
				}
				objects = s3Client.listNextBatchOfObjects(objects);
			} while (objects.isTruncated());

			try {
				ZipFile zipFile = new ZipFile(filePath+fileName);
				zipFile.extractAll(filePath);
			} catch (ZipException e) {
				e.printStackTrace();
			}

			String csvFileName = fileName.replace(".zip", "");

			AnalyseFile(filePath+csvFileName);

			try {
				Thread.sleep(period);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}

		}
	}

	/**
	 * Analyze the cost file from EC2
	 * @param EC2 cost .csv file
	 */
	private void AnalyseFile(String csvFile) {
		BufferedReader br = null;
		String line = "";

		try {
			br = new BufferedReader(new FileReader(csvFile));
			line = br.readLine();
			while ((line = br.readLine()) != null) {

				if (!line.contains("Amazon Elastic Compute Cloud"))
					continue;

				Pattern p = Pattern.compile("\"([^\"]*)\"");
				Matcher m = p.matcher(line);
				List<String> temp_str = new ArrayList<String>();
				while (m.find()) {
					temp_str.add(m.group(1));
				}

				if (temp_str.size() > 18 && Double.valueOf(temp_str.get(18)) > 0) {
					if (temp_str.get(9).contains("SpotUsage")) {
						if (cost_spot.containsKey(temp_str.get(19))) {
							double temp_cost = cost_spot.get(temp_str.get(19)) + Double.valueOf(temp_str.get(18));
							cost_spot.put(temp_str.get(19), temp_cost);
						} else {
							cost_spot.put(temp_str.get(19), Double.valueOf(temp_str.get(18)));
						}
					}
					else {
						if (cost_nonspot.containsKey(temp_str.get(19))) {
							double temp_cost = cost_nonspot.get(temp_str.get(19)) + Double.valueOf(temp_str.get(18));
							cost_nonspot.put(temp_str.get(19), temp_cost);
						} else {
							cost_nonspot.put(temp_str.get(19), Double.valueOf(temp_str.get(18)));
						}
					}

				}
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		try {
			for (Map.Entry<String, Double> entry : cost_nonspot.entrySet()) {
				String key = entry.getKey();
				Double value = entry.getValue();
				
				//System.out.println("Non spot Instance id: "+key+"\tCost: "+value);
				ddaConnector.sendSyncMonitoringDatum(String.valueOf(value), "ApacheLog", monitoredResourceID);
			}
			
			for (Map.Entry<String, Double> entry : cost_spot.entrySet()) {
				String key = entry.getKey();
				Double value = entry.getValue();

				//System.out.println("Spot Instance id: "+key+"\tCost: "+value);
				ddaConnector.sendSyncMonitoringDatum(String.valueOf(value), "ApacheLog", monitoredResourceID);
			}
		} catch (ServerErrorException e) {
			e.printStackTrace();
		} catch (StreamErrorException e) {
			e.printStackTrace();
		} catch (ValidationErrorException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void start() {
		dcmt = new Thread(this, "dcm-mon");
	}

	@Override
	public void init() {
		dcmt.start();
		System.out.println("Detailed cost monitor running!");		
	}

	@Override
	public void stop() {
		while (!dcmt.isInterrupted()) {
			dcmt.interrupt();
		}
		System.out.println("Detailed cost monitor stopped!");		
	}
}
