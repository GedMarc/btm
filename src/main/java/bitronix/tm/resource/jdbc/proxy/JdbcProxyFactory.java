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
package bitronix.tm.resource.jdbc.proxy;

import bitronix.tm.TransactionManagerServices;
import bitronix.tm.internal.BitronixRuntimeException;
import bitronix.tm.resource.jdbc.JdbcPooledConnection;
import bitronix.tm.resource.jdbc.LruStatementCache.CacheKey;
import bitronix.tm.resource.jdbc.lrc.LrcXAResource;
import bitronix.tm.utils.ClassLoaderUtils;

import javax.sql.XAConnection;
import java.sql.*;

/**
 *
 */
public interface JdbcProxyFactory
{

	/* Classes should use JdbcProxyFactory.INSTANCE to access the factory */
	JdbcProxyFactory INSTANCE = Initializer.initialize();

	/* Methods used to create the proxies around various JDBC classes */

	/**
	 * Method getProxyConnection ...
	 *
	 * @param jdbcPooledConnection
	 * 		of type JdbcPooledConnection
	 * @param connection
	 * 		of type Connection
	 *
	 * @return Connection
	 */
	Connection getProxyConnection(JdbcPooledConnection jdbcPooledConnection, Connection connection);

	/**
	 * Method getProxyStatement ...
	 *
	 * @param jdbcPooledConnection
	 * 		of type JdbcPooledConnection
	 * @param statement
	 * 		of type Statement
	 *
	 * @return Statement
	 */
	Statement getProxyStatement(JdbcPooledConnection jdbcPooledConnection, Statement statement);

	/**
	 * Method getProxyCallableStatement ...
	 *
	 * @param jdbcPooledConnection
	 * 		of type JdbcPooledConnection
	 * @param statement
	 * 		of type CallableStatement
	 *
	 * @return CallableStatement
	 */
	CallableStatement getProxyCallableStatement(JdbcPooledConnection jdbcPooledConnection, CallableStatement statement);

	/**
	 * Method getProxyPreparedStatement ...
	 *
	 * @param jdbcPooledConnection
	 * 		of type JdbcPooledConnection
	 * @param statement
	 * 		of type PreparedStatement
	 * @param cacheKey
	 * 		of type CacheKey
	 *
	 * @return PreparedStatement
	 */
	PreparedStatement getProxyPreparedStatement(JdbcPooledConnection jdbcPooledConnection, PreparedStatement statement, CacheKey cacheKey);

	/**
	 * Method getProxyResultSet ...
	 *
	 * @param statement
	 * 		of type Statement
	 * @param resultSet
	 * 		of type ResultSet
	 *
	 * @return ResultSet
	 */
	ResultSet getProxyResultSet(Statement statement, ResultSet resultSet);

	/**
	 * Method getProxyXaConnection ...
	 *
	 * @param connection
	 * 		of type Connection
	 *
	 * @return XAConnection
	 */
	XAConnection getProxyXaConnection(Connection connection);

	/**
	 * Method getProxyConnection ...
	 *
	 * @param xaResource
	 * 		of type LrcXAResource
	 * @param connection
	 * 		of type Connection
	 *
	 * @return Connection
	 */
	Connection getProxyConnection(LrcXAResource xaResource, Connection connection);

	/**
	 * Initializer class used to initialize the proxy factory.
	 */
	class Initializer
	{
		private Initializer()
		{
			//No config
		}

		/**
		 * Method initialize ...
		 *
		 * @return JdbcProxyFactory
		 */
		private static JdbcProxyFactory initialize()
		{
			try
			{
				String jdbcProxyFactoryClass = TransactionManagerServices.getConfiguration()
				                                                         .getJdbcProxyFactoryClass();
				if ("auto".equals(jdbcProxyFactoryClass))
				{
					try
					{
						ClassLoaderUtils.loadClass("javassist.CtClass");
						jdbcProxyFactoryClass = "bitronix.tm.resource.jdbc.proxy.JdbcJavassistProxyFactory";
					}
					catch (ClassNotFoundException cnfe)
					{
						try
						{
							ClassLoaderUtils.loadClass("net.sf.cglib.proxy.Enhancer");
							jdbcProxyFactoryClass = "bitronix.tm.resource.jdbc.proxy.JdbcCglibProxyFactory";
						}
						catch (ClassNotFoundException cnfe2)
						{
							jdbcProxyFactoryClass = "bitronix.tm.resource.jdbc.proxy.JdbcJavaProxyFactory";
						}
					}
				}
				Class<?> proxyFactoryClass = ClassLoaderUtils.loadClass(jdbcProxyFactoryClass);
				return (JdbcProxyFactory) proxyFactoryClass.getDeclaredConstructor()
				                                           .newInstance();
			}
			catch (Exception ex)
			{
				throw new BitronixRuntimeException("error initializing JdbcProxyFactory", ex);
			}
		}
	}
}
