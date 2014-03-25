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
import it.polimi.modaclouds.monitoring.kb.api.KBConnector;
import it.polimi.modaclouds.monitoring.objectstoreapi.ObjectStoreConnector;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.MalformedURLException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;
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
 * The monitoring collector for Apache server log file.
 *

 */
public class ApacheLogFileMonitor extends AbstractMonitor {

	/**
	 * Extract the request information according the regular expression.
	 */
	private static Pattern requestPattern;
	
	/**
	 * Log file name.
	 */
	private String fileName;
	
	/**
	 * Apache log file monitor thread.
	 */
	private Thread almt;
	
	/**
	 * Pattern to match.
	 */
	private String pattern;
		
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
	private ObjectStoreConnector objectStoreConnector;
	
	/**
	 * The unique monitored resource ID.
	 */
	private String monitoredResourceID;
	
	/**
	 * Constructor of the class.
	 * @throws MalformedURLException 
	 */
	public ApacheLogFileMonitor () throws MalformedURLException {
		this.monitoredResourceID = UUID.randomUUID().toString();
		monitorName = "apache";
		
		ddaConnector = DDAConnector.getInstance();
		kbConnector = KBConnector.getInstance();
		objectStoreConnector = ObjectStoreConnector.getInstance();
		
		ddaConnector.setDdaURL(objectStoreConnector.getDDAUrl());
	}
	
	/**
	 * Analyse file to extract information.
	 */
	private void analyseFile(String file) throws IOException, ParseException {
		RandomAccessFile in = new RandomAccessFile(file, "r");
	    String line;
	    while(!almt.isInterrupted()) {
	    	Long t0 = System.currentTimeMillis();
	    	
	    	if((line = in.readLine()) != null) {
	    		Matcher requestMatcher = requestPattern.matcher(line);
	    		while (requestMatcher.find()) {
	    			String temp = "";
	    			for (int i = 1; i <= requestMatcher.groupCount(); i++) {
	    				temp = temp + requestMatcher.group(i);
	    				if (i != requestMatcher.groupCount())
	    					temp = temp + ", ";
	    			}
	    			//System.out.println(temp);
					try {
						if (Math.random() < this.samplingProb) {
							ddaConnector.sendSyncMonitoringDatum(temp, "ApacheLog", monitoredResourceID);
						}
						//sendMonitoringDatum(Double.valueOf(temp), ResourceFactory.createResource(MC.getURI() + "ApacheLogFile"), monitoredResourceURL, monitoredResource);
					} catch (NumberFormatException e) {
						e.printStackTrace();
					} catch (ServerErrorException e) {
						e.printStackTrace();
					} catch (StreamErrorException e) {
						e.printStackTrace();
					} catch (ValidationErrorException e) {
						e.printStackTrace();
					}

	    		}	
	    	} else {
	    		Long t1 = System.currentTimeMillis();
	    		try {
	    			Thread.sleep(Math.max( period - (t1 - t0), 0));
	    		} catch (InterruptedException e) {
	    			in.close();
	    			Thread.currentThread().interrupt();
	    			break;
	    		} 
	    	}
	    }
	}
	
	@Override
	public void run() {
		String filePath = System.getProperty("user.dir") + "/config/configuration_LogFileParser.xml";
		File file = new File(filePath);
		
		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder dBuilder;
		try {
			dBuilder = dbFactory.newDocumentBuilder();
			
			Document doc = dBuilder.parse(file);
			
			doc.getDocumentElement().normalize();
	 		 
			NodeList nList = doc.getElementsByTagName("ApacheLogFile");
			
			for (int i = 0; i < nList.getLength(); i++) {
				 
				Node nNode = nList.item(i);
		 	 
				if (nNode.getNodeType() == Node.ELEMENT_NODE) {
		 
					Element eElement = (Element) nNode;
					
					fileName = eElement.getElementsByTagName("LogFileName").item(0).getTextContent();
					pattern = eElement.getElementsByTagName("Pattern").item(0).getTextContent();
					period = Integer.valueOf(eElement.getElementsByTagName("monitorPeriod").item(0).getTextContent());

				}
			}
		} catch (ParserConfigurationException e1) {
			e1.printStackTrace();
		} catch (SAXException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}		
		
		requestPattern = Pattern.compile(pattern);
		
		DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
		Date date = new Date();
		
		String fullFileName = fileName+"."+dateFormat.format(date);
		
		try {
			analyseFile(fullFileName);
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ParseException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void start() {
		almt = new Thread( this, "alm-mon");
	}
	
	@Override
	public void init() {
		almt.start();
		System.out.println("Apache log file monitor running!");
	}

	@Override
	public void stop() {
		while (!almt.isInterrupted()) {
			almt.interrupt();
		}
		System.out.println("Apache log file monitor stopped!");
	}	

}
