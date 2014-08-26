package imperial.modaclouds.monitoring.datacollectors.monitors;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.MalformedURLException;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
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

import imperial.modaclouds.monitoring.datacollectors.basic.AbstractMonitor;
import it.polimi.modaclouds.monitoring.ddaapi.DDAConnector;
import it.polimi.modaclouds.monitoring.kb.api.KBConnector;
import it.polimi.modaclouds.qos_models.monitoring_ontology.DataCollector;
import it.polimi.modaclouds.qos_models.monitoring_ontology.KBEntity;
import it.polimi.modaclouds.qos_models.monitoring_ontology.Parameter;

public class HaproxyLogMonitor extends AbstractMonitor {
	
	/**
	 * Extract the request information according the regular expression.
	 */
	private static Pattern requestPattern;
	
	/**
	 * DDa connector.
	 */
	private DDAConnector ddaConnector;

	/**
	 * Knowledge base connector.
	 */
	private KBConnector kbConnector;
	
	/**
	 * The unique monitored target.
	 */
	private String monitoredTarget;

	/**
	 * Monitoring period.
	 */
	private int period;
	
	/**
	 * Log file name.
	 */
	private String fileName;
	
	/**
	 * File position.
	 */
	private long filePointer;
	
	/**
	 * Response time monitor thread.
	 */
	private Thread hamt;

	public HaproxyLogMonitor(String ownURI, String mode) throws MalformedURLException, FileNotFoundException {
		super(ownURI, mode);
		
		monitorName = "haproxy";
		ddaConnector = DDAConnector.getInstance();
		kbConnector = KBConnector.getInstance();
		monitoredTarget = ownURI;
	}

	@Override
	public void run() {
		long startTime = 0;

		while (!hamt.isInterrupted()) {

			if (mode.equals("kb")) {

				if (System.currentTimeMillis() - startTime > 60000) {

					Set<KBEntity> dcConfig = kbConnector.getAll(DataCollector.class);
					for (KBEntity kbEntity: dcConfig) {
						DataCollector dc = (DataCollector) kbEntity;

						if (dc.getTargetResources().iterator().next().getUri().equals(ownURI)) {

							if (ModacloudsMonitor.findCollector(dc.getCollectedMetric()).equals("haproxy")) {

								Set<Parameter> parameters = dc.getParameters();

								//monitoredTarget = dc.getTargetResources().iterator().next().getId();
								
								for (Parameter par: parameters) {
									switch (par.getName()) {
									case "logFileName":
										fileName = par.getValue();
										break;
									case "samplingTime":
										period = Integer.valueOf(par.getValue());
										break;
									}
								}
								break;
							}
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
				File file_xml = new File(folder+"/config/configuration_Haproxy.xml");
				
				DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
				DocumentBuilder dBuilder;
				try {
					dBuilder = dbFactory.newDocumentBuilder();
					
					Document doc = dBuilder.parse(file_xml);
					
					doc.getDocumentElement().normalize();
			 		 
					NodeList nList = doc.getElementsByTagName("haproxy");
					for (int i = 0; i < nList.getLength(); i++) {
						 
						Node nNode = nList.item(i);
				 	 
						if (nNode.getNodeType() == Node.ELEMENT_NODE) {
				 
							Element eElement = (Element) nNode;
							
							fileName = eElement.getElementsByTagName("LogFileName").item(0).getTextContent();
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
			
			RandomAccessFile file = null;
			Long t0 = (long) 0;
			
			try {	
				t0 = System.currentTimeMillis();

				file = new RandomAccessFile(fileName, "r");

				if (file.length() < filePointer) {
					file = new RandomAccessFile(fileName, "r");
					filePointer = 0;
				}
				else {
					file.seek(filePointer);
					String line;
					
					while ( (line = file.readLine()) != null) {
						if (line.contains("JSESSIONID")) {
							System.out.println(line);
							//ddaConnector.sendSyncMonitoringDatum(line, "HaproxyLog", monitoredTarget);
							ddaConnector.sendAsyncMonitoringDatum(line, "CPUUtilization", monitoredTarget);
						}
					}
					
					filePointer = file.getFilePointer();
				}
				
				file.close();
				
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				Long t1 = System.currentTimeMillis();
				try {
					Thread.sleep(Math.max( period - (t1 - t0), 0));
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}

	}

	@Override
	public void start() {
		hamt = new Thread( this, "ham-mon");		
	}

	@Override
	public void init() {
		hamt.start();
		System.out.println("Haproxy monitor running!");		
	}

	@Override
	public void stop() {
		while (!hamt.isInterrupted()) {
			hamt.interrupt();
		}

		System.out.println("Haproxy monitor stopped!");		
	}
	
}
