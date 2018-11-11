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
package oracle.jdbc.xa;

import javax.transaction.xa.XAException;

public class OracleXAException
		extends XAException
{

	private int oracleError;

	/**
	 * Constructor OracleXAException creates a new OracleXAException instance.
	 *
	 * @param msg
	 * 		of type String
	 * @param oracleError
	 * 		of type int
	 */
	public OracleXAException(String msg, int oracleError)
	{
		super(msg);
		this.oracleError = oracleError;
	}

	/**
	 * Constructor OracleXAException creates a new OracleXAException instance.
	 *
	 * @param oracleError
	 * 		of type int
	 */
	public OracleXAException(int oracleError)
	{
		this.oracleError = oracleError;
	}

	/**
	 * Method getOracleError returns the oracleError of this OracleXAException object.
	 *
	 * @return the oracleError (type int) of this OracleXAException object.
	 */
	public int getOracleError()
	{
		return oracleError;
	}

}
