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
import imperial.modaclouds.monitoring.datacollectors.basic.DataCollectorAgent;
import it.polimi.modaclouds.monitoring.dcfactory.DCConfig;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.MalformedURLException;
import java.util.Collection;
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


/**
 * The monitoring collector for Apache server log file.
 *

 */
public class LogFileMonitor extends AbstractMonitor {

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
	 * The unique monitored target.
	 */
	private String monitoredTarget;

	/**
	 * The collected metric.
	 */
	private String CollectedMetric;

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
	public LogFileMonitor (String resourceId, String mode)  {
		//this.monitoredResourceID = "FrontendVM";
		//this.monitoredTarget = monitoredResourceID;
		super(resourceId, mode);
		monitoredTarget = resourceId;
		monitorName = "logFile";

		dcAgent = DataCollectorAgent.getInstance();
	}

	@Override
	public void run() {

		long startTime = 0;

		while(!almt.isInterrupted()) {

			if (mode.equals("kb")) {

				if (System.currentTimeMillis() - startTime > 10000) {

					Collection<DCConfig> dcConfig = dcAgent.getConfiguration(resourceId,null);

					for (DCConfig dc: dcConfig) {


							if (ModacloudsMonitor.findCollector(dc.getMonitoredMetric()).equals("logfile")) {

								Map<String, String> parameters = dc.getParameters();
								
								fileName = parameters.get("fileName");
								pattern = parameters.get("pattern");
								period = Integer.valueOf(parameters.get("samplingTime"));
								samplingProb = Double.valueOf(parameters.get("samplingProbability"));
								break;
							}
						
					}
					startTime = System.currentTimeMillis();
				}
			}
			else {
				try {
					String folder = new File(".").getCanonicalPath();
					File file = new File(folder+"/config/configuration_LogFileParser.xml");

					DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
					DocumentBuilder dBuilder;
					dBuilder = dbFactory.newDocumentBuilder();
					Document doc = dBuilder.parse(file);

					doc.getDocumentElement().normalize();

					NodeList nList_jdbc = doc.getElementsByTagName("logFileParser");

					for (int i = 0; i < nList_jdbc.getLength(); i++) {

						Node nNode = nList_jdbc.item(i);

						if (nNode.getNodeType() == Node.ELEMENT_NODE) {

							Element eElement = (Element) nNode;
							fileName = eElement.getElementsByTagName("LogFileName").item(0).getTextContent();
							pattern = eElement.getElementsByTagName("Pattern").item(0).getTextContent();
							period = Integer.valueOf(eElement.getElementsByTagName("monitorPeriod").item(0).getTextContent())*1000;
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

			//DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
			//Date date = new Date();

			//String fullFileName = fileName+"."+dateFormat.format(date);
			Long t0 = System.currentTimeMillis();
			RandomAccessFile in = null;

			try {
				in = new RandomAccessFile(fileName, "r");
				String line;


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
							if (Math.random() < samplingProb) {
								dcAgent.sendSyncMonitoringDatum(temp, CollectedMetric, monitoredTarget);
							}
							//sendMonitoringDatum(Double.valueOf(temp), ResourceFactory.createResource(MC.getURI() + "ApacheLogFile"), monitoredResourceURL, monitoredResource);
						} catch (NumberFormatException e) {
							e.printStackTrace();
						} catch (Exception e) {
							e.printStackTrace();
						} 

					}	
				} 
				in.close();
			} catch (IOException e) {
				e.printStackTrace();
			} 
			
			Long t1 = System.currentTimeMillis();
			try {
				Thread.sleep(Math.max( period - (t1 - t0), 0));
			} catch (InterruptedException e) {
				try {
					in.close();
				} catch (IOException e1) {
					e1.printStackTrace();
				}
				Thread.currentThread().interrupt();
				break;
			} 
		} 
	}

	@Override
	public void start() {
		almt = new Thread( this, "alm-mon");
	}

	@Override
	public void init() {
		almt.start();
		System.out.println("Log file monitor running!");
	}

	@Override
	public void stop() {
		while (!almt.isInterrupted()) {
			almt.interrupt();
		}
		System.out.println("Log file monitor stopped!");
	}	

}
