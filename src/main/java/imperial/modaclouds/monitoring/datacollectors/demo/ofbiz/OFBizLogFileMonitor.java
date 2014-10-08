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
package imperial.modaclouds.monitoring.datacollectors.demo.ofbiz;
import imperial.modaclouds.monitoring.datacollectors.basic.AbstractMonitor;
import imperial.modaclouds.monitoring.datacollectors.basic.DataCollectorAgent;
import imperial.modaclouds.monitoring.datacollectors.monitors.ModacloudsMonitor;
import it.polimi.modaclouds.monitoring.dcfactory.DCConfig;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

/**
 * Response time monitor.
 */
public class OFBizLogFileMonitor extends AbstractMonitor{

	/**
	 * Extract the request information according the regular expression.
	 */
	private static Pattern requestPattern;

	/**
	 * Log file name.
	 */
	private String fileName;

	/**
	 * Response time monitor thread.
	 */
	private Thread olmt;

	/**
	 * Pattern to match.
	 */
	private String pattern;

	/**
	 * Monitoring period.
	 */
	private int period;


	/**
	 * The unique monitored target.
	 */
	private String monitoredTarget;

	/**
	 * The sampling probability.
	 */
	private double samplingProb;

	private DataCollectorAgent dcAgent;

	/**
	 * Constructor of the class.
	 * @throws MalformedURLException 
	 * @throws FileNotFoundException 
	 */
	public OFBizLogFileMonitor (String resourceId, String mode ) {
		//this.monitoredResourceID = "FrontendVM";
		//this.monitoredTarget = monitoredResourceID;

		super(resourceId, mode);
		
		monitoredTarget = resourceId;

		monitorName = "ofbiz";
		dcAgent = DataCollectorAgent.getInstance();;
	}

