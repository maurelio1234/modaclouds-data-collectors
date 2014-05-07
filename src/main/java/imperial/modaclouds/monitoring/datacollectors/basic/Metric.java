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
package imperial.modaclouds.monitoring.datacollectors.basic;

public class Metric {
	
	/**
	 * The name of the metric.
	 */
	private String metricName;
		
	/**
	 * The sampling probability.
	 */
	private double samplingProb;

	/**
	 * Get the name of the metric.
	 * @return the name of the metric.
	 */
	public String getMetricName() {
		return metricName;
	}

	/**
	 * Set the name of the metric.
	 * @param metricName	the name of the metric.
	 */
	public void setMetricName(String metricName) {
		this.metricName = metricName;
	}

	/**
	 * Get the sampling probability.
	 * @return	the sampling probability.
	 */
	public double getSamplingProb() {
		return samplingProb;
	}

	/**
	 * Set the sampling probability
	 * @param samplingProb	the sampling probability
	 */
	public void setSamplingProb(double samplingProb) {
		this.samplingProb = samplingProb;
	}
	
	
	
}
