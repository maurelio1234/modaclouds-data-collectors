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

/**
 * The class for mapping from an xml file to a java class.
 */
import java.util.ArrayList;

import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamAsAttribute;

@XStreamAlias("reflectionXML")
public class ReflectionXML {
	
	/**
	 * List of the metrics according to category
	 */
	@XStreamAlias("categoryList")
	private ArrayList<ReflectionCateogry> listOfCategory;
	
	/**
	 * Constructor of the class.
	 */
	public ReflectionXML() {
		
	}
	
	/**
	 * Get listOfCategory.
	 */
	public ArrayList<ReflectionCateogry> getListOfCategory() {
		return listOfCategory;
	}

	/**
	 * Set listOfCategory.
	 */
	public void setListOfCategory(ArrayList<ReflectionCateogry> listOfCategory) {
		this.listOfCategory = listOfCategory;
	}
}
@XStreamAlias("category")
class ReflectionCateogry {
	
	/**
	 * Name of the category.
	 */
	@XStreamAsAttribute
	private String categoryName;
	
	/**
	 * Monitoring resolution.
	 */
	@XStreamAsAttribute
	private String resolution;
	
	/**
	 * Get monitoring resolution.
	 */
	public String getResolution() {
		return resolution;
	}

	/**
	 * Set monitoring resolution.
	 */
	public void setResolution(String resolution) {
		this.resolution = resolution;
	}

	/**
	 * List of metrics of the category.
	 */
	@XStreamAlias("metricList")
	private ArrayList<ReflectionMetric> listOfMetrics;
	
	/**
	 * Base constructor of the class.
	 */
	public ReflectionCateogry() {
		
	}
	
	/**
	 * Constructor of the class.
	 *
	 * @param categoryName Name of the category
	 * @param resolution Monitoring resolution
	 * @param listOfMetrics List of metrics of the category
	 */
	public ReflectionCateogry(String categoryName, String resolution, ArrayList<ReflectionMetric> listOfMetrics) {
		this.categoryName = categoryName;
		this.resolution = resolution;
		this.listOfMetrics = listOfMetrics;
	}
	
	/**
	 * Get list of metrics of the category.
	 */
	public ArrayList<ReflectionMetric> getListOfMetrics() {
		return listOfMetrics;
	}
	
	/**
	 * Get the name of the category.
	 */
	public String getCategoryName() {
		return categoryName;
	}
	
	/**
	 * Set the name of the category.
	 */
	public void setCategoryName(String categoryName) {
		this.categoryName = categoryName;
	}
	/**
	 * Set the list of metrics of the category.
	 */
	public void setListOfMethods(ArrayList<ReflectionMetric> listOfMetrics) {
		this.listOfMetrics = listOfMetrics;
	}
}

@XStreamAlias("metric")
class ReflectionMetric {
	
	/**
	 * Name of the monitoring metric.
	 */
	@XStreamAsAttribute
	private String metricName;
	
	/**
	 * List of the functions to invoke.
	 */
	@XStreamAlias("functionList")
	private ArrayList<ReflectionFunction> listOfFunctions;
	
	/**
	 * Base constructor of the class.
	 */
	public ReflectionMetric() {
		
	}
	
	/**
	 * Constructor of the class.
	 *
	 * @param metricName Name of the monitoring metric
	 * @param listOfFunctions List of the functions to invoke
	 */
	public ReflectionMetric(String metricName, ArrayList<ReflectionFunction> listOfFunctions) {
		this.metricName = metricName;
		this.listOfFunctions = listOfFunctions;
	}
	
	/**
	 * Get the name of the monitoring metric.
	 */
	public String getMetricName() {
		return metricName;
	}
	
	/**
	 * Set the name of the monitoring metric.
	 */
	public void setMetricName(String metricName) {
		this.metricName = metricName;
	}

	/**
	 * Get the list of functions to invoke.
	 */
	public ArrayList<ReflectionFunction> getListOfFunctions() {
		return listOfFunctions;
	}
	/**
	 * Set the list of functions to invoke.
	 */
	public void setListOfFunctions(ArrayList<ReflectionFunction> listOfFunctions) {
		this.listOfFunctions = listOfFunctions;
	}
	
	
}

@XStreamAlias("function")
class ReflectionFunction {
	
	/**
	 * Name of the class containing the invoked function.
	 */
	@XStreamAsAttribute
	private String className;
	
	/**
	 * Name of the function to invoke.
	 */
	@XStreamAsAttribute
	private String functionName;
	
	/**
	 * ID of the each function.
	 */
	@XStreamAsAttribute
	private String functionID;
	
	/**
	 * Base constructor of the class.
	 */
	public ReflectionFunction() {
		
	}
	
	/**
	 * Constructor of the class.
	 *
	 * @param className Name of the class containing the invoked method
	 * @param functionName Name of the function to invoke
	 * @param functionID ID of the each functionID
	 */
	public ReflectionFunction(String className, String functionName, String functionID) {
		this.className = className;
		this.functionName = functionName;
		this.functionID = functionID;
	}

	/**
	 * Get the ID of each function.
	 */
	public String getFunctionID() {
		return functionID;
	}

	/**
	 * Set the ID of each function.
	 */
	public void setFunctionID(String functionID) {
		this.functionID = functionID;
	}

	/**
	 * Get the name of the class containing the invoked method.
	 */
	public String getClassName() {
		return className;
	}
	
	/**
	 * Set the name of the class containing the invoked method.
	 */
	public void setClassName(String className) {
		this.className = className;
	}

	/**
	 * Get the name of the method to invoke.
	 */
	public String getFunctionName() {
		return functionName;
	}

	/**
	 * Set the name of the method to invoke.
	 */
	public void setFunctionName(String functionName) {
		this.functionName = functionName;
	}
	
}