	/**
	 * Analyze file to extract information.
	 */
	private void analyseFile(File file) throws IOException, ParseException, ServerErrorException, StreamErrorException {
		if (!file.exists())
			return;

		FileInputStream input = new FileInputStream(file);
		BufferedReader br = new BufferedReader(new InputStreamReader(input));

		String line;
		while ((line = br.readLine()) != null) {
			Matcher requestMatcher = requestPattern.matcher(line);
			String requestType = null;
			String responseTime = null;
			while (requestMatcher.find()) {
				String temp = "";
				for (int i = 1; i <= requestMatcher.groupCount(); i++) {
					temp = temp + requestMatcher.group(i);
					if (i != requestMatcher.groupCount())
						temp = temp + ",";
					if (i == requestMatcher.groupCount() - 2)
						requestType = requestMatcher.group(i);
					if (i == requestMatcher.groupCount())
						responseTime = requestMatcher.group(i);
				}

				//				Resource requestedOperation = null;
				//				switch(requestType) {
				//					case "getAssociatedStateList":
				//						requestedOperation = MC.getAssociatedStateList;
				//						break;
				//					case "newcustomer":
				//						requestedOperation = MC.newcustomer;
				//						break;
				//					case "main":
				//						requestedOperation = MC.main;
				//						break;
				//					case "createcustomer":
				//						requestedOperation = MC.createcustomer;
				//						break;
				//					case "processorder":
				//						requestedOperation = MC.processorder;
				//						break;
				//					case "logout":
				//						requestedOperation = MC.logout;
				//						break;
				//					case "checkLogin":
				//						requestedOperation = MC.checkLogin;
				//						break;
				//					case "login":
				//						requestedOperation = MC.login;
				//						break;
				//					case "quickadd":
				//						requestedOperation = MC.quickadd;
				//						break;
				//					case "addtocartbulk":
				//						requestedOperation = MC.addtocartbulk;
				//						break;
				//					case "checkoutoptions":
				//						requestedOperation = MC.checkoutoptions;
				//						break;
				//					case "orderhistory":
				//						requestedOperation = MC.orderhistory;
				//						break;
				//					case "orderstatus":
				//						requestedOperation = MC.orderstatus;
				//						break;
				//				}

				//				String streamIri = "http://ec2-54-216-144-180.eu-west-1.compute.amazonaws.com:8175/streams/dc2sda";
				//				Model m = ModelFactory.createDefaultModel();
				//				m.add(new ResourceImpl(streamIri+"/"+String.valueOf(System.currentTimeMillis())), new PropertyImpl(streamIri+"/ResponseInfo"), new ResourceImpl(streamIri+"/"+temp));
				//				
				//				try {
				//					csparql_api.feedStream("http://ec2-54-216-144-180.eu-west-1.compute.amazonaws.com:8175/streams/dc2sda", m);
				//				} catch (StreamErrorException | ServerErrorException e) {
				//					e.printStackTrace();
				//				}

				//				String urlToRequestedOperation = monitoredResourceURL + "/" + requestedOperation.getLocalName();
				//				Request request = DDAFactory.createRequest(urlToRequestedOperation, requestedOperation);
				//				
				//				try {
				//						sendEvent(MC.SuccessfulRequestEvent, request);
				//				} catch (ConnectionErrorException e) {
				//					System.err.println("Failed to deliver event: "
				//							+ e.getMessage());
				//				}

				try {
					if (Math.random() < samplingProb) {
						dcAgent.sendSyncMonitoringDatum(temp, "ResponseInfo", monitoredTarget);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}		
		}	
		br.close();
		input.close();
	}

	@Override
	public void run() {
		long startTime = 0;

		while (!olmt.isInterrupted()) {

			if (mode.equals("kb")) {

				if (System.currentTimeMillis() - startTime > 60000) {

					Collection<DCConfig> dcConfig = dcAgent.getConfiguration(resourceId,null);

					for (DCConfig dc: dcConfig) {

							if (ModacloudsMonitor.findCollector(dc.getMonitoredMetric()).equals("ofbiz")) {

								Map<String, String> parameters = dc.getParameters();

								fileName = parameters.get("logFileName");
								pattern = parameters.get("pattern");
								period = Integer.valueOf(parameters.get("samplingTime"));
								samplingProb = Double.valueOf(parameters.get("samplingProbability"));
								
								
								if (pattern == null) {
									pattern = "(19|20\\d{2})-(0?[1-9]|1[012])-(0?[1-9]|[12]\\d|3[01]) ([01]?\\d|2[0-3]):([0-5]\\d):([0-5]\\d),(\\d{3}).*\\((http.+?)\\).*\\[\\[\\[(.+?)\\(Domain.*(Request Done).*total:(.*),";
								}
								break;
							}
						
					}
					startTime = System.currentTimeMillis();
				}
			}
			else {
				String folder = null;
				try {
					folder = new File(".").getCanonicalPath();
				} catch (IOException e2) {
					e2.printStackTrace();
				}
				File file_xml = new File(folder+"/config/configuration_LogFileParser.xml");
				
				DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
				DocumentBuilder dBuilder;
				try {
					dBuilder = dbFactory.newDocumentBuilder();
					
					Document doc = dBuilder.parse(file_xml);
					
					doc.getDocumentElement().normalize();
			 		 
					NodeList nList = doc.getElementsByTagName("OFBizLogFile");
					for (int i = 0; i < nList.getLength(); i++) {
						 
						Node nNode = nList.item(i);
				 	 
						if (nNode.getNodeType() == Node.ELEMENT_NODE) {
				 
							Element eElement = (Element) nNode;
							
							fileName = eElement.getElementsByTagName("LogFileName").item(0).getTextContent();
							pattern = eElement.getElementsByTagName("Pattern").item(0).getTextContent();
							period = Integer.valueOf(eElement.getElementsByTagName("monitorPeriod").item(0).getTextContent());
							samplingProb = 1;
						}
					}
				} catch (ParserConfigurationException e1) {
					e1.printStackTrace();
				} catch (SAXException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}		
			}

			requestPattern = Pattern.compile(pattern);

			final File file = new File(fileName);

			String absolutePath = file.getAbsolutePath();
			File dir = new File(absolutePath.substring(0, absolutePath.lastIndexOf(File.separator)));
			File[] files = null;



			try {	
				Long t0 = System.currentTimeMillis();

				files = dir.listFiles(new FilenameFilter() {
					@Override
					public boolean accept(File dir, String name) {
						return name.startsWith(file.getName()+".");
					}
				});

				if (files.length == 0) {
					Long t1 = System.currentTimeMillis();
					try {
						Thread.sleep(Math.max( period - (t1 - t0), 0));
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					continue;
				}

				Arrays.sort(files, Collections.reverseOrder());
				for (int i = 0; i < files.length; i++) {
					try {
						analyseFile(files[i]);
					} catch (ServerErrorException e) {
						e.printStackTrace();
					} catch (StreamErrorException e) {
						e.printStackTrace();
					}

					if(files[i].delete()){
						System.out.println(files[i].getName() + " is deleted!");
					}else{
						System.out.println("Delete operation is failed.");
					}
				}

				Long t1 = System.currentTimeMillis();
				try {
					Thread.sleep(Math.max( period - (t1 - t0), 0));
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			} catch (IOException e) {
				e.printStackTrace();
			} catch (ParseException e) {
				e.printStackTrace();
			}
		}

	} 

	@Override
	public void start() {
		olmt = new Thread( this, "olm-mon");
	}

	@Override
	public void init() {
		olmt.start();
		System.out.println("OFBiz monitor running!");
	}

	@Override
	public void stop() {	
		while (!olmt.isInterrupted()) {
			olmt.interrupt();
		}

		System.out.println("OFBiz monitor stopped!");
	}
}