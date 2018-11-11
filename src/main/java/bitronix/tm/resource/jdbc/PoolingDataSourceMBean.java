/*
 * Copyright (C) 2006-2013 Bitronix Software (http://www.bitronix.be)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package bitronix.tm.resource.jdbc;

/**
 * @author Ludovic Orban
 */
public interface PoolingDataSourceMBean
{

	/**
	 * Method getMinPoolSize returns the minPoolSize of this PoolingDataSourceMBean object.
	 *
	 * @return the minPoolSize (type int) of this PoolingDataSourceMBean object.
	 */
	int getMinPoolSize();

	/**
	 * Method getMaxPoolSize returns the maxPoolSize of this PoolingDataSourceMBean object.
	 *
	 * @return the maxPoolSize (type int) of this PoolingDataSourceMBean object.
	 */
	int getMaxPoolSize();

	/**
	 * Method getInPoolSize returns the inPoolSize of this PoolingDataSourceMBean object.
	 *
	 * @return the inPoolSize (type int) of this PoolingDataSourceMBean object.
	 */
	int getInPoolSize();

	/**
	 * Method getTotalPoolSize returns the totalPoolSize of this PoolingDataSourceMBean object.
	 *
	 * @return the totalPoolSize (type int) of this PoolingDataSourceMBean object.
	 */
	int getTotalPoolSize();

	/**
	 * Method isFailed returns the failed of this PoolingDataSourceMBean object.
	 *
	 * @return the failed (type boolean) of this PoolingDataSourceMBean object.
	 */
	boolean isFailed();

	/**
	 * Method reset ...
	 *
	 * @throws Exception
	 * 		when
	 */
	void reset() throws Exception;

	/**
	 * Method isDisabled returns the disabled of this PoolingDataSourceMBean object.
	 *
	 * @return the disabled (type boolean) of this PoolingDataSourceMBean object.
	 */
	boolean isDisabled();

	/**
	 * Method setDisabled sets the disabled of this PoolingDataSourceMBean object.
	 *
	 * @param disabled
	 * 		the disabled of this PoolingDataSourceMBean object.
	 */
	void setDisabled(boolean disabled);

}
