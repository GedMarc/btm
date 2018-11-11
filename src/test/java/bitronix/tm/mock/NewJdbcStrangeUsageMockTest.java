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
package bitronix.tm.mock;

import bitronix.tm.BitronixTransactionManager;
import bitronix.tm.TransactionManagerServices;
import bitronix.tm.internal.LogDebugCheck;
import bitronix.tm.mock.events.*;
import bitronix.tm.resource.common.XAPool;

import javax.transaction.Status;
import javax.transaction.Transaction;
import javax.transaction.xa.XAResource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * @author Ludovic Orban
 */
public class NewJdbcStrangeUsageMockTest
		extends AbstractMockJdbcTest
{

	private final static java.util.logging.Logger log = java.util.logging.Logger.getLogger(NewJdbcStrangeUsageMockTest.class.toString());


	/**
	 * Method testDeferredReuse ...
	 *
	 * @throws Exception
	 * 		when
	 */
	public void testDeferredReuse() throws Exception
	{
		if (LogDebugCheck.isDebugEnabled())
		{
			log.finer("*** getting TM");
		}
		BitronixTransactionManager tm = TransactionManagerServices.getTransactionManager();

		XAPool pool1 = getPool(poolingDataSource1);

		if (LogDebugCheck.isDebugEnabled())
		{
			log.finer("*** before begin");
		}
		tm.begin();
		if (LogDebugCheck.isDebugEnabled())
		{
			log.finer("*** after begin");
		}

		assertEquals(POOL_SIZE, pool1.inPoolSize());

		if (LogDebugCheck.isDebugEnabled())
		{
			log.finer("*** getting connection from DS1");
		}
		Connection connection1 = poolingDataSource1.getConnection();
		connection1.createStatement();

		assertEquals(POOL_SIZE - 1, pool1.inPoolSize());

		if (LogDebugCheck.isDebugEnabled())
		{
			log.finer("*** closing connection 1");
		}
		connection1.close();

		assertEquals(POOL_SIZE - 1, pool1.inPoolSize());

		if (LogDebugCheck.isDebugEnabled())
		{
			log.finer("*** getting again connection from DS1");
		}
		connection1 = poolingDataSource1.getConnection();
		connection1.createStatement();

		assertEquals(POOL_SIZE - 1, pool1.inPoolSize());

		if (LogDebugCheck.isDebugEnabled())
		{
			log.finer("*** closing again connection 1");
		}
		connection1.close();

		assertEquals(POOL_SIZE - 1, pool1.inPoolSize());

		if (LogDebugCheck.isDebugEnabled())
		{
			log.finer("*** committing");
		}
		tm.commit();
		if (LogDebugCheck.isDebugEnabled())
		{
			log.finer("*** TX is done");
		}

		assertEquals(POOL_SIZE, pool1.inPoolSize());

		// check flow
		List orderedEvents = EventRecorder.getOrderedEvents();
		log.info(EventRecorder.dumpToString());

		assertEquals(11, orderedEvents.size());
		int i = 0;
		assertEquals(Status.STATUS_ACTIVE, ((JournalLogEvent) orderedEvents.get(i++)).getStatus());

		assertEquals(DATASOURCE1_NAME, ((ConnectionDequeuedEvent) orderedEvents.get(i++)).getPooledConnectionImpl()
		                                                                                 .getPoolingDataSource()
		                                                                                 .getUniqueName());
		assertEquals(XAResource.TMNOFLAGS, ((XAResourceStartEvent) orderedEvents.get(i++)).getFlag());

		assertEquals(DATASOURCE1_NAME, ((ConnectionDequeuedEvent) orderedEvents.get(i++)).getPooledConnectionImpl()
		                                                                                 .getPoolingDataSource()
		                                                                                 .getUniqueName());

		assertEquals(XAResource.TMSUCCESS, ((XAResourceEndEvent) orderedEvents.get(i++)).getFlag());
		assertEquals(Status.STATUS_PREPARING, ((JournalLogEvent) orderedEvents.get(i++)).getStatus());
		assertEquals(Status.STATUS_PREPARED, ((JournalLogEvent) orderedEvents.get(i++)).getStatus());
		assertEquals(Status.STATUS_COMMITTING, ((JournalLogEvent) orderedEvents.get(i++)).getStatus());
		assertEquals(true, ((XAResourceCommitEvent) orderedEvents.get(i++)).isOnePhase());
		assertEquals(Status.STATUS_COMMITTED, ((JournalLogEvent) orderedEvents.get(i++)).getStatus());
		assertEquals(DATASOURCE1_NAME, ((ConnectionQueuedEvent) orderedEvents.get(i++)).getPooledConnectionImpl()
		                                                                               .getPoolingDataSource()
		                                                                               .getUniqueName());
	}

	/**
	 * Method testDeferredCannotReuse ...
	 *
	 * @throws Exception
	 * 		when
	 */
	public void testDeferredCannotReuse() throws Exception
	{
		if (LogDebugCheck.isDebugEnabled())
		{
			log.finer("*** getting TM");
		}
		BitronixTransactionManager tm = TransactionManagerServices.getTransactionManager();

		// Use DataSource2 because it does not have shared accessible connections
		XAPool pool2 = getPool(poolingDataSource2);

		if (LogDebugCheck.isDebugEnabled())
		{
			log.finer("*** before begin");
		}
		tm.begin();
		if (LogDebugCheck.isDebugEnabled())
		{
			log.finer("*** after begin");
		}

		assertEquals(POOL_SIZE, pool2.inPoolSize());

		if (LogDebugCheck.isDebugEnabled())
		{
			log.finer("*** getting connection 1 from DS1");
		}
		Connection connection1 = poolingDataSource2.getConnection();
		connection1.createStatement();

		assertEquals(POOL_SIZE - 1, pool2.inPoolSize());

		if (LogDebugCheck.isDebugEnabled())
		{
			log.finer("*** getting connection 2 from DS1");
		}
		Connection connection2 = poolingDataSource2.getConnection();
		connection2.createStatement();

		assertEquals(POOL_SIZE - 2, pool2.inPoolSize());

		if (LogDebugCheck.isDebugEnabled())
		{
			log.finer("*** closing connection 1");
		}
		connection1.close();

		assertEquals(POOL_SIZE - 2, pool2.inPoolSize());

		if (LogDebugCheck.isDebugEnabled())
		{
			log.finer("*** closing connection 2");
		}
		connection2.close();

		assertEquals(POOL_SIZE - 2, pool2.inPoolSize());

		if (LogDebugCheck.isDebugEnabled())
		{
			log.finer("*** committing");
		}
		tm.commit();
		if (LogDebugCheck.isDebugEnabled())
		{
			log.finer("*** TX is done");
		}

		assertEquals(POOL_SIZE, pool2.inPoolSize());

		// check flow
		List orderedEvents = EventRecorder.getOrderedEvents();
		log.info(EventRecorder.dumpToString());

		assertEquals(17, orderedEvents.size());
		int i = 0;
		assertEquals(Status.STATUS_ACTIVE, ((JournalLogEvent) orderedEvents.get(i++)).getStatus());
		assertEquals(DATASOURCE2_NAME, ((ConnectionDequeuedEvent) orderedEvents.get(i++)).getPooledConnectionImpl()
		                                                                                 .getPoolingDataSource()
		                                                                                 .getUniqueName());
		assertEquals(XAResource.TMNOFLAGS, ((XAResourceStartEvent) orderedEvents.get(i++)).getFlag());
		assertEquals(DATASOURCE2_NAME, ((ConnectionDequeuedEvent) orderedEvents.get(i++)).getPooledConnectionImpl()
		                                                                                 .getPoolingDataSource()
		                                                                                 .getUniqueName());
		assertEquals(XAResource.TMNOFLAGS, ((XAResourceStartEvent) orderedEvents.get(i++)).getFlag());
		assertEquals(XAResource.TMSUCCESS, ((XAResourceEndEvent) orderedEvents.get(i++)).getFlag());
		assertEquals(XAResource.TMSUCCESS, ((XAResourceEndEvent) orderedEvents.get(i++)).getFlag());
		assertEquals(Status.STATUS_PREPARING, ((JournalLogEvent) orderedEvents.get(i++)).getStatus());
		assertEquals(XAResource.XA_OK, ((XAResourcePrepareEvent) orderedEvents.get(i++)).getReturnCode());
		assertEquals(XAResource.XA_OK, ((XAResourcePrepareEvent) orderedEvents.get(i++)).getReturnCode());
		assertEquals(Status.STATUS_PREPARED, ((JournalLogEvent) orderedEvents.get(i++)).getStatus());
		assertEquals(Status.STATUS_COMMITTING, ((JournalLogEvent) orderedEvents.get(i++)).getStatus());
		assertEquals(false, ((XAResourceCommitEvent) orderedEvents.get(i++)).isOnePhase());
		assertEquals(false, ((XAResourceCommitEvent) orderedEvents.get(i++)).isOnePhase());
		assertEquals(Status.STATUS_COMMITTED, ((JournalLogEvent) orderedEvents.get(i++)).getStatus());
		assertEquals(DATASOURCE2_NAME, ((ConnectionQueuedEvent) orderedEvents.get(i++)).getPooledConnectionImpl()
		                                                                               .getPoolingDataSource()
		                                                                               .getUniqueName());
		assertEquals(DATASOURCE2_NAME, ((ConnectionQueuedEvent) orderedEvents.get(i++)).getPooledConnectionImpl()
		                                                                               .getPoolingDataSource()
		                                                                               .getUniqueName());
	}

	/**
	 * Method testConnectionCloseInDifferentContext ...
	 *
	 * @throws Exception
	 * 		when
	 */
	public void testConnectionCloseInDifferentContext() throws Exception
	{
		if (LogDebugCheck.isDebugEnabled())
		{
			log.finer("*** getting TM");
		}
		BitronixTransactionManager tm = TransactionManagerServices.getTransactionManager();
		if (LogDebugCheck.isDebugEnabled())
		{
			log.finer("*** beginning");
		}
		tm.begin();

		if (LogDebugCheck.isDebugEnabled())
		{
			log.finer("*** getting connection from DS1");
		}
		Connection connection1 = poolingDataSource1.getConnection();
		connection1.createStatement();

		if (LogDebugCheck.isDebugEnabled())
		{
			log.finer("*** getting connection from DS2");
		}
		Connection connection2 = poolingDataSource2.getConnection();
		connection2.createStatement();

		if (LogDebugCheck.isDebugEnabled())
		{
			log.finer("*** closing connection 2");
		}
		connection2.close();


		if (LogDebugCheck.isDebugEnabled())
		{
			log.finer("*** committing");
		}
		tm.commit();
		if (LogDebugCheck.isDebugEnabled())
		{
			log.finer("*** TX is done");
		}

		if (LogDebugCheck.isDebugEnabled())
		{
			log.finer("*** beginning");
		}
		tm.begin();


		if (LogDebugCheck.isDebugEnabled())
		{
			log.finer("*** closing connection 1");
		}
		connection1.close();

		if (LogDebugCheck.isDebugEnabled())
		{
			log.finer("*** committing");
		}
		tm.commit();
		if (LogDebugCheck.isDebugEnabled())
		{
			log.finer("*** TX is done");
		}

		// check flow
		List orderedEvents = EventRecorder.getOrderedEvents();
		log.info(EventRecorder.dumpToString());

		assertEquals(22, orderedEvents.size());
		int i = 0;
		assertEquals(Status.STATUS_ACTIVE, ((JournalLogEvent) orderedEvents.get(i++)).getStatus());
		assertEquals(DATASOURCE1_NAME, ((ConnectionDequeuedEvent) orderedEvents.get(i++)).getPooledConnectionImpl()
		                                                                                 .getPoolingDataSource()
		                                                                                 .getUniqueName());
		assertEquals(XAResource.TMNOFLAGS, ((XAResourceStartEvent) orderedEvents.get(i++)).getFlag());
		assertEquals(DATASOURCE2_NAME, ((ConnectionDequeuedEvent) orderedEvents.get(i++)).getPooledConnectionImpl()
		                                                                                 .getPoolingDataSource()
		                                                                                 .getUniqueName());
		assertEquals(XAResource.TMNOFLAGS, ((XAResourceStartEvent) orderedEvents.get(i++)).getFlag());

		assertEquals(XAResource.TMSUCCESS, ((XAResourceEndEvent) orderedEvents.get(i++)).getFlag());
		assertEquals(XAResource.TMSUCCESS, ((XAResourceEndEvent) orderedEvents.get(i++)).getFlag());

		assertEquals(Status.STATUS_PREPARING, ((JournalLogEvent) orderedEvents.get(i++)).getStatus());
		assertEquals(XAResource.XA_OK, ((XAResourcePrepareEvent) orderedEvents.get(i++)).getReturnCode());
		assertEquals(XAResource.XA_OK, ((XAResourcePrepareEvent) orderedEvents.get(i++)).getReturnCode());
		assertEquals(Status.STATUS_PREPARED, ((JournalLogEvent) orderedEvents.get(i++)).getStatus());
		assertEquals(Status.STATUS_COMMITTING, ((JournalLogEvent) orderedEvents.get(i++)).getStatus());
		assertEquals(false, ((XAResourceCommitEvent) orderedEvents.get(i++)).isOnePhase());
		assertEquals(false, ((XAResourceCommitEvent) orderedEvents.get(i++)).isOnePhase());
		assertEquals(Status.STATUS_COMMITTED, ((JournalLogEvent) orderedEvents.get(i++)).getStatus());
		assertEquals(DATASOURCE2_NAME, ((ConnectionQueuedEvent) orderedEvents.get(i++)).getPooledConnectionImpl()
		                                                                               .getPoolingDataSource()
		                                                                               .getUniqueName());

		assertEquals(Status.STATUS_ACTIVE, ((JournalLogEvent) orderedEvents.get(i++)).getStatus());

		assertEquals(DATASOURCE1_NAME, ((ConnectionQueuedEvent) orderedEvents.get(i++)).getPooledConnectionImpl()
		                                                                               .getPoolingDataSource()
		                                                                               .getUniqueName());

		assertEquals(Status.STATUS_PREPARING, ((JournalLogEvent) orderedEvents.get(i++)).getStatus());
		assertEquals(Status.STATUS_PREPARED, ((JournalLogEvent) orderedEvents.get(i++)).getStatus());
		assertEquals(Status.STATUS_COMMITTING, ((JournalLogEvent) orderedEvents.get(i++)).getStatus());
		assertEquals(Status.STATUS_COMMITTED, ((JournalLogEvent) orderedEvents.get(i++)).getStatus());
	}


	/**
	 * Method testClosingSuspendedConnectionsInDifferentContext ...
	 *
	 * @throws Exception
	 * 		when
	 */
	public void testClosingSuspendedConnectionsInDifferentContext() throws Exception
	{
		if (LogDebugCheck.isDebugEnabled())
		{
			log.finer("*** getting TM");
		}
		BitronixTransactionManager tm = TransactionManagerServices.getTransactionManager();

		if (LogDebugCheck.isDebugEnabled())
		{
			log.finer("*** before begin");
		}
		tm.begin();

		XAPool pool1 = getPool(poolingDataSource1);

		assertEquals(POOL_SIZE, pool1.inPoolSize());

		if (LogDebugCheck.isDebugEnabled())
		{
			log.finer("*** getting connection from DS1");
		}
		Connection connection1 = poolingDataSource1.getConnection();
		connection1.createStatement();

		assertEquals(POOL_SIZE - 1, pool1.inPoolSize());

		if (LogDebugCheck.isDebugEnabled())
		{
			log.finer("*** suspending");
		}
		Transaction t1 = tm.suspend();

		assertEquals(POOL_SIZE - 1, pool1.inPoolSize());

		if (LogDebugCheck.isDebugEnabled())
		{
			log.finer("*** starting 2nd tx");
		}
		tm.begin();

		assertEquals(POOL_SIZE - 1, pool1.inPoolSize());

		if (LogDebugCheck.isDebugEnabled())
		{
			log.finer("*** closing connection 1 too eagerly within another context");
		}
		try
		{
			// TODO: the ConnectionHandler tries to 'veto' the connection close here like the old pool did.
			// Instead, close the resource immediately or defer its release.
			connection1.close();
			fail("successfully closed a connection participating in a global transaction, this should never be allowed");
		}
		catch (SQLException ex)
		{
			assertEquals("cannot close a resource when its XAResource is taking part in an unfinished global transaction", ex.getCause()
			                                                                                                                 .getMessage());
		}
		assertEquals(POOL_SIZE - 1, pool1.inPoolSize());

		if (LogDebugCheck.isDebugEnabled())
		{
			log.finer("*** committing 2nd tx");
		}
		tm.commit();

		assertEquals(POOL_SIZE - 1, pool1.inPoolSize());

		if (LogDebugCheck.isDebugEnabled())
		{
			log.finer("*** resuming");
		}
		tm.resume(t1);

		assertEquals(POOL_SIZE - 1, pool1.inPoolSize());

		if (LogDebugCheck.isDebugEnabled())
		{
			log.finer("*** committing");
		}
		tm.commit();
		if (LogDebugCheck.isDebugEnabled())
		{
			log.finer("*** TX is done");
		}

		if (LogDebugCheck.isDebugEnabled())
		{
			log.finer("*** closing connection 1");
		}
		connection1.close();

		assertEquals(POOL_SIZE, pool1.inPoolSize());

		// check flow
		List<? extends Event> orderedEvents = EventRecorder.getOrderedEvents();
		log.info(EventRecorder.dumpToString());

		assertEquals(18, orderedEvents.size());
		int i = 0;
		assertEquals(Status.STATUS_ACTIVE, ((JournalLogEvent) orderedEvents.get(i++)).getStatus());
		assertEquals(DATASOURCE1_NAME, ((ConnectionDequeuedEvent) orderedEvents.get(i++)).getPooledConnectionImpl()
		                                                                                 .getPoolingDataSource()
		                                                                                 .getUniqueName());
		assertEquals(XAResource.TMNOFLAGS, ((XAResourceStartEvent) orderedEvents.get(i++)).getFlag());
		assertEquals(XAResource.TMSUCCESS, ((XAResourceEndEvent) orderedEvents.get(i++)).getFlag());

		assertEquals(Status.STATUS_ACTIVE, ((JournalLogEvent) orderedEvents.get(i++)).getStatus());
		assertEquals(Status.STATUS_PREPARING, ((JournalLogEvent) orderedEvents.get(i++)).getStatus());
		assertEquals(Status.STATUS_PREPARED, ((JournalLogEvent) orderedEvents.get(i++)).getStatus());
		assertEquals(Status.STATUS_COMMITTING, ((JournalLogEvent) orderedEvents.get(i++)).getStatus());
		assertEquals(Status.STATUS_COMMITTED, ((JournalLogEvent) orderedEvents.get(i++)).getStatus());

		assertEquals(true, ((XAResourceIsSameRmEvent) orderedEvents.get(i++)).isSameRm());
		assertEquals(XAResource.TMJOIN, ((XAResourceStartEvent) orderedEvents.get(i++)).getFlag());
		assertEquals(XAResource.TMSUCCESS, ((XAResourceEndEvent) orderedEvents.get(i++)).getFlag());

		assertEquals(Status.STATUS_PREPARING, ((JournalLogEvent) orderedEvents.get(i++)).getStatus());
		assertEquals(Status.STATUS_PREPARED, ((JournalLogEvent) orderedEvents.get(i++)).getStatus());
		assertEquals(Status.STATUS_COMMITTING, ((JournalLogEvent) orderedEvents.get(i++)).getStatus());
		assertEquals(true, ((XAResourceCommitEvent) orderedEvents.get(i++)).isOnePhase());
		assertEquals(Status.STATUS_COMMITTED, ((JournalLogEvent) orderedEvents.get(i++)).getStatus());
		assertEquals(DATASOURCE1_NAME, ((ConnectionQueuedEvent) orderedEvents.get(i++)).getPooledConnectionImpl()
		                                                                               .getPoolingDataSource()
		                                                                               .getUniqueName());
	}

}
